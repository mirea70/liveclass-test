package com.liveclass.course.domain.vo;

import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.CourseErrorInfo;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoursePeriod {

    private LocalDate startDate;
    private LocalDate endDate;

    public CoursePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new DomainException(CourseErrorInfo.COURSE_PERIOD_INVALID);
        }
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
