package com.example.planit.user.api

import com.example.planit.auth.api.ApiSuccessResponse
import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.user.application.UserProfileService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me")
class UserProfileController(
    private val userProfileService: UserProfileService,
) {

    @GetMapping
    fun me(@AuthenticationPrincipal principal: AuthenticatedUser): ApiSuccessResponse<UserProfileResponse> =
        ApiSuccessResponse(data = userProfileService.getMyProfile(principal))

    @PutMapping("/study-profile")
    fun upsertStudyProfile(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestBody request: StudyProfileRequest,
    ): ApiSuccessResponse<UserProfileResponse> =
        ApiSuccessResponse(data = userProfileService.upsertStudyProfile(principal, request))
}
