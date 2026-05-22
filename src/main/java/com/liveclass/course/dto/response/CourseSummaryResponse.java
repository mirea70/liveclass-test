package com.liveclass.course.dto.response;

import com.liveclass.course.domain.entity.CourseStatus;

import java.time.LocalDate;

public record CourseSummaryResponse(
        Long id,
        String title,
        long price,
        int capacity,
        int enrollCount,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status
) {
}
