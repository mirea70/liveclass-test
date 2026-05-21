package com.liveclass.common.domain.vo;

import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.CommonErrorInfo;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {

    private long amount;

    public Money(long amount) {
        if (amount < 0) {
            throw new DomainException(CommonErrorInfo.MONEY_INVALID_VALUE);
        }
        this.amount = amount;
    }
}
