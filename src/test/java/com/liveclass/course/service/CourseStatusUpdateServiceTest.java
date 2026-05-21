package com.liveclass.course.service;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.course.repository.CourseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@DisplayName("CourseStatusUpdateService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class CourseStatusUpdateServiceTest {

    private static final Long COURSE_ID = 1L;
    private static final Long CREATOR_ID = 100L;
    private static final Long OTHER_USER_ID = 999L;

    @InjectMocks
    private CourseStatusUpdateService courseStatusUpdateService;

    @Mock
    private CourseRepository courseRepository;

    @Test
    @DisplayName("강의가 존재하지 않으면 COURSE_NOT_FOUND BusinessException이 발생한다")
    void throwsCourseNotFound_whenCourseDoesNotExist() {
        // given
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> courseStatusUpdateService.updateStatus(COURSE_ID, CREATOR_ID, CourseStatus.OPEN))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.COURSE_NOT_FOUND);
    }

    @Test
    @DisplayName("요청자가 크리에이터가 아니면 NOT_COURSE_CREATOR BusinessException이 발생한다")
    void throwsNotCourseCreator_whenRequesterIsNotCreator() {
        // given
        Course course = createDraftCourse();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));

        // when & then
        assertThatThrownBy(() -> courseStatusUpdateService.updateStatus(COURSE_ID, OTHER_USER_ID, CourseStatus.OPEN))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.NOT_COURSE_CREATOR);
    }

    @Test
    @DisplayName("DRAFT 강의에 OPEN 요청 시 강의가 OPEN 상태로 전이된다")
    void transitionsToOpen_whenDraftToOpen() {
        // given
        Course course = createDraftCourse();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));

        // when
        courseStatusUpdateService.updateStatus(COURSE_ID, CREATOR_ID, CourseStatus.OPEN);

        // then
        assertThat(course.getStatus()).isEqualTo(CourseStatus.OPEN);
    }

    @Test
    @DisplayName("OPEN 강의에 CLOSED 요청 시 강의가 CLOSED 상태로 전이된다")
    void transitionsToClosed_whenOpenToClosed() {
        // given
        Course course = createDraftCourse();
        course.open();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));

        // when
        courseStatusUpdateService.updateStatus(COURSE_ID, CREATOR_ID, CourseStatus.CLOSED);

        // then
        assertThat(course.getStatus()).isEqualTo(CourseStatus.CLOSED);
    }

    @Test
    @DisplayName("DRAFT를 target으로 요청하면 INVALID_STATUS_TRANSITION DomainException이 발생한다")
    void throwsInvalidStatusTransition_whenTargetIsDraft() {
        // given
        Course course = createDraftCourse();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));

        // when & then
        assertThatThrownBy(() -> courseStatusUpdateService.updateStatus(COURSE_ID, CREATOR_ID, CourseStatus.DRAFT))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("이미 OPEN인 강의를 다시 OPEN으로 요청하면 INVALID_STATUS_TRANSITION DomainException이 발생한다")
    void throwsInvalidStatusTransition_whenOpeningAlreadyOpenCourse() {
        // given
        Course course = createDraftCourse();
        course.open();
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));

        // when & then
        assertThatThrownBy(() -> courseStatusUpdateService.updateStatus(COURSE_ID, CREATOR_ID, CourseStatus.OPEN))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.INVALID_STATUS_TRANSITION);
    }

    private Course createDraftCourse() {
        Course course = Course.createNew(
                CREATOR_ID,
                "Spring Boot 마스터",
                "Spring Boot 실전",
                new Money(99_000L),
                30,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31))
        );
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        return course;
    }
}
