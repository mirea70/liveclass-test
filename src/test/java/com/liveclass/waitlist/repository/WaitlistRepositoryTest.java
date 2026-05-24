package com.liveclass.waitlist.repository;

import com.liveclass.support.JpaTestSupport;
import com.liveclass.waitlist.domain.entity.Waitlist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WaitlistRepository 슬라이스 테스트")
class WaitlistRepositoryTest extends JpaTestSupport {

    private static final Long COURSE_ID = 1L;
    private static final Long OTHER_COURSE_ID = 2L;
    private static final Long MEMBER_ID = 200L;
    private static final Long OTHER_MEMBER_ID = 999L;

    @Test
    @DisplayName("저장 시 ID와 감사 필드가 자동 설정된다")
    void generatesIdAndAuditFields_whenSaved() {
        // when
        Waitlist saved = waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, MEMBER_ID, 1));

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("동일 (course_id, member_id)로 2건 저장하면 unique 제약 위반으로 예외가 발생한다")
    void throws_whenDuplicateCourseMember() {
        // given
        waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, MEMBER_ID, 1));

        // when & then
        assertThatThrownBy(() ->
                waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, MEMBER_ID, 2))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("동일 (course_id, order_num)로 2건 저장하면 unique 제약 위반으로 예외가 발생한다")
    void throws_whenDuplicateCourseOrderNum() {
        // given
        waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, MEMBER_ID, 1));

        // when & then
        assertThatThrownBy(() ->
                waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, OTHER_MEMBER_ID, 1))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("동일 (course_id, member_id) 대기가 존재하면 existsByCourseIdAndMemberId는 true이다")
    void returnsTrue_whenWaitlistExists() {
        // given
        waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, MEMBER_ID, 1));

        // when
        boolean result = waitlistRepository.existsByCourseIdAndMemberId(COURSE_ID, MEMBER_ID);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("다른 사용자의 대기는 본인 검색에서 false이다")
    void returnsFalse_whenOtherUserOnly() {
        // given
        waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, OTHER_MEMBER_ID, 1));

        // when
        boolean result = waitlistRepository.existsByCourseIdAndMemberId(COURSE_ID, MEMBER_ID);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("같은 사용자가 다른 강의에만 대기 중이면 false이다")
    void returnsFalse_whenSameUserOnOtherCourseOnly() {
        // given
        waitlistRepository.saveAndFlush(Waitlist.createNew(OTHER_COURSE_ID, MEMBER_ID, 1));

        // when
        boolean result = waitlistRepository.existsByCourseIdAndMemberId(COURSE_ID, MEMBER_ID);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("해당 강의에 대기자가 없으면 findMaxOrderNumByCourseId는 0을 반환한다")
    void returnsZero_whenNoWaitlist() {
        // when
        int max = waitlistRepository.findMaxOrderNumByCourseId(COURSE_ID);

        // then
        assertThat(max).isZero();
    }

    @Test
    @DisplayName("해당 강의의 가장 큰 order_num을 반환한다")
    void returnsMaxOrderNum_whenWaitlistExists() {
        // given
        waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, 100L, 1));
        waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, 101L, 2));
        waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, 102L, 3));

        // when
        int max = waitlistRepository.findMaxOrderNumByCourseId(COURSE_ID);

        // then
        assertThat(max).isEqualTo(3);
    }

    @Test
    @DisplayName("다른 강의의 order_num은 카운트되지 않는다")
    void doesNotIncludeOtherCourseOrderNum() {
        // given
        waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, 100L, 1));
        waitlistRepository.saveAndFlush(Waitlist.createNew(OTHER_COURSE_ID, 200L, 99));

        // when
        int max = waitlistRepository.findMaxOrderNumByCourseId(COURSE_ID);

        // then
        assertThat(max).isEqualTo(1);
    }
}
