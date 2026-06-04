package com.example.planit.user.application

import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.common.api.CommonApiException
import com.example.planit.user.api.StudyProfileRequest
import com.example.planit.user.api.UserProfileResponse
import com.example.planit.user.domain.PreferredStudyMethod
import com.example.planit.user.domain.SchoolLevel
import com.example.planit.user.domain.StudyProfile
import com.example.planit.user.domain.StudyProfileRepository
import com.example.planit.user.domain.UserAccountRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserProfileService(
    private val userAccountRepository: UserAccountRepository,
    private val studyProfileRepository: StudyProfileRepository,
) {

    @Transactional(readOnly = true)
    fun getMyProfile(principal: AuthenticatedUser): UserProfileResponse {
        val user = userAccountRepository.findById(principal.id)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.") }
        val studyProfile = studyProfileRepository.findByUserId(user.id!!)
            .orElse(null)

        return UserProfileResponse(
            id = user.id!!,
            name = user.name,
            email = user.email,
            age = studyProfile?.age,
            schoolLevel = studyProfile?.schoolLevel?.name,
            usualStudyHoursPerDay = studyProfile?.usualStudyHoursPerDay,
            preferredStudyMethod = studyProfile?.preferredStudyMethod?.name,
            onboardingCompleted = user.onboardingCompleted,
        )
    }

    @Transactional
    fun upsertStudyProfile(principal: AuthenticatedUser, request: StudyProfileRequest): UserProfileResponse {
        require(request.age in 1..120) { "나이는 1 이상 120 이하여야 합니다." }
        require(request.usualStudyHoursPerDay in 1..24) { "하루 공부 시간은 1 이상 24 이하여야 합니다." }

        val user = userAccountRepository.findById(principal.id)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.") }
        val schoolLevel = enumValueOf<SchoolLevel>(request.schoolLevel)
        val preferredStudyMethod = enumValueOf<PreferredStudyMethod>(request.preferredStudyMethod)

        val profile = studyProfileRepository.findByUserId(user.id!!)
            .orElseGet {
                StudyProfile(
                    user = user,
                    age = request.age,
                    schoolLevel = schoolLevel,
                    usualStudyHoursPerDay = request.usualStudyHoursPerDay,
                    preferredStudyMethod = preferredStudyMethod,
                )
            }

        profile.age = request.age
        profile.schoolLevel = schoolLevel
        profile.usualStudyHoursPerDay = request.usualStudyHoursPerDay
        profile.preferredStudyMethod = preferredStudyMethod

        val saved = studyProfileRepository.save(profile)
        return UserProfileResponse(
            id = user.id!!,
            name = user.name,
            email = user.email,
            age = saved.age,
            schoolLevel = saved.schoolLevel.name,
            usualStudyHoursPerDay = saved.usualStudyHoursPerDay,
            preferredStudyMethod = saved.preferredStudyMethod.name,
            onboardingCompleted = user.onboardingCompleted,
        )
    }
}
