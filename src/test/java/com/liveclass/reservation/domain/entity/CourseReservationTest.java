package com.liveclass.reservation.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CourseReservation 도메인 테스트")
class CourseReservationTest {

    private static final Long COURSE_ID = 1L;
    private static final Long MEMBER_ID = 200L;

    @Test
    @DisplayName("of로 생성하면 입력값이 그대로 보관된다")
    void createsWithGivenValues() {
        // when
        CourseReservation reservation = CourseReservation.createNew(COURSE_ID, MEMBER_ID);

        // then
        assertThat(reservation.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(reservation.getMemberId()).isEqualTo(MEMBER_ID);
    }
}
