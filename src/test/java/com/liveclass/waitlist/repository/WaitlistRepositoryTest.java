package com.liveclass.waitlist.repository;

import com.liveclass.course.domain.entity.Course;
import com.liveclass.support.JpaTestSupport;
import com.liveclass.waitlist.domain.entity.Waitlist;
import com.liveclass.waitlist.dto.response.MyWaitlistResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Test
    @DisplayName("shiftOrderNumDownAfter는 삭제된 자리보다 큰 order_num을 모두 -1로 갱신한다")
    void shiftsOrderNumDown_whenCalled() {
        // given: 1번, 3번, 4번 (2번은 이미 삭제된 상황을 가정)
        waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, 100L, 1));
        Waitlist w3 = waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, 102L, 3));
        Waitlist w4 = waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, 103L, 4));
        entityManager.clear();

        // when (2번이 삭제됐다고 가정)
        waitlistRepository.shiftOrderNumDownAfter(COURSE_ID, 2);
        entityManager.clear();

        // then: 1은 그대로, 3·4는 각각 2·3으로
        assertThat(waitlistRepository.findById(w3.getId()).orElseThrow().getOrderNum()).isEqualTo(2);
        assertThat(waitlistRepository.findById(w4.getId()).orElseThrow().getOrderNum()).isEqualTo(3);
    }

    @Test
    @DisplayName("findOldestByCourseId는 order_num이 가장 작은 대기자를 반환한다")
    void returnsOldestWaitlist_whenWaitlistExists() {
        // given
        Waitlist w2 = waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, 102L, 2));
        Waitlist w1 = waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, 101L, 1));
        Waitlist w3 = waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, 103L, 3));

        // when
        Optional<Waitlist> result = waitlistRepository.findOldestByCourseId(COURSE_ID);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(w1.getId());
    }

    @Test
    @DisplayName("대기자가 없으면 findOldestByCourseId는 Optional.empty이다")
    void returnsEmpty_whenNoWaitlist() {
        // when
        Optional<Waitlist> result = waitlistRepository.findOldestByCourseId(COURSE_ID);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findOldestByCourseId는 다른 강의의 대기자를 반환하지 않는다")
    void doesNotReturnOtherCourseWaitlist_whenFindOldest() {
        // given
        waitlistRepository.saveAndFlush(Waitlist.createNew(OTHER_COURSE_ID, 100L, 1));

        // when
        Optional<Waitlist> result = waitlistRepository.findOldestByCourseId(COURSE_ID);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findMyWaitlists는 본인 대기 목록을 강의 정보와 함께 createdAt 내림차순으로 반환한다")
    void returnsMyWaitlistsJoinedWithCourse_orderedByCreatedAtDesc() {
        // given
        Long memberId = 200L;
        Course courseA = saveCourse("강의 A", 10_000L);
        Course courseB = saveCourse("강의 B", 20_000L);
        Waitlist older = waitlistRepository.saveAndFlush(Waitlist.createNew(courseA.getId(), memberId, 1));
        ReflectionTestUtils.setField(older, "createdAt", LocalDateTime.of(2026, 1, 1, 0, 0));
        waitlistRepository.saveAndFlush(older);
        Waitlist newer = waitlistRepository.saveAndFlush(Waitlist.createNew(courseB.getId(), memberId, 3));
        ReflectionTestUtils.setField(newer, "createdAt", LocalDateTime.of(2026, 2, 1, 0, 0));
        waitlistRepository.saveAndFlush(newer);
        entityManager.clear();

        // when
        List<MyWaitlistResponse> result = waitlistRepository.findMyWaitlists(memberId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).courseTitle()).isEqualTo("강의 B");
        assertThat(result.get(0).orderNum()).isEqualTo(3);
        assertThat(result.get(0).price()).isEqualTo(20_000L);
        assertThat(result.get(1).courseTitle()).isEqualTo("강의 A");
        assertThat(result.get(1).orderNum()).isEqualTo(1);
    }

    @Test
    @DisplayName("findMyWaitlists는 다른 사용자의 대기를 포함하지 않는다")
    void excludesOtherMembersWaitlists() {
        // given
        Course courseA = saveCourse("강의 A", 10_000L);
        waitlistRepository.saveAndFlush(Waitlist.createNew(courseA.getId(), 200L, 1));
        waitlistRepository.saveAndFlush(Waitlist.createNew(courseA.getId(), 999L, 2));

        // when
        List<MyWaitlistResponse> result = waitlistRepository.findMyWaitlists(200L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).orderNum()).isEqualTo(1);
    }

    private Course saveCourse(String title, long price) {
        Course course = Course.createNew(
                100L, title, "설명",
                price, 30,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
        return courseRepository.saveAndFlush(course);
    }

    @Test
    @DisplayName("shiftOrderNumDownAfter는 다른 강의의 order_num에 영향을 주지 않는다")
    void doesNotAffectOtherCourse_whenShifting() {
        // given
        waitlistRepository.saveAndFlush(Waitlist.createNew(COURSE_ID, 100L, 1));
        Waitlist otherCourseWaitlist = waitlistRepository.saveAndFlush(
                Waitlist.createNew(OTHER_COURSE_ID, 200L, 5));
        entityManager.clear();

        // when
        waitlistRepository.shiftOrderNumDownAfter(COURSE_ID, 0);
        entityManager.clear();

        // then
        assertThat(waitlistRepository.findById(otherCourseWaitlist.getId()).orElseThrow().getOrderNum())
                .isEqualTo(5);
    }
}
