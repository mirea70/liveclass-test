package com.liveclass.course.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CourseEnrollCount 도메인 테스트")
class CourseEnrollCountTest {

    private static final int CAPACITY = 30;

    @Test
    @DisplayName("생성 시 현재 인원수는 0이다")
    void 생성_시_현재_인원수는_0이다() {
        // when
        CourseEnrollCount count = new CourseEnrollCount(1L);

        // then
        assertThat(count.getCourseId()).isEqualTo(1L);
        assertThat(count.getCount()).isZero();
    }

    @Test
    @DisplayName("정원이 남아있으면 tryReserve는 true를 반환하고 인원수가 1 증가한다")
    void 정원이_남으면_tryReserve_는_true_반환하고_증가한다() {
        // given
        CourseEnrollCount count = new CourseEnrollCount(1L);

        // when
        boolean result = count.tryReserve(CAPACITY);

        // then
        assertThat(result).isTrue();
        assertThat(count.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("정원이 가득 차면 tryReserve는 false를 반환하고 인원수는 변하지 않는다")
    void 정원이_차면_tryReserve_는_false_반환하고_변하지_않는다() {
        // given
        CourseEnrollCount count = fullCount();

        // when
        boolean result = count.tryReserve(CAPACITY);

        // then
        assertThat(result).isFalse();
        assertThat(count.getCount()).isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("release는 현재 인원수를 1 감소시킨다")
    void release는_인원수를_감소시킨다() {
        // given
        CourseEnrollCount count = new CourseEnrollCount(1L);
        count.tryReserve(CAPACITY);

        // when
        count.release();

        // then
        assertThat(count.getCount()).isZero();
    }

    @Test
    @DisplayName("현재 인원수가 0일 때 release는 무영향이다")
    void 인원수가_0이면_release는_무영향이다() {
        // given
        CourseEnrollCount count = new CourseEnrollCount(1L);

        // when
        count.release();

        // then
        assertThat(count.getCount()).isZero();
    }

    @Test
    @DisplayName("hasAvailableSeat: 현재 인원수가 정원 미만이면 true")
    void 정원_미만이면_hasAvailableSeat은_true() {
        // given
        CourseEnrollCount count = new CourseEnrollCount(1L);

        // when & then
        assertThat(count.hasAvailableSeat(CAPACITY)).isTrue();
    }

    @Test
    @DisplayName("hasAvailableSeat: 현재 인원수가 정원과 같으면 false")
    void 정원_가득차면_hasAvailableSeat은_false() {
        // given
        CourseEnrollCount count = fullCount();

        // when & then
        assertThat(count.hasAvailableSeat(CAPACITY)).isFalse();
    }

    private CourseEnrollCount fullCount() {
        CourseEnrollCount count = new CourseEnrollCount(1L);
        for (int i = 0; i < CAPACITY; i++) {
            count.tryReserve(CAPACITY);
        }
        return count;
    }
}
