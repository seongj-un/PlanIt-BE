package com.example.planit.plan.application

import com.example.planit.plan.config.GeminiProperties
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
@ConditionalOnProperty(name = ["planit.ai.arrangement.provider"], havingValue = "gemini")
class GeminiAiClient(
    restClientBuilder: RestClient.Builder,
    private val properties: GeminiProperties,
) : AiClient {

    private val restClient: RestClient = restClientBuilder
        .baseUrl(properties.baseUrl)
        .build()

    override fun complete(prompt: String): String {
        val requestBody = mapOf(
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to prompt))),
            ),
        )
        val response = restClient.post()
            .uri("/v1beta/models/{model}:generateContent", properties.model)
            .header("x-goog-api-key", properties.apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body<JsonNode>()
            ?: error("Gemini 응답이 비어 있습니다.")

        val text = response.path("candidates").path(0)
            .path("content").path("parts").path(0)
            .path("text").asText("")
        if (text.isEmpty()) error("Gemini 응답에서 text를 찾을 수 없습니다.")
        return text
    }
}
