package com.example.planit.auth.application

import org.springframework.http.HttpStatus

class AuthException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)
