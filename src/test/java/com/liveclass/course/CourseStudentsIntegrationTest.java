package com.liveclass.course;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.member.domain.entity.Member;
import com.liveclass.member.repository.MemberRepository;
import com.liveclass.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("강의별 수강생 목록 조회 통합 테스트")
class CourseStudentsIntegrationTest extends IntegrationTestSupport {

    private static final Long CREATOR_ID = 100L;
    private static final Long OTHER_REQUESTER_ID = 999L;
    private static final Duration CANCELLATION_WINDOW = Duration.ofDays(7);

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("크리에이터가 호출하면 200과 활성 수강생 목록을 반환한다 (CANCELLED 제외)")
    void returnsActiveStudents_whenCalledByCreator() throws Exception {
        // given
        Long courseId = saveOpenCourse();
        Member m1 = memberRepository.save(Member.createNew("홍길동"));
        Member m2 = memberRepository.save(Member.createNew("이몽룡"));
        Member m3 = memberRepository.save(Member.createNew("성춘향"));
        enrollmentRepository.save(Enrollment.createPending(courseId, m1.getId()));
        enrollmentRepository.save(Enrollment.createWaiting(courseId, m2.getId()));
        Enrollment cancelled = Enrollment.createPending(courseId, m3.getId());
        cancelled.cancel(LocalDateTime.now(), CANCELLATION_WINDOW);
        enrollmentRepository.save(cancelled);

        // when & then
        mockMvc.perform(get("/api/courses/{courseId}/students", courseId)
                        .header("X-Member-Id", CREATOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 COURSE_001로 404를 반환한다")
    void returns404_whenCourseNotFound() throws Exception {
        // when & then
        mockMvc.perform(get("/api/courses/{courseId}/students", 9999L)
                        .header("X-Member-Id", CREATOR_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_001"));
    }

    @Test
    @DisplayName("크리에이터가 아니면 COURSE_003으로 403을 반환한다")
    void returns403_whenNotCreator() throws Exception {
        // given
        Long courseId = saveOpenCourse();

        // when & then
        mockMvc.perform(get("/api/courses/{courseId}/students", courseId)
                        .header("X-Member-Id", OTHER_REQUESTER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_003"));
    }

    private Long saveOpenCourse() {
        Course course = Course.createNew(
                CREATOR_ID,
                "Spring Boot 마스터",
                "Spring Boot 실전 강의",
                new Money(99_000L),
                30,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)));
        course.open();
        Course saved = courseRepository.save(course);
        courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
        return saved.getId();
    }
}
