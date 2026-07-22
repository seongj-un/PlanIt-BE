package com.example.planit.onboarding

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
import org.springframework.test.web.servlet.put

@SpringBootTest
@AutoConfigureMockMvc
class OnboardingSourceApiIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @Test
    fun `upsert study profile active exam plan and subject scopes`() {
        val authToken = signupAndGetAccessToken("student@example.com")

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

        val profilePayload = mockMvc.get("/api/v1/users/me") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val profileJson = objectMapper.readTree(profilePayload)
        assertThat(profileJson["data"]["sproutCount"].asInt()).isZero()
        assertThat(profileJson["data"]["attendanceStreakDays"].asInt()).isZero()
        assertThat(profileJson["data"]["onboardingCompleted"].asBoolean()).isFalse()

        mockMvc.put("/api/v1/exam-plans/active") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "targetExamType": "CSAT",
                  "targetExamLabel": "수능",
                  "examDate": "2099-06-12"
                }
                """.trimIndent()
        }.andExpect {
            status { isOk() }
        }

        val planPayload = mockMvc.get("/api/v1/exam-plans/active") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val planJson = objectMapper.readTree(planPayload)
        assertThat(planJson["data"]["targetExamType"].asText()).isEqualTo("CSAT")

        mockMvc.put("/api/v1/exam-plans/active/scopes") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "scopes": [
                    {
                      "subjectName": "수학",
                      "rawRangeText": "수열과 극한 1~3단원",
                      "preferredMethodNote": "개념 정리 후 대표 문제 15문제",
                      "difficulty": "HIGH",
                      "unitType": "CHAPTER",
                      "totalUnits": 3,
                      "remainingUnits": 3
                    },
                    {
                      "subjectName": "영어",
                      "rawRangeText": "빈칸 추론 10문제",
                      "preferredMethodNote": "근거 문장 표시",
                      "difficulty": "MEDIUM",
                      "unitType": "QUESTION",
                      "totalUnits": 10,
                      "remainingUnits": 10
                    }
                  ]
                }
                """.trimIndent()
        }.andExpect {
            status { isOk() }
        }

        val scopesPayload = mockMvc.get("/api/v1/exam-plans/active/scopes") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val scopesJson = objectMapper.readTree(scopesPayload)
        assertThat(scopesJson["data"].size()).isEqualTo(2)
        assertThat(scopesJson["data"][0]["unitType"].asText()).isEqualTo("CHAPTER")

        val completedOnboardingPayload = mockMvc.get("/api/v1/users/me") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val completedOnboardingJson = objectMapper.readTree(completedOnboardingPayload)
        assertThat(completedOnboardingJson["data"]["targetExamType"].asText()).isEqualTo("CSAT")
        assertThat(completedOnboardingJson["data"]["targetExamLabel"].asText()).isEqualTo("수능")
        assertThat(completedOnboardingJson["data"]["examDate"].asText()).isEqualTo("2099-06-12")
        assertThat(completedOnboardingJson["data"]["onboardingCompleted"].asBoolean()).isTrue()
    }

    private fun signupAndGetAccessToken(email: String): String {
        val signupResponse = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "name": "김민지",
                  "email": "$email",
                  "password": "P@ssw0rd!"
                }
                """.trimIndent()
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.contentAsString

        return objectMapper.readTree(signupResponse)["data"]["tokens"]["accessToken"].asText()
    }
}
