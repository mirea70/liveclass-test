package com.liveclass.enrollment.repository;

import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.dto.response.StudentResponse;
import com.liveclass.member.domain.entity.Member;
import com.liveclass.support.JpaTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
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
}
