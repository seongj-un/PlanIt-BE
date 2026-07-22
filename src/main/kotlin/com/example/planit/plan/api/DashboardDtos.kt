package com.example.planit.plan.api

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

data class DashboardResponse(
    val userName: String,
    val nextExam: DashboardNextExamResponse,
    val todayPlan: DashboardTodayPlanResponse,
    val rewards: DashboardRewardsResponse,
    val attendance: DashboardAttendanceResponse,
)

data class DashboardNextExamResponse(
    val label: String,
    val date: LocalDate,
    // Force the JSON key to "dDay" (api-spec); Jackson would otherwise emit "dday".
    @get:JsonProperty("dDay")
    val dDay: Long,
)

data class DashboardTodayPlanResponse(
    val planDate: LocalDate,
    val completedCount: Int,
    val totalCount: Int,
    val progressPercent: Int,
    val items: List<DashboardTodayPlanItemResponse>,
)

data class DashboardTodayPlanItemResponse(
    val planItemId: Long,
    val subjectName: String,
    val scopeSummary: String,
    val completed: Boolean,
)

data class DashboardRewardsResponse(
    val sproutCount: Int,
    val earnedToday: Int,
)

data class DashboardAttendanceResponse(
    val streakDays: Int,
    val calendar: List<DashboardAttendanceDayResponse>,
)

data class DashboardAttendanceDayResponse(
    val date: LocalDate,
    val completed: Boolean,
)
