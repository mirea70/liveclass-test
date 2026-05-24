package com.liveclass.outbox.repository;

import com.liveclass.outbox.domain.entity.OutboxEvent;
import com.liveclass.outbox.domain.entity.OutboxEventStatus;
import com.liveclass.outbox.domain.entity.OutboxEventType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.liveclass.outbox.domain.entity.QOutboxEvent.outboxEvent;

@RequiredArgsConstructor
public class OutboxEventCustomRepositoryImpl implements OutboxEventCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OutboxEvent> findPendingByType(OutboxEventType type, int limit) {
        return queryFactory
                .selectFrom(outboxEvent)
                .where(outboxEvent.type.eq(type)
                        .and(outboxEvent.status.eq(OutboxEventStatus.PENDING)))
                .orderBy(outboxEvent.createdAt.asc())
                .limit(limit)
                .fetch();
    }
}
