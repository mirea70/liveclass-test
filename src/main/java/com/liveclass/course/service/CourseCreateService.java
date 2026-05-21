package com.liveclass.course.service;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.course.dto.request.CourseCreateRequest;
import com.liveclass.course.dto.response.CourseCreateResponse;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseCreateService {

    private final CourseRepository courseRepository;
    private final CourseEnrollCountRepository courseEnrollCountRepository;

    @Transactional
    public CourseCreateResponse create(Long creatorId, CourseCreateRequest request) {
        Course course = courseRepository.save(
                Course.createNew(
                        creatorId,
                        request.title(),
                        request.description(),
                        new Money(request.price()),
                        request.capacity(),
                        new CoursePeriod(request.startDate(), request.endDate())
                )
        );
        CourseEnrollCount countEntity = courseEnrollCountRepository.save(CourseEnrollCount.createNew(course.getId()));
        return CourseCreateResponse.from(course, countEntity.getCount());
    }
}
