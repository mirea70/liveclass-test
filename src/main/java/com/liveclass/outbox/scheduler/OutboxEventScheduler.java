package com.liveclass.outbox.scheduler;

import com.liveclass.outbox.domain.entity.OutboxEventType;
import com.liveclass.outbox.domain.policy.OutboxPolicy;
import com.liveclass.outbox.service.OutboxEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventScheduler {

    private final OutboxEventDispatcher outboxEventDispatcher;

    @Scheduled(fixedDelay = OutboxPolicy.POLLING_INTERVAL_MS)
    public void pollEnrollmentCancelledEvents() {
        outboxEventDispatcher.dispatchPending(OutboxEventType.ENROLLMENT_CANCELLED);
    }
}
