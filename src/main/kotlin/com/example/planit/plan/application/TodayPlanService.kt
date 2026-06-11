package com.example.planit.plan.application

import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.common.api.CommonApiException
import com.example.planit.exam.domain.ExamPlanRepository
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.exam.domain.SubjectScopeRepository
import com.example.planit.plan.api.TodayPlanCompleteResponse
import com.example.planit.plan.api.TodayPlanItemResponse
import com.example.planit.plan.api.TodayPlanItemToggleRequest
import com.example.planit.plan.api.TodayPlanItemToggleResponse
import com.example.planit.plan.api.TodayPlanProgressItemResponse
import com.example.planit.plan.api.TodayPlanProgressResponse
import com.example.planit.plan.api.TodayPlanResponse
import com.example.planit.plan.api.TodayPlanUpdateItemRequest
import com.example.planit.plan.api.TodayPlanUpdateRequest
import com.example.planit.plan.api.TodayPlanUpdateResponse
import com.example.planit.plan.domain.CompletionEvent
import com.example.planit.plan.domain.CompletionEventRepository
import com.example.planit.plan.domain.CompletionEventType
import com.example.planit.plan.domain.DailyPlan
import com.example.planit.plan.domain.DailyPlanItem
import com.example.planit.plan.domain.DailyPlanItemRepository
import com.example.planit.plan.domain.DailyPlanRepository
import com.example.planit.plan.domain.DailyPlanStatus
import com.example.planit.plan.domain.PlanItemPriority
import com.example.planit.plan.domain.PlanItemStatus
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class TodayPlanService(
    private val examPlanRepository: ExamPlanRepository,
    private val subjectScopeRepository: SubjectScopeRepository,
    private val dailyPlanRepository: DailyPlanRepository,
    private val dailyPlanItemRepository: DailyPlanItemRepository,
    private val completionEventRepository: CompletionEventRepository,
) {

    @Transactional(readOnly = true)
    fun getTodayPlan(principal: AuthenticatedUser): TodayPlanResponse {
        val plan = todayPlan(principal.id)
        val items = dailyPlanItemRepository.findAllByDailyPlanIdOrderById(plan.id!!)
        return TodayPlanResponse(
            planId = plan.id!!,
            planDate = plan.planDate,
            status = plan.status.name,
            items = items.map { it.toTodayPlanItemResponse() },
        )
    }

    @Transactional
    fun updateTodayPlan(
        principal: AuthenticatedUser,
        request: TodayPlanUpdateRequest,
    ): TodayPlanUpdateResponse {
        if (request.items.isEmpty()) {
            throw CommonApiException(HttpStatus.BAD_REQUEST, "PLAN_ITEMS_REQUIRED", "플랜 항목은 최소 1개 이상이어야 합니다.")
        }

        val plan = todayPlan(principal.id)
        val existingItems = dailyPlanItemRepository.findAllByDailyPlanIdOrderById(plan.id!!).associateBy { it.id!! }.toMutableMap()

        request.deletedPlanItemIds.forEach { planItemId ->
            val item = existingItems.remove(planItemId)
                ?: throw CommonApiException(HttpStatus.NOT_FOUND, "PLAN_ITEM_NOT_FOUND", "플랜 항목을 찾을 수 없습니다.")
            completionEventRepository.deleteAllByDailyPlanItemId(item.id!!)
            dailyPlanItemRepository.delete(item)
        }

        request.items.forEach { itemRequest ->
            validateTodayPlanUpdateItem(itemRequest)
            val existing = itemRequest.planItemId?.let { existingItems[it] }
            if (itemRequest.planItemId != null && existing == null) {
                throw CommonApiException(HttpStatus.NOT_FOUND, "PLAN_ITEM_NOT_FOUND", "플랜 항목을 찾을 수 없습니다.")
            }

            if (existing != null) {
                updateExistingItem(existing, itemRequest)
            } else {
                createNewItem(plan, principal.id, itemRequest)
            }
        }

        val savedItems = dailyPlanItemRepository.findAllByDailyPlanIdOrderById(plan.id!!)
        return TodayPlanUpdateResponse(
            planId = plan.id!!,
            updated = true,
            itemCount = savedItems.size,
        )
    }

    private fun validateTodayPlanUpdateItem(request: TodayPlanUpdateItemRequest) {
        if (request.subjectName.isBlank()) {
            throw CommonApiException(HttpStatus.BAD_REQUEST, "SUBJECT_NAME_REQUIRED", "과목명은 비어 있을 수 없습니다.")
        }
        if (request.examRange.isBlank()) {
            throw CommonApiException(HttpStatus.BAD_REQUEST, "EXAM_RANGE_REQUIRED", "시험 범위는 비어 있을 수 없습니다.")
        }
        if (request.studyMethod.isBlank()) {
            throw CommonApiException(HttpStatus.BAD_REQUEST, "STUDY_METHOD_REQUIRED", "공부 방법은 비어 있을 수 없습니다.")
        }
    }

    @Transactional
    fun toggleTodayPlanItem(
        principal: AuthenticatedUser,
        planItemId: Long,
        request: TodayPlanItemToggleRequest,
    ): TodayPlanItemToggleResponse {
        val item = dailyPlanItemRepository.findByIdAndDailyPlanExamPlanUserId(planItemId, principal.id)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "PLAN_ITEM_NOT_FOUND", "플랜 항목을 찾을 수 없습니다.") }

        val targetStatus = if (request.completed) PlanItemStatus.COMPLETED else PlanItemStatus.PENDING
        var sproutAwarded = 0

        if (item.status != targetStatus) {
            if (item.status == PlanItemStatus.PENDING && targetStatus == PlanItemStatus.COMPLETED) {
                item.status = PlanItemStatus.COMPLETED
                if (!completionEventRepository.existsByDailyPlanItemId(item.id!!)) {
                    completionEventRepository.save(
                        CompletionEvent(
                            dailyPlanItem = item,
                            eventType = CompletionEventType.ITEM_CHECKED,
                            sproutDelta = 1,
                        ),
                    )
                    sproutAwarded = 1
                }
            } else if (item.status == PlanItemStatus.COMPLETED && targetStatus == PlanItemStatus.PENDING) {
                item.status = PlanItemStatus.PENDING
                item.dailyPlan.status = DailyPlanStatus.PENDING
            }
        }

        val items = dailyPlanItemRepository.findAllByDailyPlanIdOrderById(item.dailyPlan.id!!)
        val completedCount = items.count { if (it.id == item.id) targetStatus == PlanItemStatus.COMPLETED else it.status == PlanItemStatus.COMPLETED }
        return TodayPlanItemToggleResponse(
            planItemId = item.id!!,
            completed = targetStatus == PlanItemStatus.COMPLETED,
            completedCount = completedCount,
            totalCount = items.size,
            sproutAwarded = sproutAwarded,
        )
    }

    @Transactional
    fun completeTodayPlan(principal: AuthenticatedUser): TodayPlanCompleteResponse {
        val plan = todayPlan(principal.id)
        val items = dailyPlanItemRepository.findAllByDailyPlanIdOrderById(plan.id!!)
        if (items.any { it.status != PlanItemStatus.COMPLETED }) {
            throw CommonApiException(HttpStatus.CONFLICT, "PLAN_NOT_FINISHED", "미완료 항목이 남아 있습니다.")
        }

        plan.status = DailyPlanStatus.COMPLETED
        val streakDays = streakDays(principal.id)

        return TodayPlanCompleteResponse(
            planId = plan.id!!,
            completed = true,
            attendanceRecorded = true,
            streakDays = streakDays,
        )
    }

    @Transactional(readOnly = true)
    fun getTodayPlanProgress(principal: AuthenticatedUser): TodayPlanProgressResponse {
        val plan = todayPlan(principal.id)
        val items = dailyPlanItemRepository.findAllByDailyPlanIdOrderById(plan.id!!)
        val completedItems = items.filter { it.status == PlanItemStatus.COMPLETED }
        val remainingItems = items.filter { it.status != PlanItemStatus.COMPLETED }
        val sproutCount = completionEventRepository.findAllByDailyPlanItemDailyPlanExamPlanUserId(principal.id).sumOf { it.sproutDelta }

        return TodayPlanProgressResponse(
            completedCount = completedItems.size,
            totalCount = items.size,
            completedItems = completedItems.map { it.toProgressItemResponse() },
            remainingItems = remainingItems.map { it.toProgressItemResponse() },
            sproutCount = sproutCount,
            sproutPerPlanItem = 1,
        )
    }

    @Transactional(readOnly = true)
    fun streakDays(userId: Long): Int {
        val plans = dailyPlanRepository.findAllByExamPlanUserIdAndPlanDateLessThanEqualOrderByPlanDateDesc(userId, LocalDate.now())
        var expectedDate = LocalDate.now()
        var streak = 0
        for (plan in plans) {
            if (plan.planDate != expectedDate || plan.status != DailyPlanStatus.COMPLETED) {
                break
            }
            streak += 1
            expectedDate = expectedDate.minusDays(1)
        }
        return streak
    }

    private fun todayPlan(userId: Long): DailyPlan =
        dailyPlanRepository.findByExamPlanUserIdAndPlanDate(userId, LocalDate.now())
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "오늘 플랜이 없습니다.") }

    private fun updateExistingItem(item: DailyPlanItem, request: TodayPlanUpdateItemRequest) {
        item.subjectNameSnapshot = request.subjectName.trim()
        item.rangeTextSnapshot = request.examRange.trim()
        item.studyMethodSnapshot = request.studyMethod.trim()
        item.priority = parsePriority(request.priority)
        item.manuallyAdjusted = true
    }

    private fun createNewItem(plan: DailyPlan, userId: Long, request: TodayPlanUpdateItemRequest) {
        val activePlan = examPlanRepository.findByUserIdAndStatus(userId, ExamPlanStatus.ACTIVE)
            .orElseThrow { CommonApiException(HttpStatus.CONFLICT, "ACTIVE_PLAN_REQUIRED", "활성 시험 계획을 먼저 입력해주세요.") }
        val scope = subjectScopeRepository.findAllByExamPlanId(activePlan.id!!)
            .sortedBy { it.id }
            .firstOrNull { it.subjectName.equals(request.subjectName.trim(), ignoreCase = true) }
            ?: throw CommonApiException(
                HttpStatus.CONFLICT,
                "SUBJECT_SCOPE_NOT_FOUND",
                "${request.subjectName.trim()} 과목 범위를 먼저 입력해주세요.",
            )

        dailyPlanItemRepository.save(
            DailyPlanItem(
                dailyPlan = plan,
                subjectScope = scope,
                subjectNameSnapshot = request.subjectName.trim(),
                rangeTextSnapshot = request.examRange.trim(),
                studyMethodSnapshot = request.studyMethod.trim(),
                priority = parsePriority(request.priority),
                status = PlanItemStatus.PENDING,
                plannedUnits = 1,
                estimatedMinutes = estimatedMinutesOf(parsePriority(request.priority)),
                manuallyAdjusted = true,
            ),
        )
    }

    private fun parsePriority(raw: String): PlanItemPriority =
        runCatching { enumValueOf<PlanItemPriority>(raw.trim()) }
            .getOrElse {
                throw CommonApiException(HttpStatus.BAD_REQUEST, "INVALID_PRIORITY", "유효하지 않은 우선순위입니다.")
            }

    private fun estimatedMinutesOf(priority: PlanItemPriority): Int =
        when (priority) {
            PlanItemPriority.HIGH -> 90
            PlanItemPriority.MEDIUM -> 60
            PlanItemPriority.LOW -> 45
        }

    private fun DailyPlanItem.toTodayPlanItemResponse() = TodayPlanItemResponse(
        planItemId = id!!,
        subjectName = subjectNameSnapshot,
        examRange = rangeTextSnapshot,
        studyMethod = studyMethodSnapshot,
        priority = priority.name,
        status = status.name,
        estimatedMinutes = estimatedMinutes,
    )

    private fun DailyPlanItem.toProgressItemResponse() = TodayPlanProgressItemResponse(
        planItemId = id!!,
        subjectName = subjectNameSnapshot,
        label = rangeTextSnapshot,
    )
}
