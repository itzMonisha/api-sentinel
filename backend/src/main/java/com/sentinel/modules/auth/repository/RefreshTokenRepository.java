package com.sentinel.modules.auth.repository;

import com.sentinel.modules.auth.entity.RefreshToken;
import com.sentinel.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.user = :user AND r.revokedAt IS NULL")
    void revokeAllUserTokens(@Param("user") User user, @Param("now") Instant now);

    default void revokeAllUserTokens(User user) {
        revokeAllUserTokens(user, Instant.now());
    }
}
