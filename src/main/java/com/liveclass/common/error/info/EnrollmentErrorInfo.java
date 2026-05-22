package com.liveclass.common.error.info;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EnrollmentErrorInfo implements ErrorInfo {

    COURSE_NOT_OPEN(HttpStatus.BAD_REQUEST, "ENROLLMENT_001", "강의가 모집 중이 아닙니다."),
    DUPLICATE_ENROLLMENT(HttpStatus.BAD_REQUEST, "ENROLLMENT_002", "이미 신청한 강의입니다."),
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ENROLLMENT_003", "수강 신청을 찾을 수 없습니다."),
    NOT_ENROLLMENT_OWNER(HttpStatus.FORBIDDEN, "ENROLLMENT_004", "수강 신청의 본인만 수행할 수 있습니다."),
    NOT_CONFIRMABLE_STATUS(HttpStatus.BAD_REQUEST, "ENROLLMENT_005", "결제 확정이 가능한 상태가 아닙니다."),
    CANCELLATION_WINDOW_EXPIRED(HttpStatus.BAD_REQUEST, "ENROLLMENT_006", "취소 가능 기간이 지났습니다."),
    ALREADY_CANCELLED(HttpStatus.BAD_REQUEST, "ENROLLMENT_007", "이미 취소된 수강 신청입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
