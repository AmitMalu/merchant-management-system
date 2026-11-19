package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.MyMenu;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.RoutePlan;
import com.project2.ism.Repository.PaymentProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Which menus a merchant or franchise sees, from its routings, when the Menu Rules apply to it:
 *  - Payout: only if it has a Payout routing (switched on).
 *  - Bill Pay (the Bill Avenue screen) and Credit Card (the SuivrePay screen): only if it has a Bill Payment
 *    routing; then the screen of the vendor the routing puts first for the Credit Card category.
 *  - A franchise has no Bill Pay screen. It keeps Credit Card unless its routing only offers Bill Avenue.
 * The Merchant Settings switches still apply on top (a menu works only when its switch is ON).
 *
 * It never hides on doubt: rules off or not applying, a missing product, or any error answers "show everything".
 */
@Service
public class MenuRulesService {

    private static final Logger log = LoggerFactory.getLogger(MenuRulesService.class);

    private static final MyMenu EVERYTHING = new MyMenu(false, true, true, true);

    private final MenuRulesSwitch rules;
    private final PaymentRoutingConfigService configService;
    private final PaymentProductRepository productRepository;

    @Value("${routing.payout-product-code:001}")
    private String payoutProductCode = "001";

    @Value("${routing.bill-payment-product-code:002}")
    private String billPaymentProductCode = "002";

    @Value("${bbps.credit-card-category:Credit Card}")
    private String creditCardCategory = "Credit Card";

    public MenuRulesService(MenuRulesSwitch rules, PaymentRoutingConfigService configService,
                            PaymentProductRepository productRepository) {
        this.rules = rules;
        this.configService = configService;
        this.productRepository = productRepository;
    }

    public MyMenu myMenu(CustomerRef who) {
        if (who == null || who.id() == null || !rules.applies(who)) {
            return EVERYTHING;
        }
        try {
            var payoutProduct = productRepository.findByProductCode(payoutProductCode).orElse(null);
            boolean payout = payoutProduct == null
                    || configService.hasActiveRouting(payoutProduct, who.type(), who.id());

            var billProduct = productRepository.findByProductCode(billPaymentProductCode).orElse(null);
            boolean billPay = true;
            boolean creditCard = true;
            if (billProduct != null) {
                RoutePlan plan = configService.routePlan(billProduct, who.type(), who.id(), null, creditCardCategory);
                if (!plan.hasRouting()) {
                    billPay = false;
                    creditCard = false;
                } else if (who.isFranchise()) {
                    billPay = false;       // a franchise has no Bill Pay screen
                    creditCard = plan.vendors().isEmpty() || plan.vendors().stream()
                            .anyMatch(v -> !CreditCardRouteGuard.BILL_AVENUE.equals(CreditCardRouteGuard.codeOf(v.vendorName())));
                } else {
                    String vendor = plan.vendors().isEmpty() ? null
                            : CreditCardRouteGuard.codeOf(plan.vendors().get(0).vendorName());
                    billPay = !CreditCardRouteGuard.SUIVREPAY.equals(vendor);
                    creditCard = !CreditCardRouteGuard.BILL_AVENUE.equals(vendor);
                }
            }
            return new MyMenu(true, payout, billPay, creditCard);
        } catch (Exception e) {
            log.warn("Menu rules: could not work out the menus for {}, showing everything: {}", who.label(), e.toString());
            return EVERYTHING;
        }
    }
}
