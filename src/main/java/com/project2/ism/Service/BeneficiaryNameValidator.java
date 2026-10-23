package com.project2.ism.Service;

import java.util.List;
import java.util.Locale;

/**
 * Payouts to a CURRENT account can attract charges / get the amount locked
 * under the new government rule, and the bank-verification response does not
 * say whether an account is savings or current. The only signal we have is the
 * account holder's name: a business account is usually registered under a name
 * like "XYZ TRADERS" or "ABC SERVICES PVT LTD".
 *
 * This class flags a name that contains any of those business keywords. It is
 * used when a beneficiary is added (name returned by Surepass) and again just
 * before a payout is sent. A current account registered under an ordinary
 * personal name cannot be detected this way.
 */
public final class BeneficiaryNameValidator {

    // Whole-word / whole-phrase matches only (case-insensitive, punctuation ignored),
    // so "PVT." and "Pvt" both match "PVT", but "SHOPKEEPER" does not match "SHOP".
    private static final List<String> BLOCKED_KEYWORDS = List.of(
            "MULTI SERVICES",
            "ASSOCIATES",
            "ASSOCIATE",
            "PVT",
            "PRIVATE LIMITED",
            "LLP",
            "SHOP",
            "SALES",
            "LOGISTIC",
            "LOGISTICS",
            "TRADERS",
            "TRADER",
            "TRADING COMPANY",
            "TRAVEL AND SERVICES",
            "CONSTRUCTION",
            "MOBILE SALES",
            "GARMENTS",
            "GARMENT",
            "MATERIAL",
            "MATERIALS",
            "SERVICES",
            "SERVICE"
    );

    private BeneficiaryNameValidator() {
    }

    /** Returns the first blocked keyword found in the name, or null when the name is fine. */
    public static String findBlockedKeyword(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        String normalized = " " + name.toUpperCase(Locale.ROOT)
                .replace("&", " AND ")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim() + " ";

        for (String keyword : BLOCKED_KEYWORDS) {
            if (normalized.contains(" " + keyword + " ")) {
                return keyword;
            }
        }
        return null;
    }

    public static String blockedMessage(String name) {
        return "The account holder name \"" + (name == null ? "" : name.trim())
                + "\" looks like a business / current account. Payouts to current accounts "
                + "are not allowed because charges may be applied or the amount may be locked. "
                + "Please use a personal savings account.";
    }
}
