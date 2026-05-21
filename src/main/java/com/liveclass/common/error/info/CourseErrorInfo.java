package com.liveclass.common.error.info;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CourseErrorInfo implements ErrorInfo {

    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "COURSE_002", "허용되지 않은 강의 상태 전이입니다."),
    CAPACITY_INVALID_VALUE(HttpStatus.BAD_REQUEST, "COURSE_004", "정원은 1 이상의 값이어야 합니다."),
    COURSE_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "COURSE_005", "수강 기간이 유효하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
