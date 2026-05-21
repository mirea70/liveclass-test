package com.liveclass.course.repository;

import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.support.JpaTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CourseEnrollCountRepository 슬라이스 테스트")
class CourseEnrollCountRepositoryTest extends JpaTestSupport {

    private static final Long COURSE_ID = 1L;
    private static final int CAPACITY = 30;

    @Test
    @DisplayName("courseId로 저장 후 조회하면 초기 count가 0이다")
    void 저장_후_조회하면_count는_0이다() {
        // given
        courseEnrollCountRepository.saveAndFlush(new CourseEnrollCount(COURSE_ID));
        entityManager.clear();

        // when
        CourseEnrollCount loaded = courseEnrollCountRepository.findById(COURSE_ID).orElseThrow();

        // then
        assertThat(loaded.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(loaded.getCount()).isZero();
    }

    @Test
    @DisplayName("tryReserve 후 영속하면 count가 증가된 상태로 조회된다")
    void tryReserve_가_영속된다() {
        // given
        courseEnrollCountRepository.saveAndFlush(new CourseEnrollCount(COURSE_ID));
        entityManager.clear();

        // when
        CourseEnrollCount loaded = courseEnrollCountRepository.findById(COURSE_ID).orElseThrow();
        loaded.tryReserve(CAPACITY);
        entityManager.flush();
        entityManager.clear();

        // then
        CourseEnrollCount reloaded = courseEnrollCountRepository.findById(COURSE_ID).orElseThrow();
        assertThat(reloaded.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("release 후 영속하면 count가 감소된 상태로 조회된다")
    void release_가_영속된다() {
        // given
        CourseEnrollCount initial = new CourseEnrollCount(COURSE_ID);
        initial.tryReserve(CAPACITY);
        initial.tryReserve(CAPACITY);
        courseEnrollCountRepository.saveAndFlush(initial);
        entityManager.clear();

        // when
        CourseEnrollCount loaded = courseEnrollCountRepository.findById(COURSE_ID).orElseThrow();
        loaded.release();
        entityManager.flush();
        entityManager.clear();

        // then
        CourseEnrollCount reloaded = courseEnrollCountRepository.findById(COURSE_ID).orElseThrow();
        assertThat(reloaded.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("엔티티가 변경되면 version이 증가한다")
    void update_시_version이_증가한다() {
        // given
        CourseEnrollCount saved = courseEnrollCountRepository.saveAndFlush(new CourseEnrollCount(COURSE_ID));
        Long initialVersion = saved.getVersion();
        entityManager.clear();

        // when
        CourseEnrollCount loaded = courseEnrollCountRepository.findById(COURSE_ID).orElseThrow();
        loaded.tryReserve(CAPACITY);
        entityManager.flush();
        entityManager.clear();

        // then
        CourseEnrollCount reloaded = courseEnrollCountRepository.findById(COURSE_ID).orElseThrow();
        assertThat(reloaded.getVersion()).isGreaterThan(initialVersion);
    }
}
