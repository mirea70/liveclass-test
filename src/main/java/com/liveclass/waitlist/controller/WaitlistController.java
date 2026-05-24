package com.liveclass.waitlist.controller;

import com.liveclass.waitlist.dto.request.WaitlistCreateRequest;
import com.liveclass.waitlist.dto.response.WaitlistResponse;
import com.liveclass.waitlist.service.WaitlistService;
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
@RequestMapping("/api/waitlists")
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping
    public ResponseEntity<WaitlistResponse> register(
            @RequestHeader("X-Member-Id") Long memberId,
            @RequestBody @Valid WaitlistCreateRequest request
    ) {
        WaitlistResponse response = waitlistService.register(request.courseId(), memberId);
        return ResponseEntity
                .created(URI.create("/api/waitlists/" + response.id()))
                .body(response);
    }
}
