package com.commercecore.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findAllByUserIdOrderByCreatedAtAsc(UUID userId);
    Optional<Address> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update Address a set a.defaultShipping = false where a.user.id = :userId and a.defaultShipping = true")
    void clearDefaultShipping(@Param("userId") UUID userId);

    @Modifying
    @Query("update Address a set a.defaultBilling = false where a.user.id = :userId and a.defaultBilling = true")
    void clearDefaultBilling(@Param("userId") UUID userId);

    @Modifying
    @Query("update Address a set a.defaultShipping = false where a.user.id = :userId and a.id <> :exceptId and a.defaultShipping = true")
    void clearOtherDefaultShipping(@Param("userId") UUID userId, @Param("exceptId") UUID exceptId);

    @Modifying
    @Query("update Address a set a.defaultBilling = false where a.user.id = :userId and a.id <> :exceptId and a.defaultBilling = true")
    void clearOtherDefaultBilling(@Param("userId") UUID userId, @Param("exceptId") UUID exceptId);

}
