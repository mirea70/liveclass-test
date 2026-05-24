package com.liveclass.waitlist.controller;

import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.common.error.info.EnrollmentErrorInfo;
import com.liveclass.common.error.info.WaitlistErrorInfo;
import com.liveclass.support.ControllerTestSupport;
import com.liveclass.waitlist.dto.request.WaitlistCreateRequest;
import com.liveclass.waitlist.dto.response.WaitlistResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("WaitlistController 슬라이스 테스트")
class WaitlistControllerTest extends ControllerTestSupport {

    private static final Long COURSE_ID = 1L;
    private static final Long MEMBER_ID = 200L;
    private static final Long WAITLIST_ID = 55L;

    @Test
    @DisplayName("유효한 대기 등록 요청이면 201 Created와 Location 헤더, order_num 응답을 반환한다")
    void returns201WithLocation_whenRegisterSucceeds() throws Exception {
        // given
        WaitlistResponse response = new WaitlistResponse(WAITLIST_ID, COURSE_ID, MEMBER_ID, 3);
        given(waitlistService.register(COURSE_ID, MEMBER_ID)).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(COURSE_ID))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/waitlists/" + WAITLIST_ID))
                .andExpect(jsonPath("$.id").value(WAITLIST_ID))
                .andExpect(jsonPath("$.courseId").value(COURSE_ID))
                .andExpect(jsonPath("$.memberId").value(MEMBER_ID))
                .andExpect(jsonPath("$.orderNum").value(3));
    }

    @Test
    @DisplayName("X-Member-Id 헤더가 없으면 400을 반환한다")
    void returns400_whenMemberIdHeaderMissing() throws Exception {
        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(COURSE_ID))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("courseId가 null이면 INVALID_REQUEST로 400을 반환한다")
    void returns400_whenCourseIdMissing() throws Exception {
        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 COURSE_001로 404를 반환한다")
    void returns404_whenCourseNotFound() throws Exception {
        // given
        given(waitlistService.register(COURSE_ID, MEMBER_ID))
                .willThrow(new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(COURSE_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_001"));
    }

    @Test
    @DisplayName("강의가 OPEN이 아니면 ENROLLMENT_001로 409를 반환한다")
    void returns409_whenCourseNotOpen() throws Exception {
        // given
        given(waitlistService.register(COURSE_ID, MEMBER_ID))
                .willThrow(new BusinessException(EnrollmentErrorInfo.COURSE_NOT_OPEN));

        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(COURSE_ID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_001"));
    }

    @Test
    @DisplayName("활성 enrollment가 있으면 ENROLLMENT_002로 409를 반환한다")
    void returns409_whenActiveEnrollmentExists() throws Exception {
        // given
        given(waitlistService.register(COURSE_ID, MEMBER_ID))
                .willThrow(new BusinessException(EnrollmentErrorInfo.DUPLICATE_ENROLLMENT));

        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(COURSE_ID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_002"));
    }

    @Test
    @DisplayName("이미 대기 중이면 WAITLIST_003으로 409를 반환한다")
    void returns409_whenAlreadyInWaitlist() throws Exception {
        // given
        given(waitlistService.register(COURSE_ID, MEMBER_ID))
                .willThrow(new BusinessException(WaitlistErrorInfo.DUPLICATE_WAITLIST));

        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(COURSE_ID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAITLIST_003"));
    }

    @Test
    @DisplayName("본인이 대기를 취소하면 204 No Content를 반환한다")
    void returns204_whenCancelSucceeds() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/waitlists/{waitlistId}", WAITLIST_ID)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("대기 신청이 존재하지 않으면 WAITLIST_001로 404를 반환한다")
    void returns404_whenWaitlistNotFound() throws Exception {
        // given
        willThrow(new BusinessException(WaitlistErrorInfo.WAITLIST_NOT_FOUND))
                .given(waitlistService).cancel(WAITLIST_ID, MEMBER_ID);

        // when & then
        mockMvc.perform(delete("/api/waitlists/{waitlistId}", WAITLIST_ID)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WAITLIST_001"));
    }

    @Test
    @DisplayName("본인이 아니면 WAITLIST_002로 403을 반환한다")
    void returns403_whenNotOwner() throws Exception {
        // given
        willThrow(new BusinessException(WaitlistErrorInfo.NOT_WAITLIST_OWNER))
                .given(waitlistService).cancel(WAITLIST_ID, MEMBER_ID);

        // when & then
        mockMvc.perform(delete("/api/waitlists/{waitlistId}", WAITLIST_ID)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WAITLIST_002"));
    }
}
