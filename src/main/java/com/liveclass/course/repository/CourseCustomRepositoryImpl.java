package com.liveclass.course.repository;

import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.dto.response.CourseSummaryResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.liveclass.course.domain.entity.QCourse.course;
import static com.liveclass.course.domain.entity.QCourseEnrollCount.courseEnrollCount;

@RequiredArgsConstructor
public class CourseCustomRepositoryImpl implements CourseCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<CourseSummaryResponse> findSummaries(CourseStatus status) {
        return queryFactory
                .select(Projections.constructor(CourseSummaryResponse.class,
                        course.id,
                        course.title,
                        course.price.amount,
                        course.capacity,
                        courseEnrollCount.count,
                        course.period.startDate,
                        course.period.endDate,
                        course.status
                ))
                .from(course)
                .innerJoin(courseEnrollCount).on(courseEnrollCount.courseId.eq(course.id))
                .where(statusEq(status))
                .orderBy(course.id.desc())
                .fetch();
    }

    private BooleanExpression statusEq(CourseStatus status) {
        return status == null ? null : course.status.eq(status);
    }
}
