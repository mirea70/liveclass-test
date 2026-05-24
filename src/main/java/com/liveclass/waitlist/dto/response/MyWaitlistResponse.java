package com.liveclass.waitlist.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MyWaitlistResponse(
        Long waitlistId,
        Long courseId,
        String courseTitle,
        Long price,
        LocalDate startDate,
        LocalDate endDate,
        int orderNum,
        LocalDateTime createdAt
) {
}
