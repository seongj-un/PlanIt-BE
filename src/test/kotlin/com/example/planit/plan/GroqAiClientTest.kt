package com.example.planit.plan

import com.example.planit.plan.application.GroqAiClient
import com.example.planit.plan.config.GroqProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class GroqAiClientTest {

    @Test
    fun `sends prompt with bearer auth and extracts message content`() {
        val builder = RestClient.builder().baseUrl("https://groq.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = GroqProperties(baseUrl = "https://groq.test", model = "llama-3.3-70b-versatile", apiKey = "test-key")
        val client = GroqAiClient(builder, properties)

        server.expect(requestTo("https://groq.test/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andRespond(
                withSuccess(
                    """{"choices":[{"message":{"role":"assistant","content":"{\"days\":[]}"}}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = client.complete("hello")

        assertThat(result).isEqualTo("{\"days\":[]}")
        server.verify()
    }
}
