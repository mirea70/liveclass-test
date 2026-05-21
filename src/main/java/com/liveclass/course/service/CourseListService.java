package com.liveclass.course.service;

import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.dto.response.CourseSummaryResponse;
import com.liveclass.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseListService {

    private final CourseRepository courseRepository;

    public List<CourseSummaryResponse> list(CourseStatus status) {
        return courseRepository.findSummaries(status);
    }
}
