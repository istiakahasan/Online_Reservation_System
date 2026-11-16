package com.example.reservation.reservation.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reservation.business.domain.BusinessEntity;
import com.example.reservation.business.repo.BusinessRepository;
import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.reservation.repo.ReservationRepository;
import com.example.reservation.service.domain.ServiceEntity;
import com.example.reservation.service.repo.ServiceRepository;
import com.example.reservation.user.domain.UserEntity;
import com.example.reservation.user.domain.UserRole;
import com.example.reservation.user.repo.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Slf4j
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                "spring.jpa.hibernate.ddl-auto=validate"
        }
)
@Import(ShedLockConcurrencyIntegrationTest.TestLockConfig.class)
class ShedLockConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test_shedlock")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @TestConfiguration
    static class TestLockConfig {
        @Bean
        public ConcurrentLockTestTask concurrentLockTestTask() {
            return new ConcurrentLockTestTask();
        }
    }

    @Service
    public static class ConcurrentLockTestTask {
        private final AtomicInteger executionCount = new AtomicInteger(0);

        @SchedulerLock(name = "concurrentTestJob", lockAtMostFor = "10s", lockAtLeastFor = "2s")
        public void executeWithLock() {
            log.info("Executing concurrentTestJob under ShedLock in thread: {}", Thread.currentThread().getName());
            executionCount.incrementAndGet();
            try {
                // Hold lock for 600ms so competing thread definitely arrives while locked
                Thread.sleep(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public int getExecutionCount() {
            return executionCount.get();
        }

        public void reset() {
            executionCount.set(0);
        }
    }

    @Autowired
    private ConcurrentLockTestTask lockTestTask;

    @Autowired
    private ReservationScheduledJobs reservationScheduledJobs;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        lockTestTask.reset();
        jdbcTemplate.execute("DELETE FROM shedlock");
        reservationRepository.deleteAll();
    }

    @Test
    @DisplayName("ShedLock prevents double execution when 2 threads attempt to execute the locked task simultaneously")
    void shedLock_PreventsDoubleExecution_AcrossConcurrentThreads() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Simultaneous start
                    lockTestTask.executeWithLock();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Wait for both threads to be ready at the gate
        boolean ready = readyLatch.await(5, TimeUnit.SECONDS);
        assertThat(ready).isTrue();

        // Release both threads simultaneously
        startLatch.countDown();

        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        executor.shutdown();

        // PROOF OF SHEDLOCK PREVENTING DOUBLE EXECUTION:
        // Even though 2 threads were fired simultaneously, ShedLock locked out the second thread!
        assertThat(lockTestTask.getExecutionCount())
                .as("ShedLock must ensure the task executes exactly 1 time, preventing double execution")
                .isEqualTo(1);

        // Verify lock record persisted in PostgreSQL shedlock table
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shedlock WHERE name = 'concurrentTestJob'",
                Integer.class
        );
        assertThat(count).isEqualTo(1);

        String lockedBy = jdbcTemplate.queryForObject(
                "SELECT locked_by FROM shedlock WHERE name = 'concurrentTestJob'",
                String.class
        );
        assertThat(lockedBy).isNotBlank();
        log.info("Verified ShedLock record in PostgreSQL: name='concurrentTestJob', locked_by='{}'", lockedBy);
    }

    @Test
    @DisplayName("ShedLock coordinates expirePendingReservations so only 1 node processes expired reservations")
    void shedLock_CoordinatesExpirePendingReservations() throws InterruptedException {
        // Seed an expired PENDING reservation
        BusinessEntity business = businessRepository.save(new BusinessEntity(
                "Clinic Omega", "clinic-omega-" + UUID.randomUUID(), "UTC", "omega@clinic.com", "+1-555-0900"
        ));

        UserEntity customer = userRepository.save(new UserEntity(
                business, "Sam", "Smith", "sam." + UUID.randomUUID() + "@example.com", "hash", "+1-555-0901", UserRole.CUSTOMER
        ));

        UserEntity staff = userRepository.save(new UserEntity(
                business, "Dr. Davis", "Smith", "davis." + UUID.randomUUID() + "@example.com", "hash", "+1-555-0902", UserRole.STAFF
        ));

        ServiceEntity service = serviceRepository.save(new ServiceEntity(
                business, "Consultation", "Initial checkup", 30, 0, new BigDecimal("50.00"), "USD"
        ));

        Instant now = Instant.now();
        Instant pastSlot = now.plus(1, ChronoUnit.DAYS);

        // PENDING reservation with hold that expired 10 minutes ago
        ReservationEntity pending = new ReservationEntity(
                business, customer, service, staff, pastSlot, pastSlot.plus(30, ChronoUnit.MINUTES),
                new BigDecimal("50.00"), "USD", now.minus(10, ChronoUnit.MINUTES), "Hold expired"
        );
        reservationRepository.saveAndFlush(pending);

        // Simulate 2 scaled instances firing expirePendingReservations simultaneously
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger totalProcessed = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    int count = reservationScheduledJobs.expirePendingReservations();
                    totalProcessed.addAndGet(count);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 1 reservation processed
        assertThat(totalProcessed.get()).isEqualTo(1);

        // Verify reservation status in DB is CANCELLED
        ReservationEntity updated = reservationRepository.findById(pending.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReservationStatus.CANCELLED);

        // Verify lock record in shedlock table
        Integer lockCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shedlock WHERE name = 'expirePendingReservations'",
                Integer.class
        );
        assertThat(lockCount).isEqualTo(1);
    }
}
