package com.example.planit.exam.api

import com.example.planit.auth.api.ApiSuccessResponse
import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.exam.application.ActiveExamPlanService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/exam-plans/active")
class ActiveExamPlanController(
    private val activeExamPlanService: ActiveExamPlanService,
) {

    @GetMapping
    fun getActivePlan(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ApiSuccessResponse<ActiveExamPlanResponse> =
        ApiSuccessResponse(data = activeExamPlanService.getActivePlan(principal))

    @PutMapping
    fun upsertActivePlan(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestBody request: ActiveExamPlanRequest,
    ): ApiSuccessResponse<ActiveExamPlanResponse> =
        ApiSuccessResponse(data = activeExamPlanService.upsertActivePlan(principal, request))

    @PostMapping("/complete")
    fun completeActivePlan(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ApiSuccessResponse<ActiveExamPlanResponse> =
        ApiSuccessResponse(data = activeExamPlanService.completeActivePlan(principal))

    @GetMapping("/scopes")
    fun getActiveScopes(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ApiSuccessResponse<List<SubjectScopeResponse>> =
        ApiSuccessResponse(data = activeExamPlanService.getActiveScopes(principal))

    @PutMapping("/scopes")
    fun replaceActiveScopes(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestBody request: SubjectScopesReplaceRequest,
    ): ApiSuccessResponse<List<SubjectScopeResponse>> =
        ApiSuccessResponse(data = activeExamPlanService.replaceActiveScopes(principal, request))
}
