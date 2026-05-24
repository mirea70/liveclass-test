package com.liveclass.waitlist;

import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.reservation.domain.entity.CourseReservation;
import com.liveclass.reservation.repository.CourseReservationRepository;
import com.liveclass.support.IntegrationTestSupport;
import com.liveclass.waitlist.domain.entity.Waitlist;
import com.liveclass.waitlist.dto.request.WaitlistCreateRequest;
import com.liveclass.waitlist.repository.WaitlistRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("대기열 등록 통합 테스트")
class WaitlistRegisterIntegrationTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 200L;
    private static final Long CREATOR_ID = 100L;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private WaitlistRepository waitlistRepository;

    @Autowired
    private CourseReservationRepository courseReservationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("OPEN 강의의 대기열에 등록하면 201과 order_num=1 응답을 반환하고 waitlist row가 생성된다")
    void registersAtFirstPosition_whenWaitlistIsEmpty() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(1, 1);

        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(courseId))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andExpect(jsonPath("$.memberId").value(MEMBER_ID))
                .andExpect(jsonPath("$.orderNum").value(1));

        assertThat(waitlistRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("이미 대기자가 있으면 다음 사용자는 order_num = max+1로 등록된다")
    void registersAtNextPosition_whenWaitlistHasEntries() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(1, 1);
        waitlistRepository.save(Waitlist.createNew(courseId, 300L, 1));
        waitlistRepository.save(Waitlist.createNew(courseId, 301L, 2));

        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(courseId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNum").value(3));
    }

    @Test
    @DisplayName("강의가 OPEN이 아니면 ENROLLMENT_001로 409를 반환한다")
    void returns409_whenCourseNotOpen() throws Exception {
        // given
        Long courseId = saveDraftCourse(1);

        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(courseId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_001"));
    }

    @Test
    @DisplayName("같은 사용자가 동일 강의에 활성 enrollment를 가지고 있으면 WAITLIST_003 코드로 409 에러를 반환한다")
    void returns409_whenActiveEnrollmentExists() throws Exception {
        // given: enrollment 활성 상태는 course_reservation row의 존재로 표현됨
        Long courseId = saveOpenCourseWithCount(1, 1);
        enrollmentRepository.save(Enrollment.createNew(courseId, MEMBER_ID));
        courseReservationRepository.save(CourseReservation.createNew(courseId, MEMBER_ID));

        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(courseId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAITLIST_003"));
        // DataIntegrityViolationException으로 rollback된 영속 컨텍스트를 정리
        entityManager.clear();
    }

    @Test
    @DisplayName("같은 사용자가 이미 대기열에 있으면 WAITLIST_003 코드로 409 에러를 반환한다")
    void returns409_whenAlreadyInWaitlist() throws Exception {
        // given: waitlist 존재도 course_reservation row의 존재로 표현됨
        Long courseId = saveOpenCourseWithCount(1, 1);
        waitlistRepository.save(Waitlist.createNew(courseId, MEMBER_ID, 1));
        courseReservationRepository.save(CourseReservation.createNew(courseId, MEMBER_ID));

        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(courseId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAITLIST_003"));
        entityManager.clear();
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 COURSE_001로 404를 반환한다")
    void returns404_whenCourseNotFound() throws Exception {
        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(9999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_001"));
    }

    @Test
    @DisplayName("X-Member-Id 헤더가 없으면 400을 반환한다")
    void returns400_whenMemberIdHeaderMissing() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(1, 1);

        // when & then
        mockMvc.perform(post("/api/waitlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WaitlistCreateRequest(courseId))))
                .andExpect(status().isBadRequest());
    }

    private Long saveOpenCourseWithCount(int capacity, int initialCount) {
        Course course = Course.createNew(
                CREATOR_ID,
                "Spring Boot 마스터",
                "Spring Boot 실전 강의",
                99_000L,
                capacity,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
        course.open();
        Course saved = courseRepository.save(course);
        CourseEnrollCount count = courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
        for (int i = 0; i < initialCount; i++) {
            count.tryReserve(capacity);
        }
        courseEnrollCountRepository.save(count);
        return saved.getId();
    }

    private Long saveDraftCourse(int capacity) {
        Course course = Course.createNew(
                CREATOR_ID,
                "Spring Boot 마스터",
                "Spring Boot 실전 강의",
                99_000L,
                capacity,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
        Course saved = courseRepository.save(course);
        courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
        return saved.getId();
    }
}
