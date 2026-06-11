package com.example.planit.plan.application

import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.common.api.CommonApiException
import com.example.planit.plan.api.DashboardAttendanceDayResponse
import com.example.planit.plan.api.DashboardAttendanceResponse
import com.example.planit.plan.api.DashboardNextExamResponse
import com.example.planit.plan.api.DashboardResponse
import com.example.planit.plan.api.DashboardRewardsResponse
import com.example.planit.plan.api.DashboardTodayPlanItemResponse
import com.example.planit.plan.api.DashboardTodayPlanResponse
import com.example.planit.plan.domain.CompletionEventRepository
import com.example.planit.plan.domain.DailyPlanRepository
import com.example.planit.plan.domain.PlanItemStatus
import com.example.planit.user.domain.UserAccountRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class DashboardService(
    private val userAccountRepository: UserAccountRepository,
    private val dailyPlanRepository: DailyPlanRepository,
    private val completionEventRepository: CompletionEventRepository,
    private val todayPlanService: TodayPlanService,
) {

    @Transactional(readOnly = true)
    fun getDashboard(principal: AuthenticatedUser): DashboardResponse {
        val user = userAccountRepository.findById(principal.id)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.") }
        val plan = dailyPlanRepository.findByExamPlanUserIdAndPlanDate(principal.id, LocalDate.now())
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "오늘 플랜이 없습니다.") }
        val planItems = plan.id?.let { todayPlanService.getTodayPlan(principal).items } ?: emptyList()
        val completedCount = planItems.count { it.status == PlanItemStatus.COMPLETED.name }
        val totalCount = planItems.size
        val progressPercent = if (totalCount == 0) 0 else completedCount * 100 / totalCount
        val allEvents = completionEventRepository.findAllByDailyPlanItemDailyPlanExamPlanUserId(principal.id)
        val sproutCount = allEvents.sumOf { it.sproutDelta }
        val earnedToday = allEvents.filter { it.dailyPlanItem.dailyPlan.planDate == LocalDate.now() }.sumOf { it.sproutDelta }
        val monthStart = LocalDate.now().withDayOfMonth(1)
        val monthEnd = monthStart.plusMonths(1).minusDays(1)
        val calendarPlans = dailyPlanRepository.findAllByExamPlanUserIdAndPlanDateBetweenOrderByPlanDateAsc(principal.id, monthStart, monthEnd)

        return DashboardResponse(
            userName = user.name,
            nextExam = DashboardNextExamResponse(
                label = plan.examPlan.targetExamLabel,
                date = plan.examPlan.examDate,
                dDay = plan.examPlan.examDate.toEpochDay() - LocalDate.now().toEpochDay(),
            ),
            todayPlan = DashboardTodayPlanResponse(
                planDate = plan.planDate,
                completedCount = completedCount,
                totalCount = totalCount,
                progressPercent = progressPercent,
                items = planItems.map {
                    DashboardTodayPlanItemResponse(
                        planItemId = it.planItemId,
                        subjectName = it.subjectName,
                        scopeSummary = "${it.examRange}: ${it.studyMethod}",
                        completed = it.status == PlanItemStatus.COMPLETED.name,
                    )
                },
            ),
            rewards = DashboardRewardsResponse(
                sproutCount = sproutCount,
                earnedToday = earnedToday,
            ),
            attendance = DashboardAttendanceResponse(
                streakDays = todayPlanService.streakDays(principal.id),
                calendar = calendarPlans.map {
                    DashboardAttendanceDayResponse(
                        date = it.planDate,
                        completed = it.status.name == "COMPLETED",
                    )
                },
            ),
        )
    }
}
