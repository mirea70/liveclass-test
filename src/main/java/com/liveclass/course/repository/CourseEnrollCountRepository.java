package com.liveclass.course.repository;

import com.liveclass.course.domain.entity.CourseEnrollCount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseEnrollCountRepository extends JpaRepository<CourseEnrollCount, Long> {
}
