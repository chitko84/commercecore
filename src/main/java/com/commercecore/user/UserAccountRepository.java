package com.commercecore.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID>,
        JpaSpecificationExecutor<UserAccount> {

    long countByRole(UserRole role);

    @Query("select count(u) from UserAccount u where u.role = :role " +
            "and u.createdAt >= :from and u.createdAt < :toExclusive")
    long countNewByRoleBetween(@Param("role") UserRole role,
                               @Param("from") Instant from,
                               @Param("toExclusive") Instant toExclusive);

    @Query("select account from UserAccount account where account.email = lower(trim(:email))")
    Optional<UserAccount> findByEmail(@Param("email") String email);

    @Query("select (count(account) > 0) from UserAccount account where account.email = lower(trim(:email))")
    boolean existsByEmail(@Param("email") String email);
}
