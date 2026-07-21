package com.example.planit.plan.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "planit.ai.gemini")
data class GeminiProperties(
    val baseUrl: String = "https://generativelanguage.googleapis.com",
    val model: String = "gemini-2.0-flash",
    val apiKey: String = "",
)
