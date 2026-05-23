package com.liveclass.enrollment;

import com.liveclass.course.domain.entity.Course;
import com.liveclass.course.domain.entity.CourseEnrollCount;
import com.liveclass.course.repository.CourseEnrollCountRepository;
import com.liveclass.course.repository.CourseRepository;
import com.liveclass.enrollment.domain.entity.Enrollment;
import com.liveclass.enrollment.domain.entity.EnrollmentStatus;
import com.liveclass.enrollment.repository.EnrollmentRepository;
import com.liveclass.enrollment.service.EnrollmentService;
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
import java.util.concurrent.atomic.AtomicInteger;

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
    private CourseRepository courseRepository;

    @MockitoSpyBean
    private CourseEnrollCountRepository courseEnrollCountRepository;

    @MockitoSpyBean
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
    @DisplayName("마지막 자리(capacity=1)에 50명이 동시 신청하면 정확히 1명 PENDING, 나머지는 WAITING으로 등록된다")
    void registersOnePendingAndRestWaiting_whenFinalSeatRace() throws InterruptedException {
        // given
        int capacity = 1;
        int threadCount = 50;
        Long courseId = saveOpenCourseWithCapacity(capacity);

        // when
        runConcurrentWithDifferentUsers(threadCount, memberId -> enrollmentService.enroll(courseId, memberId));

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
        runConcurrentWithDifferentUsers(threadCount, memberId -> enrollmentService.enroll(courseId, memberId));

        // then
        long pendingCount = countByStatus(EnrollmentStatus.PENDING);
        long waitingCount = countByStatus(EnrollmentStatus.WAITING);
        assertThat(pendingCount).isEqualTo(capacity);
        assertThat(waitingCount).isEqualTo(threadCount - capacity);

        CourseEnrollCount count = courseEnrollCountRepository.findById(courseId).orElseThrow();
        assertThat(count.getCount()).isEqualTo(capacity);
    }

    @Test
    @DisplayName("PENDING 1명 + 대기자 50명 상태에서 PENDING이 취소되면 정확히 한 명만 PENDING으로 승격되고 count는 유지된다")
    void promotesExactlyOneWaiting_whenPendingCancelledWithManyWaiters() throws InterruptedException {
        // given
        int capacity = 1;
        int waitingCount = 50;
        Long courseId = saveOpenCourseWithCapacity(capacity);
        Long pendingEnrollmentId = enrollmentRepository.save(
                com.liveclass.enrollment.domain.entity.Enrollment.createPending(courseId, MEMBER_ID)).getId();
        com.liveclass.course.domain.entity.CourseEnrollCount counter =
                courseEnrollCountRepository.findById(courseId).orElseThrow();
        counter.tryReserve(capacity);
        courseEnrollCountRepository.save(counter);
        for (int i = 0; i < waitingCount; i++) {
            enrollmentRepository.save(
                    com.liveclass.enrollment.domain.entity.Enrollment.createWaiting(courseId, 1000L + i));
        }

        // when
        enrollmentService.cancel(pendingEnrollmentId, MEMBER_ID);

        // then
        long pendingTotal = countByStatus(EnrollmentStatus.PENDING);
        long waitingRemaining = countByStatus(EnrollmentStatus.WAITING);
        assertThat(pendingTotal).isEqualTo(capacity);
        assertThat(waitingRemaining).isEqualTo(waitingCount - 1);
        assertThat(courseEnrollCountRepository.findById(courseId).orElseThrow().getCount()).isEqualTo(capacity);
    }

    @Test
    @DisplayName("정원이 가득 찬 상태에서 수강신청 트랜잭션이 현재신청인원 카운터를 본 직후 완료되기 전, 취소가 commit되면 대기상태가 아닌 신청완료 상태로 저장된다.")
    void enrollObservesCancelledSeat_throughOptimisticRetry() throws Exception {
        // given: 정원 1짜리 강좌 생성, 수강신청 B를 PENDING으로 등록 -> 카운터 count=1 (꽉 참), 대기열 없음
        int capacity = 1;
        Long ownerOfPending = MEMBER_ID;
        Long newApplicant = 300L;
        Long courseId = saveOpenCourseWithCapacity(capacity);
        Long pendingEnrollmentId = enrollmentRepository.save(
                Enrollment.createPending(courseId, ownerOfPending)).getId();
        CourseEnrollCount counter = courseEnrollCountRepository.findById(courseId).orElseThrow();
        counter.tryReserve(capacity);
        courseEnrollCountRepository.save(counter);

        CountDownLatch enrollLoadedCounter = new CountDownLatch(1);
        CountDownLatch cancelCommitted = new CountDownLatch(1);
        AtomicBoolean hookActive = new AtomicBoolean(true);

        // Mock으로 "수강신청 A가 낡은 카운터를 보는 상황" 재현
        Mockito.doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            // ① 첫 번째 호출: 옛날 스냅샷 카운터 미리 찍어두고
            if (hookActive.compareAndSet(true, false)) {
                CourseEnrollCount snapshotCount = entityManager.find(CourseEnrollCount.class, id);
                enrollLoadedCounter.countDown();
                cancelCommitted.await(10, TimeUnit.SECONDS); // 수강신청 B 취소 끝날 때까지 대기
                return Optional.ofNullable(snapshotCount); // 이전 카운터 반환
            }
            // ② retry 시: 최신 카운터 반환
            return Optional.ofNullable(entityManager.find(CourseEnrollCount.class, id));
        }).when(courseEnrollCountRepository).findById(courseId);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // when: 두 스레드 동시 실행

        // A: 수강신청 스레드 → 카운터 로드 직후 멈춤
        Future<?> enrollFuture = executor.submit(() ->
                enrollmentService.enroll(courseId, newApplicant));

        // B: 수강취소 스레드 → 수강신청 A가 카운터 본 걸 확인 후 수강취소 실행
        Future<?> cancelFuture = executor.submit(() -> {
            try {
                assertThat(enrollLoadedCounter.await(10, TimeUnit.SECONDS)).isTrue();
                enrollmentService.cancel(pendingEnrollmentId, ownerOfPending);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                cancelCommitted.countDown();
            }
        });

        enrollFuture.get(30, TimeUnit.SECONDS);
        cancelFuture.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then

        // 수강취소가 완료되어 데이터 B가 취소상태여야한다.
        Enrollment originalPending = enrollmentRepository.findById(pendingEnrollmentId).orElseThrow();
        assertThat(originalPending.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);

        List<Enrollment> all = enrollmentRepository.findAll();
        long waitingTotal = all.stream().filter(e -> e.getStatus() == EnrollmentStatus.WAITING).count();
        long pendingTotal = all.stream().filter(e -> e.getStatus() == EnrollmentStatus.PENDING).count();

        assertThat(waitingTotal)
                .as("정원이 빈 채로 대기상태의 수강신청 데이터가 생기면 안된다.")
                .isZero();

        assertThat(pendingTotal)
                .as("취소된 자리를 신규 신청자가 차지해야 한다.")
                .isEqualTo(capacity);
        assertThat(courseEnrollCountRepository.findById(courseId).orElseThrow().getCount()).isEqualTo(capacity);
    }

    @Test
    @DisplayName("서로 다른 사용자의 수강취소가 동시에 들어와 둘 다 같은 대기자를 승격시키려고 해도, 대기자는 정확히 한 번만 승격되고 카운터는 1 감소한다.")
    void promoteOnceAndReleaseOnce_whenConcurrentCancelsCompeteForSameWaiter() throws Exception {
        // given: 정원 2짜리 강좌 생성, B/C가 PENDING, W가 대기 1명 → 카운터 count=2 (꽉 참)
        int capacity = 2;
        Long ownerB = MEMBER_ID;
        Long ownerC = 301L;
        Long ownerW = 302L;
        Long courseId = saveOpenCourseWithCapacity(capacity);
        Long bEnrollmentId = enrollmentRepository.save(Enrollment.createPending(courseId, ownerB)).getId();
        Long cEnrollmentId = enrollmentRepository.save(Enrollment.createPending(courseId, ownerC)).getId();
        Long wEnrollmentId = enrollmentRepository.save(Enrollment.createWaiting(courseId, ownerW)).getId();
        CourseEnrollCount counter = courseEnrollCountRepository.findById(courseId).orElseThrow();
        counter.tryReserve(capacity);
        counter.tryReserve(capacity);
        courseEnrollCountRepository.save(counter);

        AtomicInteger callOrder = new AtomicInteger();
        CountDownLatch bothFoundSameWaiter = new CountDownLatch(1);
        CountDownLatch t1Committed = new CountDownLatch(1);

        // Mock으로 "두 취소 트랜잭션이 같은 대기자 W를 동시에 발견하는 상황" 재현
        Mockito.doAnswer(invocation -> {
            int order = callOrder.incrementAndGet();
            Enrollment w = entityManager.find(Enrollment.class, wEnrollmentId);
            Optional<Enrollment> result = (w != null && w.getStatus() == EnrollmentStatus.WAITING)
                    ? Optional.of(w)
                    : Optional.empty();
            // ① T1 첫 호출: W 발견 후 T2도 같은 W를 볼 때까지 대기
            if (order == 1) {
                bothFoundSameWaiter.await(10, TimeUnit.SECONDS);
            }
            // ② T2 두 번째 호출: W 발견 신호 + T1이 먼저 commit하도록 대기 (commit 순서 강제)
            else if (order == 2) {
                bothFoundSameWaiter.countDown();
                t1Committed.await(10, TimeUnit.SECONDS);
            }
            // ③ T2 retry 시: W는 이미 PENDING이라 empty 반환
            return result;
        }).when(enrollmentRepository).findFirstByCourseIdAndStatusOrderByCreatedAtAsc(courseId, EnrollmentStatus.WAITING);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // when: 두 스레드 동시 실행

        // T1: B 취소 스레드 → W를 승격시킨 뒤 commit (먼저 성공)
        Future<?> t1 = executor.submit(() -> {
            try {
                enrollmentService.cancel(bEnrollmentId, ownerB);
            } finally {
                t1Committed.countDown();
            }
        });

        // T2: C 취소 스레드 → 같은 W를 승격 시도 → 대기열 데이터 Version 충돌 → 재시도 → 신청인원 수 감소
        Future<?> t2 = executor.submit(() ->
                enrollmentService.cancel(cEnrollmentId, ownerC));

        t1.get(30, TimeUnit.SECONDS);
        t2.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then

        // B, C는 모두 취소 상태여야 한다.
        assertThat(enrollmentRepository.findById(bEnrollmentId).orElseThrow().getStatus())
                .isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(enrollmentRepository.findById(cEnrollmentId).orElseThrow().getStatus())
                .isEqualTo(EnrollmentStatus.CANCELLED);

        // W는 정확히 한 번만 승격처리 되어야 한다.
        assertThat(enrollmentRepository.findById(wEnrollmentId).orElseThrow().getStatus())
                .as("두 취소가 같은 대기자를 승격 시도해도 결과적으로 한 번만 PENDING이어야 한다.")
                .isEqualTo(EnrollmentStatus.PENDING);

        // 카운터는 정확히 한 번만 감소해야 한다. (한 자리는 W가 채우고, 나머지 한 자리는 신청인원수 감소)
        assertThat(courseEnrollCountRepository.findById(courseId).orElseThrow().getCount())
                .as("승격만 두 번 처리되어 카운터 감소가 유실되면 안 된다.")
                .isEqualTo(capacity - 1);
    }

    @Test
    @DisplayName("같은 enrollment에 동시에 cancel 50건이 들어와도 최종적으로 1건만 성공한다")
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
