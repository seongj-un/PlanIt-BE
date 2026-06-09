package com.example.planit.plan.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PlanGenerationJobRepository : JpaRepository<PlanGenerationJob, Long> {
    fun findByJobIdAndUserId(jobId: String, userId: Long): Optional<PlanGenerationJob>
}
