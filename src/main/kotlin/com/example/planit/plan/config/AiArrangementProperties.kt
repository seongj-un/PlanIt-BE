package com.example.planit.plan.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "planit.ai.arrangement")
data class AiArrangementProperties(
    val mode: String = "rule-based",
    // 어떤 AiClient 구현체를 쓸지: "groq" | "gemini". mode=ai 일 때만 의미가 있다.
    val provider: String = "groq",
    val timeoutSeconds: Long = 20,
)
