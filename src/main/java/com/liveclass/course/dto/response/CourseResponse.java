package com.liveclass.course.dto.response;

import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseStatus;

import java.time.LocalDate;

public record CourseResponse(
        Long id,
        Long creatorId,
        String title,
        String description,
        long price,
        int capacity,
        int enrollCount,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status
) {

    public static CourseResponse from(Course course, int enrollCount) {
        return new CourseResponse(
                course.getId(),
                course.getCreatorId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice().amount(),
                course.getCapacity(),
                enrollCount,
                course.getPeriod().getStartDate(),
                course.getPeriod().getEndDate(),
                course.getStatus()
        );
    }
}
