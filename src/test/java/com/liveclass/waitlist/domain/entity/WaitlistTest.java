package com.liveclass.waitlist.domain.entity;

import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.WaitlistErrorInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Waitlist 도메인 테스트")
class WaitlistTest {

    private static final Long COURSE_ID = 1L;
    private static final Long MEMBER_ID = 200L;
    private static final Long OTHER_MEMBER_ID = 999L;
    private static final int ORDER_NUM = 3;

    @Test
    @DisplayName("createNew로 생성하면 입력값이 그대로 보관된다")
    void createsWithGivenValues() {
        // when
        Waitlist waitlist = Waitlist.createNew(COURSE_ID, MEMBER_ID, ORDER_NUM);

        // then
        assertThat(waitlist.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(waitlist.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(waitlist.getOrderNum()).isEqualTo(ORDER_NUM);
    }

    @Test
    @DisplayName("순번이 1 미만이면 DomainException이 발생한다")
    void throwsDomainException_whenOrderNumLessThanOne() {
        // when & then
        assertThatThrownBy(() -> Waitlist.createNew(COURSE_ID, MEMBER_ID, 0))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorInfo())
                .isEqualTo(WaitlistErrorInfo.ORDER_NUM_INVALID);
    }

    @Test
    @DisplayName("verifyOwner는 본인이 아니면 BusinessException을 던진다")
    void throwsBusinessException_whenNotOwner() {
        // given
        Waitlist waitlist = Waitlist.createNew(COURSE_ID, MEMBER_ID, ORDER_NUM);

        // when & then
        assertThatThrownBy(() -> waitlist.verifyOwner(OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(WaitlistErrorInfo.NOT_WAITLIST_OWNER);
    }

    @Test
    @DisplayName("verifyOwner는 본인이면 예외 없이 통과한다")
    void passes_whenOwner() {
        // given
        Waitlist waitlist = Waitlist.createNew(COURSE_ID, MEMBER_ID, ORDER_NUM);

        // when & then (no exception)
        waitlist.verifyOwner(MEMBER_ID);
    }
}
