package com.example.planit.auth.api

import com.example.planit.auth.application.AuthService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/signup")
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<ApiSuccessResponse<SignupResponse>> {
        val response = authService.signup(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccessResponse(data = response))
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ApiSuccessResponse<LoginResponse> =
        ApiSuccessResponse(data = authService.login(request))

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshTokenRequest): ApiSuccessResponse<RefreshResponse> =
        ApiSuccessResponse(data = authService.refresh(request))

    @PostMapping("/logout")
    fun logout(@RequestBody request: RefreshTokenRequest): ResponseEntity<Void> {
        authService.logout(request)
        return ResponseEntity.noContent().build()
    }
}
