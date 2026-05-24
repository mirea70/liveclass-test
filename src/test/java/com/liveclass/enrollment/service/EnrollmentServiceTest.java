package com.liveclass.enrollment.service;

import com.liveclass.common.dto.PageResponse;
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
import com.liveclass.enrollment.dto.response.MyEnrollmentResponse;
import com.liveclass.enrollment.dto.response.StudentResponse;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.outbox.domain.entity.OutboxEvent;
import com.liveclass.outbox.domain.entity.OutboxEventType;
import com.liveclass.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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

    @Mock
    private OutboxEventRepository outboxEventRepository;

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
    @DisplayName("정원이 차있으면 COURSE_CAPACITY_FULL BusinessException이 발생한다")
    void throwsCourseCapacityFull_whenCapacityFull() {
        // given
        Course course = createOpenCourse(1);
        CourseEnrollCount count = CourseEnrollCount.createNew(COURSE_ID);
        count.tryReserve(1);
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(courseEnrollCountRepository.findById(COURSE_ID)).willReturn(Optional.of(count));

        // when & then
        assertThatThrownBy(() -> enrollmentService.enroll(COURSE_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(EnrollmentErrorInfo.COURSE_CAPACITY_FULL);
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
                List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED)))
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
            Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
            enrollment.cancel(LocalDateTime.now(), java.time.Duration.ofDays(7));
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));

            // when & then (CANCELLED 상태는 confirm 불가)
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
        @DisplayName("PENDING 신청을 취소하면 카운터가 즉시 1 감소하고 수강신청 취소 이벤트가 발행된다")
        void releasesSeatAndPublishesEvent_whenPendingCancelled() {
            // given
            Long enrollmentId = 10L;
            Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
            CourseEnrollCount enrollCount = CourseEnrollCount.createNew(COURSE_ID);
            enrollCount.tryReserve(30);
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));
            given(courseEnrollCountRepository.findById(COURSE_ID)).willReturn(Optional.of(enrollCount));

            // when
            enrollmentService.cancel(enrollmentId, MEMBER_ID);

            // then
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(enrollCount.getCount()).isZero();
            ArgumentCaptor<OutboxEvent> captor =
                    org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(OutboxEventType.ENROLLMENT_CANCELLED);
            assertThat(captor.getValue().getDomainId()).isEqualTo(COURSE_ID);
        }

        @Test
        @DisplayName("CONFIRMED 신청도 7일 이내라면 취소되고 카운터가 1 감소하며 outbox 이벤트가 발행된다")
        void cancelsConfirmedAndPublishesEvent_whenWithinCancellationWindow() {
            // given
            Long enrollmentId = 10L;
            Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
            enrollment.confirm(LocalDateTime.now().minusDays(3));
            CourseEnrollCount enrollCount = CourseEnrollCount.createNew(COURSE_ID);
            enrollCount.tryReserve(30);
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));
            given(courseEnrollCountRepository.findById(COURSE_ID)).willReturn(Optional.of(enrollCount));

            // when
            enrollmentService.cancel(enrollmentId, MEMBER_ID);

            // then
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(enrollCount.getCount()).isZero();
            verify(outboxEventRepository).save(
                    org.mockito.ArgumentMatchers.any(OutboxEvent.class));
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

    @Nested
    @DisplayName("강의별 수강생 조회 (getStudentsByCourse)")
    class GetStudentsByCourse {

        @Test
        @DisplayName("크리에이터 본인이 호출하면 Repository 결과를 그대로 반환한다")
        void returnsRepositoryResult_whenCalledByCreator() {
            // given
            Long creatorId = 100L;
            Course course = createCourseOwnedBy(creatorId);
            List<StudentResponse> expected = List.of(
                    new StudentResponse(200L, "홍길동", EnrollmentStatus.CONFIRMED,
                            LocalDateTime.of(2026, 5, 19, 10, 0), LocalDateTime.of(2026, 5, 18, 9, 0)),
                    new StudentResponse(201L, "이몽룡", EnrollmentStatus.PENDING,
                            null, LocalDateTime.of(2026, 5, 20, 9, 0))
            );
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
            given(enrollmentRepository.findStudentsByCourse(COURSE_ID)).willReturn(expected);

            // when
            List<StudentResponse> result = enrollmentService.getStudentsByCourse(COURSE_ID, creatorId);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("요청자가 크리에이터가 아니면 NOT_COURSE_CREATOR BusinessException이 발생한다")
        void throwsNotCourseCreator_whenRequesterIsNotCreator() {
            // given
            Long creatorId = 100L;
            Long otherRequester = 999L;
            Course course = createCourseOwnedBy(creatorId);
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));

            // when & then
            assertThatThrownBy(() -> enrollmentService.getStudentsByCourse(COURSE_ID, otherRequester))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorInfo())
                    .isEqualTo(CourseErrorInfo.NOT_COURSE_CREATOR);
        }

        @Test
        @DisplayName("강의가 존재하지 않으면 COURSE_NOT_FOUND BusinessException이 발생한다")
        void throwsCourseNotFound_whenCourseDoesNotExist() {
            // given
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> enrollmentService.getStudentsByCourse(COURSE_ID, 100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorInfo())
                    .isEqualTo(CourseErrorInfo.COURSE_NOT_FOUND);
        }

        private Course createCourseOwnedBy(Long creatorId) {
            Course course = Course.createNew(
                    creatorId, "Spring Boot 마스터", "Spring Boot 실전",
                    99_000L, 30,
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
            ReflectionTestUtils.setField(course, "id", COURSE_ID);
            return course;
        }
    }

    @Nested
    @DisplayName("내 수강 신청 목록 조회 (getMyEnrollments)")
    class GetMyEnrollments {

        @Test
        @DisplayName("Repository에서 받은 페이지를 PageResponse로 변환해 반환한다")
        void returnsPageResponse_whenCalled() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            List<MyEnrollmentResponse> myEnrollments = List.of(
                    new MyEnrollmentResponse(10L, 1L, "Spring Boot", 99_000L,
                            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31),
                            EnrollmentStatus.PENDING, null, null)
            );
            Page<MyEnrollmentResponse> page = new PageImpl<>(myEnrollments, pageable, 1);
            given(enrollmentRepository.findMyEnrollments(MEMBER_ID, pageable)).willReturn(page);

            // when
            PageResponse<MyEnrollmentResponse> result = enrollmentService.getMyEnrollments(MEMBER_ID, pageable);

            // then
            assertThat(result.content()).isEqualTo(myEnrollments);
            assertThat(result.page()).isZero();
            assertThat(result.size()).isEqualTo(20);
            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.totalPages()).isEqualTo(1);
        }
    }

    private Course createDraftCourse(int capacity) {
        Course course = Course.createNew(
                100L, "Spring Boot 마스터", "Spring Boot 실전",
                99_000L, capacity,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        return course;
    }

    private Course createOpenCourse(int capacity) {
        Course course = Course.createNew(
                100L, "Spring Boot 마스터", "Spring Boot 실전",
                99_000L, capacity,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
        course.open();
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        return course;
    }
}
