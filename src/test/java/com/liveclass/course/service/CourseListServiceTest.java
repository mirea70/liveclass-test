package com.liveclass.course.service;

import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.dto.response.CourseSummaryResponse;
import com.liveclass.course.repository.CourseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("CourseListService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class CourseListServiceTest {

    @InjectMocks
    private CourseListService courseListService;

    @Mock
    private CourseRepository courseRepository;

    @Test
    @DisplayName("status를 null로 호출하면 repository에 null을 그대로 전달한다")
    void delegatesNullStatus_whenStatusIsNull() {
        // given
        List<CourseSummaryResponse> expected = List.of(summary(1L, CourseStatus.DRAFT));
        given(courseRepository.findSummaries(null)).willReturn(expected);

        // when
        List<CourseSummaryResponse> result = courseListService.list(null);

        // then
        assertThat(result).isEqualTo(expected);
        verify(courseRepository).findSummaries(null);
    }

    @Test
    @DisplayName("status 필터를 지정하면 repository에 그대로 전달한다")
    void delegatesStatusFilter_whenStatusGiven() {
        // given
        List<CourseSummaryResponse> expected = List.of(summary(1L, CourseStatus.OPEN));
        given(courseRepository.findSummaries(CourseStatus.OPEN)).willReturn(expected);

        // when
        List<CourseSummaryResponse> result = courseListService.list(CourseStatus.OPEN);

        // then
        assertThat(result).isEqualTo(expected);
        verify(courseRepository).findSummaries(CourseStatus.OPEN);
    }

    private CourseSummaryResponse summary(Long id, CourseStatus status) {
        return new CourseSummaryResponse(
                id, "title", 99_000L, 30, 0,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31),
                status
        );
    }
}
