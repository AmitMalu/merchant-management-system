package com.project2.ism.Service;

import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.PayoutBankRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Answers one question for money-moving endpoints: "is the logged-in user
 * actually the merchant/franchise this request claims to act for?".
 *
 * Several endpoints take initiatorId / merchantId / payoutBankId straight from
 * the request body, so without this check any logged-in user could name
 * someone else's account and move that account's money. The identity here
 * comes only from the verified JWT (email + role), never from the request.
 */
@Service
public class CallerIdentityService {

    private static final Logger log = LoggerFactory.getLogger(CallerIdentityService.class);

    private final MerchantRepository merchantRepository;
    private final FranchiseRepository franchiseRepository;
    private final PayoutBankRepository payoutBankRepository;

    public CallerIdentityService(MerchantRepository merchantRepository,
                                  FranchiseRepository franchiseRepository,
                                  PayoutBankRepository payoutBankRepository) {
        this.merchantRepository = merchantRepository;
        this.franchiseRepository = franchiseRepository;
        this.payoutBankRepository = payoutBankRepository;
    }

    /** 403 unless the logged-in user IS the given merchant/franchise. */
    public void requireOwner(String initiatorType, Long initiatorId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean owner = false;
        if (auth != null && auth.getName() != null && initiatorId != null && initiatorType != null) {
            String email = auth.getName();
            if ("MERCHANT".equalsIgnoreCase(initiatorType) && hasRole(auth, "ROLE_MERCHANT")) {
                owner = merchantRepository.findByContactPerson_Email(email)
                        .map(m -> initiatorId.equals(m.getId()))
                        .orElse(false);
            } else if ("FRANCHISE".equalsIgnoreCase(initiatorType) && hasRole(auth, "ROLE_FRANCHISE")) {
                owner = franchiseRepository.findByContactPerson_Email(email)
                        .map(f -> initiatorId.equals(f.getId()))
                        .orElse(false);
            }
        }

        if (!owner) {
            log.warn("Blocked: user={} tried to act on {} #{} which is not their account",
                    auth != null ? auth.getName() : "anonymous", initiatorType, initiatorId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not allowed to act on this account");
        }
    }

    /** Same as requireOwner, but admins may also read. For view-only endpoints. */
    public void requireOwnerOrAdmin(String initiatorType, Long initiatorId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && (hasRole(auth, "ROLE_ADMIN") || hasRole(auth, "ROLE_SUPER_ADMIN"))) {
            return;
        }
        requireOwner(initiatorType, initiatorId);
    }

    /**
     * 403 unless the saved bank account belongs to the logged-in user. An
     * unknown bank id is left for the payout service to reject with its usual
     * message, so error behaviour for bad ids doesn't change.
     */
    public void requireOwnsPayoutBank(Long payoutBankId) {
        if (payoutBankId == null) {
            return;
        }
        payoutBankRepository.findById(payoutBankId)
                .ifPresent(bank -> requireOwner(bank.getCustomerType(), bank.getCustomerId()));
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
    }
}
