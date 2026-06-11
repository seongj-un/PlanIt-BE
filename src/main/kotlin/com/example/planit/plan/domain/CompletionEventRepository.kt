package com.example.planit.plan.domain

import org.springframework.data.jpa.repository.JpaRepository

interface CompletionEventRepository : JpaRepository<CompletionEvent, Long> {
    fun findAllByDailyPlanItemId(dailyPlanItemId: Long): List<CompletionEvent>
    fun findAllByDailyPlanItemDailyPlanExamPlanUserId(userId: Long): List<CompletionEvent>
    fun deleteAllByDailyPlanItemId(dailyPlanItemId: Long)
}
