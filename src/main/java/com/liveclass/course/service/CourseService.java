package com.liveclass.course.service;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.course.dto.request.CourseCreateRequest;
import com.liveclass.course.dto.response.CourseResponse;
import com.liveclass.course.dto.response.CourseSummaryResponse;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseEnrollCountRepository courseEnrollCountRepository;

    @Transactional
    public CourseResponse create(Long creatorId, CourseCreateRequest request) {
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
        return CourseResponse.from(course, countEntity.getCount());
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 10)
    @Transactional
    public void updateStatus(Long courseId, Long requesterId, CourseStatus targetStatus) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));

        if (!course.getCreatorId().equals(requesterId)) {
            throw new BusinessException(CourseErrorInfo.NOT_COURSE_CREATOR);
        }

        applyTransition(course, targetStatus);
    }

    public List<CourseSummaryResponse> getList(CourseStatus status) {
        return courseRepository.findSummaries(status);
    }

    public CourseResponse getDetail(Long courseId) {
        return courseRepository.findDetail(courseId)
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
    }

    private void applyTransition(Course course, CourseStatus targetStatus) {
        switch (targetStatus) {
            case OPEN -> course.open();
            case CLOSED -> course.close();
            default -> throw new DomainException(CourseErrorInfo.INVALID_STATUS_TRANSITION);
        }
    }
}
