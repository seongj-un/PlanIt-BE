package com.example.planit.plan.application

import com.example.planit.plan.config.GroqProperties
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Groq(OpenAI 호환) 기반 [AiClient]. `planit.ai.arrangement.provider=groq` 일 때만 빈 생성.
 * 인증키는 환경변수(`GROQ_API_KEY`)로 주입하며 코드/깃에 넣지 않는다.
 */
@Component
@ConditionalOnProperty(name = ["planit.ai.arrangement.provider"], havingValue = "groq")
class GroqAiClient(
    restClientBuilder: RestClient.Builder,
    private val properties: GroqProperties,
) : AiClient {

    private val log = LoggerFactory.getLogger(GroqAiClient::class.java)

    private val restClient: RestClient = restClientBuilder
        .baseUrl(properties.baseUrl)
        .build()

    override fun complete(prompt: String): String {
        log.info("Groq arrangement request (model={})", properties.model)
        val requestBody = mapOf(
            "model" to properties.model,
            "messages" to listOf(
                mapOf("role" to "user", "content" to prompt),
            ),
        )
        val response = restClient.post()
            .uri("/v1/chat/completions")
            .header("Authorization", "Bearer ${properties.apiKey}")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body<JsonNode>()
            ?: error("Groq 응답이 비어 있습니다.")

        val text = response.path("choices").path(0)
            .path("message").path("content").asText("")
        if (text.isEmpty()) error("Groq 응답에서 content를 찾을 수 없습니다.")
        return text
    }
}
