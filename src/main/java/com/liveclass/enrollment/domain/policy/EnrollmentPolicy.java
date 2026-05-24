package com.liveclass.enrollment.domain.policy;

import java.time.Duration;

public final class EnrollmentPolicy {

    /** 결제완료 상태에서 취소 가능한 기간. 결제일자가 이 기간을 초과하면 취소 불가. */
    public static final Duration CANCELLATION_WINDOW = Duration.ofDays(7);

    private EnrollmentPolicy() {
    }
}
