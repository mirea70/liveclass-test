package com.liveclass.course.domain.entity;

import com.liveclass.common.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "course_enroll_count")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseEnrollCount extends BaseEntity {

    @Id
    @Column(name = "course_id")
    private Long courseId;

    @Column(nullable = false)
    private int count;

    @Version
    @Column(nullable = false)
    private Long version;

    private CourseEnrollCount(Long courseId) {
        this.courseId = courseId;
        this.count = 0;
    }

    public static CourseEnrollCount createNew(Long courseId) {
        return new CourseEnrollCount(courseId);
    }

    public boolean tryReserve(int capacity) {
        if (!hasAvailableSeat(capacity)) {
            return false;
        }
        this.count += 1;
        return true;
    }

    public void release() {
        if (this.count > 0) {
            this.count -= 1;
        }
    }

    public boolean hasAvailableSeat(int capacity) {
        return this.count < capacity;
    }
}
