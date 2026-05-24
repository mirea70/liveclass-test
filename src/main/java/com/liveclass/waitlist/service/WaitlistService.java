package com.liveclass.waitlist.service;

import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.common.error.info.EnrollmentErrorInfo;
import com.liveclass.common.error.info.WaitlistErrorInfo;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.repository.CourseRepository;
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
}
