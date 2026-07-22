package com.example.planit.plan

import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.exam.domain.ScopeUnitType
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.exam.domain.TargetExamType
import com.example.planit.plan.application.ArrangementContext
import com.example.planit.plan.application.ArrangementSubject
import com.example.planit.plan.application.PlanSchedule
import com.example.planit.plan.application.PlanScheduleDay
import com.example.planit.plan.application.PlanScheduleItem
import com.example.planit.plan.application.RuleBasedArrangementProvider
import com.example.planit.plan.domain.PlanItemPriority
import com.example.planit.user.domain.UserAccount
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RuleBasedArrangementProviderTest {

    private val provider = RuleBasedArrangementProvider()

    @Test
    fun `orders difficult and harder subjects first and reassigns order`() {
        val schedule = PlanSchedule(
            days = listOf(
                PlanScheduleDay(
                    planDate = LocalDate.now(),
                    items = listOf(
                        item("영어", PlanItemPriority.MEDIUM, order = 0),
                        item("수학", PlanItemPriority.LOW, order = 1),
                    ),
                ),
            ),
        )
        val context = ArrangementContext(
            preferredStudyMethod = "BALANCED",
            subjects = listOf(
                ArrangementSubject("영어", DifficultyLevel.MEDIUM, isDifficult = false),
                ArrangementSubject("수학", DifficultyLevel.HIGH, isDifficult = true),
            ),
        )

        val result = provider.arrange(schedule, context)

        val ordered = result.days.single().items.sortedBy { it.displayOrder }
        assertThat(ordered.map { it.subjectName }).containsExactly("수학", "영어")
        assertThat(ordered[0].displayOrder).isEqualTo(0)
        assertThat(ordered[1].displayOrder).isEqualTo(1)
        assertThat(ordered[0].priority).isEqualTo(PlanItemPriority.HIGH)
    }

    @Test
    fun `keeps numbers unchanged`() {
        val original = item("수학", PlanItemPriority.LOW, order = 0, units = 5, minutes = 120, start = 1, end = 5)
        val schedule = PlanSchedule(days = listOf(PlanScheduleDay(LocalDate.now(), listOf(original))))
        val context = ArrangementContext(
            preferredStudyMethod = "BALANCED",
            subjects = listOf(ArrangementSubject("수학", DifficultyLevel.HIGH, isDifficult = false)),
        )

        val result = provider.arrange(schedule, context).days.single().items.single()

        assertThat(result.plannedUnits).isEqualTo(5)
        assertThat(result.estimatedMinutes).isEqualTo(120)
        assertThat(result.startUnit).isEqualTo(1)
        assertThat(result.endUnit).isEqualTo(5)
        assertThat(result.rangeText).isEqualTo(original.rangeText)
    }

    private fun item(
        name: String,
        priority: PlanItemPriority,
        order: Int,
        units: Int = 1,
        minutes: Int = 60,
        start: Int = 1,
        end: Int = 1,
    ): PlanScheduleItem =
        PlanScheduleItem(
            scope = dummyScope(name),
            subjectName = name,
            rangeText = "$start~${end}쪽",
            studyMethod = "개념 정리",
            priority = priority,
            plannedUnits = units,
            estimatedMinutes = minutes,
            startUnit = start,
            endUnit = end,
            displayOrder = order,
        )

    private fun dummyScope(name: String): SubjectScope =
        SubjectScope(
            examPlan = ExamPlan(
                user = UserAccount(name = "학생", email = "s@e.com", passwordHash = "x"),
                targetExamType = TargetExamType.CSAT,
                targetExamLabel = "수능",
                examDate = LocalDate.now().plusDays(10),
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
