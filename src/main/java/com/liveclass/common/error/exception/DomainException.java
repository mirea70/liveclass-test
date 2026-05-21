package com.liveclass.common.error.exception;

import com.liveclass.common.error.info.ErrorInfo;
import lombok.Getter;

@Getter
public class DomainException extends RuntimeException {

    private final ErrorInfo errorInfo;

    public DomainException(ErrorInfo errorInfo) {
        super(errorInfo.getMessage());
        this.errorInfo = errorInfo;
    }
}
