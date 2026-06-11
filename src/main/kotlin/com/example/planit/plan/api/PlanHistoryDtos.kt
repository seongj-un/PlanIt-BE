package com.example.planit.plan.api

data class MonthlyPlanHistoryResponse(
    val month: String,
    val stats: MonthlyPlanHistoryStatsResponse,
    val days: List<MonthlyPlanHistoryDayResponse>,
)

data class MonthlyPlanHistoryStatsResponse(
    val completedPlans: Int,
    val incompletePlans: Int,
)

data class MonthlyPlanHistoryDayResponse(
    val date: String,
    val completedCount: Int,
    val totalCount: Int,
    val completed: Boolean,
    val subjects: List<String>,
)

data class DailyPlanHistoryResponse(
    val date: String,
    val completedCount: Int,
    val totalCount: Int,
    val items: List<DailyPlanHistoryItemResponse>,
)

data class DailyPlanHistoryItemResponse(
    val subjectName: String,
    val examRange: String,
    val studyMethod: String,
    val completed: Boolean,
)
