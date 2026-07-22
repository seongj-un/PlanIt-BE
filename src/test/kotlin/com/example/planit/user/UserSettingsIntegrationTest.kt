package com.example.planit.user

import com.example.planit.user.domain.NotificationSettingsRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalTime

@SpringBootTest
@AutoConfigureMockMvc
class UserSettingsIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val notificationSettingsRepository: NotificationSettingsRepository,
) {

    @Test
    fun `update study settings notification settings and account`() {
        val authToken = signupAndGetAccessToken("settings@example.com", "P@ssw0rd!")
        upsertStudyProfile(authToken)

        val studySettingsResponse = mockMvc.patch("/api/v1/users/me/study-settings") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "usualStudyHoursPerDay": 6,
                  "preferredStudyMethod": "CONCEPT_FIRST"
                }
                """.trimIndent()
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        assertThat(objectMapper.readTree(studySettingsResponse)["data"]["updated"].asBoolean()).isTrue()

        val profileAfterStudySettings = mockMvc.get("/api/v1/users/me") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val profileJson = objectMapper.readTree(profileAfterStudySettings)
        val userId = profileJson["data"]["id"].asLong()
        assertThat(profileJson["data"]["usualStudyHoursPerDay"].asInt()).isEqualTo(6)
        assertThat(profileJson["data"]["preferredStudyMethod"].asText()).isEqualTo("CONCEPT_FIRST")

        val notificationResponse = mockMvc.patch("/api/v1/users/me/notification-settings") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "dailyReminderEnabled": true,
                  "dailyReminderTime": "20:00"
                }
                """.trimIndent()
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        assertThat(objectMapper.readTree(notificationResponse)["data"]["updated"].asBoolean()).isTrue()
        val savedSettings = notificationSettingsRepository.findByUserId(userId).orElseThrow()
        assertThat(savedSettings.dailyReminderEnabled).isTrue()
        assertThat(savedSettings.dailyReminderTime).isEqualTo(LocalTime.of(20, 0))

        val accountResponse = mockMvc.patch("/api/v1/users/me/account") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "name": "김민서",
                  "password": "N3wP@ssw0rd!"
                }
                """.trimIndent()
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        assertThat(objectMapper.readTree(accountResponse)["data"]["updated"].asBoolean()).isTrue()

        val profileAfterAccountUpdate = mockMvc.get("/api/v1/users/me") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        assertThat(objectMapper.readTree(profileAfterAccountUpdate)["data"]["name"].asText()).isEqualTo("김민서")

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "email": "settings@example.com",
                  "password": "P@ssw0rd!"
                }
                """.trimIndent()
        }.andExpect {
            status { isUnauthorized() }
        }

        val loginWithNewPassword = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "email": "settings@example.com",
                  "password": "N3wP@ssw0rd!"
                }
                """.trimIndent()
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        assertThat(objectMapper.readTree(loginWithNewPassword)["data"]["user"]["name"].asText()).isEqualTo("김민서")
    }

    @Test
    fun `study settings update requires existing study profile`() {
        val authToken = signupAndGetAccessToken("settings-no-profile@example.com", "P@ssw0rd!")

        val response = mockMvc.patch("/api/v1/users/me/study-settings") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "usualStudyHoursPerDay": 5,
                  "preferredStudyMethod": "BALANCED"
                }
                """.trimIndent()
        }.andExpect {
            status { isConflict() }
        }.andReturn().response.contentAsString

        assertThat(objectMapper.readTree(response)["error"]["code"].asText()).isEqualTo("STUDY_PROFILE_REQUIRED")
    }

    @Test
    fun `notification settings require time when enabled`() {
        val authToken = signupAndGetAccessToken("settings-invalid-notification@example.com", "P@ssw0rd!")

        val response = mockMvc.patch("/api/v1/users/me/notification-settings") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "dailyReminderEnabled": true,
                  "dailyReminderTime": " "
                }
                """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }.andReturn().response.contentAsString

        assertThat(objectMapper.readTree(response)["error"]["code"].asText()).isEqualTo("DAILY_REMINDER_TIME_REQUIRED")
    }

    private fun signupAndGetAccessToken(email: String, password: String): String {
        val signupResponse = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "name": "김민지",
                  "email": "$email",
                  "password": "$password"
                }
                """.trimIndent()
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.contentAsString

        return objectMapper.readTree(signupResponse)["data"]["tokens"]["accessToken"].asText()
    }

    private fun upsertStudyProfile(authToken: String) {
        mockMvc.put("/api/v1/users/me/study-profile") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "usualStudyHoursPerDay": 4,
                  "preferredStudyMethod": "BALANCED"
                }
                """.trimIndent()
        }.andExpect {
            status { isOk() }
        }
    }
}
