package com.example.planit.plan.application

import com.example.planit.common.api.CommonApiException
import com.example.planit.exam.domain.ExamPlanRepository
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.exam.domain.SubjectScopeRepository
import com.example.planit.plan.domain.CompletionEventRepository
import com.example.planit.plan.domain.DailyPlan
import com.example.planit.plan.domain.DailyPlanItem
import com.example.planit.plan.domain.DailyPlanItemRepository
import com.example.planit.plan.domain.DailyPlanRepository
import com.example.planit.plan.domain.DailyPlanStatus
import com.example.planit.plan.domain.PlanGenerationJobRepository
import com.example.planit.plan.domain.PlanGenerationJobStatus
import com.example.planit.plan.domain.PlanItemStatus
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class PlanGenerationJobProcessor(
    private val examPlanRepository: ExamPlanRepository,
    private val subjectScopeRepository: SubjectScopeRepository,
    private val dailyPlanRepository: DailyPlanRepository,
    private val dailyPlanItemRepository: DailyPlanItemRepository,
    private val completionEventRepository: CompletionEventRepository,
    private val planGenerationJobRepository: PlanGenerationJobRepository,
    private val planGenerationProvider: PlanGenerationProvider,
    transactionManager: PlatformTransactionManager,
) {

    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val requiresNewTransactionTemplate = TransactionTemplate(transactionManager).apply {
        propagationBehavior = org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @Async("planGenerationExecutor")
    fun processRequested(jobPrimaryKey: Long, userId: Long, request: com.example.planit.plan.api.PlanGenerationJobCreateRequest) {
        try {
            transactionTemplate.executeWithoutResult {
                process(jobPrimaryKey, userId, request)
            }
        } catch (exception: Exception) {
            requiresNewTransactionTemplate.executeWithoutResult {
                markFailed(jobPrimaryKey, exception)
            }
        }
    }

    private fun process(
        jobPrimaryKey: Long,
        userId: Long,
        request: com.example.planit.plan.api.PlanGenerationJobCreateRequest,
    ) {
        val job = planGenerationJobRepository.findById(jobPrimaryKey)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "플랜 생성 작업을 찾을 수 없습니다.") }
        val activePlan = examPlanRepository.findByUserIdAndStatus(userId, ExamPlanStatus.ACTIVE)
            .orElseThrow {
                CommonApiException(HttpStatus.CONFLICT, "ACTIVE_PLAN_REQUIRED", "활성 시험 계획을 먼저 입력해주세요.")
            }

        job.status = PlanGenerationJobStatus.RUNNING
        job.progressPercent = 15
        job.message = "오늘 플랜을 생성하고 있어요."

        val draft = planGenerationProvider.generate(
            request = request,
            activePlan = activePlan,
            availableScopes = subjectScopeRepository.findAllByExamPlanId(activePlan.id!!),
        )

        job.progressPercent = 70
        job.message = "오늘 플랜을 저장하고 있어요."

        val savedPlan = persistPlan(activePlan.id!!, draft)

        job.status = PlanGenerationJobStatus.COMPLETED
        job.progressPercent = 100
        job.message = "오늘 플랜 생성을 완료했어요."
        job.planDate = draft.planDate
        job.generatedPlanId = savedPlan.id
    }

    private fun persistPlan(examPlanId: Long, draft: GeneratedPlanDraft): DailyPlan {
        val dailyPlan = dailyPlanRepository.findByExamPlanIdAndPlanDate(examPlanId, draft.planDate)
            .orElseGet {
                DailyPlan(
                    examPlan = draft.items.firstOrNull()?.scope?.examPlan
                        ?: throw CommonApiException(HttpStatus.CONFLICT, "PLAN_GENERATION_FAILED", "플랜 생성에 필요한 범위를 찾을 수 없습니다."),
                    planDate = draft.planDate,
                    status = DailyPlanStatus.PENDING,
                )
            }

        dailyPlan.status = DailyPlanStatus.PENDING
        val savedPlan = dailyPlanRepository.save(dailyPlan)

        completionEventRepository.deleteAllByDailyPlanItemDailyPlanId(savedPlan.id!!)
        dailyPlanItemRepository.deleteAllByDailyPlanId(savedPlan.id!!)

        draft.items.forEach { item ->
            dailyPlanItemRepository.save(
                DailyPlanItem(
                    dailyPlan = savedPlan,
                    subjectScope = item.scope,
                    subjectNameSnapshot = item.subjectName,
                    rangeTextSnapshot = item.examRange,
                    studyMethodSnapshot = item.studyMethod,
                    priority = item.priority,
                    status = PlanItemStatus.PENDING,
                    plannedUnits = item.plannedUnits,
                    estimatedMinutes = item.estimatedMinutes,
                    manuallyAdjusted = false,
                ),
            )
        }

        return savedPlan
    }

    private fun markFailed(jobPrimaryKey: Long, exception: Exception) {
        val job = planGenerationJobRepository.findById(jobPrimaryKey)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "플랜 생성 작업을 찾을 수 없습니다.") }
        job.status = PlanGenerationJobStatus.FAILED
        job.progressPercent = 100
        job.message = exception.message?.take(255) ?: "오늘 플랜 생성에 실패했어요."
    }
}
