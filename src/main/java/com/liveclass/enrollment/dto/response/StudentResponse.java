package com.liveclass.enrollment.dto.response;

import com.liveclass.enrollment.domain.entity.EnrollmentStatus;

import java.time.LocalDateTime;

public record StudentResponse(
        Long memberId,
        String name,
        EnrollmentStatus status,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt
) {
}
