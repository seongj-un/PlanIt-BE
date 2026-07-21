package com.example.planit.plan.application

interface PlanArrangementProvider {
    /** 숫자(units, range, minutes)는 유지한 채, 각 날짜 항목의 순서와 priority만 조정한다. */
    fun arrange(schedule: PlanSchedule, context: ArrangementContext): PlanSchedule
}
