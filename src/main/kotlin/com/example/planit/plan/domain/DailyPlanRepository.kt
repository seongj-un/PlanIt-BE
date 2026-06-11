package com.example.planit.plan.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.Optional

interface DailyPlanRepository : JpaRepository<DailyPlan, Long> {
    fun findByExamPlanIdAndPlanDate(examPlanId: Long, planDate: LocalDate): Optional<DailyPlan>
    fun findByExamPlanUserIdAndPlanDate(userId: Long, planDate: LocalDate): Optional<DailyPlan>
    fun findAllByExamPlanUserIdAndPlanDateBetweenOrderByPlanDateAsc(
        userId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<DailyPlan>
    fun findAllByExamPlanUserIdAndPlanDateLessThanEqualOrderByPlanDateDesc(
        userId: Long,
        planDate: LocalDate,
    ): List<DailyPlan>
}
