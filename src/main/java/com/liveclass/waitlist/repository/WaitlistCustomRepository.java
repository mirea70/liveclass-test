package com.liveclass.waitlist.repository;

public interface WaitlistCustomRepository {

    int findMaxOrderNumByCourseId(Long courseId);
}
