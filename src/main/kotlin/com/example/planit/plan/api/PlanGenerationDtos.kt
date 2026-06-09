package com.example.planit.plan.api

import java.time.LocalDate

data class PlanGenerationJobCreateRequest(
    val subjects: List<PlanGenerationSubjectRequest>,
    val preferredStudyMethod: String,
    val difficultSubjects: List<String>,
    val dailyMaxStudyHours: Int,
)

data class PlanGenerationSubjectRequest(
    val subjectName: String,
    val examRange: String,
    val preferredMethodNote: String,
    val difficulty: String,
)

data class PlanGenerationJobCreateResponse(
    val jobId: String,
    val status: String,
    val estimatedSeconds: Int,
)

data class PlanGenerationJobStatusResponse(
    val jobId: String,
    val status: String,
    val progressPercent: Int,
    val message: String,
    val planDate: LocalDate? = null,
    val planId: Long? = null,
    val dashboardAvailable: Boolean = false,
)
