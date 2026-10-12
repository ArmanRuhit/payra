package com.payra.identity.internal;

import com.payra.platform.error.ErrorCode;
import com.payra.platform.error.PayraException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

/**
 * OTP lifecycle: issue, deliver (stub), verify.
 * 
 * <p>Two load-bearing decisions:
 *  - codes are bcrypt-hashed before storage; the raw value only ever exists in
 *    the delivery channel (dev: the log)
 *  - verify runs in its own transaction with noRollbackFor=PayraException, so the
 *    attempt counter and burn survive even though the caller receives an error
 * 
 */
@Slf4j 
@Service 
public class OtpService {
    
    static final Duration TTL = Duration.ofMinutes(5);
    static final int MAX_ATTEMPTS = 5;

    private final OtpChallengeRepository challengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpChallengeRepository challengeRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.challengeRepository = challengeRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Issues a fresh PENDING challenge and delivers the code */
    @Transactional 
    public void request(String phoneNumber) {
        String code = generateCode();

        challengeRepository.save(OtpChallenge.issue(phoneNumber, passwordEncoder.encode(code), clock.instant().plus(TTL)));

        // Delivery stubL dev logs the code; a real SMS adapter plugs in at this seam.
        log.info("[dev-delivery] OTP FOR {}: {}", phoneNumber, code);
    }

    /**
     * Verifies the latest PENDING challenge for the phone.
     * 
     * <p>The pessimistic lock serializes parallel guesses; REQUIRES_NEW +
     * noRollbackFor guarantees attempt bookkeeping commits even when this method throws - a failed attempt is a fact, not a side effect of failed request.
     * 
     * @throws PayraException AUTH_OTP_INVALID | AUTH_OTP_EXPIRED | AUTH_OTP_TOO_MANY_ATTEMPTS
     */
    @Transactional (propagation = Propagation.REQUIRES_NEW, noRollbackFor = PayraException.class)
    public void verify(String phoneNumber, String code) {
        Instant now = clock.instant();

        OtpChallenge challenge = challengeRepository.findFirstByPhoneNumberAndStatusOrderByCreatedAtDesc(phoneNumber, OtpStatus.PENDING)
        .orElseThrow(() -> new PayraException(ErrorCode.AUTH_OTP_INVALID, "No active OTP for this number"));

        if (challenge.isExpired(now)) {
            challenge.markExpired();
            throw new PayraException(ErrorCode.AUTH_OTP_EXPIRED, "Request a new OTP");
        }

        if(!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.recordFailedAttempt();
            if(challenge.getAttempts() >= MAX_ATTEMPTS) {
                challenge.markExpired();
                throw new PayraException(ErrorCode.AUTH_OTP_TOO_MANY_ATTEMPTS, "Request a new OTP");
            }
            throw new PayraException(ErrorCode.AUTH_OTP_INVALID, "Wrong code - " + (MAX_ATTEMPTS - challenge.getAttempts()) + " attempts left");
        }


        challenge.markVerified(now);
    }

    private String generateCode() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
}
