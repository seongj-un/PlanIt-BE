package com.example.planit.plan.api

import com.example.planit.auth.api.ApiSuccessResponse
import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.plan.application.TodayPlanService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/plans/today")
class TodayPlanController(
    private val todayPlanService: TodayPlanService,
) {

    @GetMapping
    fun getTodayPlan(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ApiSuccessResponse<TodayPlanResponse> =
        ApiSuccessResponse(data = todayPlanService.getTodayPlan(principal))

    @PutMapping
    fun updateTodayPlan(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestBody request: TodayPlanUpdateRequest,
    ): ApiSuccessResponse<TodayPlanUpdateResponse> =
        ApiSuccessResponse(data = todayPlanService.updateTodayPlan(principal, request))

    @PatchMapping("/items/{planItemId}")
    fun toggleTodayPlanItem(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable planItemId: Long,
        @RequestBody request: TodayPlanItemToggleRequest,
    ): ApiSuccessResponse<TodayPlanItemToggleResponse> =
        ApiSuccessResponse(data = todayPlanService.toggleTodayPlanItem(principal, planItemId, request))

    @PostMapping("/complete")
    fun completeTodayPlan(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ApiSuccessResponse<TodayPlanCompleteResponse> =
        ApiSuccessResponse(data = todayPlanService.completeTodayPlan(principal))

    @GetMapping("/progress")
    fun getTodayPlanProgress(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ApiSuccessResponse<TodayPlanProgressResponse> =
        ApiSuccessResponse(data = todayPlanService.getTodayPlanProgress(principal))
}
