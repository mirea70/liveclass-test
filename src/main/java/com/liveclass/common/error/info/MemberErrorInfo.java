package com.liveclass.common.error.info;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorInfo implements ErrorInfo {

    MEMBER_NAME_EMPTY(HttpStatus.BAD_REQUEST, "MEMBER_001", "회원 이름은 비어있을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
