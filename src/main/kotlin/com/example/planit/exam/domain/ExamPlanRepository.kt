package com.example.planit.exam.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ExamPlanRepository : JpaRepository<ExamPlan, Long> {
    fun findByUserIdAndStatus(userId: Long, status: ExamPlanStatus): Optional<ExamPlan>
}
