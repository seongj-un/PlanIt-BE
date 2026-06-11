package com.example.planit.plan.application

import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.common.api.CommonApiException
import com.example.planit.plan.api.DailyPlanHistoryItemResponse
import com.example.planit.plan.api.DailyPlanHistoryResponse
import com.example.planit.plan.api.MonthlyPlanHistoryDayResponse
import com.example.planit.plan.api.MonthlyPlanHistoryResponse
import com.example.planit.plan.api.MonthlyPlanHistoryStatsResponse
import com.example.planit.plan.domain.DailyPlanRepository
import com.example.planit.plan.domain.DailyPlanStatus
import com.example.planit.plan.domain.DailyPlanItemRepository
import com.example.planit.plan.domain.PlanItemStatus
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

@Service
class PlanHistoryService(
    private val dailyPlanRepository: DailyPlanRepository,
    private val dailyPlanItemRepository: DailyPlanItemRepository,
) {

    @Transactional(readOnly = true)
    fun getMonthlyHistory(principal: AuthenticatedUser, month: String): MonthlyPlanHistoryResponse {
        val parsedMonth = runCatching { YearMonth.parse(month) }
            .getOrElse { throw CommonApiException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "월 형식이 올바르지 않습니다.") }
        val plans = dailyPlanRepository.findAllByExamPlanUserIdAndPlanDateBetweenOrderByPlanDateAsc(
            principal.id,
            parsedMonth.atDay(1),
            parsedMonth.atEndOfMonth(),
        )

        val days = plans.map { plan ->
            val items = dailyPlanItemRepository.findAllByDailyPlanIdOrderById(plan.id!!)
            MonthlyPlanHistoryDayResponse(
                date = plan.planDate.toString(),
                completedCount = items.count { it.status == PlanItemStatus.COMPLETED },
                totalCount = items.size,
                completed = plan.status == DailyPlanStatus.COMPLETED,
                subjects = items.map { it.subjectNameSnapshot }.distinct(),
            )
        }

        return MonthlyPlanHistoryResponse(
            month = parsedMonth.toString(),
            stats = MonthlyPlanHistoryStatsResponse(
                completedPlans = plans.count { it.status == DailyPlanStatus.COMPLETED },
                incompletePlans = plans.count { it.status != DailyPlanStatus.COMPLETED },
            ),
            days = days,
        )
    }

    @Transactional(readOnly = true)
    fun getDailyHistory(principal: AuthenticatedUser, date: String): DailyPlanHistoryResponse {
        val parsedDate = runCatching { LocalDate.parse(date) }
            .getOrElse { throw CommonApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "날짜 형식이 올바르지 않습니다.") }
        val plan = dailyPlanRepository.findByExamPlanUserIdAndPlanDate(principal.id, parsedDate)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "해당 날짜 플랜이 없습니다.") }
        val items = dailyPlanItemRepository.findAllByDailyPlanIdOrderById(plan.id!!)

        return DailyPlanHistoryResponse(
            date = parsedDate.toString(),
            completedCount = items.count { it.status == PlanItemStatus.COMPLETED },
            totalCount = items.size,
            items = items.map {
                DailyPlanHistoryItemResponse(
                    subjectName = it.subjectNameSnapshot,
                    examRange = it.rangeTextSnapshot,
                    studyMethod = it.studyMethodSnapshot,
                    completed = it.status == PlanItemStatus.COMPLETED,
                )
            },
        )
    }
}
