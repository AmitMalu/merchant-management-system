package com.project2.ism.Service;

import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.Logs.FranchiseOrMerchantNotificationCallback;
import com.project2.ism.Repository.FranchiseOrMerchantNotificationCallbackRepository;
import com.project2.ism.Repository.ProductSerialNumbersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Decides which notification URL (table franchise_or_merchant_notification_callback, managed in
 * Admin > Other > Notification URL) a payment notification is sent to. Used by both the Razorpay
 * and the Mosambee notification services, and so by file-uploaded transactions as well.
 *
 * The device (MID) says who the payment belongs to:
 *   1. If the device belongs to a merchant and that merchant has its own URL, that URL is used.
 *      This is a direct merchant, or a franchise merchant that was given its own URL.
 *   2. Otherwise, if the device belongs to a franchise (its own devices, and its merchants'
 *      devices that have no URL of their own), the franchise's URL is used.
 *   3. Otherwise there is nobody to notify.
 *
 * Direct merchants and franchise devices without a merchant behave exactly as before. The only
 * difference is a franchise merchant WITH its own URL: it now gets that URL instead of the
 * franchise's.
 */
@Service
public class NotificationCallbackResolver {

    private static final Logger log = LoggerFactory.getLogger(NotificationCallbackResolver.class);

    private final ProductSerialNumbersRepository productSerialNumbersRepository;
    private final FranchiseOrMerchantNotificationCallbackRepository callbackRepository;

    public NotificationCallbackResolver(ProductSerialNumbersRepository productSerialNumbersRepository,
                                        FranchiseOrMerchantNotificationCallbackRepository callbackRepository) {
        this.productSerialNumbersRepository = productSerialNumbersRepository;
        this.callbackRepository = callbackRepository;
    }

    /** The callback for this MID, or null when none is configured. */
    public FranchiseOrMerchantNotificationCallback resolveForMid(String mid) {

        ProductSerialNumbers psn = productSerialNumbersRepository.findByMid(mid).orElse(null);

        if (psn == null) {
            log.warn("No ProductSerialNumbers found for MID={}", mid);
            return null;
        }

        Long merchantId = psn.getMerchant() != null ? psn.getMerchant().getId() : null;
        Long franchiseId = psn.getFranchise() != null ? psn.getFranchise().getId() : null;

        // 1. the merchant's own URL
        if (merchantId != null) {
            Optional<FranchiseOrMerchantNotificationCallback> own = callbackRepository.findByMerchant_Id(merchantId);
            if (own.isPresent()) {
                log.debug("Merchant's own callback used for MID={}, merchantId={}", mid, merchantId);
                return own.get();
            }
            log.debug("No callback of its own for merchantId={} (MID={})", merchantId, mid);
        }

        // 2. the franchise's URL
        if (franchiseId != null) {
            log.debug("Franchise found for MID={}, franchiseId={}", mid, franchiseId);

            return callbackRepository.findByFranchise_Id(franchiseId).orElseGet(() -> {
                log.warn("No callback configured for franchiseId={}", franchiseId);
                return null;
            });
        }

        // 3. nobody to notify
        if (merchantId != null) {
            log.warn("No callback configured for merchantId={}", merchantId);
        } else {
            log.warn("ProductSerialNumbers has no franchise or merchant for MID={}", mid);
        }
        return null;
    }
}
