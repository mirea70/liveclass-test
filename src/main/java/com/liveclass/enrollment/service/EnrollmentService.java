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
import com.liveclass.common.dto.PageResponse;
import com.liveclass.enrollment.dto.response.EnrollmentResponse;
import com.liveclass.enrollment.dto.response.MyEnrollmentResponse;
import com.liveclass.enrollment.dto.response.StudentResponse;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.outbox.domain.entity.OutboxEvent;
import com.liveclass.outbox.domain.entity.OutboxEventType;
import com.liveclass.outbox.repository.OutboxEventRepository;
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

    private static final List<EnrollmentStatus> ACTIVE_STATUSES =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);
    private static final Duration CANCELLATION_WINDOW = Duration.ofDays(7);

    private final CourseRepository courseRepository;
    private final CourseEnrollCountRepository courseEnrollCountRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final EntityManager entityManager;

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 10)
    @Transactional
    public EnrollmentResponse enroll(Long courseId, Long memberId) {
        Course course = getOpenCourse(courseId);
        validateDuplicated(courseId, memberId);
        Enrollment enrollment = reserve(course, memberId);
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
        enrollment.cancel(LocalDateTime.now(), CANCELLATION_WINDOW);

        releaseSeat(enrollment.getCourseId());

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
        // 수강신청 트랜잭션이 OPEN으로 시작했지만 commit 직전 강의가 CLOSED로 전이되는 race를 막기 위해
        // Course의 @Version을 commit 시점에 명시적으로 검증한다. 충돌 시 @Retryable로 재시도되며,
        // 재시도 시 latest 상태가 CLOSED라면 COURSE_NOT_OPEN으로 정상 거부된다.
        entityManager.lock(course, LockModeType.OPTIMISTIC);
        return course;
    }

    private void validateDuplicated(Long courseId, Long memberId) {
        if (enrollmentRepository.existsByCourseIdAndMemberIdAndStatusIn(courseId, memberId, ACTIVE_STATUSES)) {
            throw new BusinessException(EnrollmentErrorInfo.DUPLICATE_ENROLLMENT);
        }
    }

    private Enrollment reserve(Course course, Long memberId) {
        CourseEnrollCount enrollCount = courseEnrollCountRepository.findById(course.getId())
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
        if (!enrollCount.tryReserve(course.getCapacity())) {
            throw new BusinessException(EnrollmentErrorInfo.COURSE_CAPACITY_FULL);
        }
        return Enrollment.createPending(course.getId(), memberId);
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
