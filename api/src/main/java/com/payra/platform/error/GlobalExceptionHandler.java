package com.payra.platform.error;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * GlobalExceptionHandler
 * 
 * Single choke point: every exception becomes an RFC 7807 ProblemDetail response.
 * 
 * Shape:
 *  { "type": "https://payra.dev/errors/wallet-insufficient-funds",
 *    "title": "Insufficient funds", "status": 422,
 *    "detail": "...", "code": "WALLET_INSUFFICIENT_FUNDS"
 *  }
 */
@Slf4j 
@RestControllerAdvice 
public class GlobalExceptionHandler {
    
    @ExceptionHandler(PayraException.class)
    ProblemDetail handleBusiness(PayraException ex) {
        ProblemDetail problem = problemOf(ex.getCode());
        // Only set detail when the throw-site had something more specific than the title
        if (!ex.getMessage().equals(ex.getCode().getTitle())){
            problem.setDetail(ex.getMessage());
        }

        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = problemOf(ErrorCode.REQUEST_VALIDATION_FAILED);
        problem.setProperty("errors", fieldErrors(ex));

        return problem;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResource(NoResourceFoundException ex){
        return problemOf(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problemOf(ErrorCode.INTERNAL_ERROR);
    }

    private static ProblemDetail problemOf(ErrorCode code){
        ProblemDetail problem = ProblemDetail.forStatus(code.getStatus());

        problem.setTitle(code.getTitle());
        problem.setType(
            URI.create("https://payra.apps.armanruhit.dev/errors/" + code.name().toLowerCase().replace('_', '-'))
        );
        problem.setProperty("code", code.name());

        return problem;
    }

    private static List<InvalidField> fieldErrors(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream().map(fe -> new InvalidField(fe.getField(), Objects.toString(fe.getDefaultMessage(), "invalid"))).toList();
    }

    /**
     * InvalidField
     */
    public record InvalidField(String field, String message) {
    }
}
