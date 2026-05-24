package com.liveclass.enrollment.repository;

import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.support.JpaTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EnrollmentRepository 슬라이스 테스트")
class EnrollmentRepositoryTest extends JpaTestSupport {

    private static final Long COURSE_ID = 1L;
    private static final Long OTHER_COURSE_ID = 2L;
    private static final Long MEMBER_ID = 200L;
    private static final Long OTHER_MEMBER_ID = 999L;
    private static final List<EnrollmentStatus> ACTIVE_STATUSES =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);
    private static final Duration CANCELLATION_WINDOW = Duration.ofDays(7);

    @Test
    @DisplayName("저장 시 ID와 감사 필드가 자동 설정된다")
    void generatesIdAndAuditFields_whenSaved() {
        // when
        Enrollment saved = enrollmentRepository.saveAndFlush(Enrollment.createPending(COURSE_ID, MEMBER_ID));

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("PENDING 활성 신청이 존재하면 existsByCourseIdAndMemberIdAndStatusIn은 true이다")
    void returnsTrue_whenPendingExists() {
        // given
        enrollmentRepository.saveAndFlush(Enrollment.createPending(COURSE_ID, MEMBER_ID));

        // when
        boolean result = enrollmentRepository.existsByCourseIdAndMemberIdAndStatusIn(COURSE_ID, MEMBER_ID, ACTIVE_STATUSES);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("CANCELLED 신청만 있으면 false이다")
    void returnsFalse_whenOnlyCancelledExists() {
        // given
        Enrollment enrollment = Enrollment.createPending(COURSE_ID, MEMBER_ID);
        enrollment.cancel(LocalDateTime.now(), CANCELLATION_WINDOW);
        enrollmentRepository.saveAndFlush(enrollment);

        // when
        boolean result = enrollmentRepository.existsByCourseIdAndMemberIdAndStatusIn(COURSE_ID, MEMBER_ID, ACTIVE_STATUSES);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 활성 신청은 본인 검색에서 false이다")
    void returnsFalse_whenOtherUserEnrolled() {
        // given
        enrollmentRepository.saveAndFlush(Enrollment.createPending(COURSE_ID, OTHER_MEMBER_ID));

        // when
        boolean result = enrollmentRepository.existsByCourseIdAndMemberIdAndStatusIn(COURSE_ID, MEMBER_ID, ACTIVE_STATUSES);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("같은 사용자가 다른 강의에만 신청했으면 false이다")
    void returnsFalse_whenSameUserEnrolledOtherCourse() {
        // given
        enrollmentRepository.saveAndFlush(Enrollment.createPending(OTHER_COURSE_ID, MEMBER_ID));

        // when
        boolean result = enrollmentRepository.existsByCourseIdAndMemberIdAndStatusIn(COURSE_ID, MEMBER_ID, ACTIVE_STATUSES);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("동일 (course_id, member_id)로 활성 신청을 2건 저장하면 unique 제약 위반으로 예외가 발생한다")
    void throws_whenDuplicateActiveEnrollment() {
        // given
        enrollmentRepository.saveAndFlush(Enrollment.createPending(COURSE_ID, MEMBER_ID));

        // when & then
        assertThatThrownBy(() ->
                enrollmentRepository.saveAndFlush(Enrollment.createPending(COURSE_ID, MEMBER_ID))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("기존 신청을 CANCELLED로 변경한 뒤에는 동일 (course_id, member_id)로 재신청이 가능하다")
    void allowsReenrollment_whenPreviousCancelled() {
        // given
        Enrollment first = enrollmentRepository.saveAndFlush(Enrollment.createPending(COURSE_ID, MEMBER_ID));
        first.cancel(LocalDateTime.now(), CANCELLATION_WINDOW);
        enrollmentRepository.saveAndFlush(first);

        // when
        Enrollment second = enrollmentRepository.saveAndFlush(Enrollment.createPending(COURSE_ID, MEMBER_ID));

        // then
        assertThat(second.getId()).isNotNull();
        assertThat(second.getId()).isNotEqualTo(first.getId());
    }
}
