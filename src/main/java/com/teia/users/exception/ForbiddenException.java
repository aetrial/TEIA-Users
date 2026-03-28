package com.teia.users.exception;

/**
 * Thrown when an authenticated user attempts to access a resource they are not allowed to view.
 * Results in HTTP 403 Forbidden.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
