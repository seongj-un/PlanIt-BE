package com.example.planit.user.api

data class UserProfileResponse(
    val id: Long,
    val name: String,
    val email: String,
    val age: Int?,
    val schoolLevel: String?,
    val usualStudyHoursPerDay: Int?,
    val preferredStudyMethod: String?,
    val onboardingCompleted: Boolean,
)
