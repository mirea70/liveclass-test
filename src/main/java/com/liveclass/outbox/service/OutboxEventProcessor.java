package com.liveclass.outbox.service;

import com.liveclass.outbox.domain.entity.OutboxEvent;
import com.liveclass.outbox.domain.entity.OutboxEventType;
import com.liveclass.outbox.repository.OutboxEventRepository;
import com.liveclass.outbox.service.handler.OutboxEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OutboxEventProcessor {

    private static final int MAX_RETRY = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final Map<OutboxEventType, OutboxEventHandler> handlersByType;

    public OutboxEventProcessor(OutboxEventRepository outboxEventRepository,
                                List<OutboxEventHandler> handlers) {
        this.outboxEventRepository = outboxEventRepository;
        this.handlersByType = handlers.stream()
                .collect(Collectors.toMap(OutboxEventHandler::supports, h -> h));
    }

    @Transactional
    public void process(Long eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
        OutboxEventHandler handler = handlersByType.get(event.getType());
        if (handler == null) {
            log.warn("Outbox 이벤트 핸들러를 찾을 수 없습니다. type={}", event.getType());
            return;
        }
        handler.handle(event);
        event.markProcessed(LocalDateTime.now());
    }

    @Transactional
    public void markRetry(Long eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
        event.incrementRetry(MAX_RETRY);
    }
}
