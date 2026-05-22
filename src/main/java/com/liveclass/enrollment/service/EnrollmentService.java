package com.liveclass.enrollment.service;

import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.common.error.info.EnrollmentErrorInfo;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.dto.response.EnrollmentResponse;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private static final List<EnrollmentStatus> ACTIVE_STATUSES =
            List.of(EnrollmentStatus.WAITING, EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

    private final CourseRepository courseRepository;
    private final CourseEnrollCountRepository courseEnrollCountRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 10)
    @Transactional
    public EnrollmentResponse enroll(Long courseId, Long userId) {
        Course course = getOpenCourse(courseId);
        validateDuplicated(courseId, userId);
        Enrollment enrollment = reserveOrEnqueue(course, userId);
        return EnrollmentResponse.from(saveOrThrowDuplicate(enrollment));
    }

    private Course getOpenCourse(Long courseId) {
        Course course = getCourse(courseId);
        if (!course.isOpen()) {
            throw new BusinessException(EnrollmentErrorInfo.COURSE_NOT_OPEN);
        }
        return course;
    }

    private void validateDuplicated(Long courseId, Long userId) {
        if (enrollmentRepository.existsByCourseIdAndUserIdAndStatusIn(courseId, userId, ACTIVE_STATUSES)) {
            throw new BusinessException(EnrollmentErrorInfo.DUPLICATE_ENROLLMENT);
        }
    }

    private Enrollment reserveOrEnqueue(Course course, Long userId) {
        CourseEnrollCount enrollCount = courseEnrollCountRepository.findById(course.getId())
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
        return enrollCount.tryReserve(course.getCapacity())
                ? Enrollment.createPending(course.getId(), userId)
                : Enrollment.createWaiting(course.getId(), userId);
    }

    private Enrollment saveOrThrowDuplicate(Enrollment enrollment) {
        try {
            return enrollmentRepository.save(enrollment);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(EnrollmentErrorInfo.DUPLICATE_ENROLLMENT);
        }
    }

    private Course getCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
    }
}
