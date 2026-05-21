package com.liveclass.course.dto.request;

import com.liveclass.course.domain.entity.CourseStatus;
import jakarta.validation.constraints.NotNull;

public record CourseStatusUpdateRequest(

        @NotNull
        CourseStatus status
) {
}
