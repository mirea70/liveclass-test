package com.liveclass.common.domain.vo;

import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.CommonErrorInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money 도메인 테스트")
class MoneyTest {

    @Test
    @DisplayName("금액이 양수면 정상 생성된다")
    void creates_whenAmountIsPositive() {
        // when
        Money money = new Money(1000L);

        // then
        assertThat(money.amount()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("금액이 0이면 정상 생성된다 (무료 강의 허용)")
    void creates_whenAmountIsZero() {
        // when & then
        assertThatCode(() -> new Money(0L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("금액이 음수면 DomainException이 발생한다")
    void throws_whenAmountIsNegative() {
        // when & then
        assertThatThrownBy(() -> new Money(-1L))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorInfo())
                .isEqualTo(CommonErrorInfo.MONEY_INVALID_VALUE);
    }
}
