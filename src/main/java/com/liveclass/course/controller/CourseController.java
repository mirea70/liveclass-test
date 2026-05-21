package com.liveclass.course.controller;

import com.liveclass.course.dto.request.CourseCreateRequest;
import com.liveclass.course.dto.request.CourseStatusUpdateRequest;
import com.liveclass.course.dto.response.CourseResponse;
import com.liveclass.course.service.CourseCreateService;
import com.liveclass.course.service.CourseStatusUpdateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseCreateService courseCreateService;
    private final CourseStatusUpdateService courseStatusUpdateService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid CourseCreateRequest request
    ) {
        return courseCreateService.create(userId, request);
    }

    @PatchMapping("/{courseId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid CourseStatusUpdateRequest request
    ) {
        courseStatusUpdateService.updateStatus(courseId, userId, request.status());
    }
}
