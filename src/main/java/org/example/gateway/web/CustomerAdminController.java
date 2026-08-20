package org.example.gateway.web;

import jakarta.validation.Valid;
import java.util.List;
import org.example.gateway.admin.CustomerAdminService;
import org.example.gateway.domain.CustomerStatus;
import org.example.gateway.web.dto.ApiKeyResponse;
import org.example.gateway.web.dto.ChangeTierRequest;
import org.example.gateway.web.dto.CreateApiKeyRequest;
import org.example.gateway.web.dto.CreateCustomerRequest;
import org.example.gateway.web.dto.CustomerResponse;
import org.example.gateway.web.dto.QuotaStatusResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Control plane for customer provisioning, credentials and plan changes. */
@RestController
@RequestMapping("/admin/customers")
public class CustomerAdminController {

    private final CustomerAdminService customerAdminService;

    public CustomerAdminController(CustomerAdminService customerAdminService) {
        this.customerAdminService = customerAdminService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerAdminService.create(request));
    }

    @GetMapping
    public List<CustomerResponse> list() {
        return customerAdminService.list().stream()
                .map(customer -> customerAdminService.get(customer.getId()))
                .toList();
    }

    @GetMapping("/{customerId}")
    public CustomerResponse get(@PathVariable Long customerId) {
        return customerAdminService.get(customerId);
    }

    @PostMapping("/{customerId}/api-keys")
    public ResponseEntity<ApiKeyResponse> createApiKey(@PathVariable Long customerId,
                                                       @Valid @RequestBody CreateApiKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(customerAdminService.createApiKey(customerId, request));
    }

    @GetMapping("/{customerId}/api-keys")
    public List<ApiKeyResponse> listApiKeys(@PathVariable Long customerId) {
        return customerAdminService.listApiKeys(customerId);
    }

    @DeleteMapping("/{customerId}/api-keys/{apiKeyId}")
    public ResponseEntity<Void> revokeApiKey(@PathVariable Long customerId, @PathVariable Long apiKeyId) {
        customerAdminService.revokeApiKey(customerId, apiKeyId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{customerId}/subscription")
    public CustomerResponse changeTier(@PathVariable Long customerId,
                                       @Valid @RequestBody ChangeTierRequest request) {
        return customerAdminService.changeTier(customerId, request.tierCode());
    }

    @PutMapping("/{customerId}/status/{status}")
    public CustomerResponse setStatus(@PathVariable Long customerId, @PathVariable CustomerStatus status) {
        return customerAdminService.setStatus(customerId, status);
    }

    /** Live quota position for the current billing period. */
    @GetMapping("/{customerId}/quota")
    public QuotaStatusResponse quota(@PathVariable Long customerId) {
        return customerAdminService.quotaStatus(customerId);
    }
}
