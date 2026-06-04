package com.example.planit.auth.api

data class SignupRequest(
    val name: String,
    val email: String,
    val password: String,
)

data class LoginRequest(
    val email: String,
    val password: String,
)

data class RefreshTokenRequest(
    val refreshToken: String,
)

data class TokenPairResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

data class UserSummaryResponse(
    val id: Long,
    val name: String,
    val email: String? = null,
    val onboardingCompleted: Boolean,
)

data class SignupResponse(
    val user: UserSummaryResponse,
    val tokens: TokenPairResponse,
)

data class LoginResponse(
    val user: UserSummaryResponse,
    val tokens: TokenPairResponse,
)

data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

data class ApiSuccessResponse<T>(
    val success: Boolean = true,
    val data: T,
)

data class ApiErrorResponse(
    val success: Boolean = false,
    val error: ErrorBody,
)

data class ErrorBody(
    val code: String,
    val message: String,
)
