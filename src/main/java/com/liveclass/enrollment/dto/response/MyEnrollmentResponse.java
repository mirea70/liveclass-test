package com.liveclass.enrollment.dto.response;

import com.liveclass.enrollment.domain.entity.EnrollmentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MyEnrollmentResponse(
        Long enrollmentId,
        Long courseId,
        String courseTitle,
        Long price,
        LocalDate startDate,
        LocalDate endDate,
        EnrollmentStatus status,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt
) {
}
