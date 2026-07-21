package com.example.planit.plan

import com.example.planit.plan.application.GeminiAiClient
import com.example.planit.plan.config.GeminiProperties
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

class GeminiAiClientTest {

    @Test
    fun `sends prompt with api key header and extracts candidate text`() {
        val builder = RestClient.builder().baseUrl("https://gemini.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = GeminiProperties(baseUrl = "https://gemini.test", model = "gemini-2.0-flash", apiKey = "test-key")
        val client = GeminiAiClient(builder, properties)

        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-2.0-flash:generateContent"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("x-goog-api-key", "test-key"))
            .andRespond(
                withSuccess(
                    """{"candidates":[{"content":{"parts":[{"text":"{\"days\":[]}"}]}}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = client.complete("hello")

        assertThat(result).isEqualTo("{\"days\":[]}")
        server.verify()
    }
}
