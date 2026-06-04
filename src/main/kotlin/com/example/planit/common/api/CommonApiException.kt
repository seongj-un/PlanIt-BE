package com.example.planit.common.api

import org.springframework.http.HttpStatus

class CommonApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)
