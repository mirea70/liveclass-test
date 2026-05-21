package com.liveclass.common.error.info;

import org.springframework.http.HttpStatus;

public interface ErrorInfo {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
