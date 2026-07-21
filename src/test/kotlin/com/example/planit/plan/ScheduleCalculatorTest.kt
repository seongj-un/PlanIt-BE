package com.example.planit.plan

import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.exam.domain.ScopeUnitType
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.exam.domain.TargetExamType
import com.example.planit.plan.api.PlanGenerationJobCreateRequest
import com.example.planit.plan.api.PlanGenerationSubjectRequest
import com.example.planit.plan.application.ScheduleCalculator
import com.example.planit.user.domain.UserAccount
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ScheduleCalculatorTest {

    private val calculator = ScheduleCalculator()
    private val today: LocalDate = LocalDate.now()

    @Test
    fun `splits scope evenly across the period and tracks unit ranges`() {
        val exam = examPlan(today.plusDays(20))
        val scope = subjectScope(exam, "수학", ScopeUnitType.PAGE, total = 100, remaining = 100)
        val request = request(subject("수학", "HIGH"), dailyMaxStudyHours = 5)

        val schedule = calculator.calculate(request, exam, listOf(scope))

        assertThat(schedule.days).hasSize(20)
        val firstItem = schedule.days.first().items.single()
        assertThat(schedule.days.first().planDate).isEqualTo(today)
        assertThat(firstItem.plannedUnits).isEqualTo(5)
        assertThat(firstItem.startUnit).isEqualTo(1)
        assertThat(firstItem.endUnit).isEqualTo(5)
        assertThat(firstItem.rangeText).isEqualTo("1~5쪽")
        assertThat(schedule.days.last().items.single().endUnit).isEqualTo(100)
        assertThat(schedule.days.sumOf { day -> day.items.sumOf { it.plannedUnits } }).isEqualTo(100)
    }

    @Test
    fun `distributes remainder to the earliest days`() {
        val exam = examPlan(today.plusDays(3))
        val scope = subjectScope(exam, "수학", ScopeUnitType.PAGE, total = 10, remaining = 10)
        val request = request(subject("수학", "MEDIUM"), dailyMaxStudyHours = 4)

        val schedule = calculator.calculate(request, exam, listOf(scope))

        assertThat(schedule.days.map { it.items.single().plannedUnits }).containsExactly(4, 3, 3)
    }

    @Test
    fun `omits empty trailing days when units are fewer than days`() {
        val exam = examPlan(today.plusDays(30))
        val scope = subjectScope(exam, "영어", ScopeUnitType.QUESTION, total = 10, remaining = 10)
        val request = request(subject("영어", "LOW"), dailyMaxStudyHours = 2)

        val schedule = calculator.calculate(request, exam, listOf(scope))

        assertThat(schedule.days).hasSize(10)
        assertThat(schedule.days.all { it.items.single().plannedUnits == 1 }).isTrue()
    }

    @Test
    fun `caps daily minutes at daily max study hours`() {
        val exam = examPlan(today.plusDays(10))
        val scopes = listOf(
            subjectScope(examPlan(today.plusDays(10)), "수학", ScopeUnitType.PAGE, 50, 50),
            subjectScope(examPlan(today.plusDays(10)), "영어", ScopeUnitType.QUESTION, 50, 50),
        )
        val request = request(subject("수학", "HIGH"), subject("영어", "MEDIUM"), dailyMaxStudyHours = 3)

        val schedule = calculator.calculate(request, exam, scopes)

        assertThat(schedule.days.first().items.sumOf { it.estimatedMinutes }).isLessThanOrEqualTo(180)
    }

    @Test
    fun `throws when exam date is not in the future`() {
        val exam = examPlan(today)
        val scope = subjectScope(exam, "수학", ScopeUnitType.PAGE, 10, 10)
        val request = request(subject("수학", "HIGH"), dailyMaxStudyHours = 4)

        assertThatThrownBy { calculator.calculate(request, exam, listOf(scope)) }
            .hasMessageContaining("시험")
    }

    @Test
    fun `throws when a requested subject has no scope`() {
        val exam = examPlan(today.plusDays(10))
        val request = request(subject("과학", "HIGH"), dailyMaxStudyHours = 4)

        assertThatThrownBy { calculator.calculate(request, exam, emptyList()) }
            .hasMessageContaining("범위")
    }

    private fun examPlan(examDate: LocalDate): ExamPlan =
        ExamPlan(
            user = UserAccount(name = "학생", email = "s@e.com", passwordHash = "x"),
            targetExamType = TargetExamType.CSAT,
            targetExamLabel = "수능",
            examDate = examDate,
            status = ExamPlanStatus.ACTIVE,
        )

    private fun subjectScope(
        exam: ExamPlan,
        name: String,
        unitType: ScopeUnitType,
        total: Int,
        remaining: Int,
    ): SubjectScope =
        SubjectScope(
            examPlan = exam,
            subjectName = name,
            rawRangeText = "$name 범위",
            preferredMethodNote = "개념 정리",
            difficulty = DifficultyLevel.MEDIUM,
            unitType = unitType,
            totalUnits = total,
            remainingUnits = remaining,
        )

    private fun subject(name: String, difficulty: String): PlanGenerationSubjectRequest =
        PlanGenerationSubjectRequest(
            subjectName = name,
            examRange = "$name 범위",
            preferredMethodNote = "개념 정리",
            difficulty = difficulty,
        )

    private fun request(vararg subjects: PlanGenerationSubjectRequest, dailyMaxStudyHours: Int): PlanGenerationJobCreateRequest =
        PlanGenerationJobCreateRequest(
            subjects = subjects.toList(),
            preferredStudyMethod = "BALANCED",
            difficultSubjects = emptyList(),
            dailyMaxStudyHours = dailyMaxStudyHours,
        )
}
