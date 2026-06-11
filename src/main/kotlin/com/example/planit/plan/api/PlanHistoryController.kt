package com.example.planit.plan.api

import com.example.planit.auth.api.ApiSuccessResponse
import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.plan.application.PlanHistoryService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/plans/history")
class PlanHistoryController(
    private val planHistoryService: PlanHistoryService,
) {

    @GetMapping
    fun getMonthlyHistory(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestParam month: String,
    ): ApiSuccessResponse<MonthlyPlanHistoryResponse> =
        ApiSuccessResponse(data = planHistoryService.getMonthlyHistory(principal, month))

    @GetMapping("/{date}")
    fun getDailyHistory(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable date: String,
    ): ApiSuccessResponse<DailyPlanHistoryResponse> =
        ApiSuccessResponse(data = planHistoryService.getDailyHistory(principal, date))
}
