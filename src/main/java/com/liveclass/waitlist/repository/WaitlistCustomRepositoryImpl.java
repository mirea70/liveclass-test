package com.liveclass.waitlist.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import static com.liveclass.waitlist.domain.entity.QWaitlist.waitlist;

@RequiredArgsConstructor
public class WaitlistCustomRepositoryImpl implements WaitlistCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public int findMaxOrderNumByCourseId(Long courseId) {
        Integer max = queryFactory
                .select(waitlist.orderNum.max())
                .from(waitlist)
                .where(waitlist.courseId.eq(courseId))
                .fetchOne();
        return max == null ? 0 : max;
    }
}
