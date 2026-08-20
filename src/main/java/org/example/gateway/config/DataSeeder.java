package org.example.gateway.config;

import java.math.BigDecimal;
import org.example.gateway.auth.ApiKeyGenerator;
import org.example.gateway.domain.ApiKey;
import org.example.gateway.domain.Customer;
import org.example.gateway.domain.Subscription;
import org.example.gateway.domain.Tier;
import org.example.gateway.repository.ApiKeyRepository;
import org.example.gateway.repository.CustomerRepository;
import org.example.gateway.repository.SubscriptionRepository;
import org.example.gateway.repository.TierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the two tiers from the product spec plus one demo customer on each, so the gateway can be
 * exercised with curl immediately after startup.
 *
 * <p>The tiers are seeded, not hardcoded: they are ordinary rows that the admin API can edit at
 * runtime. Demo API keys are fixed strings for convenience and are only ever created when
 * {@code gateway.seed.enabled} is true, which should be false anywhere real.
 */
@Component
@ConditionalOnProperty(prefix = "gateway.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final TierRepository tierRepository;
    private final CustomerRepository customerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final GatewayProperties properties;

    public DataSeeder(TierRepository tierRepository,
                      CustomerRepository customerRepository,
                      SubscriptionRepository subscriptionRepository,
                      ApiKeyRepository apiKeyRepository,
                      ApiKeyGenerator apiKeyGenerator,
                      GatewayProperties properties) {
        this.tierRepository = tierRepository;
        this.customerRepository = customerRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyGenerator = apiKeyGenerator;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        Tier free = tierRepository.findByCode("FREE").orElseGet(() -> tierRepository.save(
                new Tier("FREE", "Free", 100, 2, 2,
                        BigDecimal.ZERO, BigDecimal.ZERO, false)));

        Tier pro = tierRepository.findByCode("PRO").orElseGet(() -> tierRepository.save(
                new Tier("PRO", "Pro", 100_000, 10, 20,
                        new BigDecimal("50.00"), new BigDecimal("0.001000"), true)));

        seedCustomer("Acme Free Trial", "free-demo@example.com", free, "user-free-1",
                properties.getSeed().getFreeApiKey());
        seedCustomer("Globex Pro", "pro-demo@example.com", pro, "user-pro-1",
                properties.getSeed().getProApiKey());
    }

    private void seedCustomer(String name, String email, Tier tier, String userId, String rawApiKey) {
        if (customerRepository.findByEmail(email).isPresent()) {
            return;
        }
        Customer customer = customerRepository.save(new Customer(name, email));
        subscriptionRepository.save(new Subscription(customer, tier));
        apiKeyRepository.save(new ApiKey(apiKeyGenerator.hash(rawApiKey),
                apiKeyGenerator.prefixOf(rawApiKey), customer, userId, "seeded demo key"));

        log.info("Seeded demo customer '{}' on tier {} with API key {}", name, tier.getCode(), rawApiKey);
    }
}
