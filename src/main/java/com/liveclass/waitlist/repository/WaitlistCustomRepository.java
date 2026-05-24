package com.liveclass.waitlist.repository;

import com.liveclass.waitlist.domain.entity.Waitlist;

import java.util.Optional;

public interface WaitlistCustomRepository {

    int findMaxOrderNumByCourseId(Long courseId);

    void shiftOrderNumDownAfter(Long courseId, int deletedOrderNum);

    Optional<Waitlist> findOldestByCourseId(Long courseId);
}
