package com.project2.ism.Service;

/**
 * Who is acting: a merchant or a franchise, and their id. Merchant ids and franchise ids
 * overlap (both start at 1), so an id is only meaningful together with its type.
 */
public record CustomerRef(String type, Long id) {

    public static final String MERCHANT = "MERCHANT";
    public static final String FRANCHISE = "FRANCHISE";

    public static CustomerRef merchant(Long id) {
        return new CustomerRef(MERCHANT, id);
    }

    public static CustomerRef franchise(Long id) {
        return new CustomerRef(FRANCHISE, id);
    }

    public boolean isFranchise() {
        return FRANCHISE.equalsIgnoreCase(type);
    }

    /** For log lines, for example "FRANCHISE#5". */
    public String label() {
        return type + "#" + id;
    }
}
