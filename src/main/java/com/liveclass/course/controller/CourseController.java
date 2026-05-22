package com.liveclass.course.controller;

import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.dto.request.CourseCreateRequest;
import com.liveclass.course.dto.request.CourseStatusUpdateRequest;
import com.liveclass.course.dto.response.CourseResponse;
import com.liveclass.course.dto.response.CourseSummaryResponse;
import com.liveclass.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    public ResponseEntity<CourseResponse> create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid CourseCreateRequest request
    ) {
        CourseResponse response = courseService.create(userId, request);
        return ResponseEntity
                .created(URI.create("/api/courses/" + response.id()))
                .body(response);
    }

    @PatchMapping("/{courseId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid CourseStatusUpdateRequest request
    ) {
        courseService.updateStatus(courseId, userId, request.status());
    }

    @GetMapping
    public List<CourseSummaryResponse> getList(@RequestParam(required = false) CourseStatus status) {
        return courseService.getList(status);
    }

    @GetMapping("/{courseId}")
    public CourseResponse getDetail(@PathVariable Long courseId) {
        return courseService.getDetail(courseId);
    }
}
