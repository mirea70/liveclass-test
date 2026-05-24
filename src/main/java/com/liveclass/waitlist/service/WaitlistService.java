package com.liveclass.waitlist.service;

import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.common.error.info.EnrollmentErrorInfo;
import com.liveclass.common.error.info.WaitlistErrorInfo;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.waitlist.domain.entity.Waitlist;
import com.liveclass.waitlist.dto.response.WaitlistResponse;
import com.liveclass.waitlist.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitlistService {

    private static final List<EnrollmentStatus> ACTIVE_ENROLLMENT_STATUSES =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

    private final CourseRepository courseRepository;
    private final CourseEnrollCountRepository courseEnrollCountRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;

    @Transactional
    public WaitlistResponse register(Long courseId, Long memberId) {
        Course course = getOpenCourse(courseId);
        validateNoActiveEnrollment(course.getId(), memberId);
        validateNoActiveWaitlist(course.getId(), memberId);
        int nextOrderNum = waitlistRepository.findMaxOrderNumByCourseId(course.getId()) + 1;
        try {
            Waitlist saved = waitlistRepository.save(Waitlist.createNew(course.getId(), memberId, nextOrderNum));
            return WaitlistResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(WaitlistErrorInfo.DUPLICATE_WAITLIST);
        }
    }

    private Course getOpenCourse(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
        if (!course.isOpen()) {
            throw new BusinessException(EnrollmentErrorInfo.COURSE_NOT_OPEN);
        }
        return course;
    }

    private void validateNoActiveEnrollment(Long courseId, Long memberId) {
        if (enrollmentRepository.existsByCourseIdAndMemberIdAndStatusIn(
                courseId, memberId, ACTIVE_ENROLLMENT_STATUSES)) {
            throw new BusinessException(EnrollmentErrorInfo.DUPLICATE_ENROLLMENT);
        }
    }

    private void validateNoActiveWaitlist(Long courseId, Long memberId) {
        if (waitlistRepository.existsByCourseIdAndMemberId(courseId, memberId)) {
            throw new BusinessException(WaitlistErrorInfo.DUPLICATE_WAITLIST);
        }
    }

    @Transactional
    public void cancel(Long waitlistId, Long memberId) {
        Waitlist waitlist = waitlistRepository.findById(waitlistId)
                .orElseThrow(() -> new BusinessException(WaitlistErrorInfo.WAITLIST_NOT_FOUND));
        waitlist.verifyOwner(memberId);
        Long courseId = waitlist.getCourseId();
        int deletedOrderNum = waitlist.getOrderNum();
        waitlistRepository.delete(waitlist);
        waitlistRepository.shiftOrderNumDownAfter(courseId, deletedOrderNum);
    }

    @Transactional
    public void promoteOldest(Long courseId) {
        // 가장 오래된 대기자를 PENDING enrollment로 승격시킨다 (cancel 이벤트 처리 시 호출).
        // 그 사이 다른 신청자가 자리를 차지해 정원이 다시 가득 찼다면 승격을 skip.
        Waitlist oldest = waitlistRepository.findOldestByCourseId(courseId).orElse(null);
        if (oldest == null) {
            return;
        }
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
        CourseEnrollCount enrollCount = courseEnrollCountRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
        if (!enrollCount.tryReserve(course.getCapacity())) {
            // 정원이 이미 가득 참 → 대기열 그대로 유지, 다음 자리에서 다시 시도
            return;
        }
        int promotedOrderNum = oldest.getOrderNum();
        Long promotedMemberId = oldest.getMemberId();
        waitlistRepository.delete(oldest);
        waitlistRepository.shiftOrderNumDownAfter(courseId, promotedOrderNum);
        enrollmentRepository.save(Enrollment.createPending(courseId, promotedMemberId));
    }
}
