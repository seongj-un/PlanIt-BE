package com.example.planit.user.api

data class StudySettingsRequest(
    val usualStudyHoursPerDay: Int,
    val preferredStudyMethod: String,
)

data class NotificationSettingsRequest(
    val dailyReminderEnabled: Boolean,
    val dailyReminderTime: String?,
)

data class AccountSettingsRequest(
    val name: String?,
    val password: String?,
)

data class UpdatedResponse(
    val updated: Boolean,
)
