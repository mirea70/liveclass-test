package com.liveclass.waitlist;

import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.support.IntegrationTestSupport;
import com.liveclass.waitlist.domain.entity.Waitlist;
import com.liveclass.waitlist.repository.WaitlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("내 대기 목록 조회 통합 테스트")
class MyWaitlistsIntegrationTest extends IntegrationTestSupport {

    private static final Long CREATOR_ID = 100L;
    private static final Long MEMBER_ID = 200L;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Autowired
    private WaitlistRepository waitlistRepository;

    @Test
    @DisplayName("내 대기 목록 조회 시 200과 강의 정보 + order_num이 포함된 List를 반환한다")
    void returns200WithMyWaitlists() throws Exception {
        // given
        Long courseAId = saveOpenCourse("강의 A", 10_000L);
        Long courseBId = saveOpenCourse("강의 B", 20_000L);
        waitlistRepository.save(Waitlist.createNew(courseAId, MEMBER_ID, 2));
        waitlistRepository.save(Waitlist.createNew(courseBId, MEMBER_ID, 5));

        // when & then
        mockMvc.perform(get("/api/waitlists/me")
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("다른 사용자의 대기는 본인 목록에 포함되지 않는다")
    void excludesOtherMembersWaitlists() throws Exception {
        // given
        Long courseId = saveOpenCourse("강의 A", 10_000L);
        waitlistRepository.save(Waitlist.createNew(courseId, MEMBER_ID, 1));
        waitlistRepository.save(Waitlist.createNew(courseId, 999L, 2));

        // when & then
        mockMvc.perform(get("/api/waitlists/me")
                        .header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderNum").value(1));
    }

    private Long saveOpenCourse(String title, long price) {
        Course course = Course.createNew(
                CREATOR_ID, title, "설명",
                price, 30,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
        course.open();
        Course saved = courseRepository.save(course);
        courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
        return saved.getId();
    }
}
