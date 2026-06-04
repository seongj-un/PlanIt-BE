package com.example.planit.user.api

data class StudyProfileRequest(
    val age: Int,
    val schoolLevel: String,
    val usualStudyHoursPerDay: Int,
    val preferredStudyMethod: String,
)
