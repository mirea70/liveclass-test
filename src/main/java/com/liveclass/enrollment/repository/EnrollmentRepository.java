package com.liveclass.enrollment.repository;

import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByCourseIdAndUserIdAndStatusIn(Long courseId, Long userId, Collection<EnrollmentStatus> statuses);
}
