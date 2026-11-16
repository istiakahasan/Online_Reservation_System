package com.example.reservation.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reservation.auth.service.JwtService;
import com.example.reservation.business.domain.BusinessEntity;
import com.example.reservation.business.repo.BusinessRepository;
import com.example.reservation.reservation.api.dto.CreateReservationRequestDto;
import com.example.reservation.reservation.api.dto.ReservationResponseDto;
import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.reservation.domain.exception.SlotAlreadyBookedException;
import com.example.reservation.reservation.repo.ReservationRepository;
import com.example.reservation.reservation.service.ReservationEventListener;
import com.example.reservation.reservation.service.ReservationService;
import com.example.reservation.service.domain.ServiceEntity;
import com.example.reservation.service.repo.ServiceRepository;
import com.example.reservation.staff.domain.StaffWorkingHoursEntity;
import com.example.reservation.staff.repo.StaffWorkingHoursRepository;
import com.example.reservation.user.domain.UserEntity;
import com.example.reservation.user.domain.UserRole;
import com.example.reservation.user.repo.UserRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Slf4j
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                "spring.jpa.hibernate.ddl-auto=validate"
        }
)
class ReservationConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test_reservations")
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

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private StaffWorkingHoursRepository workingHoursRepository;

    @Autowired
    private ReservationEventListener eventListener;

    private BusinessEntity business;
    private UserEntity customer;
    private UserEntity staff;
    private ServiceEntity service;

    private Instant targetSlotStart;
    private String customerJwt;

    @BeforeEach
    void setUp() {
        eventListener.reset();
        reservationRepository.deleteAll();

        // 1. Seed Business (Tenant)
        business = businessRepository.save(new BusinessEntity(
                "Metropolis Health Spa",
                "metropolis-spa-" + UUID.randomUUID(),
                "UTC",
                "info@metropolis-spa.com",
                "+1-555-0199"
        ));

        // 2. Seed Customer & Staff
        customer = userRepository.save(new UserEntity(
                business,
                "Jane",
                "Doe",
                "jane.doe." + UUID.randomUUID() + "@example.com",
                "$2a$10$hashedpasswordplaceholder",
                "+1-555-0100",
                UserRole.CUSTOMER
        ));

        staff = userRepository.save(new UserEntity(
                business,
                "Dr. John",
                "Watson",
                "john.watson." + UUID.randomUUID() + "@example.com",
                "$2a$10$hashedpasswordplaceholder",
                "+1-555-0101",
                UserRole.STAFF
        ));

        // 3. Seed Service offering: 60 minutes duration, 0 buffer
        service = serviceRepository.save(new ServiceEntity(
                business,
                "Deep Tissue Massage",
                "Therapeutic 60-min session",
                60,
                0,
                new BigDecimal("120.00"),
                "USD"
        ));

        // 4. Configure Staff Working Hours for upcoming Monday (09:00 - 17:00 UTC)
        LocalDate nextMonday = LocalDate.now().plusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        workingHoursRepository.save(new StaffWorkingHoursEntity(
                business,
                staff,
                DayOfWeek.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0)
        ));

        // Target test slot: next Monday at 10:00:00 UTC
        targetSlotStart = nextMonday.atTime(10, 0).atZone(ZoneId.of("UTC")).toInstant();

        // Generate valid JWT token for customer
        customerJwt = jwtService.generateAccessToken(
                customer.getId(),
                business.getId(),
                customer.getEmail(),
                customer.getRole()
        );
    }

    @Test
    @DisplayName("20 parallel threads competing for the same slot via REST endpoint: Exactly 1 gets 201 Created, 19 get 409 Conflict")
    void concurrentReservationHttp_Fires20Threads_ExactlyOneSucceedsNineteenGet409() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger status201Count = new AtomicInteger(0);
        AtomicInteger status409Count = new AtomicInteger(0);
        AtomicInteger unexpectedStatusCount = new AtomicInteger(0);
        List<String> errorMessages = new CopyOnWriteArrayList<>();

        CreateReservationRequestDto requestDto = new CreateReservationRequestDto(
                business.getId(),
                customer.getId(),
                service.getId(),
                staff.getId(),
                targetSlotStart,
                "Concurrent race test"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(customerJwt);
        HttpEntity<CreateReservationRequestDto> httpEntity = new HttpEntity<>(requestDto, headers);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Hold all 20 threads until release signal
                    ResponseEntity<String> response = restTemplate.postForEntity(
                            "/api/reservations",
                            httpEntity,
                            String.class
                    );

                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        status201Count.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        status409Count.incrementAndGet();
                    } else {
                        unexpectedStatusCount.incrementAndGet();
                        errorMessages.add("Unexpected status: " + response.getStatusCode() + " body: " + response.getBody());
                    }
                } catch (Throwable t) {
                    unexpectedStatusCount.incrementAndGet();
                    errorMessages.add(t.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Wait for all 20 threads to reach starting barrier
        boolean ready = readyLatch.await(10, TimeUnit.SECONDS);
        assertThat(ready).isTrue();

        // Release the barrier - fire 20 requests at the exact same millisecond
        startLatch.countDown();

        // Wait for all threads to complete
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        executor.shutdown();

        log.info("Concurrent HTTP Results -> 201 Created: {}, 409 Conflict: {}, Unexpected: {}",
                status201Count.get(), status409Count.get(), unexpectedStatusCount.get());

        // Assert exactly 1 HTTP 201 Created and 19 HTTP 409 Conflict
        assertThat(unexpectedStatusCount.get())
                .as("No unexpected HTTP statuses should occur. Errors: " + errorMessages)
                .isEqualTo(0);

        assertThat(status201Count.get())
                .as("Exactly 1 concurrent request must succeed with 201 Created")
                .isEqualTo(1);

        assertThat(status409Count.get())
                .as("Exactly 19 concurrent requests must fail with 409 Conflict")
                .isEqualTo(threadCount - 1);

        // Assert database state: exactly 1 reservation persisted
        List<ReservationEntity> persisted = reservationRepository.findAllByStaffIdAndStartTimeBetween(
                staff.getId(),
                targetSlotStart.minusSeconds(1),
                targetSlotStart.plusSeconds(1)
        );
        assertThat(persisted)
                .as("Exactly 1 reservation row must exist in the database for the slot")
                .hasSize(1);
        assertThat(persisted.getFirst().getStatus()).isEqualTo(ReservationStatus.PENDING);

        // Assert event listener state: exactly 1 event received after commit
        assertThat(eventListener.getReceivedEventCount())
                .as("Exactly 1 ReservationCreatedEvent must be dispatched after successful transaction commit")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("20 parallel threads competing for the same slot via Service layer: Exactly 1 returns DTO, 19 throw SlotAlreadyBookedException")
    void concurrentReservationService_Fires20Threads_ExactlyOneSucceedsNineteenThrow409() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger slotAlreadyBookedCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        CreateReservationRequestDto requestDto = new CreateReservationRequestDto(
                business.getId(),
                customer.getId(),
                service.getId(),
                staff.getId(),
                targetSlotStart,
                "Direct service concurrency test"
        );

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Synchronized release
                    ReservationResponseDto response = reservationService.createReservation(requestDto);
                    if (response != null && "PENDING".equals(response.status())) {
                        successCount.incrementAndGet();
                    }
                } catch (SlotAlreadyBookedException ex) {
                    if (ex.getStatus() == HttpStatus.CONFLICT) {
                        slotAlreadyBookedCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    unexpectedErrorCount.incrementAndGet();
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("Concurrent Service Results -> Successes: {}, SlotAlreadyBooked (409): {}, Unexpected: {}",
                successCount.get(), slotAlreadyBookedCount.get(), unexpectedErrorCount.get());

        assertThat(unexpectedErrorCount.get())
                .as("No unexpected exceptions. Errors: " + exceptions)
                .isEqualTo(0);

        assertThat(successCount.get())
                .as("Exactly 1 thread must successfully book the slot")
                .isEqualTo(1);

        assertThat(slotAlreadyBookedCount.get())
                .as("Exactly 19 threads must throw SlotAlreadyBookedException (HTTP 409)")
                .isEqualTo(threadCount - 1);

        // Verify single record in DB
        List<ReservationEntity> persisted = reservationRepository.findAllByStaffIdAndStartTimeBetween(
                staff.getId(),
                targetSlotStart.minusSeconds(1),
                targetSlotStart.plusSeconds(1)
        );
        assertThat(persisted).hasSize(1);

        // Verify AFTER_COMMIT listener received exactly 1 event
        assertThat(eventListener.getReceivedEventCount()).isEqualTo(1);
    }
}
