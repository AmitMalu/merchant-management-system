package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.RoutePlan;
import com.project2.ism.Repository.PaymentProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Makes credit card bill payment follow routing, for the accounts the Menu Rules apply to (see MenuRulesSwitch).
 *
 * Two credit card screens exist: Bill Payment (Bill Avenue) and Credit Card (SuivrePay). For a MERCHANT the rules
 * apply to and that has a Bill Payment routing whose first allowed vendor, for the Credit Card category, is one of
 * them, the other vendor's screen is refused on the server (before any money moves). The sidebar hides it too
 * (see MenuRulesService).
 *
 * It never restricts on doubt. With the rules off or not applying to the account, with no routing, with a routing
 * that cannot be read, with a vendor it does not recognise (the name must contain "Bill Avenue" or "SuivrePay"),
 * or for a franchise (a franchise has no Bill Avenue screen, so restricting it would lock it out), the account
 * keeps both screens. With the rules off the payment thread reads only an in-memory flag.
 */
@Service
public class CreditCardRouteGuard {

    private static final Logger log = LoggerFactory.getLogger(CreditCardRouteGuard.class);

    public static final String BILL_AVENUE = "BILLAVENUE";
    public static final String SUIVREPAY = "SUIVREPAY";

    public static final String NOT_AVAILABLE =
            "This option is not available for your account. Please contact the administrator.";

    private final MenuRulesSwitch rules;
    private final PaymentRoutingConfigService configService;
    private final PaymentProductRepository productRepository;

    @Value("${routing.bill-payment-product-code:002}")
    private String billPaymentProductCode = "002";

    @Value("${bbps.credit-card-category:Credit Card}")
    private String creditCardCategory = "Credit Card";

    public CreditCardRouteGuard(MenuRulesSwitch rules,
                                PaymentRoutingConfigService configService,
                                PaymentProductRepository productRepository) {
        this.rules = rules;
        this.configService = configService;
        this.productRepository = productRepository;
    }

    /**
     * The vendor (BILLAVENUE or SUIVREPAY) this account's credit card is routed to, or null when the account must
     * not be restricted. Never throws.
     */
    public String routedVendor(CustomerRef who) {
        if (who == null || who.id() == null || who.isFranchise() || !rules.applies(who)) {
            return null;
        }
        try {
            var product = productRepository.findByProductCode(billPaymentProductCode).orElse(null);
            if (product == null) {
                return null;
            }
            RoutePlan plan = configService.routePlan(product, who.type(), who.id(), null, creditCardCategory);
            if (!plan.hasRouting() || plan.vendors().isEmpty()) {
                return null;
            }
            return codeOf(plan.vendors().get(0).vendorName());
        } catch (Exception e) {
            log.warn("Credit card routing: could not read the routing for {}, not restricting: {}",
                    who.label(), e.toString());
            return null;
        }
    }

    /** True only when routing clearly sends this account's credit card payments to the OTHER vendor. */
    public boolean blocks(CustomerRef who, String vendorCode) {
        String routed = routedVendor(who);
        boolean blocked = routed != null && !routed.equals(vendorCode);
        if (blocked) {
            log.warn("Credit card blocked: routing sends {} to {}, not {}", who.label(), routed, vendorCode);
        }
        return blocked;
    }

    /**
     * Bill Avenue's Bill Pay screen also pays other bill categories; only the Credit Card category follows
     * routing, so every other category is never blocked here.
     */
    public boolean blocksBillAvenueCreditCard(Long merchantId, String category) {
        if (merchantId == null || category == null || !category.trim().equalsIgnoreCase(creditCardCategory.trim())) {
            return false;
        }
        return blocks(new CustomerRef(CustomerRef.MERCHANT, merchantId), BILL_AVENUE);
    }

    /** BILLAVENUE / SUIVREPAY from a vendor's name, or null when it is neither. */
    static String codeOf(String vendorName) {
        if (vendorName == null) {
            return null;
        }
        String n = vendorName.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (n.contains("billavenue")) {
            return BILL_AVENUE;
        }
        if (n.contains("suivre")) {
            return SUIVREPAY;
        }
        return null;
    }
}
