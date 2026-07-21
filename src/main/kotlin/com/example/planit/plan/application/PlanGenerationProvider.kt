package com.example.planit.plan.application

import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.plan.api.PlanGenerationJobCreateRequest
import com.example.planit.user.domain.StudyProfile

interface PlanGenerationProvider {
    fun generate(
        request: PlanGenerationJobCreateRequest,
        activePlan: ExamPlan,
        availableScopes: List<SubjectScope>,
        studyProfile: StudyProfile?,
    ): PlanSchedule
}
