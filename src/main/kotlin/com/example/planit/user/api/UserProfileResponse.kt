package com.example.planit.user.api

import java.time.LocalDate

data class UserProfileResponse(
    val id: Long,
    val name: String,
    val email: String,
    val targetExamType: String?,
    val targetExamLabel: String?,
    val examDate: LocalDate?,
    val usualStudyHoursPerDay: Int?,
    val preferredStudyMethod: String?,
    val sproutCount: Int,
    val attendanceStreakDays: Int,
    val onboardingCompleted: Boolean,
)
