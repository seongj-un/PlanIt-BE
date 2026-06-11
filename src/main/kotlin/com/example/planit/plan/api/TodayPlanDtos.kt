package com.example.planit.plan.api

import java.time.LocalDate

data class TodayPlanResponse(
    val planId: Long,
    val planDate: LocalDate,
    val status: String,
    val items: List<TodayPlanItemResponse>,
)

data class TodayPlanItemResponse(
    val planItemId: Long,
    val subjectName: String,
    val examRange: String,
    val studyMethod: String,
    val priority: String,
    val status: String,
    val estimatedMinutes: Int,
)

data class TodayPlanUpdateRequest(
    val items: List<TodayPlanUpdateItemRequest>,
    val deletedPlanItemIds: List<Long> = emptyList(),
)

data class TodayPlanUpdateItemRequest(
    val planItemId: Long? = null,
    val subjectName: String,
    val examRange: String,
    val studyMethod: String,
    val priority: String,
)

data class TodayPlanUpdateResponse(
    val planId: Long,
    val updated: Boolean,
    val itemCount: Int,
)

data class TodayPlanItemToggleRequest(
    val completed: Boolean,
)

data class TodayPlanItemToggleResponse(
    val planItemId: Long,
    val completed: Boolean,
    val completedCount: Int,
    val totalCount: Int,
    val sproutAwarded: Int,
)

data class TodayPlanCompleteResponse(
    val planId: Long,
    val completed: Boolean,
    val attendanceRecorded: Boolean,
    val streakDays: Int,
)

data class TodayPlanProgressResponse(
    val completedCount: Int,
    val totalCount: Int,
    val completedItems: List<TodayPlanProgressItemResponse>,
    val remainingItems: List<TodayPlanProgressItemResponse>,
    val sproutCount: Int,
    val sproutPerPlanItem: Int,
)

data class TodayPlanProgressItemResponse(
    val planItemId: Long,
    val subjectName: String,
    val label: String,
)
