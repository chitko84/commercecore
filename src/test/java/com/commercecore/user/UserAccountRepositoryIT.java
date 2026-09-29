package com.commercecore.user;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserAccountRepositoryIT {

    private static final String STORED_HASH =
            "$argon2id$v=19$m=65536,t=3,p=1$test-salt$test-hash";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void applicationStartsWithAllPhase2MigrationsApplied() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13");
    }

    @Test
    void persistsAndRetrievesUuidRoleAndAccountStatus() {
        UserAccount account = new UserAccount(
                "  Staff.Member@Example.COM ", STORED_HASH, " Staff Member ", " +60123456789 ", UserRole.STAFF);
        account.changeStatus(AccountStatus.LOCKED);

        repository.saveAndFlush(account);
        UUID id = account.getId();
        entityManager.clear();

        UserAccount stored = repository.findById(id).orElseThrow();
        assertThat(id).isNotNull();
        assertThat(stored.getEmail()).isEqualTo("staff.member@example.com");
        assertThat(stored.getRole()).isEqualTo(UserRole.STAFF);
        assertThat(stored.getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(stored.isEmailVerified()).isFalse();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
    }

    @Test
    void repositoryFindsAndChecksEmailWithoutCaseSensitivity() {
        UserAccount account = new UserAccount(
                "lookup@example.com", STORED_HASH, "Lookup User", null, UserRole.CUSTOMER);
        repository.saveAndFlush(account);
        entityManager.clear();

        assertThat(repository.findByEmail(" LOOKUP@EXAMPLE.COM "))
                .get()
                .extracting(UserAccount::getId)
                .isEqualTo(account.getId());
        assertThat(repository.existsByEmail("Lookup@Example.Com")).isTrue();
        assertThat(repository.existsByEmail("missing@example.com")).isFalse();
    }

    @Test
    void databaseRejectsDuplicateEmailRegardlessOfCase() {
        repository.saveAndFlush(new UserAccount(
                "Duplicate@Example.com", STORED_HASH, "First User", null, UserRole.CUSTOMER));

        UserAccount duplicate = new UserAccount(
                "duplicate@example.COM", STORED_HASH, "Second User", null, UserRole.CUSTOMER);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void schemaAndEntityStoreOnlyThePasswordHash() {
        UserAccount account = new UserAccount(
                "hash@example.com", STORED_HASH, "Hash User", null, UserRole.ADMIN);
        repository.saveAndFlush(account);

        String storedValue = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, account.getId());
        List<String> passwordColumns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = 'users'
                   AND column_name LIKE 'password%'
                 ORDER BY column_name
                """, String.class);

        assertThat(storedValue).isEqualTo(STORED_HASH);
        assertThat(passwordColumns).containsExactly("password_hash");
    }
}
