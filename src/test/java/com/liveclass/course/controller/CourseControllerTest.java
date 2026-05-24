package com.liveclass.course.controller;

import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.dto.request.CourseCreateRequest;
import com.liveclass.course.dto.request.CourseStatusUpdateRequest;
import com.liveclass.course.dto.response.CourseResponse;
import com.liveclass.course.dto.response.CourseSummaryResponse;
import com.liveclass.common.error.info.EnrollmentErrorInfo;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.dto.response.StudentResponse;
import com.liveclass.support.ControllerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CourseController 슬라이스 테스트")
class CourseControllerTest extends ControllerTestSupport {

    @Test
    @DisplayName("유효한 강의 등록 요청이면 201 Created를 반환한다")
    void returns201Created_whenValidRequest() throws Exception {
        // given
        CourseCreateRequest request = createRequest();
        CourseResponse response = new CourseResponse(
                1L, 100L, "Spring Boot 마스터", "Spring Boot 실전",
                99_000L, 30, 0,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31),
                CourseStatus.DRAFT
        );
        given(courseService.create(eq(100L), any(CourseCreateRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/courses")
                        .header("X-Member-Id", 100L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/courses/1"))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.creatorId").value(100L))
                .andExpect(jsonPath("$.title").value("Spring Boot 마스터"))
                .andExpect(jsonPath("$.price").value(99_000L))
                .andExpect(jsonPath("$.capacity").value(30))
                .andExpect(jsonPath("$.enrollCount").value(0))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("X-Member-Id 헤더가 없으면 400을 반환한다")
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
                        .header("X-Member-Id", 100L)
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
                        .header("X-Member-Id", 100L)
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
                        .header("X-Member-Id", 100L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("강의 상태 변경 요청이 유효하면 204 No Content를 반환한다")
    void returns204_whenValidStatusUpdateRequest() throws Exception {
        // given
        CourseStatusUpdateRequest request = new CourseStatusUpdateRequest(CourseStatus.OPEN);

        // when & then
        mockMvc.perform(patch("/api/courses/{courseId}/status", 1L)
                        .header("X-Member-Id", 100L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("강의 상태 변경 요청에 status가 없으면 400을 반환한다")
    void returns400_whenStatusFieldMissing() throws Exception {
        // given
        String emptyBody = "{}";

        // when & then
        mockMvc.perform(patch("/api/courses/{courseId}/status", 1L)
                        .header("X-Member-Id", 100L)
                        .contentType("application/json")
                        .content(emptyBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("강의 상태 변경 요청에 정의되지 않은 status 값을 보내면 400과 허용되는 값 안내를 반환한다")
    void returns400_withAllowedValues_whenInvalidEnumValueForStatus() throws Exception {
        // given
        String invalidRequest = "{\"status\": \"XXX\"}";

        // when & then
        mockMvc.perform(patch("/api/courses/{courseId}/status", 1L)
                        .header("X-Member-Id", 100L)
                        .contentType("application/json")
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.status").exists())
                .andExpect(jsonPath("$.details.status").value("'XXX' 값은 유효하지 않습니다. 허용되는 값: [DRAFT, OPEN, CLOSED]"));
    }

    @Test
    @DisplayName("강의 상태 변경 요청에 X-Member-Id 헤더가 없으면 400을 반환한다")
    void returns400_whenUserIdHeaderMissingForStatusUpdate() throws Exception {
        // given
        CourseStatusUpdateRequest request = new CourseStatusUpdateRequest(CourseStatus.OPEN);

        // when & then
        mockMvc.perform(patch("/api/courses/{courseId}/status", 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 404를 반환한다")
    void returns404_whenCourseNotFoundOnStatusUpdate() throws Exception {
        // given
        CourseStatusUpdateRequest request = new CourseStatusUpdateRequest(CourseStatus.OPEN);
        willThrow(new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND))
                .given(courseService).updateStatus(eq(999L), any(), any());

        // when & then
        mockMvc.perform(patch("/api/courses/{courseId}/status", 999L)
                        .header("X-Member-Id", 100L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_001"));
    }

    @Test
    @DisplayName("크리에이터가 아니면 403을 반환한다")
    void returns403_whenNotCreator() throws Exception {
        // given
        CourseStatusUpdateRequest request = new CourseStatusUpdateRequest(CourseStatus.OPEN);
        willThrow(new BusinessException(CourseErrorInfo.NOT_COURSE_CREATOR))
                .given(courseService).updateStatus(eq(1L), eq(999L), any());

        // when & then
        mockMvc.perform(patch("/api/courses/{courseId}/status", 1L)
                        .header("X-Member-Id", 999L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_003"));
    }

    @Test
    @DisplayName("강의 목록 조회 시 200과 요약 배열을 반환한다")
    void returns200WithSummaryList_whenListWithoutFilter() throws Exception {
        // given
        List<CourseSummaryResponse> summaries = List.of(
                new CourseSummaryResponse(1L, "강의1", 1000L, 10, 0,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31), CourseStatus.OPEN),
                new CourseSummaryResponse(2L, "강의2", 2000L, 20, 5,
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30), CourseStatus.DRAFT)
        );
        given(courseService.getList(null)).willReturn(summaries);

        // when & then
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[1].id").value(2L));
    }

    @Test
    @DisplayName("status 쿼리 파라미터가 있으면 service에 해당 status가 전달된다")
    void delegatesStatusToService_whenStatusParamGiven() throws Exception {
        // given
        given(courseService.getList(CourseStatus.OPEN)).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/courses").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("강의 상세 조회 시 200과 강의 정보를 반환한다")
    void returns200WithCourseDetail_whenCourseExists() throws Exception {
        // given
        CourseResponse response = new CourseResponse(
                1L, 100L, "Spring Boot 마스터", "Spring Boot 실전",
                99_000L, 30, 5,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31),
                CourseStatus.OPEN
        );
        given(courseService.getDetail(1L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/courses/{courseId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.creatorId").value(100L))
                .andExpect(jsonPath("$.title").value("Spring Boot 마스터"))
                .andExpect(jsonPath("$.description").value("Spring Boot 실전"))
                .andExpect(jsonPath("$.enrollCount").value(5))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 상세 조회 시 COURSE_001로 404를 반환한다")
    void returns404_whenCourseNotFoundOnDetail() throws Exception {
        // given
        willThrow(new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND))
                .given(courseService).getDetail(999L);

        // when & then
        mockMvc.perform(get("/api/courses/{courseId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_001"));
    }

    @Test
    @DisplayName("강의별 수강생 목록 조회 시 200과 학생 배열을 반환한다")
    void returns200WithStudentList_whenGetStudents() throws Exception {
        // given
        List<StudentResponse> students = List.of(
                new StudentResponse(200L, "홍길동", EnrollmentStatus.CONFIRMED,
                        LocalDateTime.of(2026, 5, 19, 10, 0), LocalDateTime.of(2026, 5, 18, 9, 0)),
                new StudentResponse(201L, "이몽룡", EnrollmentStatus.PENDING,
                        null, LocalDateTime.of(2026, 5, 20, 9, 0))
        );
        given(enrollmentService.getStudentsByCourse(1L, 100L)).willReturn(students);

        // when & then
        mockMvc.perform(get("/api/courses/{courseId}/students", 1L)
                        .header("X-Member-Id", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].memberId").value(200L))
                .andExpect(jsonPath("$[0].name").value("홍길동"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[1].memberId").value(201L))
                .andExpect(jsonPath("$[1].name").value("이몽룡"));
    }

    @Test
    @DisplayName("강의별 수강생 목록 조회 시 X-Member-Id 헤더가 없으면 400을 반환한다")
    void returns400_whenMemberIdHeaderMissingOnGetStudents() throws Exception {
        // when & then
        mockMvc.perform(get("/api/courses/{courseId}/students", 1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("강의별 수강생 목록 조회 시 강의가 없으면 COURSE_001로 404를 반환한다")
    void returns404_whenCourseNotFoundOnGetStudents() throws Exception {
        // given
        willThrow(new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND))
                .given(enrollmentService).getStudentsByCourse(999L, 100L);

        // when & then
        mockMvc.perform(get("/api/courses/{courseId}/students", 999L)
                        .header("X-Member-Id", 100L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_001"));
    }

    @Test
    @DisplayName("강의별 수강생 목록 조회 시 크리에이터가 아니면 COURSE_003으로 403을 반환한다")
    void returns403_whenNotCreatorOnGetStudents() throws Exception {
        // given
        willThrow(new BusinessException(CourseErrorInfo.NOT_COURSE_CREATOR))
                .given(enrollmentService).getStudentsByCourse(1L, 999L);

        // when & then
        mockMvc.perform(get("/api/courses/{courseId}/students", 1L)
                        .header("X-Member-Id", 999L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_003"));
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
