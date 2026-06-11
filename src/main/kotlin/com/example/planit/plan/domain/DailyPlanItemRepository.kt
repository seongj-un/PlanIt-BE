package com.example.planit.plan.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface DailyPlanItemRepository : JpaRepository<DailyPlanItem, Long> {
    fun findAllByDailyPlanId(dailyPlanId: Long): List<DailyPlanItem>
    fun findAllByDailyPlanIdOrderById(dailyPlanId: Long): List<DailyPlanItem>
    fun findByIdAndDailyPlanExamPlanUserId(id: Long, userId: Long): Optional<DailyPlanItem>
    fun deleteAllByDailyPlanId(dailyPlanId: Long)
}
