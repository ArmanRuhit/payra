package com.payra.platform.error;

import lombok.Getter;

/**
 * 
 * PayraException
 * 
 * Base exception for expected, business-level failures.
 * 
 * <p>Rules:
 *  - throw this (with an {@link ErrorCode} for anything the client should see)
 *  - anything else that escapes is a bug -> mapped to INTERNAL_ERROR (500)
 */

@Getter 
public class PayraException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public PayraException(ErrorCode code){
        super(code.getTitle());
        this.code = code;
    }

    public PayraException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public PayraException(ErrorCode code, String detail, Throwable cause) {
        super(detail, cause);
        this.code = code;
    }
}
