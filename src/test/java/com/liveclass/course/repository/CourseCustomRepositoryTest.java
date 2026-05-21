package com.liveclass.course.repository;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.course.dto.response.CourseSummaryResponse;
import com.liveclass.support.JpaTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("CourseCustomRepositoryTest 슬라이스 테스트")
class CourseCustomRepositoryTest extends JpaTestSupport {

    @Test
    @DisplayName("status가 null이면 모든 강의 요약을 반환한다")
    void returnsAllSummaries_whenStatusIsNull() {
        // given
        saveCourse(CourseStatus.DRAFT, "DRAFT 강의");
        saveCourse(CourseStatus.OPEN, "OPEN 강의");
        saveCourse(CourseStatus.CLOSED, "CLOSED 강의");
        entityManager.flush();
        entityManager.clear();

        // when
        List<CourseSummaryResponse> summaries = courseRepository.findSummaries(null);

        // then
        assertThat(summaries).hasSize(3)
                .extracting(CourseSummaryResponse::title, CourseSummaryResponse::status)
                .containsExactlyInAnyOrder(
                        tuple("DRAFT 강의", CourseStatus.DRAFT),
                        tuple("OPEN 강의", CourseStatus.OPEN),
                        tuple("CLOSED 강의", CourseStatus.CLOSED)
                );
    }

    @Test
    @DisplayName("status 필터를 지정하면 해당 상태의 강의만 반환한다")
    void returnsFilteredSummaries_whenStatusGiven() {
        // given
        saveCourse(CourseStatus.DRAFT, "DRAFT 강의");
        saveCourse(CourseStatus.OPEN, "OPEN 강의 1");
        saveCourse(CourseStatus.OPEN, "OPEN 강의 2");
        saveCourse(CourseStatus.CLOSED, "CLOSED 강의");
        entityManager.flush();
        entityManager.clear();

        // when
        List<CourseSummaryResponse> summaries = courseRepository.findSummaries(CourseStatus.OPEN);

        // then
        assertThat(summaries).hasSize(2)
                .extracting(CourseSummaryResponse::title)
                .containsExactlyInAnyOrder("OPEN 강의 1", "OPEN 강의 2");
        assertThat(summaries).allMatch(s -> s.status() == CourseStatus.OPEN);
    }

    @Test
    @DisplayName("summary에는 join된 course_enroll_count의 count가 포함된다")
    void includesCountFromJoin_whenSummariesFetched() {
        // given
        Course course = saveCourse(CourseStatus.OPEN, "테스트 강의");
        CourseEnrollCount enrollCount = courseEnrollCountRepository.findById(course.getId()).orElseThrow();
        enrollCount.tryReserve(course.getCapacity());
        enrollCount.tryReserve(course.getCapacity());
        enrollCount.tryReserve(course.getCapacity());
        entityManager.flush();
        entityManager.clear();

        // when
        List<CourseSummaryResponse> summaries = courseRepository.findSummaries(CourseStatus.OPEN);

        // then
        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).count()).isEqualTo(3);
    }

    @Test
    @DisplayName("필터 조건에 일치하는 강의가 없으면 빈 리스트를 반환한다")
    void returnsEmptyList_whenNoMatchingStatus() {
        // given
        saveCourse(CourseStatus.OPEN, "OPEN 강의");
        entityManager.flush();
        entityManager.clear();

        // when
        List<CourseSummaryResponse> summaries = courseRepository.findSummaries(CourseStatus.CLOSED);

        // then
        assertThat(summaries).isEmpty();
    }

    private Course saveCourse(CourseStatus targetStatus, String title) {
        Course course = Course.createNew(
                100L,
                title,
                "설명",
                new Money(99_000L),
                30,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31))
        );
        if (targetStatus == CourseStatus.OPEN || targetStatus == CourseStatus.CLOSED) {
            course.open();
        }
        if (targetStatus == CourseStatus.CLOSED) {
            course.close();
        }
        Course saved = courseRepository.save(course);
        courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
        return saved;
    }
}
