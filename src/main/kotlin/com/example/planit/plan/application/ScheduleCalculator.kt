package com.example.planit.plan.application

import com.example.planit.common.api.CommonApiException
import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.plan.api.PlanGenerationJobCreateRequest
import com.example.planit.plan.application.PlanGenerationSupport.normalize
import com.example.planit.plan.application.PlanGenerationSupport.parseDifficulty
import com.example.planit.plan.application.PlanGenerationSupport.priorityOf
import com.example.planit.plan.application.PlanGenerationSupport.unitLabel
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

@Component
class ScheduleCalculator {

    fun calculate(
        request: PlanGenerationJobCreateRequest,
        activePlan: ExamPlan,
        availableScopes: List<SubjectScope>,
    ): PlanSchedule {
        val today = LocalDate.now()
        val studyDays = ChronoUnit.DAYS.between(today, activePlan.examDate).toInt()
        if (studyDays <= 0) {
            throw CommonApiException(HttpStatus.CONFLICT, "EXAM_DATE_PASSED", "시험일까지 학습 가능한 날이 없습니다.")
        }

        val scopesBySubject = availableScopes
            .filter { it.examPlan.id == activePlan.id }
            .filter { it.remainingUnits > 0 }
            .sortedBy { it.id }
            .groupBy { normalize(it.subjectName) }
            .mapValues { (_, scopes) -> scopes.toMutableList() }

        val capMinutes = request.dailyMaxStudyHours * 60

        // dayIndex -> mutable list of items (numbers only; priority/order default here)
        val itemsByDay = sortedMapOf<Int, MutableList<PlanScheduleItem>>()

        request.subjects.forEach { subject ->
            val candidates = scopesBySubject[normalize(subject.subjectName)]
            if (candidates.isNullOrEmpty()) {
                throw CommonApiException(
                    HttpStatus.CONFLICT,
                    "SUBJECT_SCOPE_NOT_FOUND",
                    "${subject.subjectName.trim()} 과목 범위를 먼저 입력해주세요.",
                )
            }
            val scope = candidates.removeAt(0)
            val difficulty = parseDifficulty(subject.difficulty)
            val remaining = scope.remainingUnits
            val base = remaining / studyDays
            val remainder = remaining % studyDays
            var cumulative = 0

            for (dayIndex in 0 until studyDays) {
                val units = base + if (dayIndex < remainder) 1 else 0
                if (units == 0) continue
                val start = cumulative + 1
                val end = cumulative + units
                cumulative = end
                val label = unitLabel(scope.unitType)
                val item = PlanScheduleItem(
                    scope = scope,
                    subjectName = subject.subjectName.trim(),
                    rangeText = "$start~$end$label",
                    studyMethod = subject.preferredMethodNote.trim(),
                    priority = priorityOf(difficulty),
                    plannedUnits = units,
                    estimatedMinutes = 0, // filled below
                    startUnit = start,
                    endUnit = end,
                    displayOrder = 0, // filled below
                )
                itemsByDay.getOrPut(dayIndex) { mutableListOf() }.add(item)
            }
        }

        val days = itemsByDay.map { (dayIndex, rawItems) ->
            val dayTotalUnits = rawItems.sumOf { it.plannedUnits }
            var allocated = 0
            val items = rawItems.mapIndexed { index, item ->
                val minutes = if (index == rawItems.lastIndex) {
                    max(1, capMinutes - allocated)
                } else {
                    max(1, capMinutes * item.plannedUnits / dayTotalUnits)
                }
                allocated += minutes
                item.copy(estimatedMinutes = minutes, displayOrder = index)
            }
            PlanScheduleDay(planDate = today.plusDays(dayIndex.toLong()), items = items)
        }

        return PlanSchedule(days = days)
    }
}
