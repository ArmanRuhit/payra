package com.payra.identity.internal;
/**
 * 
 * UserStatus
 * 
 * Lifecycle of a user account.
 * 
 * <p>Persisted as a string; the CHECK constraints in {@code V1__create_users.sql}
 * mirrors these constants - change one, change both.
 */
public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    CLOSED
}
