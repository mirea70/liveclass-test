package com.liveclass.enrollment;

import com.liveclass.common.domain.vo.Money;
import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.vo.CoursePeriod;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.enrollment.service.EnrollmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("수강 신청 동시성 테스트")
class EnrollmentConcurrencyTest {

    private static final Long CREATOR_ID = 100L;
    private static final Long USER_ID = 200L;
    private static final int THREAD_POOL_SIZE = 32;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @AfterEach
    void cleanUp() {
        enrollmentRepository.deleteAllInBatch();
        courseEnrollCountRepository.deleteAllInBatch();
        courseRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("같은 사용자가 동시에 50건 신청해도 정확히 1건만 활성으로 등록된다")
    void registersExactlyOneActive_whenSameUserConcurrentEnroll() throws InterruptedException {
        // given
        Long courseId = saveOpenCourseWithCapacity(30);
        int threadCount = 50;

        // when
        runConcurrent(threadCount, () -> enrollmentService.enroll(courseId, USER_ID));

        // then
        List<Enrollment> allEnrollments = enrollmentRepository.findAll();
        long activeCount = allEnrollments.stream()
                .filter(e -> e.getStatus() != EnrollmentStatus.CANCELLED)
                .count();
        assertThat(activeCount).isEqualTo(1);

        CourseEnrollCount count = courseEnrollCountRepository.findById(courseId).orElseThrow();
        assertThat(count.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("마지막 자리(capacity=1)에 50명이 동시 신청하면 정확히 1명 PENDING, 나머지는 WAITING으로 등록된다")
    void registersOnePendingAndRestWaiting_whenFinalSeatRace() throws InterruptedException {
        // given
        int capacity = 1;
        int threadCount = 50;
        Long courseId = saveOpenCourseWithCapacity(capacity);

        // when
        runConcurrentWithDifferentUsers(threadCount, userId -> enrollmentService.enroll(courseId, userId));

        // then
        long pendingCount = countByStatus(EnrollmentStatus.PENDING);
        long waitingCount = countByStatus(EnrollmentStatus.WAITING);
        assertThat(pendingCount).isEqualTo(capacity);
        assertThat(waitingCount).isEqualTo(threadCount - capacity);

        CourseEnrollCount count = courseEnrollCountRepository.findById(courseId).orElseThrow();
        assertThat(count.getCount()).isEqualTo(capacity);
    }

    @Test
    @DisplayName("정원 5명 강의에 50명이 동시 신청하면 정확히 5명 PENDING, 45명 WAITING으로 등록된다")
    void registersExactCapacityPendingAndRestWaiting_whenOverCapacityRace() throws InterruptedException {
        // given
        int capacity = 5;
        int threadCount = 50;
        Long courseId = saveOpenCourseWithCapacity(capacity);

        // when
        runConcurrentWithDifferentUsers(threadCount, userId -> enrollmentService.enroll(courseId, userId));

        // then
        long pendingCount = countByStatus(EnrollmentStatus.PENDING);
        long waitingCount = countByStatus(EnrollmentStatus.WAITING);
        assertThat(pendingCount).isEqualTo(capacity);
        assertThat(waitingCount).isEqualTo(threadCount - capacity);

        CourseEnrollCount count = courseEnrollCountRepository.findById(courseId).orElseThrow();
        assertThat(count.getCount()).isEqualTo(capacity);
    }

    private long countByStatus(EnrollmentStatus status) {
        return enrollmentRepository.findAll().stream()
                .filter(e -> e.getStatus() == status)
                .count();
    }

    private void runConcurrent(int threadCount, Runnable action) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    action.run();
                } catch (Exception ignored) {
                    // 예상되는 경합 예외(DUPLICATE 등) 무시 - 최종 상태로 검증
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    private void runConcurrentWithDifferentUsers(int threadCount, java.util.function.LongConsumer action) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final long userId = 1000L + i;
            executor.submit(() -> {
                try {
                    action.accept(userId);
                } catch (Exception ignored) {
                    // 동시성 경합 예외 무시 - 최종 상태로 검증
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    private Long saveOpenCourseWithCapacity(int capacity) {
        Course course = Course.createNew(
                CREATOR_ID,
                "Spring Boot 마스터",
                "Spring Boot 실전 강의",
                new Money(99_000L),
                capacity,
                new CoursePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)));
        course.open();
        Course saved = courseRepository.save(course);
        courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
        return saved.getId();
    }
}
