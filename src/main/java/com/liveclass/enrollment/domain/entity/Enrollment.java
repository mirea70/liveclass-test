package com.liveclass.enrollment.domain.entity;

import com.liveclass.common.domain.entity.BaseEntity;
import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.EnrollmentErrorInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "enrollment",
        indexes = {
                @Index(name = "idx_enrollment_user_status", columnList = "user_id, status"),
                @Index(name = "idx_enrollment_course_status_created", columnList = "course_id, status, created_at")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_enrollment_active",
                columnNames = {"course_id", "active_user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(
            name = "active_user_id",
            insertable = false,
            updatable = false,
            columnDefinition = "BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('WAITING','PENDING','CONFIRMED') THEN user_id END)"
    )
    private Long activeUserId;

    @Version
    @Column(nullable = false)
    private Long version;

    private Enrollment(Long courseId, Long userId, EnrollmentStatus status) {
        this.courseId = courseId;
        this.userId = userId;
        this.status = status;
    }

    public static Enrollment createPending(Long courseId, Long userId) {
        return new Enrollment(courseId, userId, EnrollmentStatus.PENDING);
    }

    public static Enrollment createWaiting(Long courseId, Long userId) {
        return new Enrollment(courseId, userId, EnrollmentStatus.WAITING);
    }

    public void promote() {
        if (this.status != EnrollmentStatus.WAITING) {
            throw new DomainException(EnrollmentErrorInfo.NOT_CONFIRMABLE_STATUS);
        }
        this.status = EnrollmentStatus.PENDING;
    }

    public void confirm(LocalDateTime now) {
        if (this.status != EnrollmentStatus.PENDING) {
            throw new BusinessException(EnrollmentErrorInfo.NOT_CONFIRMABLE_STATUS);
        }
        this.status = EnrollmentStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    public void cancel(LocalDateTime now, Duration cancellationWindow) {
        if (this.status == EnrollmentStatus.CANCELLED) {
            throw new BusinessException(EnrollmentErrorInfo.ALREADY_CANCELLED);
        }
        if (this.status == EnrollmentStatus.CONFIRMED && now.isAfter(this.confirmedAt.plus(cancellationWindow))) {
            throw new BusinessException(EnrollmentErrorInfo.CANCELLATION_WINDOW_EXPIRED);
        }
        this.status = EnrollmentStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public void verifyOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new BusinessException(EnrollmentErrorInfo.NOT_ENROLLMENT_OWNER);
        }
    }
}
