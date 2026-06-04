package com.example.planit.auth.security

data class JwtClaims(
    val subject: String,
    val type: JwtTokenType,
    val tokenId: String?,
)
