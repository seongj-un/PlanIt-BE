package com.example.planit.plan

import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.exam.domain.ScopeUnitType
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.exam.domain.TargetExamType
import com.example.planit.plan.application.AiArrangementProvider
import com.example.planit.plan.application.AiClient
import com.example.planit.plan.application.ArrangementContext
import com.example.planit.plan.application.ArrangementSubject
import com.example.planit.plan.application.PlanSchedule
import com.example.planit.plan.application.PlanScheduleDay
import com.example.planit.plan.application.PlanScheduleItem
import com.example.planit.plan.application.RuleBasedArrangementProvider
import com.example.planit.plan.domain.PlanItemPriority
import com.example.planit.user.domain.UserAccount
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AiArrangementProviderTest {

    private val objectMapper = ObjectMapper()
    private val fallback = RuleBasedArrangementProvider()
    private val today: LocalDate = LocalDate.now()

    @Test
    fun `applies AI order and priority without changing numbers`() {
        val client = AiClient {
            """
            { "days": [ { "date": "$today", "order": ["수학", "영어"],
              "priorities": { "수학": "HIGH", "영어": "LOW" } } ] }
            """.trimIndent()
        }
        val provider = AiArrangementProvider(client, fallback, objectMapper)

        val result = provider.arrange(twoSubjectSchedule(), context())

        val items = result.days.single().items.sortedBy { it.displayOrder }
        assertThat(items.map { it.subjectName }).containsExactly("수학", "영어")
        assertThat(items[0].priority).isEqualTo(PlanItemPriority.HIGH)
        assertThat(items.first { it.subjectName == "수학" }.plannedUnits).isEqualTo(5)
        assertThat(items.first { it.subjectName == "영어" }.plannedUnits).isEqualTo(10)
    }

    @Test
    fun `falls back to rule-based on invalid json`() {
        val client = AiClient { "not json at all" }
        val provider = AiArrangementProvider(client, fallback, objectMapper)

        val result = provider.arrange(twoSubjectSchedule(), context())

        // rule-based orders difficult 수학(HIGH, isDifficult) first
        assertThat(result.days.single().items.sortedBy { it.displayOrder }.map { it.subjectName })
            .containsExactly("수학", "영어")
    }

    @Test
    fun `falls back when order is not a permutation of the day's subjects`() {
        val client = AiClient {
            """{ "days": [ { "date": "$today", "order": ["수학"], "priorities": {} } ] }"""
        }
        val provider = AiArrangementProvider(client, fallback, objectMapper)

        val result = provider.arrange(twoSubjectSchedule(), context())

        assertThat(result.days.single().items).hasSize(2)
    }

    @Test
    fun `falls back when client throws`() {
        val client = AiClient { throw RuntimeException("timeout") }
        val provider = AiArrangementProvider(client, fallback, objectMapper)

        val result = provider.arrange(twoSubjectSchedule(), context())

        assertThat(result.days.single().items).hasSize(2)
    }

    private fun twoSubjectSchedule(): PlanSchedule =
        PlanSchedule(
            days = listOf(
                PlanScheduleDay(
                    planDate = today,
                    items = listOf(
                        item("영어", 10, order = 0),
                        item("수학", 5, order = 1),
                    ),
                ),
            ),
        )

    private fun context(): ArrangementContext =
        ArrangementContext(
            preferredStudyMethod = "BALANCED",
            subjects = listOf(
                ArrangementSubject("수학", DifficultyLevel.HIGH, isDifficult = true),
                ArrangementSubject("영어", DifficultyLevel.MEDIUM, isDifficult = false),
            ),
        )

    private fun item(name: String, units: Int, order: Int): PlanScheduleItem =
        PlanScheduleItem(
            scope = dummyScope(name),
            subjectName = name,
            rangeText = "1~$units",
            studyMethod = "개념 정리",
            priority = PlanItemPriority.MEDIUM,
            plannedUnits = units,
            estimatedMinutes = 60,
            startUnit = 1,
            endUnit = units,
            displayOrder = order,
        )

    private fun dummyScope(name: String): SubjectScope =
        SubjectScope(
            examPlan = ExamPlan(
                user = UserAccount(name = "학생", email = "s@e.com", passwordHash = "x"),
                targetExamType = TargetExamType.CSAT,
                targetExamLabel = "수능",
                examDate = today.plusDays(10),
                status = ExamPlanStatus.ACTIVE,
            ),
            subjectName = name,
            rawRangeText = "$name 범위",
            preferredMethodNote = "개념 정리",
            difficulty = DifficultyLevel.MEDIUM,
            unitType = ScopeUnitType.PAGE,
            totalUnits = 10,
            remainingUnits = 10,
        )
}
