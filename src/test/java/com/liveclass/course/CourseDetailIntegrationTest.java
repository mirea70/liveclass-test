package com.liveclass.course;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("강의 상세 조회 통합 테스트")
class CourseDetailIntegrationTest extends IntegrationTestSupport {

    private static final Long CREATOR_ID = 100L;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Test
    @DisplayName("강의가 존재하면 모든 필드를 포함한 강의 정보를 200으로 반환한다")
    void returnsCourseDetail_whenCourseExists() throws Exception {
        // given
        Course course = saveCourseWithCount(3);

        // when & then
        mockMvc.perform(get("/api/courses/{courseId}", course.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(course.getId()))
                .andExpect(jsonPath("$.creatorId").value(CREATOR_ID))
                .andExpect(jsonPath("$.title").value("Spring Boot 마스터"))
                .andExpect(jsonPath("$.description").value("Spring Boot 실전 강의"))
                .andExpect(jsonPath("$.price").value(99_000L))
                .andExpect(jsonPath("$.capacity").value(30))
                .andExpect(jsonPath("$.enrollCount").value(3))
                .andExpect(jsonPath("$.startDate").value("2026-06-01"))
                .andExpect(jsonPath("$.endDate").value("2026-08-31"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 COURSE_001로 404를 반환한다")
    void returns404_whenCourseNotFound() throws Exception {
        // when & then
        mockMvc.perform(get("/api/courses/{courseId}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_001"));
    }

    private Course saveCourseWithCount(int initialCount) {
        Course course = Course.createNew(
                CREATOR_ID,
                "Spring Boot 마스터",
                "Spring Boot 실전 강의",
                99_000L,
                30,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 8, 31)
        );
        Course saved = courseRepository.save(course);
        CourseEnrollCount enrollCount = courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
        for (int i = 0; i < initialCount; i++) {
            enrollCount.tryReserve(course.getCapacity());
        }
        return saved;
    }
}
