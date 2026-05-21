package com.liveclass.common.error.response;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        LocalDateTime timestamp,
        String code,
        String message,
        String path,
        Map<String, Object> details
) {

    public ErrorResponse(String code, String message, String path) {
        this(LocalDateTime.now(), code, message, path, Map.of());
    }

    public ErrorResponse(String code, String message, String path, Map<String, Object> details) {
        this(LocalDateTime.now(), code, message, path, details);
    }
}
