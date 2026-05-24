package com.liveclass.outbox.domain.entity;

import com.liveclass.common.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "outbox_event",
        indexes = @Index(name = "idx_outbox_type_status_created", columnList = "type, status, created_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OutboxEventType type;

    @Column(name = "domain_id", nullable = false)
    private Long domainId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    private OutboxEvent(OutboxEventType type, Long domainId) {
        this.type = type;
        this.domainId = domainId;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
    }

    public static OutboxEvent of(OutboxEventType type, Long domainId) {
        return new OutboxEvent(type, domainId);
    }

    public void markProcessed(LocalDateTime now) {
        this.status = OutboxEventStatus.PROCESSED;
        this.processedAt = now;
    }

    public void incrementRetry(int maxRetry) {
        this.retryCount += 1;
        if (this.retryCount >= maxRetry) {
            this.status = OutboxEventStatus.FAILED;
        }
    }
}
