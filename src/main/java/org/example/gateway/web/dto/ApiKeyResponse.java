package org.example.gateway.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import org.example.gateway.domain.ApiKey;

/**
 * @param apiKey the plaintext key. Populated only in the response that creates it - the server keeps
 *               a hash and cannot show it again.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiKeyResponse(Long id,
                             String keyPrefix,
                             String userId,
                             String label,
                             String status,
                             Instant createdAt,
                             String apiKey) {

    public static ApiKeyResponse from(ApiKey key, String plaintext) {
        return new ApiKeyResponse(key.getId(), key.getKeyPrefix(), key.getUserId(), key.getLabel(),
                key.getStatus().name(), key.getCreatedAt(), plaintext);
    }
}
