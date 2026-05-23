package com.liveclass.common.error.info;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WaitlistErrorInfo implements ErrorInfo {

    WAITLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "WAITLIST_001", "대기 신청을 찾을 수 없습니다."),
    NOT_WAITLIST_OWNER(HttpStatus.FORBIDDEN, "WAITLIST_002", "대기 신청의 본인만 수행할 수 있습니다."),
    DUPLICATE_WAITLIST(HttpStatus.CONFLICT, "WAITLIST_003", "이미 대기 신청한 강의입니다."),
    ORDER_NUM_INVALID(HttpStatus.BAD_REQUEST, "WAITLIST_004", "대기 순번은 1 이상이어야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
