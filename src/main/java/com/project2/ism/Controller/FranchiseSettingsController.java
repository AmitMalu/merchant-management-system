package com.project2.ism.Controller;

import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Repository.FranchiseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin > Merchant Settings, for franchises: the Payout and Credit card bill payment switches.
 * Admin / super admin only (see SecurityConfig). Same idea as PUT /merchants/{id}/settings,
 * without a lien amount (franchises do not have one).
 */
@RestController
@RequestMapping("/franchise")
public class FranchiseSettingsController {

    private static final Logger log = LoggerFactory.getLogger(FranchiseSettingsController.class);

    public record FranchiseSettingsRequest(Boolean isPayout, Boolean isCreditCardBillPayment) {
    }

    public record FranchiseSettingsResponse(Long franchiseId, String franchiseName, Boolean isPayout,
                                            Boolean isCreditCardBillPayment, String message) {
    }

    private final FranchiseRepository franchiseRepository;

    public FranchiseSettingsController(FranchiseRepository franchiseRepository) {
        this.franchiseRepository = franchiseRepository;
    }

    @GetMapping("/{franchiseId}/settings")
    @Transactional(readOnly = true)
    public ResponseEntity<FranchiseSettingsResponse> get(@PathVariable Long franchiseId) {
        Franchise f = find(franchiseId);
        return ResponseEntity.ok(toResponse(f, null));
    }

    @PutMapping("/{franchiseId}/settings")
    @Transactional
    public ResponseEntity<FranchiseSettingsResponse> update(@PathVariable Long franchiseId,
                                                            @RequestBody FranchiseSettingsRequest request) {
        Franchise f = find(franchiseId);

        boolean payout = Boolean.TRUE.equals(request.isPayout());
        boolean creditCard = Boolean.TRUE.equals(request.isCreditCardBillPayment());

        log.info("Franchise settings changed | franchiseId={} | payout: {} -> {} | creditCard: {} -> {}",
                franchiseId, effectivePayout(f), payout, f.getCreditCardBillPayment(), creditCard);

        f.setPayout(payout);
        f.setCreditCardBillPayment(creditCard);
        franchiseRepository.save(f);

        return ResponseEntity.ok(toResponse(f, "Franchise settings updated successfully"));
    }

    private Franchise find(Long id) {
        return franchiseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Franchise not found."));
    }

    /** NULL (franchise from before this switch existed) means payout is still allowed. */
    private static boolean effectivePayout(Franchise f) {
        return f.getPayout() == null || Boolean.TRUE.equals(f.getPayout());
    }

    private static FranchiseSettingsResponse toResponse(Franchise f, String message) {
        return new FranchiseSettingsResponse(f.getId(), f.getFranchiseName(), effectivePayout(f),
                Boolean.TRUE.equals(f.getCreditCardBillPayment()), message);
    }
}
