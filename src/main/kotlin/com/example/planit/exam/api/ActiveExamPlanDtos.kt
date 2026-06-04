package com.example.planit.exam.api

import java.time.LocalDate

data class ActiveExamPlanRequest(
    val targetExamType: String,
    val targetExamLabel: String,
    val examDate: LocalDate,
)

data class ActiveExamPlanResponse(
    val id: Long,
    val targetExamType: String,
    val targetExamLabel: String,
    val examDate: LocalDate,
    val status: String,
)

data class SubjectScopeRequest(
    val subjectName: String,
    val rawRangeText: String,
    val preferredMethodNote: String,
    val difficulty: String,
    val unitType: String,
    val totalUnits: Int,
    val remainingUnits: Int,
)

data class SubjectScopesReplaceRequest(
    val scopes: List<SubjectScopeRequest>,
)

data class SubjectScopeResponse(
    val id: Long,
    val subjectName: String,
    val rawRangeText: String,
    val preferredMethodNote: String,
    val difficulty: String,
    val unitType: String,
    val totalUnits: Int,
    val remainingUnits: Int,
)
