package com.liveclass.common.domain.vo;

import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.CommonErrorInfo;
import jakarta.persistence.Embeddable;

@Embeddable
public record Money(long amount) {

    public Money {
        if (amount < 0) {
            throw new DomainException(CommonErrorInfo.MONEY_INVALID_VALUE);
        }
    }
}
