package com.example.planit.plan.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "planit.ai.arrangement")
data class AiArrangementProperties(
    val mode: String = "rule-based",
    val timeoutSeconds: Long = 20,
)
