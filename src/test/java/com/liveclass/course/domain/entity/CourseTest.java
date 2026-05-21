package com.liveclass.course.domain.entity;

import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.common.domain.vo.Money;
import com.liveclass.course.domain.vo.CoursePeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Course 도메인 테스트")
class CourseTest {

    @Test
    @DisplayName("강의를 생성하면 DRAFT 상태로 시작한다")
    void 강의를_생성하면_DRAFT_상태로_시작한다() {
        // when
        Course course = createCourse(30);

        // then
        assertThat(course.getStatus()).isEqualTo(CourseStatus.DRAFT);
    }

    @Test
    @DisplayName("강의 생성 시 입력값이 그대로 보관된다")
    void 강의_생성_시_입력값이_그대로_보관된다() {
        // given
        Money price = new Money(99_000L);
        CoursePeriod period = new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));

        // when
        Course course = new Course(100L, "Spring Boot 마스터", "Spring Boot 실전", price, 30, period);

        // then
        assertThat(course.getCreatorId()).isEqualTo(100L);
        assertThat(course.getTitle()).isEqualTo("Spring Boot 마스터");
        assertThat(course.getDescription()).isEqualTo("Spring Boot 실전");
        assertThat(course.getPrice()).isEqualTo(price);
        assertThat(course.getCapacity()).isEqualTo(30);
        assertThat(course.getPeriod()).isEqualTo(period);
    }

    @Test
    @DisplayName("정원이 0이면 DomainException이 발생한다")
    void 정원이_0이면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> createCourse(0))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.CAPACITY_INVALID_VALUE);
    }

    @Test
    @DisplayName("정원이 음수면 DomainException이 발생한다")
    void 정원이_음수면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> createCourse(-1))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.CAPACITY_INVALID_VALUE);
    }

    @Test
    @DisplayName("DRAFT 상태에서 open()을 호출하면 OPEN으로 전이된다")
    void DRAFT에서_open_하면_OPEN으로_전이된다() {
        // given
        Course course = createCourse(30);

        // when
        course.open();

        // then
        assertThat(course.getStatus()).isEqualTo(CourseStatus.OPEN);
    }

    @Test
    @DisplayName("이미 OPEN 상태에서 open()을 호출하면 DomainException이 발생한다")
    void OPEN에서_open_하면_예외가_발생한다() {
        // given
        Course course = createCourse(30);
        course.open();

        // when & then
        assertThatThrownBy(course::open)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("CLOSED 상태에서 open()을 호출하면 DomainException이 발생한다")
    void CLOSED에서_open_하면_예외가_발생한다() {
        // given
        Course course = createCourse(30);
        course.open();
        course.close();

        // when & then
        assertThatThrownBy(course::open)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("OPEN 상태에서 close()를 호출하면 CLOSED로 전이된다")
    void OPEN에서_close_하면_CLOSED로_전이된다() {
        // given
        Course course = createCourse(30);
        course.open();

        // when
        course.close();

        // then
        assertThat(course.getStatus()).isEqualTo(CourseStatus.CLOSED);
    }

    @Test
    @DisplayName("DRAFT 상태에서 close()를 호출하면 DomainException이 발생한다")
    void DRAFT에서_close_하면_예외가_발생한다() {
        // given
        Course course = createCourse(30);

        // when & then
        assertThatThrownBy(course::close)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("이미 CLOSED 상태에서 close()를 호출하면 DomainException이 발생한다")
    void CLOSED에서_close_하면_예외가_발생한다() {
        // given
        Course course = createCourse(30);
        course.open();
        course.close();

        // when & then
        assertThatThrownBy(course::close)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.INVALID_STATUS_TRANSITION);
    }

    private Course createCourse(int capacity) {
        return new Course(
                100L,
                "Spring Boot 마스터",
                "Spring Boot 실전",
                new Money(99_000L),
                capacity,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31))
        );
    }
}
