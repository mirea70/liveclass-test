package com.liveclass.waitlist.domain.entity;

import com.liveclass.common.domain.entity.BaseEntity;
import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.WaitlistErrorInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "waitlist",
        indexes = {
                @Index(name = "idx_waitlist_member", columnList = "member_id"),
                @Index(name = "idx_waitlist_course_order", columnList = "course_id, order_num")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_waitlist_course_member", columnNames = {"course_id", "member_id"}),
                @UniqueConstraint(name = "uk_waitlist_course_order", columnNames = {"course_id", "order_num"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Waitlist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "order_num", nullable = false)
    private int orderNum;

    private Waitlist(Long courseId, Long memberId, int orderNum) {
        this.courseId = courseId;
        this.memberId = memberId;
        this.orderNum = orderNum;
    }

    public static Waitlist createNew(Long courseId, Long memberId, int orderNum) {
        if (orderNum < 1) {
            throw new DomainException(WaitlistErrorInfo.ORDER_NUM_INVALID);
        }
        return new Waitlist(courseId, memberId, orderNum);
    }

    public void verifyOwner(Long memberId) {
        if (!this.memberId.equals(memberId)) {
            throw new BusinessException(WaitlistErrorInfo.NOT_WAITLIST_OWNER);
        }
    }
}
