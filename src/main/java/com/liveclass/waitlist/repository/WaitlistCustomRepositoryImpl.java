package com.liveclass.waitlist.repository;

import com.liveclass.waitlist.domain.entity.Waitlist;
import com.liveclass.waitlist.dto.response.MyWaitlistResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

import static com.liveclass.course.domain.entity.QCourse.course;
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

    @Override
    public List<MyWaitlistResponse> findMyWaitlists(Long memberId) {
        return queryFactory
                .select(Projections.constructor(MyWaitlistResponse.class,
                        waitlist.id,
                        waitlist.courseId,
                        course.title,
                        course.price.amount,
                        course.period.startDate,
                        course.period.endDate,
                        waitlist.orderNum,
                        waitlist.createdAt
                ))
                .from(waitlist)
                .innerJoin(course).on(course.id.eq(waitlist.courseId))
                .where(waitlist.memberId.eq(memberId))
                .orderBy(waitlist.createdAt.desc())
                .fetch();
    }
}
