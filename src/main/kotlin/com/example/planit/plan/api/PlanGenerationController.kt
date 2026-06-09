package com.example.planit.plan.api

import com.example.planit.auth.api.ApiSuccessResponse
import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.plan.application.PlanGenerationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/plan-generation-jobs")
class PlanGenerationController(
    private val planGenerationService: PlanGenerationService,
) {

    @PostMapping
    fun createJob(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestBody request: PlanGenerationJobCreateRequest,
    ): ResponseEntity<ApiSuccessResponse<PlanGenerationJobCreateResponse>> {
        val response = planGenerationService.createJob(principal, request)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiSuccessResponse(data = response))
    }

    @GetMapping("/{jobId}")
    fun getJobStatus(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable jobId: String,
    ): ApiSuccessResponse<PlanGenerationJobStatusResponse> =
        ApiSuccessResponse(data = planGenerationService.getJobStatus(principal, jobId))
}
