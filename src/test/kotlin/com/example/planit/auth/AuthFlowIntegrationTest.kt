package com.example.planit.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @Test
    fun `signup login refresh and logout with jwt`() {
        val signupResponse = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "name": "김민지",
                  "email": "minji@example.com",
                  "password": "P@ssw0rd!"
                }
                """.trimIndent()
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.contentAsString

        val signupPayload = objectMapper.readTree(signupResponse)
        val accessToken = signupPayload["data"]["tokens"]["accessToken"].asText()
        val issuedRefreshToken = signupPayload["data"]["tokens"]["refreshToken"].asText()
        assertThat(accessToken).isNotBlank()

        mockMvc.get("/api/v1/users/me") {
            header("Authorization", "Bearer $accessToken")
        }.andExpect {
            status { isOk() }
        }

        val loginResponse = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "email": "minji@example.com",
                  "password": "P@ssw0rd!"
                }
                """.trimIndent()
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val loginPayload = objectMapper.readTree(loginResponse)
        val loginRefreshToken = loginPayload["data"]["tokens"]["refreshToken"].asText()
        assertThat(loginPayload["data"]["user"]["name"].asText()).isEqualTo("김민지")

        val refreshResponse = mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$loginRefreshToken"}"""
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val refreshPayload = objectMapper.readTree(refreshResponse)
        val rotatedRefreshToken = refreshPayload["data"]["refreshToken"].asText()
        assertThat(refreshPayload["data"]["accessToken"].asText()).isNotBlank()
        assertThat(rotatedRefreshToken).isNotEqualTo(loginRefreshToken)

        mockMvc.post("/api/v1/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$rotatedRefreshToken"}"""
        }.andExpect {
            status { isNoContent() }
        }

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$rotatedRefreshToken"}"""
        }.andExpect {
            status { isUnauthorized() }
        }

        assertThat(issuedRefreshToken).isNotBlank()
    }
}
