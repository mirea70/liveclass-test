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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("결제 확정 통합 테스트")
class EnrollmentConfirmIntegrationTest extends IntegrationTestSupport {

    private static final Long CREATOR_ID = 100L;
    private static final Long USER_ID = 200L;
    private static final Long OTHER_USER_ID = 999L;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Test
    @DisplayName("PENDING 신청에 본인이 confirm을 호출하면 200과 CONFIRMED 응답을 반환하고 DB에 confirmedAt이 기록된다")
    void confirmsPendingEnrollment_whenOwnerRequests() throws Exception {
        // given
        Long courseId = saveOpenCourse(30);
        Enrollment enrollment = enrollmentRepository.save(Enrollment.createPending(courseId, USER_ID));

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", enrollment.getId())
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(enrollment.getId()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());

        Enrollment reloaded = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(reloaded.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("신청 본인이 아니면 ENROLLMENT_004로 403을 반환하고 상태는 PENDING으로 유지된다")
    void returns403_whenNotOwner() throws Exception {
        // given
        Long courseId = saveOpenCourse(30);
        Enrollment enrollment = enrollmentRepository.save(Enrollment.createPending(courseId, USER_ID));

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", enrollment.getId())
                        .header("X-User-Id", OTHER_USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_004"));

        Enrollment reloaded = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    @DisplayName("WAITING 상태에서 confirm을 호출하면 ENROLLMENT_005로 409를 반환한다")
    void returns409_whenWaitingStatus() throws Exception {
        // given
        Long courseId = saveOpenCourse(30);
        Enrollment enrollment = enrollmentRepository.save(Enrollment.createWaiting(courseId, USER_ID));

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", enrollment.getId())
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_005"));
    }

    @Test
    @DisplayName("존재하지 않는 enrollmentId면 ENROLLMENT_003으로 404를 반환한다")
    void returns404_whenEnrollmentNotFound() throws Exception {
        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", 9999L)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_003"));
    }

    private Long saveOpenCourse(int capacity) {
        Course course = Course.createNew(
                CREATOR_ID,
                "Spring Boot 마스터",
                "Spring Boot 실전 강의",
                new Money(99_000L),
                capacity,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)));
        course.open();
        Course saved = courseRepository.save(course);
        courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
        return saved.getId();
    }
}
