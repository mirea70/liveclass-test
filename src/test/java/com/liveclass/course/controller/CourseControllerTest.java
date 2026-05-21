package com.liveclass.course.controller;

import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.dto.request.CourseCreateRequest;
import com.liveclass.course.dto.response.CourseCreateResponse;
import com.liveclass.support.ControllerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CourseController 슬라이스 테스트")
class CourseControllerTest extends ControllerTestSupport {

    @Test
    @DisplayName("유효한 강의 등록 요청이면 201 Created를 반환한다")
    void returns201Created_whenValidRequest() throws Exception {
        // given
        CourseCreateRequest request = createRequest();
        CourseCreateResponse response = new CourseCreateResponse(
                1L, 100L, "Spring Boot 마스터", "Spring Boot 실전",
                99_000L, 30, 0,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31),
                CourseStatus.DRAFT
        );
        given(courseCreateService.create(eq(100L), any(CourseCreateRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/courses")
                        .header("X-User-Id", 100L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.creatorId").value(100L))
                .andExpect(jsonPath("$.title").value("Spring Boot 마스터"))
                .andExpect(jsonPath("$.price").value(99_000L))
                .andExpect(jsonPath("$.capacity").value(30))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 400을 반환한다")
    void returns400_whenUserIdHeaderMissing() throws Exception {
        // given
        CourseCreateRequest request = createRequest();

        // when & then
        mockMvc.perform(post("/api/courses")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("title이 비어있으면 400을 반환한다")
    void returns400_whenTitleIsBlank() throws Exception {
        // given
        CourseCreateRequest request = new CourseCreateRequest(
                "", "desc", 99_000L, 30,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)
        );

        // when & then
        mockMvc.perform(post("/api/courses")
                        .header("X-User-Id", 100L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("capacity가 0이면 400을 반환한다")
    void returns400_whenCapacityIsZero() throws Exception {
        // given
        CourseCreateRequest request = new CourseCreateRequest(
                "title", "desc", 99_000L, 0,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)
        );

        // when & then
        mockMvc.perform(post("/api/courses")
                        .header("X-User-Id", 100L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("price가 음수면 400을 반환한다")
    void returns400_whenPriceIsNegative() throws Exception {
        // given
        CourseCreateRequest request = new CourseCreateRequest(
                "title", "desc", -1L, 30,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)
        );

        // when & then
        mockMvc.perform(post("/api/courses")
                        .header("X-User-Id", 100L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private CourseCreateRequest createRequest() {
        return new CourseCreateRequest(
                "Spring Boot 마스터",
                "Spring Boot 실전",
                99_000L,
                30,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 8, 31)
        );
    }
}
