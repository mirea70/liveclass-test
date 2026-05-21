package com.liveclass.course;

import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.dto.request.CourseCreateRequest;
import com.liveclass.course.dto.response.CourseCreateResponse;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("강의 등록 통합 테스트")
class CourseCreateIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Test
    @DisplayName("유효한 강의 등록 요청 시 Course와 CourseEnrollCount가 DB에 저장된다")
    void savesCourseAndEnrollCountToDb_whenValidRequest() throws Exception {
        // given
        CourseCreateRequest request = new CourseCreateRequest(
                "Spring Boot 마스터",
                "Spring Boot 실전 강의",
                99_000L,
                30,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 8, 31)
        );

        // when
        MvcResult result = mockMvc.perform(post("/api/courses")
                        .header("X-User-Id", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.count").value(0))
                .andReturn();

        // then
        CourseCreateResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), CourseCreateResponse.class);
        Long courseId = response.id();

        Course saved = courseRepository.findById(courseId).orElseThrow();
        assertThat(saved.getCreatorId()).isEqualTo(100L);
        assertThat(saved.getTitle()).isEqualTo("Spring Boot 마스터");
        assertThat(saved.getDescription()).isEqualTo("Spring Boot 실전 강의");
        assertThat(saved.getPrice().getAmount()).isEqualTo(99_000L);
        assertThat(saved.getCapacity()).isEqualTo(30);
        assertThat(saved.getPeriod().getStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(saved.getPeriod().getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(saved.getStatus()).isEqualTo(CourseStatus.DRAFT);

        CourseEnrollCount count = courseEnrollCountRepository.findById(courseId).orElseThrow();
        assertThat(count.getCount()).isZero();
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 COURSE_005로 400을 반환한다")
    void returns400WithCoursePeriodInvalid_whenStartDateIsAfterEndDate() throws Exception {
        // given
        CourseCreateRequest request = new CourseCreateRequest(
                "Spring Boot 마스터",
                "Spring Boot 실전 강의",
                99_000L,
                30,
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 6, 1)
        );
        long beforeCourseCount = courseRepository.count();

        // when & then
        mockMvc.perform(post("/api/courses")
                        .header("X-User-Id", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_005"));

        assertThat(courseRepository.count()).isEqualTo(beforeCourseCount);
    }

    @Test
    @DisplayName("필수 필드가 누락되면 400을 반환하고 어떤 데이터도 저장되지 않는다")
    void doesNotSaveAndReturns400_whenRequestIsInvalid() throws Exception {
        // given
        CourseCreateRequest invalidRequest = new CourseCreateRequest(
                "",                                  // title 누락
                "desc",
                99_000L,
                30,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 8, 31)
        );
        long beforeCourseCount = courseRepository.count();
        long beforeEnrollCountTotal = courseEnrollCountRepository.count();

        // when & then
        mockMvc.perform(post("/api/courses")
                        .header("X-User-Id", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertThat(courseRepository.count()).isEqualTo(beforeCourseCount);
        assertThat(courseEnrollCountRepository.count()).isEqualTo(beforeEnrollCountTotal);
    }
}
