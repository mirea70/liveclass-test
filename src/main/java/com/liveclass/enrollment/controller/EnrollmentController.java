package com.liveclass.enrollment.controller;

import com.liveclass.enrollment.dto.request.EnrollmentCreateRequest;
import com.liveclass.enrollment.dto.response.EnrollmentResponse;
import com.liveclass.enrollment.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestHeader("X-Member-Id") Long memberId,
            @RequestBody @Valid EnrollmentCreateRequest request
    ) {
        EnrollmentResponse response = enrollmentService.enroll(request.courseId(), memberId);
        return ResponseEntity
                .created(URI.create("/api/enrollments/" + response.id()))
                .body(response);
    }

    @PostMapping("/{enrollmentId}/confirmation")
    public EnrollmentResponse confirm(
            @PathVariable Long enrollmentId,
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        return enrollmentService.confirm(enrollmentId, memberId);
    }

    @PostMapping("/{enrollmentId}/cancellation")
    public EnrollmentResponse cancel(
            @PathVariable Long enrollmentId,
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        return enrollmentService.cancel(enrollmentId, memberId);
    }
}
