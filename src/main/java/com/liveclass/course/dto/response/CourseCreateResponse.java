package com.liveclass.course.dto.response;

import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseStatus;

import java.time.LocalDate;

public record CourseCreateResponse(
        Long id,
        Long creatorId,
        String title,
        String description,
        long price,
        int capacity,
        int count,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status
) {

    public static CourseCreateResponse from(Course course, int count) {
        return new CourseCreateResponse(
                course.getId(),
                course.getCreatorId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice().getAmount(),
                course.getCapacity(),
                count,
                course.getPeriod().getStartDate(),
                course.getPeriod().getEndDate(),
                course.getStatus()
        );
    }
}
