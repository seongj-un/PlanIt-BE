package com.example.planit.plan.application

import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.plan.domain.PlanItemPriority
import java.time.LocalDate

data class PlanSchedule(
    val days: List<PlanScheduleDay>,
)

data class PlanScheduleDay(
    val planDate: LocalDate,
    val items: List<PlanScheduleItem>,
)

data class PlanScheduleItem(
    val scope: SubjectScope,
    val subjectName: String,
    val rangeText: String,
    val studyMethod: String,
    val priority: PlanItemPriority,
    val plannedUnits: Int,
    val estimatedMinutes: Int,
    val startUnit: Int,
    val endUnit: Int,
    val displayOrder: Int,
)

data class ArrangementSubject(
    val subjectName: String,
    val difficulty: DifficultyLevel,
    val isDifficult: Boolean,
)

data class ArrangementContext(
    val preferredStudyMethod: String,
    val subjects: List<ArrangementSubject>,
)
