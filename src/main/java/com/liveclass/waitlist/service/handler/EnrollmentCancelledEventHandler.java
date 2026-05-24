package com.liveclass.waitlist.service.handler;

import com.liveclass.outbox.domain.entity.OutboxEvent;
import com.liveclass.outbox.domain.entity.OutboxEventType;
import com.liveclass.outbox.service.handler.OutboxEventHandler;
import com.liveclass.waitlist.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrollmentCancelledEventHandler implements OutboxEventHandler {

    private final WaitlistService waitlistService;

    @Override
    public OutboxEventType supports() {
        return OutboxEventType.ENROLLMENT_CANCELLED;
    }

    @Override
    public void handle(OutboxEvent event) {
        waitlistService.promoteOldest(event.getDomainId());
    }
}
