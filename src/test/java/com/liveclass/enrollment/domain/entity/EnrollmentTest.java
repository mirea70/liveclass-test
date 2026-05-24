package com.liveclass.enrollment.domain.entity;

import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.info.EnrollmentErrorInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Enrollment 도메인 테스트")
class EnrollmentTest {

    private static final Long COURSE_ID = 1L;
    private static final Long MEMBER_ID = 200L;
    private static final Long OTHER_MEMBER_ID = 999L;
    private static final Duration CANCELLATION_WINDOW = Duration.ofDays(7);

    @Test
    @DisplayName("createPending으로 생성하면 PENDING 상태이고 시각 필드가 비어있다")
    void startsAsPending_whenCreatedAsPending() {
        // when
        Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);

        // then
        assertThat(enrollment.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(enrollment.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(enrollment.getConfirmedAt()).isNull();
        assertThat(enrollment.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("PENDING 상태에서 confirm()을 호출하면 CONFIRMED로 전이되고 confirmedAt이 설정된다")
    void transitionsToConfirmed_whenConfirmedFromPending() {
        // given
        Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);

        // when
        enrollment.confirm(now);

        // then
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(enrollment.getConfirmedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("PENDING이 아닌 상태에서 confirm()을 호출하면 NOT_CONFIRMABLE_STATUS BusinessException이 발생한다")
    void throws_whenConfirmedFromNonPending() {
        // given
        Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
        enrollment.confirm(LocalDateTime.of(2026, 5, 15, 10, 0));

        // when & then (이미 CONFIRMED)
        assertThatThrownBy(() -> enrollment.confirm(LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(EnrollmentErrorInfo.NOT_CONFIRMABLE_STATUS);
    }

    @Test
    @DisplayName("PENDING 상태에서 cancel()을 호출하면 CANCELLED로 전이된다")
    void transitionsToCancelled_whenCancelledFromPending() {
        // given
        Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);

        // when
        enrollment.cancel(now, CANCELLATION_WINDOW);

        // then
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("CONFIRMED 상태에서 confirmedAt + window 이내라면 cancel()이 허용된다")
    void transitionsToCancelled_whenCancelledWithinWindow() {
        // given
        Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 5, 15, 10, 0);
        enrollment.confirm(confirmedAt);
        LocalDateTime sevenDaysLater = confirmedAt.plusDays(7);

        // when
        enrollment.cancel(sevenDaysLater, CANCELLATION_WINDOW);

        // then
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(enrollment.getCancelledAt()).isEqualTo(sevenDaysLater);
    }

    @Test
    @DisplayName("CONFIRMED 상태에서 confirmedAt + window를 넘긴 시점이면 CANCELLATION_WINDOW_EXPIRED BusinessException이 발생한다")
    void throws_whenCancelledAfterWindow() {
        // given
        Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 5, 15, 10, 0);
        enrollment.confirm(confirmedAt);
        LocalDateTime afterWindow = confirmedAt.plusDays(7).plusSeconds(1);

        // when & then
        assertThatThrownBy(() -> enrollment.cancel(afterWindow, CANCELLATION_WINDOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(EnrollmentErrorInfo.CANCELLATION_WINDOW_EXPIRED);
    }

    @Test
    @DisplayName("이미 CANCELLED 상태에서 cancel()을 호출하면 ALREADY_CANCELLED BusinessException이 발생한다")
    void throws_whenCancelledFromCancelled() {
        // given
        Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
        enrollment.cancel(now, CANCELLATION_WINDOW);

        // when & then
        assertThatThrownBy(() -> enrollment.cancel(now.plusDays(1), CANCELLATION_WINDOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(EnrollmentErrorInfo.ALREADY_CANCELLED);
    }

    @Test
    @DisplayName("verifyOwner는 신청자 본인 ID와 일치하면 통과한다")
    void doesNotThrow_whenOwnerMatches() {
        // given
        Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);

        // when & then
        enrollment.verifyOwner(MEMBER_ID);
    }

    @Test
    @DisplayName("verifyOwner는 다른 사용자 ID면 NOT_ENROLLMENT_OWNER BusinessException을 던진다")
    void throws_whenOwnerDoesNotMatch() {
        // given
        Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);

        // when & then
        assertThatThrownBy(() -> enrollment.verifyOwner(OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(EnrollmentErrorInfo.NOT_ENROLLMENT_OWNER);
    }
}
