package com.payra.identity.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.payra.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Slice test: JPA + Postgres only — no web layer, no other modules.
 * Runs against a real Postgres 18 container, so the V1 migration, the unique
 * constraint, and @Version optimistic locking are exercised for real — not mocked.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesUserWithGeneratedIdAndTimestamps() {
        User saved = userRepository.saveAndFlush(User.register("+8801700000001"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsByPhoneNumber() {
        userRepository.saveAndFlush(User.register("+8801700000002"));

        assertThat(userRepository.findByPhoneNumber("+8801700000002")).isPresent();
        assertThat(userRepository.existsByPhoneNumber("+8801700000002")).isTrue();
        assertThat(userRepository.findByPhoneNumber("+8801700000003")).isEmpty();
    }

    @Test
    void activateFlipsStatusAndBumpsVersion() {
        User saved = userRepository.saveAndFlush(User.register("+8801700000004"));

        saved.activate();
        userRepository.saveAndFlush(saved);

        User reloaded = userRepository.findByPhoneNumber("+8801700000004").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    void duplicatePhoneNumberIsRejectedByDatabase() {
        userRepository.saveAndFlush(User.register("+8801700000005"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.register("+8801700000005")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}