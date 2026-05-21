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
    void countIsZero_whenSavedFresh() {
        // given
        courseEnrollCountRepository.saveAndFlush(CourseEnrollCount.createNew(COURSE_ID));
        entityManager.clear();

        // when
        CourseEnrollCount loaded = courseEnrollCountRepository.findById(COURSE_ID).orElseThrow();

        // then
        assertThat(loaded.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(loaded.getCount()).isZero();
    }

    @Test
    @DisplayName("tryReserve 후 영속하면 count가 증가된 상태로 조회된다")
    void persistsCountIncrement_whenTryReserved() {
        // given
        courseEnrollCountRepository.saveAndFlush(CourseEnrollCount.createNew(COURSE_ID));
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
    void persistsCountDecrement_whenReleased() {
        // given
        CourseEnrollCount initial = CourseEnrollCount.createNew(COURSE_ID);
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
    void incrementsVersion_whenUpdated() {
        // given
        CourseEnrollCount saved = courseEnrollCountRepository.saveAndFlush(CourseEnrollCount.createNew(COURSE_ID));
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
