package com.liveclass.enrollment;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

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
    private static final Long USER_ID = 200L;
    private static final Long OTHER_USER_ID = 999L;
    private static final Duration CANCELLATION_WINDOW = Duration.ofDays(7);

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Test
    @DisplayName("WAITING 신청을 본인이 취소하면 200과 CANCELLED 응답을 반환하고 count는 변경되지 않는다")
    void cancelsWaiting_andCountUnchanged() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(30, 5);
        Enrollment waiting = enrollmentRepository.save(Enrollment.createWaiting(courseId, USER_ID));

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", waiting.getId())
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Enrollment reloaded = enrollmentRepository.findById(waiting.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(courseEnrollCountRepository.findById(courseId).orElseThrow().getCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("PENDING 신청을 취소하고 대기자가 없으면 count가 1 감소한다")
    void decreasesCount_whenPendingCancelledAndNoWaiting() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(30, 1);
        Enrollment pending = enrollmentRepository.save(Enrollment.createPending(courseId, USER_ID));

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", pending.getId())
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk());

        assertThat(courseEnrollCountRepository.findById(courseId).orElseThrow().getCount()).isZero();
    }

    @Test
    @DisplayName("PENDING 신청을 취소하고 대기자가 있으면 가장 오래된 WAITING이 PENDING으로 승격되고 count는 유지된다")
    void promotesOldestWaiting_whenPendingCancelled() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(1, 1);
        Enrollment pending = enrollmentRepository.save(Enrollment.createPending(courseId, USER_ID));
        Enrollment olderWaiting = enrollmentRepository.save(Enrollment.createWaiting(courseId, 300L));
        ReflectionTestUtils.setField(olderWaiting, "createdAt", LocalDateTime.of(2026, 1, 1, 0, 0));
        enrollmentRepository.save(olderWaiting);
        Enrollment newerWaiting = enrollmentRepository.save(Enrollment.createWaiting(courseId, 400L));
        ReflectionTestUtils.setField(newerWaiting, "createdAt", LocalDateTime.of(2026, 2, 1, 0, 0));
        enrollmentRepository.save(newerWaiting);

        // when
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", pending.getId())
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk());

        // then
        Enrollment reloadedOlder = enrollmentRepository.findById(olderWaiting.getId()).orElseThrow();
        Enrollment reloadedNewer = enrollmentRepository.findById(newerWaiting.getId()).orElseThrow();
        assertThat(reloadedOlder.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(reloadedNewer.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
        assertThat(courseEnrollCountRepository.findById(courseId).orElseThrow().getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("CONFIRMED 신청이 7일 이내라면 취소가 허용된다")
    void cancelsConfirmed_whenWithinWindow() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(30, 1);
        Enrollment enrollment = Enrollment.createPending(courseId, USER_ID);
        enrollment.confirm(LocalDateTime.now().minusDays(3));
        Enrollment saved = enrollmentRepository.save(enrollment);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", saved.getId())
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("CONFIRMED 신청이 7일을 초과했으면 ENROLLMENT_006으로 400을 반환한다")
    void returns400_whenCancellationWindowExpired() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(30, 1);
        Enrollment enrollment = Enrollment.createPending(courseId, USER_ID);
        enrollment.confirm(LocalDateTime.now().minusDays(8));
        Enrollment saved = enrollmentRepository.save(enrollment);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", saved.getId())
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_006"));
    }

    @Test
    @DisplayName("신청 본인이 아니면 ENROLLMENT_004로 403을 반환한다")
    void returns403_whenNotOwner() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(30, 1);
        Enrollment enrollment = enrollmentRepository.save(Enrollment.createPending(courseId, USER_ID));

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", enrollment.getId())
                        .header("X-User-Id", OTHER_USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_004"));
    }

    @Test
    @DisplayName("이미 CANCELLED 상태이면 ENROLLMENT_007로 409를 반환한다")
    void returns409_whenAlreadyCancelled() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(30, 0);
        Enrollment enrollment = Enrollment.createPending(courseId, USER_ID);
        enrollment.cancel(LocalDateTime.now(), CANCELLATION_WINDOW);
        Enrollment saved = enrollmentRepository.save(enrollment);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", saved.getId())
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_007"));
    }

    @Test
    @DisplayName("신청이 존재하지 않으면 ENROLLMENT_003으로 404를 반환한다")
    void returns404_whenEnrollmentNotFound() throws Exception {
        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", 9999L)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_003"));
    }

    private Long saveOpenCourseWithCount(int capacity, int initialCount) {
        Course course = Course.createNew(
                CREATOR_ID,
                "Spring Boot 마스터",
                "Spring Boot 실전 강의",
                new Money(99_000L),
                capacity,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)));
        course.open();
        Course saved = courseRepository.save(course);
        CourseEnrollCount count = courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
        for (int i = 0; i < initialCount; i++) {
            count.tryReserve(capacity);
        }
        return saved.getId();
    }
}
