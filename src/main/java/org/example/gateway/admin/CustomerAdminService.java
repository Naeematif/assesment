package org.example.gateway.admin;

import java.util.List;
import org.example.gateway.auth.ApiKeyGenerator;
import org.example.gateway.auth.CredentialResolver;
import org.example.gateway.auth.TierSnapshot;
import org.example.gateway.domain.ApiKey;
import org.example.gateway.domain.ApiKeyStatus;
import org.example.gateway.domain.Customer;
import org.example.gateway.domain.CustomerStatus;
import org.example.gateway.domain.QuotaCounter;
import org.example.gateway.domain.Subscription;
import org.example.gateway.domain.Tier;
import org.example.gateway.error.ErrorCode;
import org.example.gateway.error.GatewayException;
import org.example.gateway.quota.QuotaService;
import org.example.gateway.repository.ApiKeyRepository;
import org.example.gateway.repository.CustomerRepository;
import org.example.gateway.repository.SubscriptionRepository;
import org.example.gateway.web.dto.ApiKeyResponse;
import org.example.gateway.web.dto.CreateApiKeyRequest;
import org.example.gateway.web.dto.CreateCustomerRequest;
import org.example.gateway.web.dto.CustomerResponse;
import org.example.gateway.web.dto.QuotaStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provisioning and account management: onboarding, keys, plan changes, suspension. */
@Service
public class CustomerAdminService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAdminService.class);

    private final CustomerRepository customerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final TierAdminService tierAdminService;
    private final ApiKeyGenerator apiKeyGenerator;
    private final CredentialResolver credentialResolver;
    private final QuotaService quotaService;

    public CustomerAdminService(CustomerRepository customerRepository,
                                SubscriptionRepository subscriptionRepository,
                                ApiKeyRepository apiKeyRepository,
                                TierAdminService tierAdminService,
                                ApiKeyGenerator apiKeyGenerator,
                                CredentialResolver credentialResolver,
                                QuotaService quotaService) {
        this.customerRepository = customerRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.tierAdminService = tierAdminService;
        this.apiKeyGenerator = apiKeyGenerator;
        this.credentialResolver = credentialResolver;
        this.quotaService = quotaService;
    }

    /** Onboards a customer: account, subscription and a first API key in one call. */
    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        customerRepository.findByEmail(request.email()).ifPresent(existing -> {
            throw new GatewayException(ErrorCode.CONFLICT, "Customer " + request.email() + " already exists");
        });

        Tier tier = tierAdminService.get(request.tierCode());
        Customer customer = customerRepository.save(new Customer(request.name(), request.email()));
        Subscription subscription = subscriptionRepository.save(new Subscription(customer, tier));

        ApiKeyIssue issue = issueKey(customer, request.effectiveUserId(), "initial key");
        log.info("Onboarded customer {} ({}) on tier {}", customer.getId(), customer.getEmail(), tier.getCode());

        return CustomerResponse.from(customer, tier.getCode(), subscription.getStatus().name(),
                ApiKeyResponse.from(issue.apiKey(), issue.plaintext()));
    }

    @Transactional(readOnly = true)
    public List<Customer> list() {
        return customerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long customerId) {
        Customer customer = requireCustomer(customerId);
        Subscription subscription = requireSubscription(customerId);
        return CustomerResponse.from(customer, subscription.getTier().getCode(),
                subscription.getStatus().name(), null);
    }

    @Transactional
    public ApiKeyResponse createApiKey(Long customerId, CreateApiKeyRequest request) {
        Customer customer = requireCustomer(customerId);
        ApiKeyIssue issue = issueKey(customer, request.userId(), request.label());
        return ApiKeyResponse.from(issue.apiKey(), issue.plaintext());
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(Long customerId) {
        requireCustomer(customerId);
        return apiKeyRepository.findByCustomerId(customerId).stream()
                .map(key -> ApiKeyResponse.from(key, null))
                .toList();
    }

    @Transactional
    public void revokeApiKey(Long customerId, Long apiKeyId) {
        ApiKey key = apiKeyRepository.findById(apiKeyId)
                .filter(k -> k.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new GatewayException(ErrorCode.NOT_FOUND, "No such API key"));
        key.setStatus(ApiKeyStatus.REVOKED);
        apiKeyRepository.save(key);
        credentialResolver.invalidateAll();
        log.info("Revoked API key {} for customer {}", apiKeyId, customerId);
    }

    /** Upgrade or downgrade. The new limits apply from the next request; the quota counter is kept. */
    @Transactional
    public CustomerResponse changeTier(Long customerId, String tierCode) {
        Customer customer = requireCustomer(customerId);
        Subscription subscription = requireSubscription(customerId);
        Tier tier = tierAdminService.get(tierCode);

        subscription.changeTier(tier);
        subscriptionRepository.save(subscription);
        credentialResolver.invalidateAll();
        log.info("Customer {} moved to tier {}", customerId, tierCode);

        return CustomerResponse.from(customer, tier.getCode(), subscription.getStatus().name(), null);
    }

    @Transactional
    public CustomerResponse setStatus(Long customerId, CustomerStatus status) {
        Customer customer = requireCustomer(customerId);
        customer.setStatus(status);
        customerRepository.save(customer);
        credentialResolver.invalidateAll();
        Subscription subscription = requireSubscription(customerId);
        return CustomerResponse.from(customer, subscription.getTier().getCode(),
                subscription.getStatus().name(), null);
    }

    /** Live quota position, i.e. what the customer would see in a usage dashboard. */
    @Transactional(readOnly = true)
    public QuotaStatusResponse quotaStatus(Long customerId) {
        requireCustomer(customerId);
        Subscription subscription = requireSubscription(customerId);
        TierSnapshot tier = TierSnapshot.from(subscription.getTier());

        String period = quotaService.currentPeriod();
        QuotaCounter counter = quotaService.snapshot(customerId, period);
        long used = counter == null ? 0 : counter.getUsed();

        return new QuotaStatusResponse(customerId, period, tier.code(), tier.monthlyQuota(), used,
                Math.max(0, tier.monthlyQuota() - used), tier.rateLimitPerSecond());
    }

    private ApiKeyIssue issueKey(Customer customer, String userId, String label) {
        String plaintext = apiKeyGenerator.generate();
        ApiKey saved = apiKeyRepository.save(new ApiKey(apiKeyGenerator.hash(plaintext),
                apiKeyGenerator.prefixOf(plaintext), customer, userId, label));
        return new ApiKeyIssue(saved, plaintext);
    }

    private Customer requireCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new GatewayException(ErrorCode.NOT_FOUND, "No customer " + customerId));
    }

    private Subscription requireSubscription(Long customerId) {
        return subscriptionRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new GatewayException(ErrorCode.NOT_FOUND,
                        "Customer " + customerId + " has no subscription"));
    }

    /** The saved key plus its plaintext, which exists only for the duration of this response. */
    private record ApiKeyIssue(ApiKey apiKey, String plaintext) {
    }
}
