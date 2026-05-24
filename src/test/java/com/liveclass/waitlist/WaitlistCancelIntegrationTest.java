package com.liveclass.waitlist;

import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.support.IntegrationTestSupport;
import com.liveclass.waitlist.domain.entity.Waitlist;
import com.liveclass.waitlist.repository.WaitlistRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("대기 취소 통합 테스트")
class WaitlistCancelIntegrationTest extends IntegrationTestSupport {

    private static final Long CREATOR_ID = 100L;
    private static final Long MEMBER_ID = 200L;
    private static final Long OTHER_MEMBER_ID = 999L;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Autowired
    private WaitlistRepository waitlistRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("본인이 대기를 취소하면 204를 반환하고 waitlist row가 삭제되며 뒷사람 order_num이 한 칸씩 당겨진다")
    void cancelsAndShiftsOrderNum_whenOwnerRequests() throws Exception {
        // given: 1, 2, 3, 4번 대기자가 있고 그 중 2번이 취소
        Long courseId = saveOpenCourse();
        waitlistRepository.save(Waitlist.createNew(courseId, 1001L, 1));
        Waitlist target = waitlistRepository.save(Waitlist.createNew(courseId, MEMBER_ID, 2));
        Waitlist third = waitlistRepository.save(Waitlist.createNew(courseId, 1003L, 3));
        Waitlist fourth = waitlistRepository.save(Waitlist.createNew(courseId, 1004L, 4));

        // when & then
        mockMvc.perform(delete("/api/waitlists/{waitlistId}", target.getId())
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isNoContent());

        // bulk UPDATE는 영속성 컨텍스트를 우회하므로 명시적 clear 후 검증
        entityManager.clear();
        assertThat(waitlistRepository.findById(target.getId())).isEmpty();
        assertThat(waitlistRepository.findById(third.getId()).orElseThrow().getOrderNum()).isEqualTo(2);
        assertThat(waitlistRepository.findById(fourth.getId()).orElseThrow().getOrderNum()).isEqualTo(3);
    }

    @Test
    @DisplayName("대기 신청이 존재하지 않으면 WAITLIST_001로 404를 반환한다")
    void returns404_whenWaitlistNotFound() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/waitlists/{waitlistId}", 9999L)
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WAITLIST_001"));
    }

    @Test
    @DisplayName("본인이 아니면 WAITLIST_002로 403을 반환한다")
    void returns403_whenNotOwner() throws Exception {
        // given
        Long courseId = saveOpenCourse();
        Waitlist waitlist = waitlistRepository.save(Waitlist.createNew(courseId, MEMBER_ID, 1));

        // when & then
        mockMvc.perform(delete("/api/waitlists/{waitlistId}", waitlist.getId())
                        .header("X-Member-Id", OTHER_MEMBER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WAITLIST_002"));

        // waitlist는 그대로 유지
        assertThat(waitlistRepository.findById(waitlist.getId())).isPresent();
    }

    private Long saveOpenCourse() {
        Course course = Course.createNew(
                CREATOR_ID,
                "Spring Boot 마스터",
                "Spring Boot 실전 강의",
                99_000L, 1,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
        course.open();
        Course saved = courseRepository.save(course);
        courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
        return saved.getId();
    }
}
