package com.liveclass.enrollment;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("내 수강 신청 목록 조회 통합 테스트")
class MyEnrollmentsIntegrationTest extends IntegrationTestSupport {

    private static final Long CREATOR_ID = 100L;
    private static final Long MEMBER_ID = 200L;
    private static final Long OTHER_MEMBER_ID = 999L;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Test
    @DisplayName("본인 신청 2건이 있으면 200과 페이지 응답으로 본인 것만 반환된다")
    void returnsOwnEnrollmentsOnly() throws Exception {
        // given
        Long courseA = saveOpenCourse("강의 A", 10_000L);
        Long courseB = saveOpenCourse("강의 B", 20_000L);
        enrollmentRepository.save(Enrollment.createNew(courseA, MEMBER_ID));
        enrollmentRepository.save(Enrollment.createNew(courseB, MEMBER_ID));
        enrollmentRepository.save(Enrollment.createNew(courseA, OTHER_MEMBER_ID));

        // when & then
        mockMvc.perform(get("/api/enrollments/me")
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("page/size 쿼리로 페이지네이션이 동작한다")
    void paginates_whenPageSizeGiven() throws Exception {
        // given
        for (int i = 0; i < 5; i++) {
            Long courseId = saveOpenCourse("강의 " + i, 10_000L);
            enrollmentRepository.save(Enrollment.createNew(courseId, MEMBER_ID));
        }

        // when & then
        mockMvc.perform(get("/api/enrollments/me")
                        .header("X-Member-Id", MEMBER_ID)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    private Long saveOpenCourse(String title, long price) {
        Course course = Course.createNew(
                CREATOR_ID, title, "desc",
                price, 30,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
        course.open();
        Course saved = courseRepository.save(course);
        courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
        return saved.getId();
    }
}
