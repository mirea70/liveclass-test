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
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.reservation.domain.entity.CourseReservation;
import com.liveclass.reservation.repository.CourseReservationRepository;
import com.liveclass.waitlist.domain.entity.Waitlist;
import com.liveclass.waitlist.dto.response.MyWaitlistResponse;
import com.liveclass.waitlist.dto.response.WaitlistResponse;
import com.liveclass.waitlist.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitlistService {

    private final CourseRepository courseRepository;
    private final CourseEnrollCountRepository courseEnrollCountRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final CourseReservationRepository courseReservationRepository;

    @Transactional
    public WaitlistResponse register(Long courseId, Long memberId) {
        Course course = getOpenCourse(courseId);

        saveReservationOrThrowDuplicate(course.getId(), memberId);
        int nextOrderNum = waitlistRepository.findMaxOrderNumByCourseId(course.getId()) + 1;
        try {
            Waitlist saved = waitlistRepository.save(Waitlist.createNew(course.getId(), memberId, nextOrderNum));
            return WaitlistResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(WaitlistErrorInfo.DUPLICATE_WAITLIST);
        }
    }

    private void saveReservationOrThrowDuplicate(Long courseId, Long memberId) {
        try {
            courseReservationRepository.save(CourseReservation.createNew(courseId, memberId));
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

    @Transactional
    public void cancel(Long waitlistId, Long memberId) {
        Waitlist waitlist = waitlistRepository.findById(waitlistId)
                .orElseThrow(() -> new BusinessException(WaitlistErrorInfo.WAITLIST_NOT_FOUND));
        waitlist.verifyOwner(memberId);
        Long courseId = waitlist.getCourseId();
        int deletedOrderNum = waitlist.getOrderNum();
        waitlistRepository.delete(waitlist);
        waitlistRepository.shiftOrderNumDownAfter(courseId, deletedOrderNum);

        courseReservationRepository.deleteByCourseIdAndMemberId(courseId, memberId);
    }

    public java.util.List<MyWaitlistResponse> getMyWaitlists(Long memberId) {
        return waitlistRepository.findMyWaitlists(memberId);
    }

    @Transactional
    public void promoteOldest(Long courseId) {
        Waitlist oldest = waitlistRepository.findOldestByCourseId(courseId).orElse(null);
        if (oldest == null) {
            return;
        }
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
        CourseEnrollCount enrollCount = courseEnrollCountRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(CourseErrorInfo.COURSE_NOT_FOUND));
        if (!enrollCount.tryReserve(course.getCapacity())) {
            return;
        }
        int promotedOrderNum = oldest.getOrderNum();
        Long promotedMemberId = oldest.getMemberId();
        waitlistRepository.delete(oldest);
        waitlistRepository.shiftOrderNumDownAfter(courseId, promotedOrderNum);

        enrollmentRepository.save(Enrollment.createNew(courseId, promotedMemberId));
    }
}
