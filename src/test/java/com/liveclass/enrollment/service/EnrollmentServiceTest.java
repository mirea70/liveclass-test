package com.liveclass.enrollment.service;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.common.error.info.EnrollmentErrorInfo;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.dto.response.EnrollmentResponse;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@DisplayName("EnrollmentService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    private static final Long COURSE_ID = 1L;
    private static final Long MEMBER_ID = 200L;
    private static final Long OTHER_MEMBER_ID = 999L;
    private static final Long ENROLLMENT_ID = 10L;

    @InjectMocks
    private EnrollmentService enrollmentService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Test
    @DisplayName("정원이 남아있으면 PENDING 상태로 등록되고 count가 +1된다")
    void registersAsPending_whenCapacityAvailable() {
        // given
        Course course = createOpenCourse(30);
        CourseEnrollCount count = CourseEnrollCount.createNew(COURSE_ID);
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(courseEnrollCountRepository.findById(COURSE_ID)).willReturn(Optional.of(count));
        given(enrollmentRepository.save(any(Enrollment.class))).willAnswer(invocation -> {
            Enrollment e = invocation.getArgument(0);
            ReflectionTestUtils.setField(e, "id", ENROLLMENT_ID);
            return e;
        });

        // when
        EnrollmentResponse response = enrollmentService.enroll(COURSE_ID, MEMBER_ID);

        // then
        assertThat(response.id()).isEqualTo(ENROLLMENT_ID);
        assertThat(response.courseId()).isEqualTo(COURSE_ID);
        assertThat(response.memberId()).isEqualTo(MEMBER_ID);
        assertThat(response.status()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(count.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("정원이 차있으면 WAITING 상태로 등록되고 count는 변하지 않는다")
    void registersAsWaiting_whenCapacityFull() {
        // given
        Course course = createOpenCourse(1);
        CourseEnrollCount count = CourseEnrollCount.createNew(COURSE_ID);
        count.tryReserve(1);
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(courseEnrollCountRepository.findById(COURSE_ID)).willReturn(Optional.of(count));
        given(enrollmentRepository.save(any(Enrollment.class))).willAnswer(invocation -> {
            Enrollment e = invocation.getArgument(0);
            ReflectionTestUtils.setField(e, "id", ENROLLMENT_ID);
            return e;
        });

        // when
        EnrollmentResponse response = enrollmentService.enroll(COURSE_ID, MEMBER_ID);

        // then
        assertThat(response.status()).isEqualTo(EnrollmentStatus.WAITING);
        assertThat(count.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("강의가 OPEN이 아니면 COURSE_NOT_OPEN BusinessException이 발생한다")
    void throwsCourseNotOpen_whenCourseNotOpen() {
        // given
        Course course = createDraftCourse(30);
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));

        // when & then
        assertThatThrownBy(() -> enrollmentService.enroll(COURSE_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(EnrollmentErrorInfo.COURSE_NOT_OPEN);
    }

    @Test
    @DisplayName("동일 사용자의 활성 신청이 존재하면 DUPLICATE_ENROLLMENT BusinessException이 발생한다")
    void throwsDuplicateEnrollment_whenActiveEnrollmentExists() {
        // given
        Course course = createOpenCourse(30);
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(enrollmentRepository.existsByCourseIdAndMemberIdAndStatusIn(
                COURSE_ID, MEMBER_ID,
                List.of(EnrollmentStatus.WAITING, EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED)))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> enrollmentService.enroll(COURSE_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(EnrollmentErrorInfo.DUPLICATE_ENROLLMENT);
    }

    @Test
    @DisplayName("save 시 DataIntegrityViolationException이 발생하면 DUPLICATE_ENROLLMENT BusinessException으로 변환된다")
    void throwsDuplicateEnrollment_whenSaveViolatesUniqueConstraint() {
        // given
        Course course = createOpenCourse(30);
        CourseEnrollCount count = CourseEnrollCount.createNew(COURSE_ID);
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(courseEnrollCountRepository.findById(COURSE_ID)).willReturn(Optional.of(count));
        given(enrollmentRepository.save(any(Enrollment.class)))
                .willThrow(new DataIntegrityViolationException("unique constraint"));

        // when & then
        assertThatThrownBy(() -> enrollmentService.enroll(COURSE_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(EnrollmentErrorInfo.DUPLICATE_ENROLLMENT);
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 COURSE_NOT_FOUND BusinessException이 발생한다")
    void throwsCourseNotFound_whenCourseDoesNotExist() {
        // given
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> enrollmentService.enroll(COURSE_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.COURSE_NOT_FOUND);
    }

    @Nested
    @DisplayName("결제 확정 (confirm)")
    class Confirm {

        @Test
        @DisplayName("PENDING 신청에 본인이 confirm을 요청하면 CONFIRMED로 전이되고 confirmedAt이 설정된다")
        void transitionsToConfirmed_whenValidPendingRequest() {
            // given
            Long enrollmentId = 10L;
            Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));

            // when
            EnrollmentResponse response = enrollmentService.confirm(enrollmentId, MEMBER_ID);

            // then
            assertThat(response.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
            assertThat(response.confirmedAt()).isNotNull();
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
            assertThat(enrollment.getConfirmedAt()).isNotNull();
        }

        @Test
        @DisplayName("신청자 본인이 아니면 NOT_ENROLLMENT_OWNER BusinessException이 발생한다")
        void throwsNotEnrollmentOwner_whenRequesterIsNotOwner() {
            // given
            Long enrollmentId = 10L;
            Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));

            // when & then
            assertThatThrownBy(() -> enrollmentService.confirm(enrollmentId, OTHER_MEMBER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorInfo())
                    .isEqualTo(EnrollmentErrorInfo.NOT_ENROLLMENT_OWNER);
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        }

        @Test
        @DisplayName("PENDING이 아닌 신청에 confirm을 요청하면 NOT_CONFIRMABLE_STATUS BusinessException이 발생한다")
        void throwsNotConfirmableStatus_whenEnrollmentIsNotPending() {
            // given
            Long enrollmentId = 10L;
            Enrollment enrollment = Enrollment.createWaiting(COURSE_ID, MEMBER_ID);
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));

            // when & then
            assertThatThrownBy(() -> enrollmentService.confirm(enrollmentId, MEMBER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorInfo())
                    .isEqualTo(EnrollmentErrorInfo.NOT_CONFIRMABLE_STATUS);
        }

        @Test
        @DisplayName("신청이 존재하지 않으면 ENROLLMENT_NOT_FOUND BusinessException이 발생한다")
        void throwsEnrollmentNotFound_whenEnrollmentDoesNotExist() {
            // given
            Long enrollmentId = 9999L;
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> enrollmentService.confirm(enrollmentId, MEMBER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorInfo())
                    .isEqualTo(EnrollmentErrorInfo.ENROLLMENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("수강 취소 (cancel)")
    class Cancel {

        @Test
        @DisplayName("WAITING 신청을 본인이 취소하면 CANCELLED로 전이되고 count는 변경되지 않는다")
        void cancelsWaiting_whenOwnerRequests() {
            // given
            Long enrollmentId = 10L;
            Enrollment enrollment = Enrollment.createWaiting(COURSE_ID, MEMBER_ID);
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));

            // when
            EnrollmentResponse response = enrollmentService.cancel(enrollmentId, MEMBER_ID);

            // then
            assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(response.cancelledAt()).isNotNull();
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("PENDING 신청을 취소하고 대기자가 없으면 count가 1 감소한다")
        void decreasesCount_whenPendingCancelledAndNoWaiting() {
            // given
            Long enrollmentId = 10L;
            Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
            CourseEnrollCount enrollCount = CourseEnrollCount.createNew(COURSE_ID);
            enrollCount.tryReserve(30);
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));
            given(enrollmentRepository.findFirstByCourseIdAndStatusOrderByCreatedAtAsc(COURSE_ID, EnrollmentStatus.WAITING))
                    .willReturn(Optional.empty());
            given(courseEnrollCountRepository.findById(COURSE_ID)).willReturn(Optional.of(enrollCount));

            // when
            enrollmentService.cancel(enrollmentId, MEMBER_ID);

            // then
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(enrollCount.getCount()).isZero();
        }

        @Test
        @DisplayName("CONFIRMED 신청도 7일 이내라면 취소되고 정원 정리 흐름이 동작한다")
        void cancelsConfirmed_whenWithinCancellationWindow() {
            // given
            Long enrollmentId = 10L;
            Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
            enrollment.confirm(LocalDateTime.now().minusDays(3));
            CourseEnrollCount enrollCount = CourseEnrollCount.createNew(COURSE_ID);
            enrollCount.tryReserve(30);
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));
            given(enrollmentRepository.findFirstByCourseIdAndStatusOrderByCreatedAtAsc(COURSE_ID, EnrollmentStatus.WAITING))
                    .willReturn(Optional.empty());
            given(courseEnrollCountRepository.findById(COURSE_ID)).willReturn(Optional.of(enrollCount));

            // when
            enrollmentService.cancel(enrollmentId, MEMBER_ID);

            // then
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(enrollCount.getCount()).isZero();
        }

        @Test
        @DisplayName("CONFIRMED 신청이 7일을 초과했으면 CANCELLATION_WINDOW_EXPIRED BusinessException이 발생한다")
        void throwsCancellationWindowExpired_whenConfirmedTooLongAgo() {
            // given
            Long enrollmentId = 10L;
            Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
            enrollment.confirm(LocalDateTime.now().minusDays(8));
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));

            // when & then
            assertThatThrownBy(() -> enrollmentService.cancel(enrollmentId, MEMBER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorInfo())
                    .isEqualTo(EnrollmentErrorInfo.CANCELLATION_WINDOW_EXPIRED);
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        }

        @Test
        @DisplayName("PENDING 신청을 취소하고 대기자가 있으면 가장 오래된 WAITING이 PENDING으로 승격되고 count는 유지된다")
        void promotesOldestWaiting_whenPendingCancelledAndWaitingExists() {
            // given
            Long enrollmentId = 10L;
            Enrollment cancelled = Enrollment.createPending(COURSE_ID, MEMBER_ID);
            Enrollment oldestWaiting = Enrollment.createWaiting(COURSE_ID, 300L);
            CourseEnrollCount enrollCount = CourseEnrollCount.createNew(COURSE_ID);
            enrollCount.tryReserve(30);
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(cancelled));
            given(enrollmentRepository.findFirstByCourseIdAndStatusOrderByCreatedAtAsc(COURSE_ID, EnrollmentStatus.WAITING))
                    .willReturn(Optional.of(oldestWaiting));

            // when
            enrollmentService.cancel(enrollmentId, MEMBER_ID);

            // then
            assertThat(cancelled.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(oldestWaiting.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(enrollCount.getCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("신청자 본인이 아니면 NOT_ENROLLMENT_OWNER BusinessException이 발생한다")
        void throwsNotEnrollmentOwner_whenRequesterIsNotOwner() {
            // given
            Long enrollmentId = 10L;
            Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));

            // when & then
            assertThatThrownBy(() -> enrollmentService.cancel(enrollmentId, OTHER_MEMBER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorInfo())
                    .isEqualTo(EnrollmentErrorInfo.NOT_ENROLLMENT_OWNER);
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        }

        @Test
        @DisplayName("신청이 존재하지 않으면 ENROLLMENT_NOT_FOUND BusinessException이 발생한다")
        void throwsEnrollmentNotFound_whenEnrollmentDoesNotExist() {
            // given
            Long enrollmentId = 9999L;
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> enrollmentService.cancel(enrollmentId, MEMBER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorInfo())
                    .isEqualTo(EnrollmentErrorInfo.ENROLLMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 CANCELLED 상태이면 ALREADY_CANCELLED BusinessException이 발생한다")
        void throwsAlreadyCancelled_whenAlreadyCancelled() {
            // given
            Long enrollmentId = 10L;
            Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
            enrollment.cancel(LocalDateTime.now().minusDays(1), java.time.Duration.ofDays(7));
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));

            // when & then
            assertThatThrownBy(() -> enrollmentService.cancel(enrollmentId, MEMBER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorInfo())
                    .isEqualTo(EnrollmentErrorInfo.ALREADY_CANCELLED);
        }
    }

    private Course createDraftCourse(int capacity) {
        Course course = Course.createNew(
                100L, "Spring Boot 마스터", "Spring Boot 실전",
                new Money(99_000L), capacity,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)));
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        return course;
    }

    private Course createOpenCourse(int capacity) {
        Course course = Course.createNew(
                100L, "Spring Boot 마스터", "Spring Boot 실전",
                new Money(99_000L), capacity,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)));
        course.open();
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        return course;
    }
}
