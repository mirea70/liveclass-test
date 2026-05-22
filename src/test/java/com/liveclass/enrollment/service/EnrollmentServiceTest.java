package com.liveclass.enrollment.service;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.info.CourseErrorInfo;
import com.liveclass.common.error.info.EnrollmentErrorInfo;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.dto.response.EnrollmentResponse;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@DisplayName("EnrollmentService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    private static final Long COURSE_ID = 1L;
    private static final Long USER_ID = 200L;
    private static final Long ENROLLMENT_ID = 10L;

    @InjectMocks
    private EnrollmentService enrollmentService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Test
    @DisplayName("정원이 남아있으면 PENDING 상태로 등록되고 count가 +1된다")
    void registersAsPending_whenCapacityAvailable() {
        // given
        Course course = createOpenCourse(30);
        CourseEnrollCount count = CourseEnrollCount.createNew(COURSE_ID);
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(courseEnrollCountRepository.findById(COURSE_ID)).willReturn(Optional.of(count));
        given(enrollmentRepository.save(any(Enrollment.class))).willAnswer(invocation -> {
            Enrollment e = invocation.getArgument(0);
            ReflectionTestUtils.setField(e, "id", ENROLLMENT_ID);
            return e;
        });

        // when
        EnrollmentResponse response = enrollmentService.enroll(COURSE_ID, USER_ID);

        // then
        assertThat(response.id()).isEqualTo(ENROLLMENT_ID);
        assertThat(response.courseId()).isEqualTo(COURSE_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(count.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("정원이 차있으면 WAITING 상태로 등록되고 count는 변하지 않는다")
    void registersAsWaiting_whenCapacityFull() {
        // given
        Course course = createOpenCourse(1);
        CourseEnrollCount count = CourseEnrollCount.createNew(COURSE_ID);
        count.tryReserve(1);
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(courseEnrollCountRepository.findById(COURSE_ID)).willReturn(Optional.of(count));
        given(enrollmentRepository.save(any(Enrollment.class))).willAnswer(invocation -> {
            Enrollment e = invocation.getArgument(0);
            ReflectionTestUtils.setField(e, "id", ENROLLMENT_ID);
            return e;
        });

        // when
        EnrollmentResponse response = enrollmentService.enroll(COURSE_ID, USER_ID);

        // then
        assertThat(response.status()).isEqualTo(EnrollmentStatus.WAITING);
        assertThat(count.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("강의가 OPEN이 아니면 COURSE_NOT_OPEN BusinessException이 발생한다")
    void throwsCourseNotOpen_whenCourseNotOpen() {
        // given
        Course course = createDraftCourse(30);
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));

        // when & then
        assertThatThrownBy(() -> enrollmentService.enroll(COURSE_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(EnrollmentErrorInfo.COURSE_NOT_OPEN);
    }

    @Test
    @DisplayName("동일 사용자의 활성 신청이 존재하면 DUPLICATE_ENROLLMENT BusinessException이 발생한다")
    void throwsDuplicateEnrollment_whenActiveEnrollmentExists() {
        // given
        Course course = createOpenCourse(30);
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(enrollmentRepository.existsByCourseIdAndUserIdAndStatusIn(
                COURSE_ID, USER_ID,
                List.of(EnrollmentStatus.WAITING, EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED)))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> enrollmentService.enroll(COURSE_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(EnrollmentErrorInfo.DUPLICATE_ENROLLMENT);
    }

    @Test
    @DisplayName("save 시 DataIntegrityViolationException이 발생하면 DUPLICATE_ENROLLMENT BusinessException으로 변환된다")
    void throwsDuplicateEnrollment_whenSaveViolatesUniqueConstraint() {
        // given
        Course course = createOpenCourse(30);
        CourseEnrollCount count = CourseEnrollCount.createNew(COURSE_ID);
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(courseEnrollCountRepository.findById(COURSE_ID)).willReturn(Optional.of(count));
        given(enrollmentRepository.save(any(Enrollment.class)))
                .willThrow(new DataIntegrityViolationException("unique constraint"));

        // when & then
        assertThatThrownBy(() -> enrollmentService.enroll(COURSE_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(EnrollmentErrorInfo.DUPLICATE_ENROLLMENT);
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 COURSE_NOT_FOUND BusinessException이 발생한다")
    void throwsCourseNotFound_whenCourseDoesNotExist() {
        // given
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> enrollmentService.enroll(COURSE_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorInfo())
                .isEqualTo(CourseErrorInfo.COURSE_NOT_FOUND);
    }

    private Course createDraftCourse(int capacity) {
        Course course = Course.createNew(
                100L, "Spring Boot 마스터", "Spring Boot 실전",
                new Money(99_000L), capacity,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)));
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        return course;
    }

    private Course createOpenCourse(int capacity) {
        Course course = Course.createNew(
                100L, "Spring Boot 마스터", "Spring Boot 실전",
                new Money(99_000L), capacity,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)));
        course.open();
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        return course;
    }
}
