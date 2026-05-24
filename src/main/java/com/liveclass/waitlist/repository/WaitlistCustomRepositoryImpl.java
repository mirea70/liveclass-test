package com.liveclass.waitlist.repository;

import com.liveclass.waitlist.domain.entity.Waitlist;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

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

    @Override
    public void shiftOrderNumDownAfter(Long courseId, int deletedOrderNum) {
        queryFactory
                .update(waitlist)
                .set(waitlist.orderNum, waitlist.orderNum.subtract(1))
                .where(waitlist.courseId.eq(courseId)
                        .and(waitlist.orderNum.gt(deletedOrderNum)))
                .execute();
    }

    @Override
    public Optional<Waitlist> findOldestByCourseId(Long courseId) {
        return Optional.ofNullable(queryFactory
                .selectFrom(waitlist)
                .where(waitlist.courseId.eq(courseId))
                .orderBy(waitlist.orderNum.asc())
                .fetchFirst());
    }
}
