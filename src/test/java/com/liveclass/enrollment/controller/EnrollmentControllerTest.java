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
    private static final Long USER_ID = 200L;
    private static final Long ENROLLMENT_ID = 10L;

    @Test
    @DisplayName("유효한 신청 요청이면 201 Created와 Location 헤더, PENDING 응답을 반환한다")
    void returns201WithLocation_whenEnrollSucceeds() throws Exception {
        // given
        given(enrollmentService.enroll(COURSE_ID, USER_ID)).willReturn(pendingResponse());

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", USER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/enrollments/" + ENROLLMENT_ID))
                .andExpect(jsonPath("$.id").value(ENROLLMENT_ID))
                .andExpect(jsonPath("$.courseId").value(COURSE_ID))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.confirmedAt").isEmpty())
                .andExpect(jsonPath("$.cancelledAt").isEmpty());
    }

    @Test
    @DisplayName("정원이 차서 대기열로 등록되면 status가 WAITING으로 반환된다")
    void returnsWaitingStatus_whenQueued() throws Exception {
        // given
        EnrollmentResponse waiting = new EnrollmentResponse(
                ENROLLMENT_ID, COURSE_ID, USER_ID, EnrollmentStatus.WAITING, null, null);
        given(enrollmentService.enroll(COURSE_ID, USER_ID)).willReturn(waiting);

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", USER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 400을 반환한다")
    void returns400_whenUserIdHeaderMissing() throws Exception {
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
                        .header("X-User-Id", USER_ID)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 COURSE_001로 404를 반환한다")
    void returns404_whenCourseNotFound() throws Exception {
        // given
        willThrow(new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND))
                .given(enrollmentService).enroll(COURSE_ID, USER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", USER_ID)
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
                .given(enrollmentService).enroll(COURSE_ID, USER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", USER_ID)
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
                .given(enrollmentService).enroll(COURSE_ID, USER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", USER_ID)
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
                ENROLLMENT_ID, COURSE_ID, USER_ID, EnrollmentStatus.CONFIRMED, confirmedAt, null);
        given(enrollmentService.confirm(ENROLLMENT_ID, USER_ID)).willReturn(confirmed);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", ENROLLMENT_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ENROLLMENT_ID))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").value("2026-05-22T10:00:00"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 confirm 요청 시 400을 반환한다")
    void returns400_whenUserIdHeaderMissingOnConfirm() throws Exception {
        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", ENROLLMENT_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("신청 본인이 아니면 ENROLLMENT_004로 403을 반환한다")
    void returns403_whenNotEnrollmentOwner() throws Exception {
        // given
        willThrow(new BusinessException(EnrollmentErrorInfo.NOT_ENROLLMENT_OWNER))
                .given(enrollmentService).confirm(ENROLLMENT_ID, USER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", ENROLLMENT_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_004"));
    }

    @Test
    @DisplayName("PENDING이 아니면 ENROLLMENT_005로 409를 반환한다")
    void returns409_whenNotConfirmableStatus() throws Exception {
        // given
        willThrow(new BusinessException(EnrollmentErrorInfo.NOT_CONFIRMABLE_STATUS))
                .given(enrollmentService).confirm(ENROLLMENT_ID, USER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", ENROLLMENT_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_005"));
    }

    @Test
    @DisplayName("신청이 존재하지 않으면 ENROLLMENT_003으로 404를 반환한다")
    void returns404_whenEnrollmentNotFound() throws Exception {
        // given
        willThrow(new BusinessException(EnrollmentErrorInfo.ENROLLMENT_NOT_FOUND))
                .given(enrollmentService).confirm(ENROLLMENT_ID, USER_ID);

        // when & then
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirmation", ENROLLMENT_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_003"));
    }

    private EnrollmentResponse pendingResponse() {
        return new EnrollmentResponse(ENROLLMENT_ID, COURSE_ID, USER_ID, EnrollmentStatus.PENDING, null, null);
    }
}
