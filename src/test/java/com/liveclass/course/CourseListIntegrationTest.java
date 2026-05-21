package com.liveclass.course;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.entity.CourseStatus;
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

@DisplayName("강의 목록 조회 통합 테스트")
class CourseListIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Test
    @DisplayName("status 없이 조회하면 모든 강의를 반환한다")
    void returnsAllCourses_whenNoStatusFilter() throws Exception {
        // given
        saveCourse(CourseStatus.DRAFT, "DRAFT 강의");
        saveCourse(CourseStatus.OPEN, "OPEN 강의");
        saveCourse(CourseStatus.CLOSED, "CLOSED 강의");

        // when & then
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("status=OPEN으로 조회하면 OPEN 강의만 반환한다")
    void returnsOnlyOpenCourses_whenStatusIsOpen() throws Exception {
        // given
        saveCourse(CourseStatus.DRAFT, "DRAFT 강의");
        saveCourse(CourseStatus.OPEN, "OPEN 강의 1");
        saveCourse(CourseStatus.OPEN, "OPEN 강의 2");
        saveCourse(CourseStatus.CLOSED, "CLOSED 강의");

        // when & then
        mockMvc.perform(get("/api/courses").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo("OPEN"))));
    }

    @Test
    @DisplayName("status=DRAFT로 조회하면 DRAFT 강의만 반환한다")
    void returnsOnlyDraftCourses_whenStatusIsDraft() throws Exception {
        // given
        saveCourse(CourseStatus.DRAFT, "DRAFT 강의");
        saveCourse(CourseStatus.OPEN, "OPEN 강의");

        // when & then
        mockMvc.perform(get("/api/courses").param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("DRAFT 강의"));
    }

    @Test
    @DisplayName("강의가 없으면 빈 배열을 반환한다")
    void returnsEmptyArray_whenNoCourses() throws Exception {
        // when & then
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private void saveCourse(CourseStatus targetStatus, String title) {
        Course course = Course.createNew(
                100L,
                title,
                "설명",
                new Money(99_000L),
                30,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31))
        );
        if (targetStatus == CourseStatus.OPEN || targetStatus == CourseStatus.CLOSED) {
            course.open();
        }
        if (targetStatus == CourseStatus.CLOSED) {
            course.close();
        }
        Course saved = courseRepository.save(course);
        courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
    }
}
