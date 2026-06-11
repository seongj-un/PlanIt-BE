package com.example.planit.plan

import com.example.planit.plan.domain.DailyPlanItemRepository
import com.example.planit.plan.domain.DailyPlanRepository
import com.fasterxml.jackson.databind.JsonNode
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
class TodayPlanApiIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val dailyPlanRepository: DailyPlanRepository,
    @Autowired private val dailyPlanItemRepository: DailyPlanItemRepository,
) {

    @Test
    fun `today plan lifecycle supports read update toggle progress and complete`() {
        val authToken = createGeneratedTodayPlan("today-plan@example.com")

        val todayPlanResponse = mockMvc.get("/api/v1/plans/today") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val todayPlanJson = objectMapper.readTree(todayPlanResponse)
        val planId = todayPlanJson["data"]["planId"].asLong()
        val items = todayPlanJson["data"]["items"]
        assertThat(items.size()).isEqualTo(2)

        val mathItem = items.first { it["subjectName"].asText() == "수학" }
        val englishItem = items.first { it["subjectName"].asText() == "영어" }

        val updateResponse = mockMvc.put("/api/v1/plans/today") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "items": [
                    {
                      "planItemId": ${mathItem["planItemId"].asLong()},
                      "subjectName": "수학",
                      "examRange": "수열과 극한 2단원",
                      "studyMethod": "오답 정리 포함",
                      "priority": "HIGH"
                    },
                    {
                      "subjectName": "영어",
                      "examRange": "빈칸 추론 5문제",
                      "studyMethod": "근거 문장 밑줄 표시",
                      "priority": "MEDIUM"
                    }
                  ],
                  "deletedPlanItemIds": [${englishItem["planItemId"].asLong()}]
                }
                """.trimIndent()
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val updateJson = objectMapper.readTree(updateResponse)
        assertThat(updateJson["data"]["planId"].asLong()).isEqualTo(planId)
        assertThat(updateJson["data"]["itemCount"].asInt()).isEqualTo(2)

        val progressResponse = mockMvc.get("/api/v1/plans/today/progress") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val progressJson = objectMapper.readTree(progressResponse)
        assertThat(progressJson["data"]["completedCount"].asInt()).isZero()
        assertThat(progressJson["data"]["totalCount"].asInt()).isEqualTo(2)
        assertThat(progressJson["data"]["remainingItems"].size()).isEqualTo(2)

        val refreshedTodayPlanResponse = mockMvc.get("/api/v1/plans/today") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val refreshedItems = objectMapper.readTree(refreshedTodayPlanResponse)["data"]["items"]
        val refreshedMathItem = refreshedItems.first { it["subjectName"].asText() == "수학" }
        val refreshedEnglishItem = refreshedItems.first { it["subjectName"].asText() == "영어" }

        val checkMathResponse = mockMvc.patch("/api/v1/plans/today/items/${refreshedMathItem["planItemId"].asLong()}") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"completed":true}"""
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val checkMathJson = objectMapper.readTree(checkMathResponse)
        assertThat(checkMathJson["data"]["completed"].asBoolean()).isTrue()
        assertThat(checkMathJson["data"]["sproutAwarded"].asInt()).isEqualTo(1)
        assertThat(checkMathJson["data"]["completedCount"].asInt()).isEqualTo(1)

        val uncheckMathResponse = mockMvc.patch("/api/v1/plans/today/items/${refreshedMathItem["planItemId"].asLong()}") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"completed":false}"""
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val uncheckMathJson = objectMapper.readTree(uncheckMathResponse)
        assertThat(uncheckMathJson["data"]["completed"].asBoolean()).isFalse()
        assertThat(uncheckMathJson["data"]["sproutAwarded"].asInt()).isZero()
        assertThat(uncheckMathJson["data"]["completedCount"].asInt()).isZero()

        mockMvc.patch("/api/v1/plans/today/items/${refreshedMathItem["planItemId"].asLong()}") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"completed":true}"""
        }.andExpect {
            status { isOk() }
        }
        mockMvc.patch("/api/v1/plans/today/items/${refreshedEnglishItem["planItemId"].asLong()}") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"completed":true}"""
        }.andExpect {
            status { isOk() }
        }

        val completeResponse = mockMvc.post("/api/v1/plans/today/complete") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val completeJson = objectMapper.readTree(completeResponse)
        assertThat(completeJson["data"]["planId"].asLong()).isEqualTo(planId)
        assertThat(completeJson["data"]["completed"].asBoolean()).isTrue()
        assertThat(completeJson["data"]["attendanceRecorded"].asBoolean()).isTrue()
        assertThat(completeJson["data"]["streakDays"].asInt()).isEqualTo(1)

        val savedPlan = dailyPlanRepository.findById(planId).orElseThrow()
        val savedItems = dailyPlanItemRepository.findAllByDailyPlanId(planId)
        assertThat(savedPlan.status.name).isEqualTo("COMPLETED")
        assertThat(savedItems.all { it.status.name == "COMPLETED" }).isTrue()
    }

    @Test
    fun `cannot complete today plan while pending items remain`() {
        val authToken = createGeneratedTodayPlan("today-plan-incomplete@example.com")

        val response = mockMvc.post("/api/v1/plans/today/complete") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isConflict() }
        }.andReturn().response.contentAsString

        val responseJson = objectMapper.readTree(response)
        assertThat(responseJson["error"]["code"].asText()).isEqualTo("PLAN_NOT_FINISHED")
    }

    private fun createGeneratedTodayPlan(email: String): String {
        val authToken = signupAndGetAccessToken(email)
        upsertStudyProfile(authToken)
        upsertActivePlan(authToken)
        replaceSubjectScopes(authToken)
        generateTodayPlan(authToken)
        return authToken
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
                  "examDate": "${LocalDate.now().plusDays(30)}"
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

    private fun generateTodayPlan(authToken: String) {
        mockMvc.post("/api/v1/plan-generation-jobs") {
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
        }
    }

    private fun JsonNode.first(predicate: (JsonNode) -> Boolean): JsonNode =
        this.firstOrNull(predicate) ?: throw NoSuchElementException("matching node not found")

    private fun JsonNode.firstOrNull(predicate: (JsonNode) -> Boolean): JsonNode? =
        this.elements().asSequence().firstOrNull(predicate)
}
