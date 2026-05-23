package com.liveclass.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CourseCreateRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        String description,

        @NotNull
        @PositiveOrZero
        Long price,

        @NotNull
        @Positive
        Integer capacity,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate
) {
}
