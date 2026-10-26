package com.project2.ism.Exception;

import org.springframework.http.HttpStatus;

/**
 * A credit-card bill request that cannot go ahead, with a message that is safe
 * to show to the merchant (internal vendor details are never put in it).
 */
public class CreditCardBillException extends RuntimeException {

    private final HttpStatus status;

    public CreditCardBillException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
