package com.emerald.member.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAccountRepository extends JpaRepository<AdminAccountEntity, UUID> {
    Optional<AdminAccountEntity> findByUsername(String username);

    @Modifying
    @Query(value = """
            INSERT INTO admin_users (id, username, password_hash, role, enabled, created_at, updated_at)
            VALUES (:id, :username, :passwordHash, 'ADMIN', TRUE, :now, :now)
            ON CONFLICT (username) DO NOTHING
            """, nativeQuery = true)
    int insertIfMissing(@Param("id") UUID id, @Param("username") String username,
                        @Param("passwordHash") String passwordHash, @Param("now") Instant now);
}
