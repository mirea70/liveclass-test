package com.liveclass.course.service;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.course.dto.request.CourseCreateRequest;
import com.liveclass.course.dto.response.CourseResponse;
import com.liveclass.course.dto.response.CourseSummaryResponse;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("CourseService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    private static final Long COURSE_ID = 1L;
    private static final Long CREATOR_ID = 100L;
    private static final Long OTHER_USER_ID = 999L;

    @InjectMocks
    private CourseService courseService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Nested
    @DisplayName("강의 등록 (create)")
    class Create {

        @Test
        @DisplayName("강의 등록 시 Course와 CourseEnrollCount가 모두 저장된다")
        void savesBothCourseAndEnrollCount_whenCreated() {
            // given
            CourseCreateRequest request = createRequest();
            given(courseRepository.save(any(Course.class))).willAnswer(invocation -> {
                Course course = invocation.getArgument(0);
                ReflectionTestUtils.setField(course, "id", COURSE_ID);
                return course;
            });
            given(courseEnrollCountRepository.save(any(CourseEnrollCount.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            courseService.create(CREATOR_ID, request);

            // then
            verify(courseRepository).save(any(Course.class));
            ArgumentCaptor<CourseEnrollCount> countCaptor = ArgumentCaptor.forClass(CourseEnrollCount.class);
            verify(courseEnrollCountRepository).save(countCaptor.capture());
            assertThat(countCaptor.getValue().getCourseId()).isEqualTo(COURSE_ID);
            assertThat(countCaptor.getValue().getCount()).isZero();
        }

        @Test
        @DisplayName("요청 입력값이 Course에 그대로 전달되고 DRAFT 상태로 생성된다")
        void mapsRequestFieldsToCourse_whenCreated() {
            // given
            CourseCreateRequest request = createRequest();
            ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
            given(courseRepository.save(courseCaptor.capture())).willAnswer(invocation -> {
                Course course = invocation.getArgument(0);
                ReflectionTestUtils.setField(course, "id", COURSE_ID);
                return course;
            });
            given(courseEnrollCountRepository.save(any(CourseEnrollCount.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            courseService.create(CREATOR_ID, request);

            // then
            Course saved = courseCaptor.getValue();
            assertThat(saved.getCreatorId()).isEqualTo(CREATOR_ID);
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
            given(courseRepository.save(any(Course.class))).willAnswer(invocation -> {
                Course course = invocation.getArgument(0);
                ReflectionTestUtils.setField(course, "id", COURSE_ID);
                return course;
            });
            given(courseEnrollCountRepository.save(any(CourseEnrollCount.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            CourseResponse response = courseService.create(CREATOR_ID, request);

            // then
            assertThat(response.id()).isEqualTo(COURSE_ID);
            assertThat(response.creatorId()).isEqualTo(CREATOR_ID);
            assertThat(response.title()).isEqualTo("Spring Boot 마스터");
            assertThat(response.price()).isEqualTo(99_000L);
            assertThat(response.capacity()).isEqualTo(30);
            assertThat(response.enrollCount()).isZero();
            assertThat(response.status()).isEqualTo(CourseStatus.DRAFT);
        }
    }

    @Nested
    @DisplayName("강의 상태 변경 (updateStatus)")
    class UpdateStatus {

        @Test
        @DisplayName("강의가 존재하지 않으면 COURSE_NOT_FOUND BusinessException이 발생한다")
        void throwsCourseNotFound_whenCourseDoesNotExist() {
            // given
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> courseService.updateStatus(COURSE_ID, CREATOR_ID, CourseStatus.OPEN))
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
            assertThatThrownBy(() -> courseService.updateStatus(COURSE_ID, OTHER_USER_ID, CourseStatus.OPEN))
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
            courseService.updateStatus(COURSE_ID, CREATOR_ID, CourseStatus.OPEN);

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
            courseService.updateStatus(COURSE_ID, CREATOR_ID, CourseStatus.CLOSED);

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
            assertThatThrownBy(() -> courseService.updateStatus(COURSE_ID, CREATOR_ID, CourseStatus.DRAFT))
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
            assertThatThrownBy(() -> courseService.updateStatus(COURSE_ID, CREATOR_ID, CourseStatus.OPEN))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).getErrorInfo())
                    .isEqualTo(CourseErrorInfo.INVALID_STATUS_TRANSITION);
        }
    }

    @Nested
    @DisplayName("강의 목록 조회 (list)")
    class ListCourses {

        @Test
        @DisplayName("status를 null로 호출하면 repository에 null을 그대로 전달한다")
        void delegatesNullStatus_whenStatusIsNull() {
            // given
            List<CourseSummaryResponse> expected = List.of(summary(1L, CourseStatus.DRAFT));
            given(courseRepository.findSummaries(null)).willReturn(expected);

            // when
            List<CourseSummaryResponse> result = courseService.getList(null);

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
            List<CourseSummaryResponse> result = courseService.getList(CourseStatus.OPEN);

            // then
            assertThat(result).isEqualTo(expected);
            verify(courseRepository).findSummaries(CourseStatus.OPEN);
        }
    }

    @Nested
    @DisplayName("강의 상세 조회 (getDetail)")
    class GetDetail {

        @Test
        @DisplayName("강의가 존재하면 CourseResponse를 반환한다")
        void returnsCourseResponse_whenCourseExists() {
            // given
            CourseResponse expected = sampleResponse();
            given(courseRepository.findDetail(COURSE_ID)).willReturn(Optional.of(expected));

            // when
            CourseResponse result = courseService.getDetail(COURSE_ID);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("강의가 존재하지 않으면 COURSE_NOT_FOUND BusinessException이 발생한다")
        void throwsCourseNotFound_whenCourseDoesNotExist() {
            // given
            given(courseRepository.findDetail(COURSE_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> courseService.getDetail(COURSE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorInfo())
                    .isEqualTo(CourseErrorInfo.COURSE_NOT_FOUND);
        }
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

    private CourseSummaryResponse summary(Long id, CourseStatus status) {
        return new CourseSummaryResponse(
                id, "title", 99_000L, 30, 0,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31),
                status
        );
    }

    private CourseResponse sampleResponse() {
        return new CourseResponse(
                COURSE_ID, CREATOR_ID, "Spring Boot 마스터", "Spring Boot 실전",
                99_000L, 30, 0,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31),
                CourseStatus.DRAFT
        );
    }
}
