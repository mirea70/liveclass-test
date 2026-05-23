package com.liveclass.course.repository;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.support.JpaTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CourseRepository 슬라이스 테스트")
class CourseRepositoryTest extends JpaTestSupport {

    @Test
    @DisplayName("강의를 저장하면 ID가 자동 생성된다")
    void generatesId_whenSaved() {
        // given
        Course course = createCourse();

        // when
        Course saved = courseRepository.save(course);

        // then
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("저장된 강의를 ID로 조회할 수 있다")
    void findsById_whenSaved() {
        // given
        Course saved = courseRepository.saveAndFlush(createCourse());
        Long id = saved.getId();
        entityManager.clear();

        // when
        Course loaded = courseRepository.findById(id).orElseThrow();

        // then
        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getTitle()).isEqualTo("Spring Boot 마스터");
        assertThat(loaded.getCreatorId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("저장 시 createdAt과 updatedAt이 자동 설정된다")
    void setsAuditFields_whenSaved() {
        // when
        Course saved = courseRepository.saveAndFlush(createCourse());

        // then
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("새로 저장된 강의는 DRAFT 상태로 영속된다")
    void persistsAsDraft_whenSaved() {
        // given
        Course saved = courseRepository.saveAndFlush(createCourse());
        Long id = saved.getId();
        entityManager.clear();

        // when
        Course loaded = courseRepository.findById(id).orElseThrow();

        // then
        assertThat(loaded.getStatus()).isEqualTo(CourseStatus.DRAFT);
    }

    @Test
    @DisplayName("강의 상태 변경 후 영속되면 변경된 상태로 조회된다")
    void persistsStatusChange_whenUpdated() {
        // given
        Course saved = courseRepository.saveAndFlush(createCourse());
        Long id = saved.getId();
        entityManager.clear();

        Course loaded = courseRepository.findById(id).orElseThrow();
        loaded.open();
        entityManager.flush();
        entityManager.clear();

        // when
        Course reloaded = courseRepository.findById(id).orElseThrow();

        // then
        assertThat(reloaded.getStatus()).isEqualTo(CourseStatus.OPEN);
    }

    @Test
    @DisplayName("Money와 CoursePeriod 값 객체가 영속·조회된다")
    void persistsEmbeddedValueObjects_whenSaved() {
        // given
        Money price = new Money(99_000L);
        CoursePeriod period = new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
        Course course = Course.createNew(100L, "Spring Boot 마스터", "Spring Boot 실전", price, 30, period);

        Course saved = courseRepository.saveAndFlush(course);
        Long id = saved.getId();
        entityManager.clear();

        // when
        Course loaded = courseRepository.findById(id).orElseThrow();

        // then
        assertThat(loaded.getPrice().amount()).isEqualTo(99_000L);
        assertThat(loaded.getPeriod().startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(loaded.getPeriod().endDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(loaded.getCapacity()).isEqualTo(30);
    }

    @Test
    @DisplayName("엔티티가 변경되면 version이 증가한다")
    void incrementsVersion_whenUpdated() {
        // given
        Course saved = courseRepository.saveAndFlush(createCourse());
        Long id = saved.getId();
        Long initialVersion = saved.getVersion();
        entityManager.clear();

        // when
        Course loaded = courseRepository.findById(id).orElseThrow();
        loaded.open();
        entityManager.flush();
        entityManager.clear();

        // then
        Course reloaded = courseRepository.findById(id).orElseThrow();
        assertThat(reloaded.getVersion()).isGreaterThan(initialVersion);
    }

    private Course createCourse() {
        return Course.createNew(
                100L,
                "Spring Boot 마스터",
                "Spring Boot 실전",
                new Money(99_000L),
                30,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31))
        );
    }
}
