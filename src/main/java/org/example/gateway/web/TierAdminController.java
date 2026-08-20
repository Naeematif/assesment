package org.example.gateway.web;

import jakarta.validation.Valid;
import java.util.List;
import org.example.gateway.admin.TierAdminService;
import org.example.gateway.web.dto.TierRequest;
import org.example.gateway.web.dto.TierResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control plane for tier configuration.
 *
 * <p>Lives outside the metered path prefix, so operator traffic is never billed to a customer. In a
 * real deployment this would sit behind operator authentication and network isolation - see the
 * "not built here" section of the README.
 */
@RestController
@RequestMapping("/admin/tiers")
public class TierAdminController {

    private final TierAdminService tierAdminService;

    public TierAdminController(TierAdminService tierAdminService) {
        this.tierAdminService = tierAdminService;
    }

    @GetMapping
    public List<TierResponse> list() {
        return tierAdminService.list().stream().map(TierResponse::from).toList();
    }

    @GetMapping("/{code}")
    public TierResponse get(@PathVariable String code) {
        return TierResponse.from(tierAdminService.get(code));
    }

    @PostMapping
    public ResponseEntity<TierResponse> create(@Valid @RequestBody TierRequest request) {
        TierResponse created = TierResponse.from(tierAdminService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Takes effect on the next request from any customer on this tier. */
    @PutMapping("/{code}")
    public TierResponse update(@PathVariable String code, @Valid @RequestBody TierRequest request) {
        return TierResponse.from(tierAdminService.update(code, request));
    }
}
