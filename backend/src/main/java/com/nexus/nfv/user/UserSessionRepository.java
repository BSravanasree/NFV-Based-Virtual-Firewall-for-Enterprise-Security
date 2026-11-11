package com.nexus.nfv.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByJti(String jti);

    @Query("select s from UserSession s join fetch s.user u join fetch u.role where s.jti = :jti")
    Optional<UserSession> findWithUserByJti(@Param("jti") String jti);

    @Query("select s from UserSession s join fetch s.user where s.revokedAt is null and s.expiresAt > :now order by s.issuedAt desc")
    List<UserSession> findActive(@Param("now") Instant now);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.user.id = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
