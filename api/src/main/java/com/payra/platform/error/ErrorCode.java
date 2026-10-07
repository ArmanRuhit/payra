package com.payra.platform.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * The platform-wide error catalogue - part of the public API contract.
 * 
 * Rules:
 *  - the enum name IS the wire code the frontend switches on
 *  - codes never change once shipped; new situation get new codes
 *  - entries are grouped per module, added in the phase that introduces them
 * 
 */
@Getter 
public enum ErrorCode {
    // --- Platform ---
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong"),
    REQUEST_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }


}
