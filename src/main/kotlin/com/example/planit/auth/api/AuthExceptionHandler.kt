package com.example.planit.auth.api

import com.example.planit.auth.application.AuthException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AuthExceptionHandler {

    @ExceptionHandler(AuthException::class)
    fun handleAuthException(exception: AuthException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(exception.status)
            .body(
                ApiErrorResponse(
                    error = ErrorBody(
                        code = exception.code,
                        message = exception.message,
                    ),
                ),
            )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    error = ErrorBody(
                        code = "VALIDATION_ERROR",
                        message = exception.message ?: "invalid request",
                    ),
                ),
            )
}
