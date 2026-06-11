package com.project2.ism.Exception;

// ValidationException.java


public class ValidationException extends RuntimeException {
    private String field;
    private String errorCode;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, String field) {
        super(message);
        this.field = field;
    }

    public ValidationException(String message, String field, String errorCode) {
        super(message);
        this.field = field;
        this.errorCode = errorCode;
    }

    // Generate: getters, setters

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}