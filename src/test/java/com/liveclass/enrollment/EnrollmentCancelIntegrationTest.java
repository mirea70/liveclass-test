package com.liveclass.enrollment;

import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.outbox.domain.entity.OutboxEventStatus;
import com.liveclass.outbox.domain.entity.OutboxEventType;
import com.liveclass.outbox.repository.OutboxEventRepository;
import com.liveclass.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("수강 취소 통합 테스트")
class EnrollmentCancelIntegrationTest extends IntegrationTestSupport {

    private static final Long CREATOR_ID = 100L;
    private static final Long MEMBER_ID = 200L;
    private static final Long OTHER_MEMBER_ID = 999L;
    private static final Duration CANCELLATION_WINDOW = Duration.ofDays(7);

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("PENDING 신청을 취소하면 카운터가 즉시 1 감소하고 ENROLLMENT_CANCELLED outbox 이벤트가 발행된다")
    void releasesSeatAndPublishesEvent_whenPendingCancelled() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(30, 1);
        Enrollment pending = enrollmentRepository.save(Enrollment.createNew(courseId, MEMBER_ID));

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", pending.getId())
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 카운터는 즉시 release되어 자리 가용성이 즉시 일관성 있게 반영된다
        assertThat(courseEnrollCountRepository.findById(courseId).orElseThrow().getCount()).isZero();

        // 대기자 승격은 비동기 처리 → ENROLLMENT_CANCELLED 이벤트가 PENDING 상태로 발행됨
        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getType()).isEqualTo(OutboxEventType.ENROLLMENT_CANCELLED);
                    assertThat(event.getDomainId()).isEqualTo(courseId);
                    assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
                    assertThat(event.getRetryCount()).isZero();
                });
    }

    @Test
    @DisplayName("CONFIRMED 신청이 7일 이내라면 취소가 허용된다")
    void cancelsConfirmed_whenWithinWindow() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(30, 1);
        Enrollment enrollment = Enrollment.createNew(courseId, MEMBER_ID);
        enrollment.confirm(LocalDateTime.now().minusDays(3));
        Enrollment saved = enrollmentRepository.save(enrollment);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", saved.getId())
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("CONFIRMED 신청이 7일을 초과했으면 ENROLLMENT_006으로 400을 반환한다")
    void returns400_whenCancellationWindowExpired() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(30, 1);
        Enrollment enrollment = Enrollment.createNew(courseId, MEMBER_ID);
        enrollment.confirm(LocalDateTime.now().minusDays(8));
        Enrollment saved = enrollmentRepository.save(enrollment);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", saved.getId())
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_006"));
    }

    @Test
    @DisplayName("신청 본인이 아니면 ENROLLMENT_004로 403을 반환한다")
    void returns403_whenNotOwner() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(30, 1);
        Enrollment enrollment = enrollmentRepository.save(Enrollment.createNew(courseId, MEMBER_ID));

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", enrollment.getId())
                        .header("X-Member-Id", OTHER_MEMBER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_004"));
    }

    @Test
    @DisplayName("이미 CANCELLED 상태이면 ENROLLMENT_007로 409를 반환한다")
    void returns409_whenAlreadyCancelled() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(30, 0);
        Enrollment enrollment = Enrollment.createNew(courseId, MEMBER_ID);
        enrollment.cancel(LocalDateTime.now(), CANCELLATION_WINDOW);
        Enrollment saved = enrollmentRepository.save(enrollment);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", saved.getId())
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_007"));
    }

    @Test
    @DisplayName("신청이 존재하지 않으면 ENROLLMENT_003으로 404를 반환한다")
    void returns404_whenEnrollmentNotFound() throws Exception {
        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", 9999L)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_003"));
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
        return saved.getId();
    }
}
