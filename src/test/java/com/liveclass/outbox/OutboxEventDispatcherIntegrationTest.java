package com.liveclass.outbox;

import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.outbox.domain.entity.OutboxEvent;
import com.liveclass.outbox.domain.entity.OutboxEventStatus;
import com.liveclass.outbox.domain.entity.OutboxEventType;
import com.liveclass.outbox.repository.OutboxEventRepository;
import com.liveclass.outbox.service.OutboxEventDispatcher;
import com.liveclass.reservation.repository.CourseReservationRepository;
import com.liveclass.waitlist.domain.entity.Waitlist;
import com.liveclass.waitlist.repository.WaitlistRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Outbox 이벤트 디스패치 통합 테스트")
class OutboxEventDispatcherIntegrationTest {

    private static final Long CREATOR_ID = 100L;
    private static final Long WAITER_MEMBER_ID = 300L;

    @Autowired
    private OutboxEventDispatcher outboxEventDispatcher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private WaitlistRepository waitlistRepository;

    @Autowired
    private CourseReservationRepository courseReservationRepository;

    @AfterEach
    void cleanUp() {
        outboxEventRepository.deleteAllInBatch();
        enrollmentRepository.deleteAllInBatch();
        waitlistRepository.deleteAllInBatch();
        courseReservationRepository.deleteAllInBatch();
        courseEnrollCountRepository.deleteAllInBatch();
        courseRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("대기자가 있는 강의의 ENROLLMENT_CANCELLED 이벤트가 처리되면 가장 오래된 대기자가 PENDING으로 승격되고 카운터가 +1된다")
    void promotesOldestWaiter_whenEventProcessed() {
        // given: 정원 1, 카운터는 release 직후라 0, 대기자 2명
        int capacity = 1;
        Long courseId = saveOpenCourseWithCount(capacity, 0);
        Waitlist oldest = waitlistRepository.save(Waitlist.createNew(courseId, WAITER_MEMBER_ID, 1));
        Waitlist next = waitlistRepository.save(Waitlist.createNew(courseId, 301L, 2));
        OutboxEvent event = outboxEventRepository.save(
                OutboxEvent.of(OutboxEventType.ENROLLMENT_CANCELLED, courseId));

        // when
        outboxEventDispatcher.dispatchPending(OutboxEventType.ENROLLMENT_CANCELLED);

        // then
        // 1) 카운터가 +1되어 capacity와 동일
        assertThat(courseEnrollCountRepository.findById(courseId).orElseThrow().getCount())
                .isEqualTo(capacity);
        // 2) 가장 오래된 대기자가 enrollment PENDING으로 등록됨
        List<Enrollment> pendings = enrollmentRepository.findAll().stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.PENDING)
                .toList();
        assertThat(pendings).singleElement().satisfies(e ->
                assertThat(e.getMemberId()).isEqualTo(WAITER_MEMBER_ID));
        // 3) 승격된 대기자는 waitlist에서 사라지고 뒷사람의 order_num이 한 칸씩 당겨짐
        assertThat(waitlistRepository.findById(oldest.getId())).isEmpty();
        assertThat(waitlistRepository.findById(next.getId()).orElseThrow().getOrderNum()).isEqualTo(1);
        // 4) 이벤트는 PROCESSED로 마킹됨
        assertThat(outboxEventRepository.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PROCESSED);
    }

    @Test
    @DisplayName("대기자가 없는 강의의 이벤트는 no-op로 처리되고 PROCESSED로 마킹된다")
    void marksProcessed_whenNoWaiter() {
        // given
        Long courseId = saveOpenCourseWithCount(1, 0);
        OutboxEvent event = outboxEventRepository.save(
                OutboxEvent.of(OutboxEventType.ENROLLMENT_CANCELLED, courseId));

        // when
        outboxEventDispatcher.dispatchPending(OutboxEventType.ENROLLMENT_CANCELLED);

        // then
        assertThat(courseEnrollCountRepository.findById(courseId).orElseThrow().getCount()).isZero();
        assertThat(outboxEventRepository.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PROCESSED);
    }

    @Test
    @DisplayName("그 사이 다른 신청자가 자리를 차지해 정원이 가득 차있으면 승격을 skip하고 PROCESSED로 마킹된다 (대기자는 유지)")
    void skipsPromotion_whenSeatAlreadyTakenByAnother() {
        // given: 카운터가 이미 가득 (다른 신청자가 자리를 차지한 상황)
        int capacity = 1;
        Long courseId = saveOpenCourseWithCount(capacity, capacity);
        Waitlist waiter = waitlistRepository.save(Waitlist.createNew(courseId, WAITER_MEMBER_ID, 1));
        OutboxEvent event = outboxEventRepository.save(
                OutboxEvent.of(OutboxEventType.ENROLLMENT_CANCELLED, courseId));

        // when
        outboxEventDispatcher.dispatchPending(OutboxEventType.ENROLLMENT_CANCELLED);

        // then
        // 카운터는 그대로
        assertThat(courseEnrollCountRepository.findById(courseId).orElseThrow().getCount())
                .isEqualTo(capacity);
        // 대기자는 waitlist에 유지 (다음 자리에서 다시 시도)
        assertThat(waitlistRepository.findById(waiter.getId())).isPresent();
        // 이벤트는 PROCESSED
        assertThat(outboxEventRepository.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PROCESSED);
    }

    @Test
    @DisplayName("처리 중 예외가 발생하면 retry_count가 +1되고 status는 PENDING으로 유지된다")
    void incrementsRetry_whenHandlerThrows() {
        // given: course/count 없는 상태에서 이벤트만 발행 → handler가 COURSE_NOT_FOUND로 예외
        Long missingCourseId = 9999L;
        OutboxEvent event = outboxEventRepository.save(
                OutboxEvent.of(OutboxEventType.ENROLLMENT_CANCELLED, missingCourseId));

        // when: 대기자도 없으니 promoteOldest는 no-op로 성공하므로, course가 없는 케이스는 만들기 어려움.
        // 대신 대기자가 있는데 course가 없는 케이스로 강제로 실패 유도
        Waitlist orphan = waitlistRepository.save(Waitlist.createNew(missingCourseId, 999L, 1));
        outboxEventDispatcher.dispatchPending(OutboxEventType.ENROLLMENT_CANCELLED);

        // then
        OutboxEvent reloaded = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getRetryCount()).isEqualTo(1);
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        // 대기자는 그대로 (rollback)
        assertThat(waitlistRepository.findById(orphan.getId())).isPresent();
    }

    @Test
    @DisplayName("이벤트가 이미 PROCESSED 상태면 다시 처리되지 않는다")
    void skipsAlreadyProcessedEvent() {
        // given
        Long courseId = saveOpenCourseWithCount(1, 0);
        Waitlist waiter = waitlistRepository.save(Waitlist.createNew(courseId, WAITER_MEMBER_ID, 1));
        OutboxEvent event = OutboxEvent.of(OutboxEventType.ENROLLMENT_CANCELLED, courseId);
        event.markProcessed(java.time.LocalDateTime.now().minusMinutes(1));
        outboxEventRepository.save(event);

        // when
        outboxEventDispatcher.dispatchPending(OutboxEventType.ENROLLMENT_CANCELLED);

        // then: 카운터·대기자 모두 변동 없음
        assertThat(courseEnrollCountRepository.findById(courseId).orElseThrow().getCount()).isZero();
        assertThat(waitlistRepository.findById(waiter.getId())).isPresent();
    }

    private Long saveOpenCourseWithCount(int capacity, int initialCount) {
        Course course = Course.createNew(
                CREATOR_ID,
                "Spring Boot 마스터",
                "Spring Boot 실전 강의",
                99_000L,
                capacity,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
        course.open();
        Course saved = courseRepository.save(course);
        CourseEnrollCount count = courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
        for (int i = 0; i < initialCount; i++) {
            count.tryReserve(capacity);
        }
        courseEnrollCountRepository.save(count);
        return saved.getId();
    }
}
