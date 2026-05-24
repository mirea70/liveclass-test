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
import com.liveclass.reservation.domain.entity.CourseReservation;
import com.liveclass.reservation.repository.CourseReservationRepository;
import com.liveclass.waitlist.domain.entity.Waitlist;
import com.liveclass.waitlist.dto.response.MyWaitlistResponse;
import com.liveclass.waitlist.dto.response.WaitlistResponse;
import com.liveclass.waitlist.repository.WaitlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @InjectMocks
    private WaitlistService waitlistService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private WaitlistRepository waitlistRepository;

    @Mock
    private CourseReservationRepository courseReservationRepository;

    @Test
    @DisplayName("OPEN 강의의 대기열이 비어있으면 order_num=1로 등록된다")
    void registersAtFirstPosition_whenWaitlistIsEmpty() {
        // given
        Course course = createOpenCourse();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
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
    @DisplayName("이미 활성 상태 수강신청/대기 데이터(reservation row 존재)이 있으면 DB unique 제약 위반으로 DUPLICATE_WAITLIST BusinessException이 발생한다")
    void throwsDuplicateWaitlist_whenReservationConflict() {
        // given
        Course course = createOpenCourse();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(courseReservationRepository.save(any(CourseReservation.class)))
                .willThrow(new DataIntegrityViolationException("uk_course_reservation"));

        // when & then
        assertThatThrownBy(() -> waitlistService.register(COURSE_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(WaitlistErrorInfo.DUPLICATE_WAITLIST);
    }

    @Test
    @DisplayName("대기열 저장 시 DataIntegrityViolationException이 발생하면 DUPLICATE_WAITLIST BusinessException으로 변환된다")
    void throwsDuplicateWaitlist_whenWaitlistSaveViolatesUniqueConstraint() {
        // given
        Course course = createOpenCourse();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(waitlistRepository.findMaxOrderNumByCourseId(COURSE_ID)).willReturn(0);
        given(waitlistRepository.save(any(Waitlist.class)))
                .willThrow(new DataIntegrityViolationException("uk_waitlist_course_order"));

        // when & then
        assertThatThrownBy(() -> waitlistService.register(COURSE_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(WaitlistErrorInfo.DUPLICATE_WAITLIST);
    }

    @Nested
    @DisplayName("대기 취소 (cancel)")
    class Cancel {

        @Test
        @DisplayName("본인이 대기를 취소하면 waitlist가 삭제되고 뒷사람들의 order_num이 한 칸씩 당겨진다")
        void deletesAndShiftsOrderNum_whenOwnerCancels() {
            // given
            Long waitlistId = 55L;
            Waitlist waitlist = Waitlist.createNew(COURSE_ID, MEMBER_ID, 3);
            ReflectionTestUtils.setField(waitlist, "id", waitlistId);
            given(waitlistRepository.findById(waitlistId)).willReturn(Optional.of(waitlist));

            // when
            waitlistService.cancel(waitlistId, MEMBER_ID);

            // then
            org.mockito.Mockito.verify(waitlistRepository).delete(waitlist);
            org.mockito.Mockito.verify(waitlistRepository).shiftOrderNumDownAfter(COURSE_ID, 3);
        }

        @Test
        @DisplayName("대기 신청이 존재하지 않으면 WAITLIST_NOT_FOUND BusinessException이 발생한다")
        void throwsWaitlistNotFound_whenNotFound() {
            // given
            Long waitlistId = 9999L;
            given(waitlistRepository.findById(waitlistId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> waitlistService.cancel(waitlistId, MEMBER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorInfo())
                    .isEqualTo(WaitlistErrorInfo.WAITLIST_NOT_FOUND);
        }

        @Test
        @DisplayName("본인이 아니면 NOT_WAITLIST_OWNER BusinessException이 발생한다")
        void throwsNotWaitlistOwner_whenNotOwner() {
            // given
            Long waitlistId = 55L;
            Long otherMemberId = 999L;
            Waitlist waitlist = Waitlist.createNew(COURSE_ID, MEMBER_ID, 1);
            given(waitlistRepository.findById(waitlistId)).willReturn(Optional.of(waitlist));

            // when & then
            assertThatThrownBy(() -> waitlistService.cancel(waitlistId, otherMemberId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorInfo())
                    .isEqualTo(WaitlistErrorInfo.NOT_WAITLIST_OWNER);
        }
    }

    @Nested
    @DisplayName("내 대기 목록 조회 (getMyWaitlists)")
    class GetMyWaitlists {

        @Test
        @DisplayName("Repository에서 받은 목록을 그대로 반환한다")
        void returnsList_whenCalled() {
            // given
            java.util.List<MyWaitlistResponse> myWaitlists = java.util.List.of(
                    new MyWaitlistResponse(55L, 1L, "Spring Boot", 99_000L,
                            java.time.LocalDate.of(2026, 6, 1), java.time.LocalDate.of(2026, 8, 31),
                            3, java.time.LocalDateTime.of(2026, 5, 20, 9, 0))
            );
            given(waitlistRepository.findMyWaitlists(MEMBER_ID)).willReturn(myWaitlists);

            // when
            java.util.List<MyWaitlistResponse> result = waitlistService.getMyWaitlists(MEMBER_ID);

            // then
            assertThat(result).isEqualTo(myWaitlists);
        }
    }

    @Nested
    @DisplayName("대기자 승격 (promoteOldest)")
    class PromoteOldest {

        @Test
        @DisplayName("대기자가 없으면 아무 작업도 하지 않는다")
        void doesNothing_whenNoWaitlist() {
            // given
            given(waitlistRepository.findOldestByCourseId(COURSE_ID)).willReturn(Optional.empty());

            // when
            waitlistService.promoteOldest(COURSE_ID);

            // then
            org.mockito.Mockito.verifyNoInteractions(enrollmentRepository);
            org.mockito.Mockito.verifyNoInteractions(courseEnrollCountRepository);
            org.mockito.Mockito.verify(waitlistRepository, org.mockito.Mockito.never())
                    .delete(any(Waitlist.class));
        }

        @Test
        @DisplayName("대기자가 있고 정원에 자리가 있으면 가장 오래된 대기자가 승격되고 카운터가 +1된다")
        void promotes_whenSeatAvailable() {
            // given
            Long promotedMemberId = 300L;
            Waitlist oldest = Waitlist.createNew(COURSE_ID, promotedMemberId, 1);
            Course course = createOpenCourse();
            CourseEnrollCount enrollCount = CourseEnrollCount.createNew(COURSE_ID);
            // 1자리는 비어있는 상황 (정원=1, count=0 → A가 방금 취소해서 release한 직후)
            given(waitlistRepository.findOldestByCourseId(COURSE_ID)).willReturn(Optional.of(oldest));
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
            given(courseEnrollCountRepository.findById(COURSE_ID)).willReturn(Optional.of(enrollCount));

            // when
            waitlistService.promoteOldest(COURSE_ID);

            // then
            assertThat(enrollCount.getCount()).isEqualTo(1); // release된 자리를 W가 채움
            org.mockito.Mockito.verify(waitlistRepository).delete(oldest);
            org.mockito.Mockito.verify(waitlistRepository).shiftOrderNumDownAfter(COURSE_ID, 1);
            org.mockito.ArgumentCaptor<Enrollment> captor =
                    org.mockito.ArgumentCaptor.forClass(Enrollment.class);
            org.mockito.Mockito.verify(enrollmentRepository).save(captor.capture());
            assertThat(captor.getValue().getMemberId()).isEqualTo(promotedMemberId);
            assertThat(captor.getValue().getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        }

        @Test
        @DisplayName("정원이 이미 가득 차있으면 (다른 신청자가 자리를 차지함) 승격을 skip한다")
        void skipsPromotion_whenSeatNotAvailable() {
            // given
            Waitlist oldest = Waitlist.createNew(COURSE_ID, 300L, 1);
            Course course = createOpenCourse(); // capacity = 1
            CourseEnrollCount enrollCount = CourseEnrollCount.createNew(COURSE_ID);
            enrollCount.tryReserve(1); // 이미 가득 (다른 신청자가 자리 차지)
            given(waitlistRepository.findOldestByCourseId(COURSE_ID)).willReturn(Optional.of(oldest));
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
            given(courseEnrollCountRepository.findById(COURSE_ID)).willReturn(Optional.of(enrollCount));

            // when
            waitlistService.promoteOldest(COURSE_ID);

            // then: 대기자는 그대로, enrollment INSERT 없음
            assertThat(enrollCount.getCount()).isEqualTo(1);
            org.mockito.Mockito.verify(waitlistRepository, org.mockito.Mockito.never()).delete(any(Waitlist.class));
            org.mockito.Mockito.verify(enrollmentRepository, org.mockito.Mockito.never()).save(any(Enrollment.class));
        }
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
