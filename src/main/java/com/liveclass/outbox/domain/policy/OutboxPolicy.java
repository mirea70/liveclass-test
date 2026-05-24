package com.liveclass.outbox.domain.policy;

public final class OutboxPolicy {

    /** 한 이벤트 처리 실패 시 재시도 한도. 초과하면 실패 상태로 변경한다. */
    public static final int MAX_RETRY = 5;

    /** 한 사이클당 가져올 이벤트 최대 개수. */
    public static final int BATCH_SIZE = 100;

    /** 스케줄러 폴링 주기(밀리초). */
    public static final long POLLING_INTERVAL_MS = 5000L;

    private OutboxPolicy() {
    }
}
