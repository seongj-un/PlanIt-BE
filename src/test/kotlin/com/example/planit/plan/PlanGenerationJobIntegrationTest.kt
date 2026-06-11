package com.example.planit.plan

import com.example.planit.plan.domain.DailyPlanItemRepository
import com.example.planit.plan.domain.DailyPlanRepository
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
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
class PlanGenerationJobIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val dailyPlanRepository: DailyPlanRepository,
    @Autowired private val dailyPlanItemRepository: DailyPlanItemRepository,
) {

    @Test
    fun `create plan generation job and persist today's plan`() {
        val authToken = signupAndGetAccessToken("planner@example.com")
        upsertStudyProfile(authToken)
        upsertActivePlan(authToken)
        replaceSubjectScopes(authToken)

        val createResponse = mockMvc.post("/api/v1/plan-generation-jobs") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "subjects": [
                    {
                      "subjectName": "수학",
                      "examRange": "수열과 극한 1~3단원",
                      "preferredMethodNote": "개념 정리 후 대표 문제 15문제",
                      "difficulty": "HIGH"
                    },
                    {
                      "subjectName": "영어",
                      "examRange": "빈칸 추론 10문제",
                      "preferredMethodNote": "근거 문장 표시",
                      "difficulty": "MEDIUM"
                    }
                  ],
                  "preferredStudyMethod": "BALANCED",
                  "difficultSubjects": ["수학"],
                  "dailyMaxStudyHours": 5
                }
                """.trimIndent()
        }.andExpect {
            status { isAccepted() }
        }.andReturn().response.contentAsString

        val createJson = objectMapper.readTree(createResponse)
        val jobId = createJson["data"]["jobId"].asText()
        assertThat(createJson["data"]["status"].asText()).isEqualTo("PENDING")
        assertThat(createJson["data"]["estimatedSeconds"].asInt()).isEqualTo(0)

        val statusResponse = mockMvc.get("/api/v1/plan-generation-jobs/$jobId") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val statusJson = objectMapper.readTree(statusResponse)
        val planId = statusJson["data"]["planId"].asLong()
        assertThat(statusJson["data"]["status"].asText()).isEqualTo("COMPLETED")
        assertThat(statusJson["data"]["dashboardAvailable"].asBoolean()).isTrue()
        assertThat(statusJson["data"]["planDate"].asText()).isEqualTo(LocalDate.now().toString())

        val dailyPlan = dailyPlanRepository.findById(planId).orElseThrow()
        val items = dailyPlanItemRepository.findAllByDailyPlanId(planId)
        assertThat(dailyPlan.planDate).isEqualTo(LocalDate.now())
        assertThat(items).hasSize(2)
        assertThat(items.map { it.subjectNameSnapshot }).containsExactlyInAnyOrder("수학", "영어")
        assertThat(items.all { it.plannedUnits > 0 }).isTrue()
        assertThat(items.sumOf { it.estimatedMinutes }).isLessThanOrEqualTo(300)
    }

    @Test
    fun `fail plan generation when subject scope is missing`() {
        val authToken = signupAndGetAccessToken("missing-scope@example.com")
        upsertStudyProfile(authToken)
        upsertActivePlan(authToken)

        val response = mockMvc.post("/api/v1/plan-generation-jobs") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "subjects": [
                    {
                      "subjectName": "수학",
                      "examRange": "수열과 극한 1~3단원",
                      "preferredMethodNote": "개념 정리 후 대표 문제 15문제",
                      "difficulty": "HIGH"
                    }
                  ],
                  "preferredStudyMethod": "BALANCED",
                  "difficultSubjects": [],
                  "dailyMaxStudyHours": 4
                }
                """.trimIndent()
        }.andExpect {
            status { isConflict() }
        }.andReturn().response.contentAsString

        val responseJson = objectMapper.readTree(response)
        assertThat(responseJson["error"]["code"].asText()).isEqualTo("SUBJECT_SCOPE_NOT_FOUND")
    }

    @Test
    fun `regenerating today's plan removes stale completion rewards`() {
        val authToken = signupAndGetAccessToken("regenerate@example.com")
        upsertStudyProfile(authToken)
        upsertActivePlan(authToken)
        replaceSubjectScopes(authToken)

        mockMvc.post("/api/v1/plan-generation-jobs") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content = validGenerationRequest()
        }.andExpect {
            status { isAccepted() }
        }

        val todayPlanResponse = mockMvc.get("/api/v1/plans/today") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val todayPlanJson = objectMapper.readTree(todayPlanResponse)
        val firstItemId = todayPlanJson["data"]["items"][0]["planItemId"].asLong()

        mockMvc.patch("/api/v1/plans/today/items/$firstItemId") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"completed":true}"""
        }.andExpect {
            status { isOk() }
        }

        val progressBeforeRegeneration = mockMvc.get("/api/v1/plans/today/progress") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        assertThat(objectMapper.readTree(progressBeforeRegeneration)["data"]["sproutCount"].asInt()).isEqualTo(1)

        mockMvc.post("/api/v1/plan-generation-jobs") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content = validGenerationRequest()
        }.andExpect {
            status { isAccepted() }
        }

        val progressAfterRegeneration = mockMvc.get("/api/v1/plans/today/progress") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val progressJson = objectMapper.readTree(progressAfterRegeneration)
        assertThat(progressJson["data"]["completedCount"].asInt()).isZero()
        assertThat(progressJson["data"]["sproutCount"].asInt()).isZero()
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

    private fun upsertStudyProfile(authToken: String) {
        mockMvc.put("/api/v1/users/me/study-profile") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "age": 18,
                  "schoolLevel": "HIGH_SCHOOL",
                  "usualStudyHoursPerDay": 4,
                  "preferredStudyMethod": "BALANCED"
                }
                """.trimIndent()
        }.andExpect {
            status { isOk() }
        }
    }

    private fun upsertActivePlan(authToken: String) {
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
    }

    private fun replaceSubjectScopes(authToken: String) {
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
    }

    private fun validGenerationRequest(): String =
        """
        {
          "subjects": [
            {
              "subjectName": "수학",
              "examRange": "수열과 극한 1~3단원",
              "preferredMethodNote": "개념 정리 후 대표 문제 15문제",
              "difficulty": "HIGH"
            },
            {
              "subjectName": "영어",
              "examRange": "빈칸 추론 10문제",
              "preferredMethodNote": "근거 문장 표시",
              "difficulty": "MEDIUM"
            }
          ],
          "preferredStudyMethod": "BALANCED",
          "difficultSubjects": ["수학"],
          "dailyMaxStudyHours": 5
        }
        """.trimIndent()
}
