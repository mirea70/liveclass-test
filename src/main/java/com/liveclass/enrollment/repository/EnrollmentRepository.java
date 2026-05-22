package com.liveclass.enrollment.repository;

import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByCourseIdAndUserIdAndStatusIn(Long courseId, Long userId, Collection<EnrollmentStatus> statuses);

    Optional<Enrollment> findFirstByCourseIdAndStatusOrderByCreatedAtAsc(Long courseId, EnrollmentStatus status);
}
