package com.liveclass.course.repository;

import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.dto.response.CourseResponse;
import com.liveclass.course.dto.response.CourseSummaryResponse;

import java.util.List;
import java.util.Optional;

public interface CourseCustomRepository {

    List<CourseSummaryResponse> findSummaries(CourseStatus status);

    Optional<CourseResponse> findDetail(Long courseId);
}
