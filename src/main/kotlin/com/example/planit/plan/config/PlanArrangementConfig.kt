package com.example.planit.plan.config

import com.example.planit.plan.application.AiArrangementProvider
import com.example.planit.plan.application.AiClient
import com.example.planit.plan.application.PlanArrangementProvider
import com.example.planit.plan.application.RuleBasedArrangementProvider
import com.example.planit.plan.config.GeminiProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@EnableConfigurationProperties(AiArrangementProperties::class, GeminiProperties::class, GroqProperties::class)
class PlanArrangementConfig {

    @Bean
    @Primary
    fun activeArrangementProvider(
        properties: AiArrangementProperties,
        ruleBased: RuleBasedArrangementProvider,
        aiClientProvider: ObjectProvider<AiClient>,
        objectMapper: ObjectMapper,
    ): PlanArrangementProvider {
        val aiClient = aiClientProvider.getIfAvailable()
        return if (properties.mode == "ai" && aiClient != null) {
            AiArrangementProvider(aiClient, ruleBased, objectMapper)
        } else {
            ruleBased
        }
    }
}
