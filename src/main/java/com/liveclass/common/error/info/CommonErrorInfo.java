package com.liveclass.common.error.info;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorInfo implements ErrorInfo {

    MONEY_INVALID_VALUE(HttpStatus.BAD_REQUEST, "COMMON_001", "금액은 0 이상의 값이어야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
