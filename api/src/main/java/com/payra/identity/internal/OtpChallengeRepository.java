package com.payra.identity.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID>{
    /**
     * Latest challenge for a phone in a given state - row-locked (SELECT ... FOR UPDATE).
     * 
     * <p>The lock serializes parallel verifies for the same phone number, so the
     * attempt counter can never lose an increment: with N threads guessing at once,
     * each waits its turn and sees the true count.
     */
    @Lock (LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpChallenge> findFirstByPhoneNumberAndStatusOrderByCreatedAtDesc(String phoneNumber, OtpStatus status);
}
