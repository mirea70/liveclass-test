package com.liveclass.enrollment.repository;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.dto.response.MyEnrollmentResponse;
import com.liveclass.enrollment.dto.response.StudentResponse;
import com.liveclass.member.domain.entity.Member;
import com.liveclass.support.JpaTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EnrollmentCustomRepository 슬라이스 테스트")
class EnrollmentCustomRepositoryTest extends JpaTestSupport {

    private static final Long COURSE_ID = 1L;
    private static final Long OTHER_COURSE_ID = 2L;
    private static final Duration CANCELLATION_WINDOW = Duration.ofDays(7);

    @Test
    @DisplayName("활성 상태(WAITING/PENDING/CONFIRMED)만 반환하고 CANCELLED는 제외한다")
    void returnsActiveOnly() {
        // given
        Member m1 = memberRepository.saveAndFlush(Member.createNew("홍길동"));
        Member m2 = memberRepository.saveAndFlush(Member.createNew("이몽룡"));
        Member m3 = memberRepository.saveAndFlush(Member.createNew("성춘향"));
        enrollmentRepository.saveAndFlush(Enrollment.createPending(COURSE_ID, m1.getId()));
        enrollmentRepository.saveAndFlush(Enrollment.createWaiting(COURSE_ID, m2.getId()));
        Enrollment cancelled = Enrollment.createPending(COURSE_ID, m3.getId());
        cancelled.cancel(LocalDateTime.now(), CANCELLATION_WINDOW);
        enrollmentRepository.saveAndFlush(cancelled);
        entityManager.clear();

        // when
        List<StudentResponse> result = enrollmentRepository.findStudentsByCourse(COURSE_ID);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(StudentResponse::name).containsExactlyInAnyOrder("홍길동", "이몽룡");
    }

    @Test
    @DisplayName("다른 강의의 신청은 결과에 포함되지 않는다")
    void doesNotIncludeOtherCourseEnrollments() {
        // given
        Member m1 = memberRepository.saveAndFlush(Member.createNew("홍길동"));
        enrollmentRepository.saveAndFlush(Enrollment.createPending(COURSE_ID, m1.getId()));
        enrollmentRepository.saveAndFlush(Enrollment.createPending(OTHER_COURSE_ID, m1.getId()));
        entityManager.clear();

        // when
        List<StudentResponse> result = enrollmentRepository.findStudentsByCourse(COURSE_ID);

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("응답은 createdAt 내림차순으로 정렬된다 (최신 신청이 먼저)")
    void sortsByCreatedAtDesc() {
        // given
        Member m1 = memberRepository.saveAndFlush(Member.createNew("홍길동"));
        Member m2 = memberRepository.saveAndFlush(Member.createNew("이몽룡"));
        Enrollment older = enrollmentRepository.saveAndFlush(Enrollment.createPending(COURSE_ID, m1.getId()));
        ReflectionTestUtils.setField(older, "createdAt", LocalDateTime.of(2026, 1, 1, 0, 0));
        enrollmentRepository.saveAndFlush(older);
        Enrollment newer = enrollmentRepository.saveAndFlush(Enrollment.createPending(COURSE_ID, m2.getId()));
        ReflectionTestUtils.setField(newer, "createdAt", LocalDateTime.of(2026, 2, 1, 0, 0));
        enrollmentRepository.saveAndFlush(newer);
        entityManager.clear();

        // when
        List<StudentResponse> result = enrollmentRepository.findStudentsByCourse(COURSE_ID);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("이몽룡");
        assertThat(result.get(1).name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("응답 필드(memberId, name, status, confirmedAt, createdAt)가 모두 채워진다")
    void populatesAllFields() {
        // given
        Member m1 = memberRepository.saveAndFlush(Member.createNew("홍길동"));
        Enrollment enrollment = Enrollment.createPending(COURSE_ID, m1.getId());
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 5, 19, 10, 0);
        enrollment.confirm(confirmedAt);
        enrollmentRepository.saveAndFlush(enrollment);
        entityManager.clear();

        // when
        List<StudentResponse> result = enrollmentRepository.findStudentsByCourse(COURSE_ID);

        // then
        assertThat(result).hasSize(1);
        StudentResponse student = result.get(0);
        assertThat(student.memberId()).isEqualTo(m1.getId());
        assertThat(student.name()).isEqualTo("홍길동");
        assertThat(student.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(student.confirmedAt()).isEqualTo(confirmedAt);
        assertThat(student.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("findMyEnrollments는 본인의 신청만 페이지로 반환한다")
    void returnsOwnEnrollmentsOnly_whenFindMyEnrollments() {
        // given
        Long memberId = 200L;
        Long otherMemberId = 999L;
        Long courseId = saveCourse("Spring Boot", 99_000L);
        enrollmentRepository.saveAndFlush(Enrollment.createPending(courseId, memberId));
        enrollmentRepository.saveAndFlush(Enrollment.createPending(courseId, otherMemberId));
        entityManager.clear();

        // when
        Page<MyEnrollmentResponse> result = enrollmentRepository.findMyEnrollments(memberId, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).courseTitle()).isEqualTo("Spring Boot");
    }

    @Test
    @DisplayName("findMyEnrollments 응답은 enrollment + course 필드가 모두 채워진다")
    void populatesAllFields_whenFindMyEnrollments() {
        // given
        Long memberId = 200L;
        Long courseId = saveCourse("JPA 심화", 79_000L);
        Enrollment enrollment = Enrollment.createPending(courseId, memberId);
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 5, 19, 10, 0);
        enrollment.confirm(confirmedAt);
        enrollmentRepository.saveAndFlush(enrollment);
        entityManager.clear();

        // when
        Page<MyEnrollmentResponse> result = enrollmentRepository.findMyEnrollments(memberId, PageRequest.of(0, 20));

        // then
        MyEnrollmentResponse my = result.getContent().get(0);
        assertThat(my.courseId()).isEqualTo(courseId);
        assertThat(my.courseTitle()).isEqualTo("JPA 심화");
        assertThat(my.price()).isEqualTo(79_000L);
        assertThat(my.startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(my.endDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(my.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(my.confirmedAt()).isEqualTo(confirmedAt);
        assertThat(my.cancelledAt()).isNull();
    }

    @Test
    @DisplayName("findMyEnrollments는 createdAt 내림차순으로 정렬된다 (최신 신청이 먼저)")
    void sortsByCreatedAtDesc_whenFindMyEnrollments() {
        // given
        Long memberId = 200L;
        Long courseA = saveCourse("강의 A", 10_000L);
        Long courseB = saveCourse("강의 B", 20_000L);
        Enrollment older = enrollmentRepository.saveAndFlush(Enrollment.createPending(courseA, memberId));
        ReflectionTestUtils.setField(older, "createdAt", LocalDateTime.of(2026, 1, 1, 0, 0));
        enrollmentRepository.saveAndFlush(older);
        Enrollment newer = enrollmentRepository.saveAndFlush(Enrollment.createPending(courseB, memberId));
        ReflectionTestUtils.setField(newer, "createdAt", LocalDateTime.of(2026, 2, 1, 0, 0));
        enrollmentRepository.saveAndFlush(newer);
        entityManager.clear();

        // when
        Page<MyEnrollmentResponse> result = enrollmentRepository.findMyEnrollments(memberId, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).courseTitle()).isEqualTo("강의 B");
        assertThat(result.getContent().get(1).courseTitle()).isEqualTo("강의 A");
    }

    @Test
    @DisplayName("findMyEnrollments는 page/size에 맞게 페이지네이션된다")
    void paginates_whenFindMyEnrollments() {
        // given
        Long memberId = 200L;
        for (int i = 0; i < 5; i++) {
            Long courseId = saveCourse("강의 " + i, 10_000L);
            enrollmentRepository.saveAndFlush(Enrollment.createPending(courseId, memberId));
        }
        entityManager.clear();

        // when
        Page<MyEnrollmentResponse> page0 = enrollmentRepository.findMyEnrollments(memberId, PageRequest.of(0, 2));
        Page<MyEnrollmentResponse> page2 = enrollmentRepository.findMyEnrollments(memberId, PageRequest.of(2, 2));

        // then
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page2.getContent()).hasSize(1);
    }

    private Long saveCourse(String title, long price) {
        Course course = Course.createNew(
                100L, title, "description",
                new Money(price), 30,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)));
        course.open();
        return courseRepository.saveAndFlush(course).getId();
    }
}
