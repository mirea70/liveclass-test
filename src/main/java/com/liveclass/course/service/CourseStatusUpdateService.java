package com.liveclass.course.service;

import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseStatusUpdateService {

    private final CourseRepository courseRepository;

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

    private void applyTransition(Course course, CourseStatus targetStatus) {
        switch (targetStatus) {
            case OPEN -> course.open();
            case CLOSED -> course.close();
            default -> throw new DomainException(CourseErrorInfo.INVALID_STATUS_TRANSITION);
        }
    }
}
