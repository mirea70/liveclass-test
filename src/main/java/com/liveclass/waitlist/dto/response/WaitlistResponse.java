package com.liveclass.waitlist.dto.response;

import com.liveclass.waitlist.domain.entity.Waitlist;

public record WaitlistResponse(
        Long id,
        Long courseId,
        Long memberId,
        int orderNum
) {
    public static WaitlistResponse from(Waitlist waitlist) {
        return new WaitlistResponse(
                waitlist.getId(),
                waitlist.getCourseId(),
                waitlist.getMemberId(),
                waitlist.getOrderNum()
        );
    }
}
