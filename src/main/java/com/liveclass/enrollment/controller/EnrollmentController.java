package com.liveclass.enrollment.controller;

import com.liveclass.enrollment.dto.request.EnrollmentCreateRequest;
import com.liveclass.enrollment.dto.response.EnrollmentResponse;
import com.liveclass.enrollment.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    public ResponseEntity<EnrollmentResponse> enroll(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid EnrollmentCreateRequest request
    ) {
        EnrollmentResponse response = enrollmentService.enroll(request.courseId(), userId);
        return ResponseEntity
                .created(URI.create("/api/enrollments/" + response.id()))
                .body(response);
    }
}
