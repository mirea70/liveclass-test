package com.liveclass.course.domain.entity;

import com.liveclass.common.domain.entity.BaseEntity;
import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.common.domain.vo.Money;
import com.liveclass.course.domain.vo.CoursePeriod;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(
        name = "course",
        indexes = @Index(name = "idx_course_status_creator", columnList = "status, creator_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long creatorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Money price;

    @Column(nullable = false)
    private int capacity;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "startDate", column = @Column(name = "start_date", nullable = false)),
            @AttributeOverride(name = "endDate", column = @Column(name = "end_date", nullable = false))
    })
    private CoursePeriod period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    private Course(Long creatorId, String title, String description, Money price, int capacity, CoursePeriod period) {
        this.creatorId = creatorId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.capacity = capacity;
        this.period = period;
        this.status = CourseStatus.DRAFT;
    }

    public static Course createNew(Long creatorId, String title, String description,
                                   long price, int capacity,
                                   LocalDate startDate, LocalDate endDate) {
        if (capacity < 1) {
            throw new DomainException(CourseErrorInfo.CAPACITY_INVALID_VALUE);
        }
        return new Course(
                creatorId,
                title,
                description,
                new Money(price),
                capacity,
                new CoursePeriod(startDate, endDate)
        );
    }

    public void open() {
        if (this.status != CourseStatus.DRAFT) {
            throw new DomainException(CourseErrorInfo.INVALID_STATUS_TRANSITION);
        }
        this.status = CourseStatus.OPEN;
    }

    public boolean isOpen() {
        return this.status == CourseStatus.OPEN;
    }

    public void close() {
        if (this.status != CourseStatus.OPEN) {
            throw new DomainException(CourseErrorInfo.INVALID_STATUS_TRANSITION);
        }
        this.status = CourseStatus.CLOSED;
    }
}
