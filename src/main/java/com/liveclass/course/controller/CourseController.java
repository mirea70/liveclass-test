package com.liveclass.course.controller;

import com.liveclass.course.dto.request.CourseCreateRequest;
import com.liveclass.course.dto.response.CourseCreateResponse;
import com.liveclass.course.service.CourseCreateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseCreateResponse create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid CourseCreateRequest request
    ) {
        return courseCreateService.create(userId, request);
    }
}
