package com.project2.ism.Service;

import com.project2.ism.Exception.CreditCardBillException;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The per-account switches an admin sets in Merchant Settings: "Payout" and "Credit card bill
 * payment". Seeing the menu is not enough: when the switch is off, the transaction is refused.
 */
@Service
public class FeatureAccessService {

    private static final Logger log = LoggerFactory.getLogger(FeatureAccessService.class);

    static final String PAYOUT_DISABLED =
            "Payout is not enabled for your account. Please contact the administrator.";
    static final String CREDIT_CARD_DISABLED =
            "Credit card bill payment is not enabled for your account. Please contact the administrator.";

    private final MerchantRepository merchantRepository;
    private final FranchiseRepository franchiseRepository;

    public FeatureAccessService(MerchantRepository merchantRepository, FranchiseRepository franchiseRepository) {
        this.merchantRepository = merchantRepository;
        this.franchiseRepository = franchiseRepository;
    }

    public boolean isPayoutEnabled(CustomerRef who) {
        if (who == null || who.id() == null) {
            return false;
        }
        if (who.isFranchise()) {
            Franchise f = franchiseRepository.findById(who.id()).orElse(null);
            if (f == null) {
                return false;
            }
            // NULL = franchise from before this switch existed: payout keeps working until an admin
            // saves its settings.
            return f.getPayout() == null || Boolean.TRUE.equals(f.getPayout());
        }
        Merchant m = merchantRepository.findById(who.id()).orElse(null);
        return m != null && Boolean.TRUE.equals(m.getPayout());
    }

    public boolean isCreditCardEnabled(CustomerRef who) {
        if (who == null || who.id() == null) {
            return false;
        }
        if (who.isFranchise()) {
            Franchise f = franchiseRepository.findById(who.id()).orElse(null);
            return f != null && Boolean.TRUE.equals(f.getCreditCardBillPayment());
        }
        Merchant m = merchantRepository.findById(who.id()).orElse(null);
        return m != null && Boolean.TRUE.equals(m.getCreditCardBillPayment());
    }

    /** Payout is refused (IllegalStateException, handled like the other payout validations). */
    public void requirePayoutEnabled(CustomerRef who) {
        if (!isPayoutEnabled(who)) {
            log.warn("Payout blocked: switch is off | {}", who != null ? who.label() : "unknown");
            throw new IllegalStateException(PAYOUT_DISABLED);
        }
    }

    /** Credit card billers / fetch bill / pay are refused with a 403 and a clear message. */
    public void requireCreditCardEnabled(CustomerRef who) {
        if (!isCreditCardEnabled(who)) {
            log.warn("Credit card blocked: switch is off | {}", who != null ? who.label() : "unknown");
            throw new CreditCardBillException(HttpStatus.FORBIDDEN, CREDIT_CARD_DISABLED);
        }
    }
}
