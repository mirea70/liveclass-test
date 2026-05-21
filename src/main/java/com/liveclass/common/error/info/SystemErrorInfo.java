package com.liveclass.common.error.info;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SystemErrorInfo implements ErrorInfo {

    UNKNOWN_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM_001", "시스템에서 알 수 없는 에러가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
