package com.example.planit.plan

import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.ExamPlanRepository
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.exam.domain.ScopeUnitType
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.exam.domain.SubjectScopeRepository
import com.example.planit.exam.domain.TargetExamType
import com.example.planit.plan.domain.CompletionEvent
import com.example.planit.plan.domain.CompletionEventRepository
import com.example.planit.plan.domain.CompletionEventType
import com.example.planit.plan.domain.DailyPlan
import com.example.planit.plan.domain.DailyPlanItem
import com.example.planit.plan.domain.DailyPlanItemRepository
import com.example.planit.plan.domain.DailyPlanRepository
import com.example.planit.plan.domain.DailyPlanStatus
import com.example.planit.plan.domain.PlanItemPriority
import com.example.planit.plan.domain.PlanItemStatus
import com.example.planit.user.domain.UserAccount
import com.example.planit.user.domain.UserAccountRepository
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
import java.time.LocalDate
import java.time.YearMonth

@SpringBootTest
@AutoConfigureMockMvc
class DashboardAndHistoryIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userAccountRepository: UserAccountRepository,
    @Autowired private val examPlanRepository: ExamPlanRepository,
    @Autowired private val subjectScopeRepository: SubjectScopeRepository,
    @Autowired private val dailyPlanRepository: DailyPlanRepository,
    @Autowired private val dailyPlanItemRepository: DailyPlanItemRepository,
    @Autowired private val completionEventRepository: CompletionEventRepository,
) {

    @Test
    fun `dashboard returns exam today plan rewards and attendance summary`() {
        val email = "dashboard@example.com"
        val authToken = signupAndGetAccessToken(email)
        val user = userAccountRepository.findByEmail(email).orElseThrow()
        seedDashboardAndHistoryData(user)

        val response = mockMvc.get("/api/v1/dashboard") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val json = objectMapper.readTree(response)
        assertThat(json["data"]["userName"].asText()).isEqualTo("김민지")
        assertThat(json["data"]["nextExam"]["label"].asText()).isEqualTo("수능")
        assertThat(json["data"]["todayPlan"]["completedCount"].asInt()).isEqualTo(2)
        assertThat(json["data"]["todayPlan"]["totalCount"].asInt()).isEqualTo(2)
        assertThat(json["data"]["todayPlan"]["progressPercent"].asInt()).isEqualTo(100)
        assertThat(json["data"]["todayPlan"]["items"].size()).isEqualTo(2)
        assertThat(json["data"]["rewards"]["sproutCount"].asInt()).isEqualTo(4)
        assertThat(json["data"]["rewards"]["earnedToday"].asInt()).isEqualTo(2)
        assertThat(json["data"]["attendance"]["streakDays"].asInt()).isEqualTo(2)
        assertThat(json["data"]["attendance"]["calendar"].size()).isGreaterThanOrEqualTo(3)
    }

    @Test
    fun `history returns monthly summary and daily detail`() {
        val email = "history@example.com"
        val authToken = signupAndGetAccessToken(email)
        val user = userAccountRepository.findByEmail(email).orElseThrow()
        seedDashboardAndHistoryData(user)

        val month = YearMonth.now().toString()
        val monthResponse = mockMvc.get("/api/v1/plans/history") {
            header("Authorization", "Bearer $authToken")
            param("month", month)
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val monthJson = objectMapper.readTree(monthResponse)
        assertThat(monthJson["data"]["month"].asText()).isEqualTo(month)
        assertThat(monthJson["data"]["stats"]["completedPlans"].asInt()).isEqualTo(2)
        assertThat(monthJson["data"]["stats"]["incompletePlans"].asInt()).isEqualTo(1)
        assertThat(monthJson["data"]["days"].size()).isEqualTo(3)

        val today = LocalDate.now().toString()
        val detailResponse = mockMvc.get("/api/v1/plans/history/$today") {
            header("Authorization", "Bearer $authToken")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val detailJson = objectMapper.readTree(detailResponse)
        assertThat(detailJson["data"]["date"].asText()).isEqualTo(today)
        assertThat(detailJson["data"]["completedCount"].asInt()).isEqualTo(2)
        assertThat(detailJson["data"]["totalCount"].asInt()).isEqualTo(2)
        assertThat(detailJson["data"]["items"].size()).isEqualTo(2)
        assertThat(detailJson["data"]["items"][0]["completed"].asBoolean()).isTrue()
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

    private fun seedDashboardAndHistoryData(user: UserAccount) {
        val examPlan = examPlanRepository.save(
            ExamPlan(
                user = user,
                targetExamType = TargetExamType.CSAT,
                targetExamLabel = "수능",
                examDate = LocalDate.now().plusDays(5),
                status = ExamPlanStatus.ACTIVE,
            ),
        )

        val mathScope = subjectScopeRepository.save(
            SubjectScope(
                examPlan = examPlan,
                subjectName = "수학",
                rawRangeText = "수열과 극한 1~3단원",
                preferredMethodNote = "개념 정리 후 대표 문제 15문제",
                difficulty = DifficultyLevel.HIGH,
                unitType = ScopeUnitType.CHAPTER,
                totalUnits = 3,
                remainingUnits = 3,
            ),
        )
        val englishScope = subjectScopeRepository.save(
            SubjectScope(
                examPlan = examPlan,
                subjectName = "영어",
                rawRangeText = "빈칸 추론 10문제",
                preferredMethodNote = "근거 문장 표시",
                difficulty = DifficultyLevel.MEDIUM,
                unitType = ScopeUnitType.QUESTION,
                totalUnits = 10,
                remainingUnits = 10,
            ),
        )
        val koreanScope = subjectScopeRepository.save(
            SubjectScope(
                examPlan = examPlan,
                subjectName = "국어",
                rawRangeText = "독서 지문 5개",
                preferredMethodNote = "근거 표시",
                difficulty = DifficultyLevel.MEDIUM,
                unitType = ScopeUnitType.PASSAGE,
                totalUnits = 5,
                remainingUnits = 5,
            ),
        )

        val todayPlan = dailyPlanRepository.save(
            DailyPlan(
                examPlan = examPlan,
                planDate = LocalDate.now(),
                status = DailyPlanStatus.COMPLETED,
            ),
        )
        val yesterdayPlan = dailyPlanRepository.save(
            DailyPlan(
                examPlan = examPlan,
                planDate = LocalDate.now().minusDays(1),
                status = DailyPlanStatus.COMPLETED,
            ),
        )
        val twoDaysAgoPlan = dailyPlanRepository.save(
            DailyPlan(
                examPlan = examPlan,
                planDate = LocalDate.now().minusDays(2),
                status = DailyPlanStatus.PENDING,
            ),
        )

        val todayMath = savePlanItem(
            todayPlan,
            mathScope,
            "수학",
            "수열과 극한 2단원",
            "오답 정리 포함",
            PlanItemPriority.HIGH,
            PlanItemStatus.COMPLETED,
            90,
        )
        val todayEnglish = savePlanItem(
            todayPlan,
            englishScope,
            "영어",
            "빈칸 추론 5문제",
            "근거 문장 밑줄 표시",
            PlanItemPriority.MEDIUM,
            PlanItemStatus.COMPLETED,
            60,
        )
        val yesterdayKorean = savePlanItem(
            yesterdayPlan,
            koreanScope,
            "국어",
            "독서 지문 2개",
            "근거 표시",
            PlanItemPriority.MEDIUM,
            PlanItemStatus.COMPLETED,
            50,
        )
        val twoDaysAgoMath = savePlanItem(
            twoDaysAgoPlan,
            mathScope,
            "수학",
            "수열과 극한 1단원",
            "개념 복습",
            PlanItemPriority.HIGH,
            PlanItemStatus.COMPLETED,
            70,
        )
        savePlanItem(
            twoDaysAgoPlan,
            englishScope,
            "영어",
            "순서 배열 5문제",
            "근거 표시",
            PlanItemPriority.MEDIUM,
            PlanItemStatus.PENDING,
            45,
        )

        completionEventRepository.save(
            CompletionEvent(
                dailyPlanItem = todayMath,
                eventType = CompletionEventType.ITEM_CHECKED,
                sproutDelta = 1,
            ),
        )
        completionEventRepository.save(
            CompletionEvent(
                dailyPlanItem = todayEnglish,
                eventType = CompletionEventType.ITEM_CHECKED,
                sproutDelta = 1,
            ),
        )
        completionEventRepository.save(
            CompletionEvent(
                dailyPlanItem = yesterdayKorean,
                eventType = CompletionEventType.ITEM_CHECKED,
                sproutDelta = 1,
            ),
        )
        completionEventRepository.save(
            CompletionEvent(
                dailyPlanItem = twoDaysAgoMath,
                eventType = CompletionEventType.ITEM_CHECKED,
                sproutDelta = 1,
            ),
        )
    }

    private fun savePlanItem(
        dailyPlan: DailyPlan,
        subjectScope: SubjectScope,
        subjectName: String,
        rangeText: String,
        studyMethod: String,
        priority: PlanItemPriority,
        status: PlanItemStatus,
        estimatedMinutes: Int,
    ): DailyPlanItem =
        dailyPlanItemRepository.save(
            DailyPlanItem(
                dailyPlan = dailyPlan,
                subjectScope = subjectScope,
                subjectNameSnapshot = subjectName,
                rangeTextSnapshot = rangeText,
                studyMethodSnapshot = studyMethod,
                priority = priority,
                status = status,
                plannedUnits = 1,
                estimatedMinutes = estimatedMinutes,
                manuallyAdjusted = false,
            ),
        )
}
