package com.liveclass.common.error.info;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CourseErrorInfo implements ErrorInfo {

    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "COURSE_001", "강의를 찾을 수 없습니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "COURSE_002", "허용되지 않은 강의 상태 전이입니다."),
    NOT_COURSE_CREATOR(HttpStatus.FORBIDDEN, "COURSE_003", "강의의 크리에이터만 수행할 수 있습니다."),
    CAPACITY_INVALID_VALUE(HttpStatus.BAD_REQUEST, "COURSE_004", "정원은 1 이상의 값이어야 합니다."),
    COURSE_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "COURSE_005", "수강 기간이 유효하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
