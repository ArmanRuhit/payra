package com.payra.identity.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.payra.TestcontainersConfiguration;
import com.payra.platform.error.ErrorCode;
import com.payra.platform.error.PayraException;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Five OTP contracts, against real Postgres:
 *  1. request stores a hashed code with ~5 min TTL — never the raw code
 *  2. the correct code verifies the challenge
 *  3. a wrong code increments attempts AND the counter survives the thrown error
 *  4. the fifth wrong attempt burns the challenge
 *  5. verifying an expired challenge burns it
 *
 * <p>Deliberately no @Transactional on this class: we need to observe COMMITTED
 * state across separate service transactions — that IS the contract under test.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OtpServiceTest {

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpChallengeRepository challenges;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private final TransactionTemplate tx;

    @Autowired 
    OtpServiceTest(PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Test
    void requestStoresHashedCodeWithTtl() {
        otpService.request("+8801711110001");

        OtpChallenge challenge = latest("+8801711110001");
        assertThat(challenge.getStatus()).isEqualTo(OtpStatus.PENDING);
        assertThat(challenge.getCodeHash()).startsWith("$2");       // bcrypt format
        assertThat(challenge.getCodeHash()).doesNotMatch("\\d{6}"); // never the raw code
        assertThat(challenge.getExpiresAt())
                .isAfter(clock.instant().plus(OtpService.TTL).minusSeconds(5));
    }

    @Test
    void correctCodeVerifies() {
        OtpChallenge challenge = issuedChallenge("+8801711110002", "123456");

        otpService.verify("+8801711110002", "123456");

        OtpChallenge reloaded = challenges.findById(challenge.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OtpStatus.VERIFIED);
        assertThat(reloaded.getVerifiedAt()).isNotNull();
    }

    @Test
    void wrongCodeCountsAndSurvivesTheThrownError() {
        OtpChallenge challenge = issuedChallenge("+8801711110003", "123456");

        assertThatThrownBy(() -> otpService.verify("+8801711110003", "999999"))
                .isInstanceOf(PayraException.class)
                .satisfies(ex -> assertThat(((PayraException) ex).getCode())
                        .isEqualTo(ErrorCode.AUTH_OTP_INVALID));

        OtpChallenge reloaded = challenges.findById(challenge.getId()).orElseThrow();
        assertThat(reloaded.getAttempts()).isEqualTo(1); // committed — not rolled back
    }

    @Test
    void fifthWrongAttemptBurnsTheChallenge() {
        OtpChallenge challenge = issuedChallenge("+8801711110004", "123456");

        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> otpService.verify("+8801711110004", "999999"))
                    .isInstanceOf(PayraException.class);
        }

        assertThatThrownBy(() -> otpService.verify("+8801711110004", "999999"))
                .isInstanceOf(PayraException.class)
                .satisfies(ex -> assertThat(((PayraException) ex).getCode())
                        .isEqualTo(ErrorCode.AUTH_OTP_TOO_MANY_ATTEMPTS));

        OtpChallenge reloaded = challenges.findById(challenge.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OtpStatus.EXPIRED);
        assertThat(reloaded.getAttempts()).isEqualTo(5);

        // burned is burned: even the CORRECT code gets nothing (no PENDING row left)
        assertThatThrownBy(() -> otpService.verify("+8801711110004", "123456"))
                .isInstanceOf(PayraException.class)
                .satisfies(ex -> assertThat(((PayraException) ex).getCode())
                        .isEqualTo(ErrorCode.AUTH_OTP_INVALID));
    }

    @Test
    void expiredChallengeIsBurnedAndRejected() {
        OtpChallenge challenge = challenges.saveAndFlush(OtpChallenge.issue(
                "+8801711110005",
                passwordEncoder.encode("123456"),
                clock.instant().minusSeconds(1))); // expired on arrival

        assertThatThrownBy(() -> otpService.verify("+8801711110005", "123456"))
                .isInstanceOf(PayraException.class)
                .satisfies(ex -> assertThat(((PayraException) ex).getCode())
                        .isEqualTo(ErrorCode.AUTH_OTP_EXPIRED));

        OtpChallenge reloaded = challenges.findById(challenge.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OtpStatus.EXPIRED);
    }

    private OtpChallenge issuedChallenge(String phone, String rawCode) {
        return challenges.saveAndFlush(OtpChallenge.issue(
                phone, passwordEncoder.encode(rawCode), clock.instant().plus(OtpService.TTL)));
    }

    private OtpChallenge latest(String phone) {
        // The finder is @Lock(PESSIMISTIC_WRITE): Hibernate demands an active
        // transaction for SELECT ... FOR UPDATE — same requirement the service
        // satisfies in OtpService.verify().
        return tx.execute(status ->
                challenges.findFirstByPhoneNumberAndStatusOrderByCreatedAtDesc(phone, OtpStatus.PENDING)
                        .orElseThrow());
    }
}