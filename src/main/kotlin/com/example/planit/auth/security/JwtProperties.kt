package com.example.planit.auth.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "planit.security.jwt")
data class JwtProperties(
    val issuer: String,
    val secret: String,
    val accessTokenExpirationSeconds: Long,
    val refreshTokenExpirationSeconds: Long,
)
