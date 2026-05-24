package com.liveclass.outbox.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxEvent 도메인 테스트")
class OutboxEventTest {

    private static final Long DOMAIN_ID = 1L;
    private static final int MAX_RETRY = 5;

    @Test
    @DisplayName("of로 생성하면 PENDING 상태이고 retry_count는 0이며 processedAt은 비어있다")
    void startsAsPending_whenCreated() {
        // when
        OutboxEvent event = OutboxEvent.of(OutboxEventType.ENROLLMENT_CANCELLED, DOMAIN_ID);

        // then
        assertThat(event.getType()).isEqualTo(OutboxEventType.ENROLLMENT_CANCELLED);
        assertThat(event.getDomainId()).isEqualTo(DOMAIN_ID);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("markProcessed를 호출하면 PROCESSED로 전이되고 processedAt이 설정된다")
    void transitionsToProcessed_whenMarkProcessed() {
        // given
        OutboxEvent event = OutboxEvent.of(OutboxEventType.ENROLLMENT_CANCELLED, DOMAIN_ID);
        LocalDateTime now = LocalDateTime.of(2026, 5, 24, 10, 0);

        // when
        event.markProcessed(now);

        // then
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(event.getProcessedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("incrementRetry를 호출하면 retry_count가 +1되고 임계치 미만이면 PENDING이 유지된다")
    void incrementsRetryAndStaysAsPending_whenBelowThreshold() {
        // given
        OutboxEvent event = OutboxEvent.of(OutboxEventType.ENROLLMENT_CANCELLED, DOMAIN_ID);

        // when
        event.incrementRetry(MAX_RETRY);

        // then
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("incrementRetry가 임계치(maxRetry)에 도달하면 FAILED로 전이된다")
    void transitionsToFailed_whenRetryReachesThreshold() {
        // given
        OutboxEvent event = OutboxEvent.of(OutboxEventType.ENROLLMENT_CANCELLED, DOMAIN_ID);

        // when
        for (int i = 0; i < MAX_RETRY; i++) {
            event.incrementRetry(MAX_RETRY);
        }

        // then
        assertThat(event.getRetryCount()).isEqualTo(MAX_RETRY);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    }
}
