package com.example.planit.auth.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenId(tokenId: String): Optional<RefreshToken>
}
