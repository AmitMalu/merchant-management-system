package com.project2.ism.Exception;

public class NoAvailableVendorException extends RuntimeException {
    public NoAvailableVendorException(String message) {
        super(message);
    }
}
