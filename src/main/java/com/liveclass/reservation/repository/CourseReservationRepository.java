package com.liveclass.reservation.repository;

import com.liveclass.reservation.domain.entity.CourseReservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseReservationRepository extends JpaRepository<CourseReservation, Long> {

    boolean existsByCourseIdAndMemberId(Long courseId, Long memberId);

    void deleteByCourseIdAndMemberId(Long courseId, Long memberId);
}
