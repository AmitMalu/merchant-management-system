package com.project2.ism.DTO;

import java.time.LocalDateTime;

/** Request / response shapes for Admin > Other > Notification URL. */
public final class NotificationCallbackDtos {

    private NotificationCallbackDtos() {
    }

    /** What kind of account a URL belongs to. */
    public static final String FRANCHISE = "FRANCHISE";
    public static final String FRANCHISE_MERCHANT = "FRANCHISE_MERCHANT";
    public static final String DIRECT_MERCHANT = "DIRECT_MERCHANT";

    /**
     * type: FRANCHISE (franchiseId), FRANCHISE_MERCHANT (merchantId; franchiseId optional, only
     * checked against the merchant) or DIRECT_MERCHANT (merchantId).
     */
    public record CreateRequest(String type, Long franchiseId, Long merchantId, String callback) {
    }

    /** Only the URL can be changed; to move a URL to another account, delete it and add it again. */
    public record UpdateRequest(String callback) {
    }

    /**
     * warning is filled for old rows that do not fit the rules (both a franchise and a merchant
     * set, or neither), so admin can see and clean them up.
     */
    public record View(Long id,
                       String type,
                       Long franchiseId,
                       String franchiseName,
                       Long merchantId,
                       String merchantName,
                       String callback,
                       LocalDateTime createdAt,
                       LocalDateTime updatedAt,
                       String warning) {
    }
}
