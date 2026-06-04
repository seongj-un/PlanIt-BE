package com.example.planit.auth.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
) {
    private val signingKey: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(encodeSecret(jwtProperties.secret)))

    fun createAccessToken(userId: Long, email: String): String {
        val now = Instant.now()
        val expiry = now.plusSeconds(jwtProperties.accessTokenExpirationSeconds)
        return Jwts.builder()
            .issuer(jwtProperties.issuer)
            .subject(userId.toString())
            .claim("email", email)
            .claim("type", JwtTokenType.ACCESS.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey)
            .compact()
    }

    fun createRefreshToken(userId: Long): Pair<String, String> {
        val now = Instant.now()
        val expiry = now.plusSeconds(jwtProperties.refreshTokenExpirationSeconds)
        val tokenId = UUID.randomUUID().toString().replace("-", "")
        val token = Jwts.builder()
            .issuer(jwtProperties.issuer)
            .subject(userId.toString())
            .id(tokenId)
            .claim("type", JwtTokenType.REFRESH.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey)
            .compact()
        return tokenId to token
    }

    fun parse(token: String): JwtClaims {
        val claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
        return JwtClaims(
            subject = claims.subject,
            type = JwtTokenType.valueOf(claims["type", String::class.java]),
            tokenId = claims.id,
        )
    }

    fun getRefreshExpiry(token: String) = getClaims(token).expiration.toInstant()

    private fun getClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload

    private fun encodeSecret(secret: String): String = java.util.Base64.getEncoder().encodeToString(secret.toByteArray())
}
