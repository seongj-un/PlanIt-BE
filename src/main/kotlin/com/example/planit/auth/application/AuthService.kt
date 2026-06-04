package com.example.planit.auth.application

import com.example.planit.auth.api.LoginRequest
import com.example.planit.auth.api.LoginResponse
import com.example.planit.auth.api.RefreshResponse
import com.example.planit.auth.api.RefreshTokenRequest
import com.example.planit.auth.api.SignupRequest
import com.example.planit.auth.api.SignupResponse
import com.example.planit.auth.api.TokenPairResponse
import com.example.planit.auth.api.UserSummaryResponse
import com.example.planit.auth.domain.RefreshToken
import com.example.planit.auth.domain.RefreshTokenRepository
import com.example.planit.auth.security.JwtProperties
import com.example.planit.auth.security.JwtTokenProvider
import com.example.planit.auth.security.JwtTokenType
import com.example.planit.user.domain.UserAccount
import com.example.planit.user.domain.UserAccountRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class AuthService(
    private val userAccountRepository: UserAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties,
) {

    @Transactional
    fun signup(request: SignupRequest): SignupResponse {
        if (userAccountRepository.findByEmail(request.email).isPresent) {
            throw AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다.")
        }

        val user = userAccountRepository.save(
            UserAccount(
                name = request.name.trim(),
                email = request.email.trim().lowercase(),
                passwordHash = passwordEncoder.encode(request.password),
                onboardingCompleted = false,
            ),
        )

        val tokens = issueTokens(user)
        return SignupResponse(
            user = UserSummaryResponse(
                id = user.id!!,
                name = user.name,
                email = user.email,
                onboardingCompleted = user.onboardingCompleted,
            ),
            tokens = tokens,
        )
    }

    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        val user = userAccountRepository.findByEmail(request.email.trim().lowercase())
            .orElseThrow { AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "이메일 또는 비밀번호가 올바르지 않습니다.") }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "이메일 또는 비밀번호가 올바르지 않습니다.")
        }

        val tokens = issueTokens(user)
        return LoginResponse(
            user = UserSummaryResponse(
                id = user.id!!,
                name = user.name,
                onboardingCompleted = user.onboardingCompleted,
            ),
            tokens = tokens,
        )
    }

    @Transactional
    fun refresh(request: RefreshTokenRequest): RefreshResponse {
        val claims = parseRefreshClaims(request.refreshToken)
        val storedToken = refreshTokenRepository.findByTokenId(claims.tokenId!!)
            .orElseThrow { AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "유효하지 않은 리프레시 토큰입니다.") }

        if (storedToken.revoked || storedToken.expiresAt.isBefore(LocalDateTime.now())) {
            throw AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "만료되었거나 로그아웃된 리프레시 토큰입니다.")
        }

        storedToken.revoked = true
        val user = storedToken.user
        val tokens = issueTokens(user)
        return RefreshResponse(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresIn = tokens.expiresIn,
        )
    }

    @Transactional
    fun logout(request: RefreshTokenRequest) {
        val claims = parseRefreshClaims(request.refreshToken)
        refreshTokenRepository.findByTokenId(claims.tokenId!!)
            .ifPresent { it.revoked = true }
    }

    private fun parseRefreshClaims(token: String) = runCatching { jwtTokenProvider.parse(token) }
        .getOrElse { throw AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "유효하지 않은 리프레시 토큰입니다.") }
        .also {
            if (it.type != JwtTokenType.REFRESH) {
                throw AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "리프레시 토큰이 아닙니다.")
            }
        }

    private fun issueTokens(user: UserAccount): TokenPairResponse {
        val accessToken = jwtTokenProvider.createAccessToken(user.id!!, user.email)
        val (refreshTokenId, refreshToken) = jwtTokenProvider.createRefreshToken(user.id!!)
        refreshTokenRepository.save(
            RefreshToken(
                user = user,
                tokenId = refreshTokenId,
                expiresAt = LocalDateTime.ofInstant(jwtTokenProvider.getRefreshExpiry(refreshToken), ZoneOffset.UTC),
                revoked = false,
            ),
        )
        return TokenPairResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = jwtProperties.accessTokenExpirationSeconds,
        )
    }
}
