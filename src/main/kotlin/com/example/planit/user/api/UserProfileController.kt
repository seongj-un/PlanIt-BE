package com.example.planit.user.api

import com.example.planit.auth.api.ApiSuccessResponse
import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.user.domain.StudyProfileRepository
import com.example.planit.user.domain.UserAccountRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me")
class UserProfileController(
    private val userAccountRepository: UserAccountRepository,
    private val studyProfileRepository: StudyProfileRepository,
) {

    @GetMapping
    fun me(@AuthenticationPrincipal principal: AuthenticatedUser): ApiSuccessResponse<UserProfileResponse> {
        val user = userAccountRepository.findById(principal.id)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }
        val studyProfile = studyProfileRepository.findByUserId(user.id!!)
            .orElse(null)

        return ApiSuccessResponse(
            data = UserProfileResponse(
                id = user.id!!,
                name = user.name,
                email = user.email,
                age = studyProfile?.age,
                schoolLevel = studyProfile?.schoolLevel?.name,
                usualStudyHoursPerDay = studyProfile?.usualStudyHoursPerDay,
                preferredStudyMethod = studyProfile?.preferredStudyMethod?.name,
                onboardingCompleted = user.onboardingCompleted,
            ),
        )
    }
}
