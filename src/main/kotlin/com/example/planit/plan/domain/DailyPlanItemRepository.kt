package com.example.planit.plan.domain

import org.springframework.data.jpa.repository.JpaRepository

interface DailyPlanItemRepository : JpaRepository<DailyPlanItem, Long> {
    fun findAllByDailyPlanId(dailyPlanId: Long): List<DailyPlanItem>
}
