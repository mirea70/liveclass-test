package com.liveclass.enrollment.dto.response;

import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;

import java.time.LocalDateTime;

public record EnrollmentResponse(
        Long id,
        Long courseId,
        Long memberId,
        EnrollmentStatus status,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt
) {
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getCourseId(),
                enrollment.getMemberId(),
                enrollment.getStatus(),
                enrollment.getConfirmedAt(),
                enrollment.getCancelledAt()
        );
    }
}
