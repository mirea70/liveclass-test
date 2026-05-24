package com.liveclass.waitlist.repository;

public interface WaitlistCustomRepository {

    int findMaxOrderNumByCourseId(Long courseId);

    void shiftOrderNumDownAfter(Long courseId, int deletedOrderNum);
}
