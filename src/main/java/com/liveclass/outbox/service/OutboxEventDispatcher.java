package com.liveclass.outbox.service;

import com.liveclass.outbox.domain.entity.OutboxEvent;
import com.liveclass.outbox.domain.entity.OutboxEventType;
import com.liveclass.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventDispatcher {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProcessor outboxEventProcessor;

    public void dispatchPending(OutboxEventType type) {
        List<OutboxEvent> events = outboxEventRepository.findPendingByType(type, BATCH_SIZE);
        for (OutboxEvent event : events) {
            try {
                outboxEventProcessor.process(event.getId());
            } catch (Exception e) {
                log.warn("Outbox 이벤트 처리 실패 → 재시도 카운트 증가. eventId={}, type={}",
                        event.getId(), event.getType(), e);
                try {
                    outboxEventProcessor.markRetry(event.getId());
                } catch (Exception inner) {
                    log.error("재시도 카운트 갱신 실패. eventId={}", event.getId(), inner);
                }
            }
        }
    }
}
