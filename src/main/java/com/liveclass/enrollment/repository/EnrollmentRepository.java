package com.liveclass.enrollment.repository;

import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByCourseIdAndMemberIdAndStatusIn(Long courseId, Long memberId, Collection<EnrollmentStatus> statuses);

    Optional<Enrollment> findFirstByCourseIdAndStatusOrderByCreatedAtAsc(Long courseId, EnrollmentStatus status);
}
