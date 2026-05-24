package com.liveclass.enrollment;

import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.domain.entity.CourseStatus;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.enrollment.service.EnrollmentService;
import com.liveclass.course.service.CourseService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("수강 신청 동시성 테스트")
class EnrollmentConcurrencyTest {

    private static final Long CREATOR_ID = 100L;
    private static final Long MEMBER_ID = 200L;
    private static final int THREAD_POOL_SIZE = 32;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CourseService courseService;

    @MockitoSpyBean
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @PersistenceContext
    private EntityManager entityManager;

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
        runConcurrent(threadCount, () -> enrollmentService.enroll(courseId, MEMBER_ID));

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
    @DisplayName("마지막 자리(capacity=1)에 50명이 동시 신청하면 정확히 1명 PENDING으로 등록되고 나머지는 거부된다")
    void registersOnePending_andRestRejected_whenFinalSeatRace() throws InterruptedException {
        // given
        int capacity = 1;
        int threadCount = 50;
        Long courseId = saveOpenCourseWithCapacity(capacity);

        // when
        runConcurrentWithDifferentUsers(threadCount, memberId -> enrollmentService.enroll(courseId, memberId));

        // then
        long pendingCount = countByStatus(EnrollmentStatus.PENDING);
        assertThat(pendingCount).isEqualTo(capacity);

        CourseEnrollCount count = courseEnrollCountRepository.findById(courseId).orElseThrow();
        assertThat(count.getCount()).isEqualTo(capacity);
    }

    @Test
    @DisplayName("정원 5명 강의에 50명이 동시 신청하면 정확히 5명 PENDING으로 등록되고 45명은 거부된다")
    void registersExactCapacityPending_andRestRejected_whenOverCapacityRace() throws InterruptedException {
        // given
        int capacity = 5;
        int threadCount = 50;
        Long courseId = saveOpenCourseWithCapacity(capacity);

        // when
        runConcurrentWithDifferentUsers(threadCount, memberId -> enrollmentService.enroll(courseId, memberId));

        // then
        long pendingCount = countByStatus(EnrollmentStatus.PENDING);
        assertThat(pendingCount).isEqualTo(capacity);

        CourseEnrollCount count = courseEnrollCountRepository.findById(courseId).orElseThrow();
        assertThat(count.getCount()).isEqualTo(capacity);
    }

    @Test
    @DisplayName("수강신청 트랜잭션이 OPEN 강의를 본 직후 완료되기 전, 강의가 CLOSED로 전환되면 신청이 거부된다 (낙관적 락 + 재시도)")
    void rejectsEnrollment_whenCourseClosedDuringTransaction() throws Exception {
        // given: OPEN 강의, 카운터 0, 대기열 없음
        Long courseId = saveOpenCourseWithCapacity(10);

        CountDownLatch enrollLoadedCourse = new CountDownLatch(1);
        CountDownLatch courseClosed = new CountDownLatch(1);
        AtomicBoolean hookActive = new AtomicBoolean(true);

        // Mock으로 "수강신청 A가 OPEN 강의 조회 직후 commit 직전에 close 트랜잭션이 commit되는 상황" 재현
        Mockito.doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            // ① 첫 번째 호출 (enroll의 getOpenCourse): OPEN 스냅샷 미리 찍고
            if (hookActive.compareAndSet(true, false)) {
                Course staleSnapshot = entityManager.find(Course.class, id);
                enrollLoadedCourse.countDown();
                courseClosed.await(10, TimeUnit.SECONDS); // close 커밋까지 대기
                return Optional.ofNullable(staleSnapshot); // 이전(OPEN) 스냅샷 반환
            }
            // ② retry / close 트랜잭션의 호출: 최신 강의 반환
            return Optional.ofNullable(entityManager.find(Course.class, id));
        }).when(courseRepository).findById(courseId);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // when: 두 스레드 동시 실행

        // A: 수강신청 스레드 → Course 로드 직후 멈춤
        Future<?> enrollFuture = executor.submit(() -> {
            try {
                enrollmentService.enroll(courseId, MEMBER_ID);
            } catch (Exception ignored) {
                // COURSE_NOT_OPEN으로 거부될 것 — 정상 동작
            }
        });

        // B: 강의 종료 스레드 → enroll이 Course 본 걸 확인 후 CLOSED 전환
        Future<?> closeFuture = executor.submit(() -> {
            try {
                assertThat(enrollLoadedCourse.await(10, TimeUnit.SECONDS)).isTrue();
                courseService.updateStatus(courseId, CREATOR_ID, CourseStatus.CLOSED);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                courseClosed.countDown();
            }
        });

        enrollFuture.get(30, TimeUnit.SECONDS);
        closeFuture.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then

        // 강의는 CLOSED로 정상 전환됐어야 한다.
        assertThat(courseRepository.findById(courseId).orElseThrow().getStatus())
                .isEqualTo(CourseStatus.CLOSED);

        // enrollment는 생성되지 않아야 한다.
        assertThat(enrollmentRepository.findAll())
                .as("CLOSED로 전환된 강의에 enrollment가 생성되면 안 된다")
                .isEmpty();

        // 카운터도 증가하지 않아야 한다.
        assertThat(courseEnrollCountRepository.findById(courseId).orElseThrow().getCount())
                .isZero();
    }

    @Test
    @DisplayName("같은 enrollment에 동시에 cancel 50건이 들어와도 최종적으로 1건만 성공해 카운터가 정확히 1 감소한다")
    void cancelsExactlyOnce_whenConcurrentCancelOnSameEnrollment() throws InterruptedException {
        // given
        Long courseId = saveOpenCourseWithCapacity(30);
        Long pendingEnrollmentId = enrollmentRepository.save(
                com.liveclass.enrollment.domain.entity.Enrollment.createPending(courseId, MEMBER_ID)).getId();
        com.liveclass.course.domain.entity.CourseEnrollCount counter =
                courseEnrollCountRepository.findById(courseId).orElseThrow();
        counter.tryReserve(30);
        courseEnrollCountRepository.save(counter);
        int threadCount = 50;

        // when
        runConcurrent(threadCount, () -> enrollmentService.cancel(pendingEnrollmentId, MEMBER_ID));

        // then
        com.liveclass.enrollment.domain.entity.Enrollment reloaded =
                enrollmentRepository.findById(pendingEnrollmentId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(courseEnrollCountRepository.findById(courseId).orElseThrow().getCount()).isZero();
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
            final long memberId = 1000L + i;
            executor.submit(() -> {
                try {
                    action.accept(memberId);
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
                99_000L,
                capacity,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
        course.open();
        Course saved = courseRepository.save(course);
        courseEnrollCountRepository.save(CourseEnrollCount.createNew(saved.getId()));
        return saved.getId();
    }
}
