package com.liveclass.course.domain.vo;

import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.CourseErrorInfo;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;

@Embeddable
public record CoursePeriod(LocalDate startDate, LocalDate endDate) {

    public CoursePeriod {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new DomainException(CourseErrorInfo.COURSE_PERIOD_INVALID);
        }
    }
}
