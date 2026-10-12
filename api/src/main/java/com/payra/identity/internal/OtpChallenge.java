package com.payra.identity.internal;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * One OTP request. The code is stored hashed; verification, expiry, and the 
 * attempt cap all flow through the named transitions below.
 */
@Getter 
@Entity 
@Table(name = "otp_challenges")
public class OtpChallenge {
    
    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column (name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column (name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Enumerated (EnumType.STRING)
    @Column (nullable = false, length = 20)
    private OtpStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column (name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column (name = "verified_at")
    private Instant verifiedAt;

    @Version 
    @Column (nullable = false)
    private long version;

    @CreationTimestamp 
    @Column (name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp 
    @Column (name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** For JPA only */
    protected OtpChallenge() {

    }

    private OtpChallenge(String phoneNumber, String codeHash, Instant expiresAt) {
        this.phoneNumber = phoneNumber;
        this.codeHash = codeHash;
        this.status = OtpStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    /** Issues a fresh PENDING challenge for the given phone. */
    public static OtpChallenge issue(String phoneNumber, String codeHash, Instant expiresAt) {
        return new OtpChallenge(phoneNumber, codeHash, expiresAt);
    }

    public boolean isPending() {
        return status == OtpStatus.PENDING;
    }

    /** Expiry is computed against a caller-supplied "now" — never against the clock inside. */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /** Wrong code: counts toward the cap. The service decides when the cap is hit. */
    public void recordFailedAttempt() {
        this.attempts++;
    }

    /** Correct code: terminal success. */
    public void markVerified(Instant now) {
        this.status = OtpStatus.VERIFIED;
        this.verifiedAt = now;
    }

    /** Burned — expired on arrival or attempt cap reached. No further tries. */
    public void markExpired() {
        this.status = OtpStatus.EXPIRED;
    }
    
}
