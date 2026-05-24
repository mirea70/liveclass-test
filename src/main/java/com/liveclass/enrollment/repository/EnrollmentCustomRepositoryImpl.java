package com.liveclass.enrollment.repository;

import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.dto.response.MyEnrollmentResponse;
import com.liveclass.enrollment.dto.response.StudentResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static com.liveclass.course.domain.entity.QCourse.course;
import static com.liveclass.enrollment.domain.entity.QEnrollment.enrollment;
import static com.liveclass.member.domain.entity.QMember.member;

@RequiredArgsConstructor
public class EnrollmentCustomRepositoryImpl implements EnrollmentCustomRepository {

    private static final List<EnrollmentStatus> ACTIVE_STATUSES =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

    private final JPAQueryFactory queryFactory;

    @Override
    public List<StudentResponse> findStudentsByCourse(Long courseId) {
        return queryFactory
                .select(Projections.constructor(StudentResponse.class,
                        enrollment.memberId,
                        member.name,
                        enrollment.status,
                        enrollment.confirmedAt,
                        enrollment.createdAt
                ))
                .from(enrollment)
                .innerJoin(member).on(member.id.eq(enrollment.memberId))
                .where(enrollment.courseId.eq(courseId), enrollment.status.in(ACTIVE_STATUSES))
                .orderBy(enrollment.createdAt.desc(), enrollment.id.desc())
                .fetch();
    }

    @Override
    public Page<MyEnrollmentResponse> findMyEnrollments(Long memberId, Pageable pageable) {
        List<MyEnrollmentResponse> content = queryFactory
                .select(Projections.constructor(MyEnrollmentResponse.class,
                        enrollment.id,
                        enrollment.courseId,
                        course.title,
                        course.price.amount,
                        course.period.startDate,
                        course.period.endDate,
                        enrollment.status,
                        enrollment.confirmedAt,
                        enrollment.cancelledAt
                ))
                .from(enrollment)
                .innerJoin(course).on(course.id.eq(enrollment.courseId))
                .where(enrollment.memberId.eq(memberId))
                .orderBy(enrollment.createdAt.desc(), enrollment.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(enrollment.count())
                .from(enrollment)
                .where(enrollment.memberId.eq(memberId))
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }
}
