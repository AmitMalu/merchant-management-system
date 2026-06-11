package com.project2.ism.Exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resourceName, Object resourceId) {
        super(resourceName + " not found with id: " + resourceId);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
