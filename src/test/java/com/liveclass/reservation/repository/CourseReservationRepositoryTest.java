package com.liveclass.reservation.repository;

import com.liveclass.reservation.domain.entity.CourseReservation;
import com.liveclass.support.JpaTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CourseReservationRepository 슬라이스 테스트")
class CourseReservationRepositoryTest extends JpaTestSupport {

    private static final Long COURSE_ID = 1L;
    private static final Long OTHER_COURSE_ID = 2L;
    private static final Long MEMBER_ID = 200L;
    private static final Long OTHER_MEMBER_ID = 999L;

    @Test
    @DisplayName("저장 시 ID와 감사 필드가 자동 설정된다")
    void generatesIdAndAuditFields_whenSaved() {
        // when
        CourseReservation saved = courseReservationRepository.saveAndFlush(
                CourseReservation.createNew(COURSE_ID, MEMBER_ID));

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("동일 (course_id, member_id)로 2건 저장하면 unique 제약 위반으로 예외가 발생한다")
    void throws_whenDuplicateCourseMember() {
        // given
        courseReservationRepository.saveAndFlush(CourseReservation.createNew(COURSE_ID, MEMBER_ID));

        // when & then
        assertThatThrownBy(() ->
                courseReservationRepository.saveAndFlush(CourseReservation.createNew(COURSE_ID, MEMBER_ID))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("기존 reservation을 삭제한 뒤에는 동일 (course_id, member_id)로 재등록이 가능하다")
    void allowsReReservation_whenPreviousDeleted() {
        // given
        courseReservationRepository.saveAndFlush(CourseReservation.createNew(COURSE_ID, MEMBER_ID));
        courseReservationRepository.deleteByCourseIdAndMemberId(COURSE_ID, MEMBER_ID);
        courseReservationRepository.flush();

        // when
        CourseReservation second = courseReservationRepository.saveAndFlush(
                CourseReservation.createNew(COURSE_ID, MEMBER_ID));

        // then
        assertThat(second.getId()).isNotNull();
    }

    @Test
    @DisplayName("existsByCourseIdAndMemberId는 존재하면 true이다")
    void returnsTrue_whenReservationExists() {
        // given
        courseReservationRepository.saveAndFlush(CourseReservation.createNew(COURSE_ID, MEMBER_ID));

        // when & then
        assertThat(courseReservationRepository.existsByCourseIdAndMemberId(COURSE_ID, MEMBER_ID)).isTrue();
    }

    @Test
    @DisplayName("existsByCourseIdAndMemberId는 다른 강의·다른 사용자에 대해 false이다")
    void returnsFalse_whenDifferentCourseOrMember() {
        // given
        courseReservationRepository.saveAndFlush(CourseReservation.createNew(COURSE_ID, MEMBER_ID));

        // when & then
        assertThat(courseReservationRepository.existsByCourseIdAndMemberId(OTHER_COURSE_ID, MEMBER_ID)).isFalse();
        assertThat(courseReservationRepository.existsByCourseIdAndMemberId(COURSE_ID, OTHER_MEMBER_ID)).isFalse();
    }
}
