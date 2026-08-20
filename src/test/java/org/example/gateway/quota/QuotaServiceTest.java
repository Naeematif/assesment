package org.example.gateway.quota;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.gateway.auth.TierSnapshot;
import org.example.gateway.domain.Customer;
import org.example.gateway.repository.CustomerRepository;
import org.example.gateway.repository.QuotaCounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Monthly quota enforcement.
 *
 * <p>Run against a real database rather than a mock, because the property under test - that a
 * check-and-increment cannot interleave - lives in the SQL, not in the Java.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "gateway.seed.enabled=false",
        "gateway.usage.async=false"
})
class QuotaServiceTest {

    private static final String PERIOD = "2026-03";

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private QuotaCounterRepository quotaCounterRepository;

    private Long customerId;

    @BeforeEach
    void setUp() {
        Customer customer = customerRepository.save(
                new Customer("Quota Test", "quota-" + System.nanoTime() + "@example.com"));
        customerId = customer.getId();
    }

    private static TierSnapshot tier(long monthlyQuota, boolean overageAllowed) {
        return new TierSnapshot(1L, "TEST", monthlyQuota, 1000, 1000,
                BigDecimal.ZERO, new BigDecimal("0.001"), overageAllowed, true);
    }

    @Test
    @DisplayName("Admits exactly the monthly allowance and then refuses")
    void enforcesTheMonthlyAllowance() {
        TierSnapshot tier = tier(5, false);

        for (int i = 1; i <= 5; i++) {
            QuotaDecision decision = quotaService.tryConsume(customerId, tier, PERIOD);
            assertThat(decision.allowed()).as("request %d", i).isTrue();
            assertThat(decision.used()).isEqualTo(i);
            assertThat(decision.remaining()).isEqualTo(5 - i);
        }

        QuotaDecision exhausted = quotaService.tryConsume(customerId, tier, PERIOD);
        assertThat(exhausted.allowed()).isFalse();
        assertThat(exhausted.remaining()).isZero();
        assertThat(exhausted.limit()).isEqualTo(5);
    }

    @Test
    @DisplayName("The allowance resets when the billing period rolls over")
    void countersAreScopedToTheBillingPeriod() {
        TierSnapshot tier = tier(2, false);

        assertThat(quotaService.tryConsume(customerId, tier, "2026-03").allowed()).isTrue();
        assertThat(quotaService.tryConsume(customerId, tier, "2026-03").allowed()).isTrue();
        assertThat(quotaService.tryConsume(customerId, tier, "2026-03").allowed()).isFalse();

        assertThat(quotaService.tryConsume(customerId, tier, "2026-04").allowed()).isTrue();
    }

    @Test
    void customersDoNotShareAnAllowance() {
        TierSnapshot tier = tier(1, false);
        Long otherCustomer = customerRepository
                .save(new Customer("Other", "other-" + System.nanoTime() + "@example.com")).getId();

        assertThat(quotaService.tryConsume(customerId, tier, PERIOD).allowed()).isTrue();
        assertThat(quotaService.tryConsume(customerId, tier, PERIOD).allowed()).isFalse();
        assertThat(quotaService.tryConsume(otherCustomer, tier, PERIOD).allowed()).isTrue();
    }

    @Test
    @DisplayName("Raising the quota unblocks a customer on their next request")
    void quotaLimitIsReadLiveSoTierChangesApplyImmediately() {
        assertThat(quotaService.tryConsume(customerId, tier(1, false), PERIOD).allowed()).isTrue();
        assertThat(quotaService.tryConsume(customerId, tier(1, false), PERIOD).allowed()).isFalse();

        // Operator raises the tier's monthly quota; the same counter row is now judged against it.
        assertThat(quotaService.tryConsume(customerId, tier(10, false), PERIOD).allowed()).isTrue();
    }

    @Test
    @DisplayName("A tier that sells overage keeps serving past the quota and flags it as billable")
    void allowsBillableOverageWhenTheTierPermitsIt() {
        TierSnapshot tier = tier(2, true);

        assertThat(quotaService.tryConsume(customerId, tier, PERIOD).overage()).isFalse();
        assertThat(quotaService.tryConsume(customerId, tier, PERIOD).overage()).isFalse();

        QuotaDecision overage = quotaService.tryConsume(customerId, tier, PERIOD);
        assertThat(overage.allowed()).isTrue();
        assertThat(overage.overage()).isTrue();
        assertThat(overage.used()).isEqualTo(3);
        assertThat(overage.remaining()).isZero();
    }

    @Test
    @DisplayName("A failed downstream call gives the allowance back")
    void refundsConsumedQuota() {
        TierSnapshot tier = tier(1, false);
        assertThat(quotaService.tryConsume(customerId, tier, PERIOD).allowed()).isTrue();
        assertThat(quotaService.tryConsume(customerId, tier, PERIOD).allowed()).isFalse();

        quotaService.refund(customerId, PERIOD);

        assertThat(quotaService.tryConsume(customerId, tier, PERIOD).allowed()).isTrue();
    }

    @Test
    void refundNeverDrivesTheCounterNegative() {
        quotaService.tryConsume(customerId, tier(5, false), PERIOD);
        quotaService.refund(customerId, PERIOD);
        quotaService.refund(customerId, PERIOD);

        assertThat(quotaCounterRepository.findByCustomerIdAndBillingPeriod(customerId, PERIOD))
                .get()
                .extracting("used")
                .isEqualTo(0L);
    }

    @Test
    @DisplayName("Concurrent requests cannot overshoot the quota")
    void isSafeUnderConcurrency() throws Exception {
        int limit = 50;
        int threads = 16;
        int attemptsPerThread = 10;
        TierSnapshot tier = tier(limit, false);

        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < attemptsPerThread; i++) {
                            if (quotaService.tryConsume(customerId, tier, PERIOD).allowed()) {
                                allowed.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        // A read-then-write would let several threads see the same "used" value and overshoot.
        assertThat(allowed.get()).isEqualTo(limit);
        assertThat(quotaCounterRepository.findByCustomerIdAndBillingPeriod(customerId, PERIOD))
                .get()
                .extracting("used")
                .isEqualTo((long) limit);
    }
}
