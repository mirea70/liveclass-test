package com.liveclass.enrollment.controller;

import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.common.error.info.EnrollmentErrorInfo;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.dto.request.EnrollmentCreateRequest;
import com.liveclass.enrollment.dto.response.EnrollmentResponse;
import com.liveclass.support.ControllerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("EnrollmentController 슬라이스 테스트")
class EnrollmentControllerTest extends ControllerTestSupport {

    private static final Long COURSE_ID = 1L;
    private static final Long MEMBER_ID = 200L;
    private static final Long ENROLLMENT_ID = 10L;

    @Test
    @DisplayName("유효한 신청 요청이면 201 Created와 Location 헤더, PENDING 응답을 반환한다")
    void returns201WithLocation_whenEnrollSucceeds() throws Exception {
        // given
        given(enrollmentService.enroll(COURSE_ID, MEMBER_ID)).willReturn(pendingResponse());

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/enrollments/" + ENROLLMENT_ID))
                .andExpect(jsonPath("$.id").value(ENROLLMENT_ID))
                .andExpect(jsonPath("$.courseId").value(COURSE_ID))
                .andExpect(jsonPath("$.memberId").value(MEMBER_ID))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.confirmedAt").isEmpty())
                .andExpect(jsonPath("$.cancelledAt").isEmpty());
    }

    @Test
    @DisplayName("정원이 차서 대기열로 등록되면 status가 WAITING으로 반환된다")
    void returnsWaitingStatus_whenQueued() throws Exception {
        // given
        EnrollmentResponse waiting = new EnrollmentResponse(
                ENROLLMENT_ID, COURSE_ID, MEMBER_ID, EnrollmentStatus.WAITING, null, null);
        given(enrollmentService.enroll(COURSE_ID, MEMBER_ID)).willReturn(waiting);

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    @DisplayName("X-Member-Id 헤더가 없으면 400을 반환한다")
    void returns400_whenMemberIdHeaderMissing() throws Exception {
        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("courseId가 null이면 400을 반환한다")
    void returns400_whenCourseIdMissing() throws Exception {
        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 COURSE_001로 404를 반환한다")
    void returns404_whenCourseNotFound() throws Exception {
        // given
        willThrow(new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND))
                .given(enrollmentService).enroll(COURSE_ID, MEMBER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_001"));
    }

    @Test
    @DisplayName("강의가 OPEN이 아니면 ENROLLMENT_001로 409를 반환한다")
    void returns409_whenCourseNotOpen() throws Exception {
        // given
        willThrow(new BusinessException(EnrollmentErrorInfo.COURSE_NOT_OPEN))
                .given(enrollmentService).enroll(COURSE_ID, MEMBER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_001"));
    }

    @Test
    @DisplayName("중복 신청이면 ENROLLMENT_002로 409를 반환한다")
    void returns409_whenDuplicateEnrollment() throws Exception {
        // given
        willThrow(new BusinessException(EnrollmentErrorInfo.DUPLICATE_ENROLLMENT))
                .given(enrollmentService).enroll(COURSE_ID, MEMBER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_002"));
    }

    @Test
    @DisplayName("PENDING 신청에 본인이 confirm을 호출하면 200과 CONFIRMED 응답을 반환한다")
    void returns200WithConfirmed_whenConfirmSucceeds() throws Exception {
        // given
        java.time.LocalDateTime confirmedAt = java.time.LocalDateTime.of(2026, 5, 22, 10, 0);
        EnrollmentResponse confirmed = new EnrollmentResponse(
                ENROLLMENT_ID, COURSE_ID, MEMBER_ID, EnrollmentStatus.CONFIRMED, confirmedAt, null);
        given(enrollmentService.confirm(ENROLLMENT_ID, MEMBER_ID)).willReturn(confirmed);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", ENROLLMENT_ID)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ENROLLMENT_ID))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").value("2026-05-22T10:00:00"));
    }

    @Test
    @DisplayName("X-Member-Id 헤더가 없으면 confirm 요청 시 400을 반환한다")
    void returns400_whenMemberIdHeaderMissingOnConfirm() throws Exception {
        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", ENROLLMENT_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("신청 본인이 아니면 ENROLLMENT_004로 403을 반환한다")
    void returns403_whenNotEnrollmentOwner() throws Exception {
        // given
        willThrow(new BusinessException(EnrollmentErrorInfo.NOT_ENROLLMENT_OWNER))
                .given(enrollmentService).confirm(ENROLLMENT_ID, MEMBER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", ENROLLMENT_ID)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_004"));
    }

    @Test
    @DisplayName("PENDING이 아니면 ENROLLMENT_005로 409를 반환한다")
    void returns409_whenNotConfirmableStatus() throws Exception {
        // given
        willThrow(new BusinessException(EnrollmentErrorInfo.NOT_CONFIRMABLE_STATUS))
                .given(enrollmentService).confirm(ENROLLMENT_ID, MEMBER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", ENROLLMENT_ID)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_005"));
    }

    @Test
    @DisplayName("신청이 존재하지 않으면 ENROLLMENT_003으로 404를 반환한다")
    void returns404_whenEnrollmentNotFound() throws Exception {
        // given
        willThrow(new BusinessException(EnrollmentErrorInfo.ENROLLMENT_NOT_FOUND))
                .given(enrollmentService).confirm(ENROLLMENT_ID, MEMBER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", ENROLLMENT_ID)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_003"));
    }

    @Test
    @DisplayName("정상 cancel 요청이면 200과 CANCELLED 응답을 반환한다")
    void returns200WithCancelled_whenCancelSucceeds() throws Exception {
        // given
        java.time.LocalDateTime cancelledAt = java.time.LocalDateTime.of(2026, 5, 22, 12, 0);
        EnrollmentResponse cancelled = new EnrollmentResponse(
                ENROLLMENT_ID, COURSE_ID, MEMBER_ID, EnrollmentStatus.CANCELLED, null, cancelledAt);
        given(enrollmentService.cancel(ENROLLMENT_ID, MEMBER_ID)).willReturn(cancelled);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", ENROLLMENT_ID)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ENROLLMENT_ID))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").value("2026-05-22T12:00:00"));
    }

    @Test
    @DisplayName("X-Member-Id 헤더가 없으면 cancel 요청 시 400을 반환한다")
    void returns400_whenMemberIdHeaderMissingOnCancel() throws Exception {
        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", ENROLLMENT_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cancel 시 본인이 아니면 ENROLLMENT_004로 403을 반환한다")
    void returns403_whenNotOwnerOnCancel() throws Exception {
        // given
        willThrow(new BusinessException(EnrollmentErrorInfo.NOT_ENROLLMENT_OWNER))
                .given(enrollmentService).cancel(ENROLLMENT_ID, MEMBER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", ENROLLMENT_ID)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_004"));
    }

    @Test
    @DisplayName("cancel 시 7일 초과면 ENROLLMENT_006으로 400을 반환한다")
    void returns400_whenCancellationWindowExpired() throws Exception {
        // given
        willThrow(new BusinessException(EnrollmentErrorInfo.CANCELLATION_WINDOW_EXPIRED))
                .given(enrollmentService).cancel(ENROLLMENT_ID, MEMBER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", ENROLLMENT_ID)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_006"));
    }

    @Test
    @DisplayName("cancel 시 이미 CANCELLED이면 ENROLLMENT_007로 409를 반환한다")
    void returns409_whenAlreadyCancelled() throws Exception {
        // given
        willThrow(new BusinessException(EnrollmentErrorInfo.ALREADY_CANCELLED))
                .given(enrollmentService).cancel(ENROLLMENT_ID, MEMBER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", ENROLLMENT_ID)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_007"));
    }

    @Test
    @DisplayName("cancel 시 신청이 없으면 ENROLLMENT_003으로 404를 반환한다")
    void returns404_whenEnrollmentNotFoundOnCancel() throws Exception {
        // given
        willThrow(new BusinessException(EnrollmentErrorInfo.ENROLLMENT_NOT_FOUND))
                .given(enrollmentService).cancel(ENROLLMENT_ID, MEMBER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancellation", ENROLLMENT_ID)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_003"));
    }

    @Test
    @DisplayName("내 수강 신청 목록 조회 시 200과 페이지네이션된 응답을 반환한다")
    void returns200WithPageResponse_whenGetMyEnrollments() throws Exception {
        // given
        com.liveclass.enrollment.dto.response.MyEnrollmentResponse my =
                new com.liveclass.enrollment.dto.response.MyEnrollmentResponse(
                        ENROLLMENT_ID, COURSE_ID, "Spring Boot", 99_000L,
                        java.time.LocalDate.of(2026, 6, 1), java.time.LocalDate.of(2026, 8, 31),
                        EnrollmentStatus.WAITING, null, null);
        com.liveclass.common.dto.PageResponse<com.liveclass.enrollment.dto.response.MyEnrollmentResponse> pageResponse =
                new com.liveclass.common.dto.PageResponse<>(java.util.List.of(my), 0, 20, 1L, 1);
        given(enrollmentService.getMyEnrollments(org.mockito.ArgumentMatchers.eq(MEMBER_ID),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .willReturn(pageResponse);

        // when & then
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/enrollments/me")
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].enrollmentId").value(ENROLLMENT_ID))
                .andExpect(jsonPath("$.content[0].courseTitle").value("Spring Boot"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("내 수강 신청 목록 조회 시 X-Member-Id 헤더가 없으면 400을 반환한다")
    void returns400_whenMemberIdHeaderMissingOnGetMyEnrollments() throws Exception {
        // when & then
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/enrollments/me"))
                .andExpect(status().isBadRequest());
    }

    private EnrollmentResponse pendingResponse() {
        return new EnrollmentResponse(ENROLLMENT_ID, COURSE_ID, MEMBER_ID, EnrollmentStatus.PENDING, null, null);
    }
}
