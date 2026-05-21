package com.liveclass.course.repository;

import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.dto.response.CourseSummaryResponse;

import java.util.List;

public interface CourseCustomRepository {

    List<CourseSummaryResponse> findSummaries(CourseStatus status);
}
