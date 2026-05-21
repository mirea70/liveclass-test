package com.liveclass.course.service;

import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.dto.request.CourseCreateRequest;
import com.liveclass.course.dto.response.CourseResponse;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("CourseCreateService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class CourseCreateServiceTest {

    @InjectMocks
    private CourseCreateService courseCreateService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Test
    @DisplayName("강의 등록 시 Course와 CourseEnrollCount가 모두 저장된다")
    void savesBothCourseAndEnrollCount_whenCreated() {
        // given
        CourseCreateRequest request = createRequest();
        Long creatorId = 100L;
        given(courseRepository.save(any(Course.class))).willAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            ReflectionTestUtils.setField(course, "id", 1L);
            return course;
        });
        given(courseEnrollCountRepository.save(any(CourseEnrollCount.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        courseCreateService.create(creatorId, request);

        // then
        verify(courseRepository).save(any(Course.class));
        ArgumentCaptor<CourseEnrollCount> countCaptor = ArgumentCaptor.forClass(CourseEnrollCount.class);
        verify(courseEnrollCountRepository).save(countCaptor.capture());
        assertThat(countCaptor.getValue().getCourseId()).isEqualTo(1L);
        assertThat(countCaptor.getValue().getCount()).isZero();
    }

    @Test
    @DisplayName("요청 입력값이 Course에 그대로 전달되고 DRAFT 상태로 생성된다")
    void mapsRequestFieldsToCourse_whenCreated() {
        // given
        CourseCreateRequest request = createRequest();
        Long creatorId = 100L;
        ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
        given(courseRepository.save(courseCaptor.capture())).willAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            ReflectionTestUtils.setField(course, "id", 1L);
            return course;
        });
        given(courseEnrollCountRepository.save(any(CourseEnrollCount.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        courseCreateService.create(creatorId, request);

        // then
        Course saved = courseCaptor.getValue();
        assertThat(saved.getCreatorId()).isEqualTo(creatorId);
        assertThat(saved.getTitle()).isEqualTo("Spring Boot 마스터");
        assertThat(saved.getDescription()).isEqualTo("Spring Boot 실전");
        assertThat(saved.getPrice().getAmount()).isEqualTo(99_000L);
        assertThat(saved.getCapacity()).isEqualTo(30);
        assertThat(saved.getPeriod().getStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(saved.getPeriod().getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(saved.getStatus()).isEqualTo(CourseStatus.DRAFT);
    }

    @Test
    @DisplayName("응답에 생성된 강의 정보와 count 0이 포함된다")
    void returnsResponseWithCourseInfoAndZeroCount_whenCreated() {
        // given
        CourseCreateRequest request = createRequest();
        Long creatorId = 100L;
        given(courseRepository.save(any(Course.class))).willAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            ReflectionTestUtils.setField(course, "id", 1L);
            return course;
        });
        given(courseEnrollCountRepository.save(any(CourseEnrollCount.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        CourseResponse response = courseCreateService.create(creatorId, request);

        // then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.creatorId()).isEqualTo(creatorId);
        assertThat(response.title()).isEqualTo("Spring Boot 마스터");
        assertThat(response.price()).isEqualTo(99_000L);
        assertThat(response.capacity()).isEqualTo(30);
        assertThat(response.count()).isZero();
        assertThat(response.status()).isEqualTo(CourseStatus.DRAFT);
    }

    private CourseCreateRequest createRequest() {
        return new CourseCreateRequest(
                "Spring Boot 마스터",
                "Spring Boot 실전",
                99_000L,
                30,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 8, 31)
        );
    }
}
