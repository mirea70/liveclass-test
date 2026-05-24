package com.liveclass.outbox.repository;

import com.liveclass.outbox.domain.entity.OutboxEvent;
import com.liveclass.outbox.domain.entity.OutboxEventType;

import java.util.List;

public interface OutboxEventCustomRepository {

    List<OutboxEvent> findPendingByType(OutboxEventType type, int limit);
}
