package com.liveclass.outbox.service.handler;

import com.liveclass.outbox.domain.entity.OutboxEvent;
import com.liveclass.outbox.domain.entity.OutboxEventType;

public interface OutboxEventHandler {

    OutboxEventType supports();

    void handle(OutboxEvent event);
}
