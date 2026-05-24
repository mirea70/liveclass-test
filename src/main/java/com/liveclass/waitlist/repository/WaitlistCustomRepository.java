package com.liveclass.waitlist.repository;

import com.liveclass.waitlist.domain.entity.Waitlist;
import com.liveclass.waitlist.dto.response.MyWaitlistResponse;

import java.util.List;
import java.util.Optional;

public interface WaitlistCustomRepository {

    int findMaxOrderNumByCourseId(Long courseId);

    void shiftOrderNumDownAfter(Long courseId, int deletedOrderNum);

    Optional<Waitlist> findOldestByCourseId(Long courseId);

    List<MyWaitlistResponse> findMyWaitlists(Long memberId);
}
