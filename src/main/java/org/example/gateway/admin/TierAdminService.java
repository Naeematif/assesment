package org.example.gateway.admin;

import java.util.List;
import org.example.gateway.auth.CredentialResolver;
import org.example.gateway.domain.Tier;
import org.example.gateway.error.ErrorCode;
import org.example.gateway.error.GatewayException;
import org.example.gateway.repository.TierRepository;
import org.example.gateway.web.dto.TierRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runtime management of tier configuration.
 *
 * <p>This is what makes tiers "dynamic and configurable": quota, rate limit and pricing are rows,
 * and changing one takes effect on the next request without a deploy or restart. Every write
 * invalidates the credential cache so operators see the change immediately rather than up to one
 * cache TTL later.
 */
@Service
public class TierAdminService {

    private static final Logger log = LoggerFactory.getLogger(TierAdminService.class);

    private final TierRepository tierRepository;
    private final CredentialResolver credentialResolver;

    public TierAdminService(TierRepository tierRepository, CredentialResolver credentialResolver) {
        this.tierRepository = tierRepository;
        this.credentialResolver = credentialResolver;
    }

    @Transactional(readOnly = true)
    public List<Tier> list() {
        return tierRepository.findAllByOrderByMonthlyPriceAsc();
    }

    @Transactional(readOnly = true)
    public Tier get(String code) {
        return tierRepository.findByCode(code)
                .orElseThrow(() -> new GatewayException(ErrorCode.NOT_FOUND, "No tier with code " + code));
    }

    @Transactional
    public Tier create(TierRequest request) {
        if (tierRepository.existsByCode(request.code())) {
            throw new GatewayException(ErrorCode.CONFLICT, "Tier " + request.code() + " already exists");
        }
        Tier tier = new Tier(request.code(), request.displayName(), request.monthlyQuota(),
                request.rateLimitPerSecond(), request.effectiveBurstCapacity(), request.monthlyPrice(),
                request.effectiveOveragePrice(), request.overageAllowed());
        if (request.active() != null) {
            tier.setActive(request.active());
        }
        Tier saved = tierRepository.save(tier);
        credentialResolver.invalidateAll();
        log.info("Created tier {}: quota={}/month, rate={}/s, price={}", saved.getCode(),
                saved.getMonthlyQuota(), saved.getRateLimitPerSecond(), saved.getMonthlyPrice());
        return saved;
    }

    @Transactional
    public Tier update(String code, TierRequest request) {
        Tier tier = get(code);
        tier.setDisplayName(request.displayName());
        tier.setMonthlyQuota(request.monthlyQuota());
        tier.setRateLimitPerSecond(request.rateLimitPerSecond());
        tier.setBurstCapacity(request.effectiveBurstCapacity());
        tier.setMonthlyPrice(request.monthlyPrice());
        tier.setOveragePricePerRequest(request.effectiveOveragePrice());
        tier.setOverageAllowed(request.overageAllowed());
        if (request.active() != null) {
            tier.setActive(request.active());
        }
        Tier saved = tierRepository.save(tier);

        // Callers already authenticated against the old settings hold a cached snapshot; drop it so
        // the new limits apply from the very next request.
        credentialResolver.invalidateAll();
        log.info("Updated tier {}: quota={}/month, rate={}/s, price={}", saved.getCode(),
                saved.getMonthlyQuota(), saved.getRateLimitPerSecond(), saved.getMonthlyPrice());
        return saved;
    }
}
