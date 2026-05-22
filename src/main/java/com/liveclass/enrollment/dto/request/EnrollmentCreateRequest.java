package com.liveclass.enrollment.dto.request;

import jakarta.validation.constraints.NotNull;

public record EnrollmentCreateRequest(
        @NotNull Long courseId
) {
}
