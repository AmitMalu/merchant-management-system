package com.project2.ism.Exception;


import com.project2.ism.DTO.ReportDTO.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import jakarta.validation.ConstraintViolation;


import jakarta.servlet.http.HttpServletRequest;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ErrorResponse buildErrorResponse(HttpStatus status, String message, String path) {
        return new ErrorResponse(
                status.value(),
                message,
                path,
                LocalDateTime.now()
        );
    }

    // Resource Not Found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        logger.error("Resource not found: {}", ex.getMessage());
        return new ResponseEntity<>(buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getDescription(false)), HttpStatus.NOT_FOUND);
    }

    // No PaymentVendor Not Found
    @ExceptionHandler(NoAvailableVendorException.class)
    public ResponseEntity<ErrorResponse> handleNoAvailableVendorException(NoAvailableVendorException ex, WebRequest request) {
        logger.error("NoAvailableVendor : {}", ex.getMessage());
        return new ResponseEntity<>(buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getDescription(false)), HttpStatus.NOT_FOUND);
    }

    // Duplicate Product Code
    @ExceptionHandler(DuplicateProductCodeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateProductCodeException(DuplicateProductCodeException ex, WebRequest request) {
        logger.error("Duplicate product code: {}", ex.getMessage());
        return new ResponseEntity<>(buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request.getDescription(false)), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            WebRequest request) {

        String message = "Database constraint violation";
        HttpStatus status = HttpStatus.CONFLICT; // default

        Throwable rootCause = ex.getRootCause();
        if (rootCause != null && rootCause instanceof SQLException sqlEx) {
            String sqlMessage = sqlEx.getMessage();

            if (sqlMessage.contains("Duplicate entry")) {
                message = "A record with the same unique fields already exists.";
                status = HttpStatus.CONFLICT;
            } else if (sqlMessage.contains("cannot be null")) {
                message = "A required field is missing.";
                status = HttpStatus.BAD_REQUEST;
            } else if (sqlMessage.contains("foreign key constraint fails")) {
                message = "This record is linked to another resource and cannot be saved/deleted.";
                status = HttpStatus.CONFLICT;
            } else if (sqlMessage.contains("check constraint")) {
                message = "Data does not meet required validation rules.";
                status = HttpStatus.BAD_REQUEST;
            } else {
                // fallback for other DB errors
                message = "Database error: " + sqlMessage;
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
        }

        ErrorResponse error = new ErrorResponse(
                status.value(),
                message,
                request.getDescription(false),
                LocalDateTime.now()
        );

        return ResponseEntity.status(status).body(error);
    }
    //duplicate TID/MID/SID/VPAID
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex,
            WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),     // 409
                ex.getMessage(),                 // "TID already exists: 32971249"
                request.getDescription(false),   // e.g. URI=/api/v1/transactions
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // Validation Errors (Bean Validation)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        logger.error("Validation error: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ValidationErrorResponse errorResponse = new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                errors,
                LocalDateTime.now()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // Validation Errors (Entity Persist)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        logger.error("Constraint violation: {}", ex.getMessage());
        return buildValidationErrorResponse(
                ex.getConstraintViolations().stream()
                        .collect(Collectors.toMap(
                                v -> v.getPropertyPath().toString(),
                                ConstraintViolation::getMessage,
                                (m1, m2) -> m1
                        ))
        );
    }

    private ResponseEntity<ValidationErrorResponse> buildValidationErrorResponse(Map<String, String> errors) {
        ValidationErrorResponse errorResponse = new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                errors,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    // Invalid JSON
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return new ResponseEntity<>(
                buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid JSON input: " + ex.getMostSpecificCause().getMessage(), request.getRequestURI()),
                HttpStatus.BAD_REQUEST
        );
    }

    // Illegal Arguments
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        logger.error("Illegal argument: {}", ex.getMessage());
        return new ResponseEntity<>(buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getDescription(false)), HttpStatus.BAD_REQUEST);
    }

    // Catch-All (Includes Runtime Exceptions)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request.getDescription(false)), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex, WebRequest request) {
        logger.error("Illegal state: {}", ex.getMessage());
        return new ResponseEntity<>(
                buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getDescription(false)),
                HttpStatus.BAD_REQUEST
        );
    }
    @ExceptionHandler(UnexpectedRollbackException.class)
    public ResponseEntity<ErrorResponse> handleRollback(UnexpectedRollbackException ex, WebRequest request) {
        logger.error("Transaction rollback: {}", ex.getMessage());
        return new ResponseEntity<>(
                buildErrorResponse(HttpStatus.CONFLICT,
                        "Transaction failed due to rollback: " + ex.getMessage(),
                        request.getDescription(false)),
                HttpStatus.CONFLICT
        );
    }


    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(ValidationException ex) {
        logger.error("Validation error: {}", ex.getMessage());

        ApiResponse<Object> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(ex.getMessage());
        response.setErrorCode("VALIDATION_ERROR");
        response.setTimestamp(LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException ex) {
        logger.error("Business error: {}", ex.getMessage());

        ApiResponse<Object> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(ex.getMessage());
        response.setErrorCode(ex.getErrorCode() != null ? ex.getErrorCode() : "BUSINESS_ERROR");
        response.setTimestamp(LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}



