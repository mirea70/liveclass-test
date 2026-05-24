package com.liveclass.outbox.service;

import com.liveclass.outbox.domain.entity.OutboxEvent;
import com.liveclass.outbox.domain.entity.OutboxEventStatus;
import com.liveclass.outbox.domain.entity.OutboxEventType;
import com.liveclass.outbox.repository.OutboxEventRepository;
import com.liveclass.outbox.service.handler.OutboxEventHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("OutboxEventProcessor 단위 테스트")
@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorTest {

    private static final Long EVENT_ID = 100L;
    private static final Long DOMAIN_ID = 1L;
    private static final int MAX_RETRY = 5;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventHandler handler;

    @Test
    @DisplayName("process는 type에 맞는 handler를 호출하고 이벤트를 PROCESSED로 마킹한다")
    void invokesHandlerAndMarksProcessed() {
        // given
        given(handler.supports()).willReturn(OutboxEventType.ENROLLMENT_CANCELLED);
        OutboxEventProcessor processor = new OutboxEventProcessor(outboxEventRepository, List.of(handler));
        OutboxEvent event = OutboxEvent.of(OutboxEventType.ENROLLMENT_CANCELLED, DOMAIN_ID);
        given(outboxEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        // when
        processor.process(EVENT_ID);

        // then
        verify(handler).handle(event);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(event.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("markRetry는 retry_count를 +1 한다")
    void incrementsRetryCount() {
        // given
        given(handler.supports()).willReturn(OutboxEventType.ENROLLMENT_CANCELLED);
        OutboxEventProcessor processor = new OutboxEventProcessor(outboxEventRepository, List.of(handler));
        OutboxEvent event = OutboxEvent.of(OutboxEventType.ENROLLMENT_CANCELLED, DOMAIN_ID);
        given(outboxEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        // when
        processor.markRetry(EVENT_ID);

        // then
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("markRetry가 임계치(5회)에 도달하면 FAILED로 전이된다")
    void transitionsToFailed_whenRetryReachesMax() {
        // given
        given(handler.supports()).willReturn(OutboxEventType.ENROLLMENT_CANCELLED);
        OutboxEventProcessor processor = new OutboxEventProcessor(outboxEventRepository, List.of(handler));
        OutboxEvent event = OutboxEvent.of(OutboxEventType.ENROLLMENT_CANCELLED, DOMAIN_ID);
        given(outboxEventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        // when
        for (int i = 0; i < MAX_RETRY; i++) {
            processor.markRetry(EVENT_ID);
        }

        // then
        assertThat(event.getRetryCount()).isEqualTo(MAX_RETRY);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    }
}
