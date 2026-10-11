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
 * A Payra account - the identity root: wallet, KYC, PIN all hang off this row.
 * 
 * <p>No setters on purpose: state changes go through named methods
 * ({@link #activate()} and later suspend/close), so every transition is
 * greppable and auditable.
 */
@Getter 
@Entity 
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Version 
    @Column(nullable = false)
    private long version;

    @CreationTimestamp 
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp 
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** For JPA only. */
    protected User() {

    }

    private User(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.status = UserStatus.PENDING_VERIFICATION;
    }

    /** A fresh account, awaiting OTP verification. */
    public static User register(String phoneNumber) {
        return new User(phoneNumber);
    }

    /** Domain transition: OTP verified. */
    public void activate() {
        this.status = UserStatus.ACTIVE;
    }
}
