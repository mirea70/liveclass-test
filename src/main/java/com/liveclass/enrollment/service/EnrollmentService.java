package com.liveclass.enrollment.service;

import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.common.error.info.EnrollmentErrorInfo;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.common.dto.PageResponse;
import com.liveclass.enrollment.dto.response.EnrollmentResponse;
import com.liveclass.enrollment.dto.response.MyEnrollmentResponse;
import com.liveclass.enrollment.dto.response.StudentResponse;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.outbox.domain.entity.OutboxEvent;
import com.liveclass.outbox.domain.entity.OutboxEventType;
import com.liveclass.outbox.repository.OutboxEventRepository;
import com.liveclass.reservation.domain.entity.CourseReservation;
import com.liveclass.reservation.repository.CourseReservationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private static final Duration CANCELLATION_WINDOW = Duration.ofDays(7);

    private final CourseRepository courseRepository;
    private final CourseEnrollCountRepository courseEnrollCountRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseReservationRepository courseReservationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final EntityManager entityManager;

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 10)
    @Transactional
    public EnrollmentResponse enroll(Long courseId, Long memberId) {
        Course course = getOpenCourse(courseId);

        saveReservationOrThrowDuplicate(courseId, memberId);
        Enrollment enrollment = reserve(course, memberId);
        return EnrollmentResponse.from(enrollmentRepository.save(enrollment));
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
        enrollment.cancel(LocalDateTime.now(), CANCELLATION_WINDOW);

        releaseSeat(enrollment.getCourseId());
        courseReservationRepository.deleteByCourseIdAndMemberId(enrollment.getCourseId(), memberId);

        outboxEventRepository.save(
                OutboxEvent.of(OutboxEventType.ENROLLMENT_CANCELLED, enrollment.getCourseId())
        );
        return EnrollmentResponse.from(enrollment);
    }

    private void releaseSeat(Long courseId) {
        CourseEnrollCount enrollCount = courseEnrollCountRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
        enrollCount.release();
    }

    public PageResponse<MyEnrollmentResponse> getMyEnrollments(Long memberId, Pageable pageable) {
        return PageResponse.from(enrollmentRepository.findMyEnrollments(memberId, pageable));
    }

    public List<StudentResponse> getStudentsByCourse(Long courseId, Long requesterId) {
        Course course = getCourse(courseId);
        if (!course.getCreatorId().equals(requesterId)) {
            throw new BusinessException(CourseErrorInfo.NOT_COURSE_CREATOR);
        }
        return enrollmentRepository.findStudentsByCourse(courseId);
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

        entityManager.lock(course, LockModeType.OPTIMISTIC);
        return course;
    }

    private Enrollment reserve(Course course, Long memberId) {
        CourseEnrollCount enrollCount = courseEnrollCountRepository.findById(course.getId())
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
        if (!enrollCount.tryReserve(course.getCapacity())) {
            throw new BusinessException(EnrollmentErrorInfo.COURSE_CAPACITY_FULL);
        }
        return Enrollment.createNew(course.getId(), memberId);
    }

    private void saveReservationOrThrowDuplicate(Long courseId, Long memberId) {
        try {
            courseReservationRepository.save(CourseReservation.createNew(courseId, memberId));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(EnrollmentErrorInfo.DUPLICATE_ENROLLMENT);
        }
    }

    private Course getCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
    }
}
