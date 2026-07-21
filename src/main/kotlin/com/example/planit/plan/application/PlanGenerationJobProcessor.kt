package com.example.planit.plan.application

import com.example.planit.common.api.CommonApiException
import com.example.planit.exam.domain.ExamPlan
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
import com.example.planit.user.domain.StudyProfileRepository
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate

@Component
class PlanGenerationJobProcessor(
    private val examPlanRepository: ExamPlanRepository,
    private val subjectScopeRepository: SubjectScopeRepository,
    private val dailyPlanRepository: DailyPlanRepository,
    private val dailyPlanItemRepository: DailyPlanItemRepository,
    private val completionEventRepository: CompletionEventRepository,
    private val planGenerationJobRepository: PlanGenerationJobRepository,
    private val studyProfileRepository: StudyProfileRepository,
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
        job.message = "학습 계획을 생성하고 있어요."

        val studyProfile = studyProfileRepository.findByUserId(userId).orElse(null)
        val schedule = planGenerationProvider.generate(
            request = request,
            activePlan = activePlan,
            availableScopes = subjectScopeRepository.findAllByExamPlanId(activePlan.id!!),
            studyProfile = studyProfile,
        )

        job.progressPercent = 70
        job.message = "학습 계획을 저장하고 있어요."

        val todayPlan = persistSchedule(activePlan, schedule)

        job.status = PlanGenerationJobStatus.COMPLETED
        job.progressPercent = 100
        job.message = "학습 계획 생성을 완료했어요."
        job.planDate = todayPlan.planDate
        job.generatedPlanId = todayPlan.id
    }

    /** 오늘 이후 기존 계획을 지우고 전체 기간을 새로 저장한 뒤, 오늘(없으면 가장 이른 날) 계획을 반환. */
    private fun persistSchedule(activePlan: ExamPlan, schedule: PlanSchedule): DailyPlan {
        if (schedule.days.isEmpty()) {
            throw CommonApiException(HttpStatus.CONFLICT, "PLAN_GENERATION_FAILED", "생성된 학습 계획이 없습니다.")
        }
        val today = LocalDate.now()

        val existing = dailyPlanRepository.findAllByExamPlanIdAndPlanDateGreaterThanEqual(activePlan.id!!, today)
        existing.forEach { plan ->
            completionEventRepository.deleteAllByDailyPlanItemDailyPlanId(plan.id!!)
            dailyPlanItemRepository.deleteAllByDailyPlanId(plan.id!!)
        }
        dailyPlanRepository.deleteAll(existing)
        dailyPlanRepository.flush()

        var todayPlan: DailyPlan? = null
        var earliestPlan: DailyPlan? = null

        schedule.days.forEach { day ->
            val savedPlan = dailyPlanRepository.save(
                DailyPlan(examPlan = activePlan, planDate = day.planDate, status = DailyPlanStatus.PENDING),
            )
            day.items.forEach { item ->
                dailyPlanItemRepository.save(
                    DailyPlanItem(
                        dailyPlan = savedPlan,
                        subjectScope = item.scope,
                        subjectNameSnapshot = item.subjectName,
                        rangeTextSnapshot = item.rangeText,
                        studyMethodSnapshot = item.studyMethod,
                        priority = item.priority,
                        status = PlanItemStatus.PENDING,
                        plannedUnits = item.plannedUnits,
                        estimatedMinutes = item.estimatedMinutes,
                        manuallyAdjusted = false,
                        startUnit = item.startUnit,
                        endUnit = item.endUnit,
                        displayOrder = item.displayOrder,
                    ),
                )
            }
            if (earliestPlan == null) earliestPlan = savedPlan
            if (day.planDate == today) todayPlan = savedPlan
        }

        return todayPlan ?: earliestPlan!!
    }

    private fun markFailed(jobPrimaryKey: Long, exception: Exception) {
        val job = planGenerationJobRepository.findById(jobPrimaryKey)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "플랜 생성 작업을 찾을 수 없습니다.") }
        job.status = PlanGenerationJobStatus.FAILED
        job.progressPercent = 100
        job.message = exception.message?.take(255) ?: "학습 계획 생성에 실패했어요."
    }
}
