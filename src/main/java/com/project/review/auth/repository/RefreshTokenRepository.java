package com.project.review.auth.repository;

import com.project.review.auth.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @EntityGraph(attributePaths = "member")
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
