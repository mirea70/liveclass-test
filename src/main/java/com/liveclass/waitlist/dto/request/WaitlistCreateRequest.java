package com.liveclass.waitlist.dto.request;

import jakarta.validation.constraints.NotNull;

public record WaitlistCreateRequest(
        @NotNull(message = "courseId는 필수입니다.")
        Long courseId
) {
}
