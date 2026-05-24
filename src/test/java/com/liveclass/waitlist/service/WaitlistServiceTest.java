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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@DisplayName("WaitlistService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    private static final Long COURSE_ID = 1L;
    private static final Long MEMBER_ID = 200L;
    private static final Long WAITLIST_ID = 55L;
    private static final List<EnrollmentStatus> ACTIVE_ENROLLMENT_STATUSES =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

    @InjectMocks
    private WaitlistService waitlistService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private WaitlistRepository waitlistRepository;

    @Test
    @DisplayName("OPEN 강의의 대기열이 비어있으면 order_num=1로 등록된다")
    void registersAtFirstPosition_whenWaitlistIsEmpty() {
        // given
        Course course = createOpenCourse();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(enrollmentRepository.existsByCourseIdAndMemberIdAndStatusIn(
                COURSE_ID, MEMBER_ID, ACTIVE_ENROLLMENT_STATUSES)).willReturn(false);
        given(waitlistRepository.existsByCourseIdAndMemberId(COURSE_ID, MEMBER_ID)).willReturn(false);
        given(waitlistRepository.findMaxOrderNumByCourseId(COURSE_ID)).willReturn(0);
        given(waitlistRepository.save(any(Waitlist.class))).willAnswer(invocation -> {
            Waitlist w = invocation.getArgument(0);
            ReflectionTestUtils.setField(w, "id", WAITLIST_ID);
            return w;
        });

        // when
        WaitlistResponse response = waitlistService.register(COURSE_ID, MEMBER_ID);

        // then
        assertThat(response.id()).isEqualTo(WAITLIST_ID);
        assertThat(response.courseId()).isEqualTo(COURSE_ID);
        assertThat(response.memberId()).isEqualTo(MEMBER_ID);
        assertThat(response.orderNum()).isEqualTo(1);
    }

    @Test
    @DisplayName("기존 대기자가 있으면 order_num = max + 1로 등록된다")
    void registersAtNextPosition_whenWaitlistHasEntries() {
        // given
        Course course = createOpenCourse();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(enrollmentRepository.existsByCourseIdAndMemberIdAndStatusIn(
                COURSE_ID, MEMBER_ID, ACTIVE_ENROLLMENT_STATUSES)).willReturn(false);
        given(waitlistRepository.existsByCourseIdAndMemberId(COURSE_ID, MEMBER_ID)).willReturn(false);
        given(waitlistRepository.findMaxOrderNumByCourseId(COURSE_ID)).willReturn(5);
        given(waitlistRepository.save(any(Waitlist.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        WaitlistResponse response = waitlistService.register(COURSE_ID, MEMBER_ID);

        // then
        assertThat(response.orderNum()).isEqualTo(6);
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 COURSE_NOT_FOUND BusinessException이 발생한다")
    void throwsCourseNotFound_whenCourseDoesNotExist() {
        // given
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> waitlistService.register(COURSE_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.COURSE_NOT_FOUND);
    }

    @Test
    @DisplayName("강의가 OPEN이 아니면 COURSE_NOT_OPEN BusinessException이 발생한다")
    void throwsCourseNotOpen_whenCourseNotOpen() {
        // given
        Course course = createDraftCourse();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));

        // when & then
        assertThatThrownBy(() -> waitlistService.register(COURSE_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(EnrollmentErrorInfo.COURSE_NOT_OPEN);
    }

    @Test
    @DisplayName("동일 사용자의 활성 enrollment가 존재하면 DUPLICATE_ENROLLMENT BusinessException이 발생한다")
    void throwsDuplicateEnrollment_whenActiveEnrollmentExists() {
        // given
        Course course = createOpenCourse();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(enrollmentRepository.existsByCourseIdAndMemberIdAndStatusIn(
                COURSE_ID, MEMBER_ID, ACTIVE_ENROLLMENT_STATUSES)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> waitlistService.register(COURSE_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(EnrollmentErrorInfo.DUPLICATE_ENROLLMENT);
    }

    @Test
    @DisplayName("동일 사용자가 이미 대기 중이면 DUPLICATE_WAITLIST BusinessException이 발생한다")
    void throwsDuplicateWaitlist_whenAlreadyInWaitlist() {
        // given
        Course course = createOpenCourse();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(enrollmentRepository.existsByCourseIdAndMemberIdAndStatusIn(
                COURSE_ID, MEMBER_ID, ACTIVE_ENROLLMENT_STATUSES)).willReturn(false);
        given(waitlistRepository.existsByCourseIdAndMemberId(COURSE_ID, MEMBER_ID)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> waitlistService.register(COURSE_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(WaitlistErrorInfo.DUPLICATE_WAITLIST);
    }

    @Test
    @DisplayName("save 시 DataIntegrityViolationException이 발생하면 DUPLICATE_WAITLIST BusinessException으로 변환된다")
    void throwsDuplicateWaitlist_whenSaveViolatesUniqueConstraint() {
        // given
        Course course = createOpenCourse();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(enrollmentRepository.existsByCourseIdAndMemberIdAndStatusIn(
                COURSE_ID, MEMBER_ID, ACTIVE_ENROLLMENT_STATUSES)).willReturn(false);
        given(waitlistRepository.existsByCourseIdAndMemberId(COURSE_ID, MEMBER_ID)).willReturn(false);
        given(waitlistRepository.findMaxOrderNumByCourseId(COURSE_ID)).willReturn(0);
        given(waitlistRepository.save(any(Waitlist.class)))
                .willThrow(new DataIntegrityViolationException("unique constraint"));

        // when & then
        assertThatThrownBy(() -> waitlistService.register(COURSE_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(WaitlistErrorInfo.DUPLICATE_WAITLIST);
    }

    private Course createDraftCourse() {
        Course course = Course.createNew(
                100L, "Spring Boot 마스터", "Spring Boot 실전",
                99_000L, 1,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        return course;
    }

    private Course createOpenCourse() {
        Course course = Course.createNew(
                100L, "Spring Boot 마스터", "Spring Boot 실전",
                99_000L, 1,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
        course.open();
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        return course;
    }
}
