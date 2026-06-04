package com.example.planit.common.api

import com.example.planit.auth.api.ApiErrorResponse
import com.example.planit.auth.api.ErrorBody
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class CommonApiExceptionHandler {

    @ExceptionHandler(CommonApiException::class)
    fun handleCommonApiException(exception: CommonApiException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(exception.status)
            .body(
                ApiErrorResponse(
                    error = ErrorBody(
                        code = exception.code,
                        message = exception.message,
                    ),
                ),
            )

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(exception: NoSuchElementException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiErrorResponse(
                    error = ErrorBody(
                        code = "NOT_FOUND",
                        message = exception.message ?: "대상을 찾을 수 없습니다.",
                    ),
                ),
            )
}
