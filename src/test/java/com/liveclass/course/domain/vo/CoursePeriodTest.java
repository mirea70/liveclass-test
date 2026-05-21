package com.liveclass.course.domain.vo;

import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.CourseErrorInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CoursePeriod 도메인 테스트")
class CoursePeriodTest {

    @Test
    @DisplayName("시작일이 종료일보다 이전이면 정상 생성된다")
    void creates_whenStartDateIsBeforeEndDate() {
        // given
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);

        // when
        CoursePeriod period = new CoursePeriod(start, end);

        // then
        assertThat(period.getStartDate()).isEqualTo(start);
        assertThat(period.getEndDate()).isEqualTo(end);
    }

    @Test
    @DisplayName("시작일과 종료일이 같으면 정상 생성된다")
    void creates_whenStartDateEqualsEndDate() {
        // given
        LocalDate date = LocalDate.of(2026, 6, 1);

        // when & then
        assertThatCode(() -> new CoursePeriod(date, date))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 DomainException이 발생한다")
    void throws_whenStartDateIsAfterEndDate() {
        // given
        LocalDate start = LocalDate.of(2026, 8, 31);
        LocalDate end = LocalDate.of(2026, 6, 1);

        // when & then
        assertThatThrownBy(() -> new CoursePeriod(start, end))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.COURSE_PERIOD_INVALID);
    }

    @Test
    @DisplayName("시작일이 null이면 DomainException이 발생한다")
    void throws_whenStartDateIsNull() {
        // when & then
        assertThatThrownBy(() -> new CoursePeriod(null, LocalDate.of(2026, 8, 31)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.COURSE_PERIOD_INVALID);
    }

    @Test
    @DisplayName("종료일이 null이면 DomainException이 발생한다")
    void throws_whenEndDateIsNull() {
        // when & then
        assertThatThrownBy(() -> new CoursePeriod(LocalDate.of(2026, 6, 1), null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.COURSE_PERIOD_INVALID);
    }
}
