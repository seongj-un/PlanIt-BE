package com.example.planit.plan.api

import com.example.planit.auth.api.ApiSuccessResponse
import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.plan.application.DashboardService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController(
    private val dashboardService: DashboardService,
) {

    @GetMapping
    fun getDashboard(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ApiSuccessResponse<DashboardResponse> =
        ApiSuccessResponse(data = dashboardService.getDashboard(principal))
}
