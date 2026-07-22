package com.example.planit.plan.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "planit.ai.groq")
data class GroqProperties(
    val baseUrl: String = "https://api.groq.com/openai",
    val model: String = "llama-3.3-70b-versatile",
    val apiKey: String = "",
)
