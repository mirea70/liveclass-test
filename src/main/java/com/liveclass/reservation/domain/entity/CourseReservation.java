package com.liveclass.reservation.domain.entity;

import com.liveclass.common.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 사용자가 한 강의에 수강신청(enrollment PENDING/CONFIRMED 또는 waitlist) 중임을 나타내는 테이블
 * enrollment와 waitlist 양쪽에서 INSERT/DELETE해 cross-table 중복 등록을 DB 레벨에서 차단한다.
 */
@Getter
@Entity
@Table(
        name = "course_reservation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_course_reservation",
                columnNames = {"course_id", "member_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseReservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    private CourseReservation(Long courseId, Long memberId) {
        this.courseId = courseId;
        this.memberId = memberId;
    }

    public static CourseReservation createNew(Long courseId, Long memberId) {
        return new CourseReservation(courseId, memberId);
    }
}
