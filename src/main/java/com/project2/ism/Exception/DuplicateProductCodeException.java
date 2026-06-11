package com.project2.ism.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class DuplicateProductCodeException extends RuntimeException {

    public DuplicateProductCodeException(String message) {
        super(message);
    }

    public DuplicateProductCodeException(String message, Throwable cause) {
        super(message, cause);
    }
}