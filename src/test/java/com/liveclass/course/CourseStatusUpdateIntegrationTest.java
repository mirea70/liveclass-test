package com.liveclass.course;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.course.dto.request.CourseStatusUpdateRequest;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("강의 상태 변경 통합 테스트")
class CourseStatusUpdateIntegrationTest extends IntegrationTestSupport {

    private static final Long CREATOR_ID = 100L;
    private static final Long OTHER_MEMBER_ID = 999L;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Test
    @DisplayName("DRAFT 강의에 OPEN 요청 시 204를 반환하고 DB의 상태가 OPEN으로 변경된다")
    void persistsOpenStatus_whenOpenRequestOnDraftCourse() throws Exception {
        // given
        Course course = saveDraftCourse();
        CourseStatusUpdateRequest request = new CourseStatusUpdateRequest(CourseStatus.OPEN);

        // when & then
        mockMvc.perform(patch("/api/courses/{courseId}/status", course.getId())
                        .header("X-Member-Id", CREATOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        Course reloaded = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CourseStatus.OPEN);
    }

    @Test
    @DisplayName("OPEN 강의에 CLOSED 요청 시 204를 반환하고 DB의 상태가 CLOSED로 변경된다")
    void persistsClosedStatus_whenCloseRequestOnOpenCourse() throws Exception {
        // given
        Course course = saveDraftCourse();
        course.open();
        courseRepository.saveAndFlush(course);
        CourseStatusUpdateRequest request = new CourseStatusUpdateRequest(CourseStatus.CLOSED);

        // when & then
        mockMvc.perform(patch("/api/courses/{courseId}/status", course.getId())
                        .header("X-Member-Id", CREATOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        Course reloaded = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CourseStatus.CLOSED);
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 COURSE_001로 404를 반환한다")
    void returns404_whenCourseNotFound() throws Exception {
        // given
        CourseStatusUpdateRequest request = new CourseStatusUpdateRequest(CourseStatus.OPEN);

        // when & then
        mockMvc.perform(patch("/api/courses/{courseId}/status", 9999L)
                        .header("X-Member-Id", CREATOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_001"));
    }

    @Test
    @DisplayName("크리에이터가 아닌 사용자의 요청은 COURSE_003으로 403을 반환한다")
    void returns403_whenRequesterIsNotCreator() throws Exception {
        // given
        Course course = saveDraftCourse();
        CourseStatusUpdateRequest request = new CourseStatusUpdateRequest(CourseStatus.OPEN);

        // when & then
        mockMvc.perform(patch("/api/courses/{courseId}/status", course.getId())
                        .header("X-Member-Id", OTHER_MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_003"));

        Course reloaded = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CourseStatus.DRAFT);
    }

    @Test
    @DisplayName("DRAFT 강의에 CLOSED 요청 시 COURSE_002로 409를 반환한다")
    void returns409_whenInvalidStatusTransition() throws Exception {
        // given
        Course course = saveDraftCourse();
        CourseStatusUpdateRequest request = new CourseStatusUpdateRequest(CourseStatus.CLOSED);

        // when & then
        mockMvc.perform(patch("/api/courses/{courseId}/status", course.getId())
                        .header("X-Member-Id", CREATOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_002"));
    }

    private Course saveDraftCourse() {
        Course course = Course.createNew(
                CREATOR_ID,
                "Spring Boot 마스터",
                "Spring Boot 실전",
                new Money(99_000L),
                30,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31))
        );
        Course saved = courseRepository.saveAndFlush(course);
        courseEnrollCountRepository.saveAndFlush(CourseEnrollCount.createNew(saved.getId()));
        return saved;
    }
}
