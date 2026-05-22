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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private static final List<EnrollmentStatus> ACTIVE_STATUSES =
            List.of(EnrollmentStatus.WAITING, EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);
    private static final Duration CANCELLATION_WINDOW = Duration.ofDays(7);

    private final CourseRepository courseRepository;
    private final CourseEnrollCountRepository courseEnrollCountRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 10)
    @Transactional
    public EnrollmentResponse enroll(Long courseId, Long memberId) {
        Course course = getOpenCourse(courseId);
        validateDuplicated(courseId, memberId);
        Enrollment enrollment = reserveOrEnqueue(course, memberId);
        return EnrollmentResponse.from(saveOrThrowDuplicate(enrollment));
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 10)
    @Transactional
    public EnrollmentResponse confirm(Long enrollmentId, Long memberId) {
        Enrollment enrollment = getEnrollment(enrollmentId);
        enrollment.verifyOwner(memberId);
        enrollment.confirm(LocalDateTime.now());
        return EnrollmentResponse.from(enrollment);
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 10)
    @Transactional
    public EnrollmentResponse cancel(Long enrollmentId, Long memberId) {
        Enrollment enrollment = getEnrollment(enrollmentId);
        enrollment.verifyOwner(memberId);
        EnrollmentStatus before = enrollment.getStatus();
        enrollment.cancel(LocalDateTime.now(), CANCELLATION_WINDOW);
        if (before != EnrollmentStatus.WAITING) {
            promoteOrRelease(enrollment.getCourseId());
        }
        return EnrollmentResponse.from(enrollment);
    }

    private void promoteOrRelease(Long courseId) {
        Optional<Enrollment> oldestWaiting = enrollmentRepository
                .findFirstByCourseIdAndStatusOrderByCreatedAtAsc(courseId, EnrollmentStatus.WAITING);
        if (oldestWaiting.isPresent()) {
            oldestWaiting.get().promote();
            return;
        }
        CourseEnrollCount enrollCount = courseEnrollCountRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
        enrollCount.release();
    }

    private Enrollment getEnrollment(Long enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(EnrollmentErrorInfo.ENROLLMENT_NOT_FOUND));
    }

    private Course getOpenCourse(Long courseId) {
        Course course = getCourse(courseId);
        if (!course.isOpen()) {
            throw new BusinessException(EnrollmentErrorInfo.COURSE_NOT_OPEN);
        }
        return course;
    }

    private void validateDuplicated(Long courseId, Long memberId) {
        if (enrollmentRepository.existsByCourseIdAndMemberIdAndStatusIn(courseId, memberId, ACTIVE_STATUSES)) {
            throw new BusinessException(EnrollmentErrorInfo.DUPLICATE_ENROLLMENT);
        }
    }

    private Enrollment reserveOrEnqueue(Course course, Long memberId) {
        CourseEnrollCount enrollCount = courseEnrollCountRepository.findById(course.getId())
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
        return enrollCount.tryReserve(course.getCapacity())
                ? Enrollment.createPending(course.getId(), memberId)
                : Enrollment.createWaiting(course.getId(), memberId);
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
