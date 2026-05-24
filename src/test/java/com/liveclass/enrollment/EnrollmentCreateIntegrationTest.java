package com.liveclass.enrollment;

import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.dto.request.EnrollmentCreateRequest;
import com.liveclass.enrollment.dto.response.EnrollmentResponse;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.reservation.domain.entity.CourseReservation;
import com.liveclass.reservation.repository.CourseReservationRepository;
import com.liveclass.support.IntegrationTestSupport;
import com.liveclass.waitlist.repository.WaitlistRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("수강 신청 통합 테스트")
class EnrollmentCreateIntegrationTest extends IntegrationTestSupport {

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
    @DisplayName("OPEN 강의에 정원이 남아있으면 201과 PENDING 응답을 반환하고 enrollment가 저장되며 count가 +1된다")
    void registersPendingEnrollment_whenOpenCourseHasCapacity() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(30, 0);

        // when
        MvcResult result = mockMvc.perform(post("/api/enrollments")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(courseId))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/enrollments/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andExpect(jsonPath("$.memberId").value(MEMBER_ID))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.confirmedAt").isEmpty())
                .andExpect(jsonPath("$.cancelledAt").isEmpty())
                .andReturn();

        // then
        EnrollmentResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), EnrollmentResponse.class);
        Enrollment saved = enrollmentRepository.findById(response.id()).orElseThrow();
        assertThat(saved.getCourseId()).isEqualTo(courseId);
        assertThat(saved.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getStatus()).isEqualTo(EnrollmentStatus.PENDING);

        CourseEnrollCount count = courseEnrollCountRepository.findById(courseId).orElseThrow();
        assertThat(count.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("정원이 차있으면 ENROLLMENT_008로 409를 반환하고 count는 변하지 않는다")
    void returns409_whenCapacityFull() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(1, 1);

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(courseId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_008"));

        CourseEnrollCount count = courseEnrollCountRepository.findById(courseId).orElseThrow();
        assertThat(count.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 COURSE_001로 404를 반환한다")
    void returns404_whenCourseNotFound() throws Exception {
        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(9999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_001"));
    }

    @Test
    @DisplayName("강의가 OPEN이 아니면 ENROLLMENT_001로 409를 반환하고 enrollment는 저장되지 않는다")
    void returns409_whenCourseNotOpen() throws Exception {
        // given
        Long courseId = saveDraftCourseWithCount(30);
        long beforeEnrollmentCount = enrollmentRepository.count();

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(courseId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_001"));

        assertThat(enrollmentRepository.count()).isEqualTo(beforeEnrollmentCount);
    }

    @Test
    @DisplayName("이미 활성 신청이 존재하면 ENROLLMENT_002로 409를 반환한다")
    void returns409_whenDuplicateEnrollment() throws Exception {
        // given
        Long courseId = saveOpenCourseWithCount(30, 0);
        mockMvc.perform(post("/api/enrollments")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(courseId))))
                .andExpect(status().isCreated());

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(courseId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_002"));
        // DataIntegrityViolationException으로 marked-for-rollback된 영속 컨텍스트를 정리 (이후 검증 가능 상태로)
        entityManager.clear();
    }

    @Test
    @DisplayName("이미 대기 신청한 강의에 수강신청하면 DUPLICATE_ENROLLMENT로 409 에러를 반환하고 enrollment는 저장되지 않는다")
    void returns409_whenAlreadyInWaitlist() throws Exception {
        // given: 정원에 자리가 있더라도, 이미 대기(reservation row 존재)에 등록된 사용자는 수강신청 불가
        Long courseId = saveOpenCourseWithCount(30, 0);
        waitlistRepository.save(com.liveclass.waitlist.domain.entity.Waitlist.createNew(courseId, MEMBER_ID, 1));
        courseReservationRepository.save(CourseReservation.createNew(courseId, MEMBER_ID));
        long beforeEnrollmentCount = enrollmentRepository.count();

        // when & then
        mockMvc.perform(post("/api/enrollments")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(courseId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_002"));
        entityManager.clear();

        assertThat(enrollmentRepository.count()).isEqualTo(beforeEnrollmentCount);
    }

    private Long saveDraftCourseWithCount(int capacity) {
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
        return saved.getId();
    }
}
