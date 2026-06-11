package com.example.planit.plan.application

import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.plan.api.PlanGenerationJobCreateRequest
import com.example.planit.plan.domain.PlanItemPriority
import java.time.LocalDate

interface PlanGenerationProvider {
    fun generate(
        request: PlanGenerationJobCreateRequest,
        activePlan: ExamPlan,
        availableScopes: List<SubjectScope>,
    ): GeneratedPlanDraft
}

data class GeneratedPlanDraft(
    val planDate: LocalDate,
    val items: List<GeneratedPlanDraftItem>,
)

data class GeneratedPlanDraftItem(
    val scope: SubjectScope,
    val subjectName: String,
    val examRange: String,
    val studyMethod: String,
    val priority: PlanItemPriority,
    val plannedUnits: Int,
    val estimatedMinutes: Int,
)
