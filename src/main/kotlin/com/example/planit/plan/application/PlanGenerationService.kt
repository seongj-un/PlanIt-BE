package com.example.planit.plan.application

import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.common.api.CommonApiException
import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.exam.domain.ExamPlanRepository
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.exam.domain.SubjectScopeRepository
import com.example.planit.plan.api.PlanGenerationJobCreateRequest
import com.example.planit.plan.api.PlanGenerationJobCreateResponse
import com.example.planit.plan.api.PlanGenerationJobStatusResponse
import com.example.planit.plan.api.PlanGenerationSubjectRequest
import com.example.planit.plan.domain.DailyPlan
import com.example.planit.plan.domain.DailyPlanItem
import com.example.planit.plan.domain.DailyPlanItemRepository
import com.example.planit.plan.domain.DailyPlanRepository
import com.example.planit.plan.domain.DailyPlanStatus
import com.example.planit.plan.domain.PlanGenerationJob
import com.example.planit.plan.domain.PlanGenerationJobRepository
import com.example.planit.plan.domain.PlanGenerationJobStatus
import com.example.planit.plan.domain.PlanItemPriority
import com.example.planit.plan.domain.PlanItemStatus
import com.example.planit.user.domain.UserAccountRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

@Service
class PlanGenerationService(
    private val userAccountRepository: UserAccountRepository,
    private val examPlanRepository: ExamPlanRepository,
    private val subjectScopeRepository: SubjectScopeRepository,
    private val dailyPlanRepository: DailyPlanRepository,
    private val dailyPlanItemRepository: DailyPlanItemRepository,
    private val planGenerationJobRepository: PlanGenerationJobRepository,
) {

    @Transactional
    fun createJob(
        principal: AuthenticatedUser,
        request: PlanGenerationJobCreateRequest,
    ): PlanGenerationJobCreateResponse {
        validateRequest(request)

        val user = userAccountRepository.findById(principal.id)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.") }
        val activePlan = examPlanRepository.findByUserIdAndStatus(user.id!!, ExamPlanStatus.ACTIVE)
            .orElseThrow {
                CommonApiException(HttpStatus.CONFLICT, "ACTIVE_PLAN_REQUIRED", "활성 시험 계획을 먼저 입력해주세요.")
            }

        val job = planGenerationJobRepository.save(
            PlanGenerationJob(
                jobId = generateJobId(),
                user = user,
                status = PlanGenerationJobStatus.PENDING,
                progressPercent = 0,
                message = "오늘 플랜 생성을 준비하고 있어요.",
            ),
        )

        val scopesBySubject = subjectScopeRepository.findAllByExamPlanId(activePlan.id!!)
            .filter { it.remainingUnits > 0 }
            .sortedBy { it.id }
            .groupBy { normalize(it.subjectName) }
            .mapValues { (_, scopes) -> scopes.toMutableList() }

        val matchedSubjects = request.subjects.map { subject ->
            val key = normalize(subject.subjectName)
            val candidates = scopesBySubject[key]
                ?: throw CommonApiException(
                    HttpStatus.CONFLICT,
                    "SUBJECT_SCOPE_NOT_FOUND",
                    "${subject.subjectName.trim()} 과목 범위를 먼저 입력해주세요.",
                )

            if (candidates.isEmpty()) {
                throw CommonApiException(
                    HttpStatus.CONFLICT,
                    "SUBJECT_SCOPE_NOT_FOUND",
                    "${subject.subjectName.trim()} 과목 범위를 먼저 입력해주세요.",
                )
            }

            MatchedSubject(subject = subject, scope = candidates.removeAt(0), difficulty = parseDifficulty(subject.difficulty))
        }

        val planDate = LocalDate.now()
        val dailyPlan = dailyPlanRepository.findByExamPlanIdAndPlanDate(activePlan.id!!, planDate)
            .orElseGet {
                DailyPlan(
                    examPlan = activePlan,
                    planDate = planDate,
                    status = DailyPlanStatus.PENDING,
                )
            }
        dailyPlan.status = DailyPlanStatus.PENDING
        val savedPlan = dailyPlanRepository.save(dailyPlan)

        dailyPlanItemRepository.deleteAllByDailyPlanId(savedPlan.id!!)

        val difficultSubjects = request.difficultSubjects.map { normalize(it) }.toSet()
        val totalMinutes = request.dailyMaxStudyHours * 60
        val totalWeight = matchedSubjects.sumOf { weightOf(it.difficulty, difficultSubjects.contains(normalize(it.subject.subjectName))) }
        var allocatedMinutes = 0

        matchedSubjects.forEachIndexed { index, matched ->
            val itemWeight = weightOf(matched.difficulty, difficultSubjects.contains(normalize(matched.subject.subjectName)))
            val estimatedMinutes = if (index == matchedSubjects.lastIndex) {
                max(1, totalMinutes - allocatedMinutes)
            } else {
                max(1, totalMinutes * itemWeight / totalWeight)
            }
            allocatedMinutes += estimatedMinutes

            val remainingUnits = matched.scope.remainingUnits
            if (remainingUnits <= 0) {
                throw CommonApiException(
                    HttpStatus.CONFLICT,
                    "SUBJECT_SCOPE_EXHAUSTED",
                    "${matched.subject.subjectName.trim()} 과목에 남은 범위가 없습니다.",
                )
            }

            dailyPlanItemRepository.save(
                DailyPlanItem(
                    dailyPlan = savedPlan,
                    subjectScope = matched.scope,
                    subjectNameSnapshot = matched.subject.subjectName.trim(),
                    rangeTextSnapshot = matched.subject.examRange.trim(),
                    studyMethodSnapshot = matched.subject.preferredMethodNote.trim(),
                    priority = priorityOf(matched.difficulty),
                    status = PlanItemStatus.PENDING,
                    plannedUnits = min(remainingUnits, max(1, estimatedMinutes / 60)),
                    estimatedMinutes = estimatedMinutes,
                    manuallyAdjusted = false,
                ),
            )
        }

        job.status = PlanGenerationJobStatus.COMPLETED
        job.progressPercent = 100
        job.message = "오늘 플랜 생성을 완료했어요."
        job.planDate = planDate
        job.generatedPlanId = savedPlan.id

        return PlanGenerationJobCreateResponse(
            jobId = job.jobId,
            status = PlanGenerationJobStatus.PENDING.name,
            estimatedSeconds = 0,
        )
    }

    @Transactional(readOnly = true)
    fun getJobStatus(principal: AuthenticatedUser, jobId: String): PlanGenerationJobStatusResponse {
        val job = planGenerationJobRepository.findByJobIdAndUserId(jobId, principal.id)
            .orElseThrow {
                CommonApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "플랜 생성 작업을 찾을 수 없습니다.")
            }

        return PlanGenerationJobStatusResponse(
            jobId = job.jobId,
            status = job.status.name,
            progressPercent = job.progressPercent,
            message = job.message,
            planDate = job.planDate,
            planId = job.generatedPlanId,
            dashboardAvailable = job.status == PlanGenerationJobStatus.COMPLETED && job.generatedPlanId != null,
        )
    }

    private fun validateRequest(request: PlanGenerationJobCreateRequest) {
        if (request.subjects.isEmpty()) {
            throw CommonApiException(HttpStatus.BAD_REQUEST, "SUBJECTS_REQUIRED", "과목은 최소 1개 이상이어야 합니다.")
        }
        if (request.dailyMaxStudyHours !in 1..24) {
            throw CommonApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_DAILY_MAX_STUDY_HOURS",
                "하루 최대 공부 시간은 1 이상 24 이하여야 합니다.",
            )
        }

        request.subjects.forEach { subject ->
            if (subject.subjectName.isBlank()) {
                throw CommonApiException(HttpStatus.BAD_REQUEST, "SUBJECT_NAME_REQUIRED", "과목명은 비어 있을 수 없습니다.")
            }
            if (subject.examRange.isBlank()) {
                throw CommonApiException(HttpStatus.BAD_REQUEST, "EXAM_RANGE_REQUIRED", "시험 범위는 비어 있을 수 없습니다.")
            }
            if (subject.preferredMethodNote.isBlank()) {
                throw CommonApiException(
                    HttpStatus.BAD_REQUEST,
                    "PREFERRED_METHOD_NOTE_REQUIRED",
                    "공부 방법 메모는 비어 있을 수 없습니다.",
                )
            }
            parseDifficulty(subject.difficulty)
        }
    }

    private fun generateJobId(): String =
        "plan-job-${UUID.randomUUID().toString().replace("-", "").take(12)}"

    private fun parseDifficulty(raw: String): DifficultyLevel =
        runCatching { enumValueOf<DifficultyLevel>(raw.trim()) }
            .getOrElse {
                throw CommonApiException(HttpStatus.BAD_REQUEST, "INVALID_DIFFICULTY", "유효하지 않은 난이도입니다.")
            }

    private fun weightOf(difficulty: DifficultyLevel, difficultSubject: Boolean): Int =
        when (difficulty) {
            DifficultyLevel.HIGH -> 3
            DifficultyLevel.MEDIUM -> 2
            DifficultyLevel.LOW -> 1
        } + if (difficultSubject) 1 else 0

    private fun priorityOf(difficulty: DifficultyLevel): PlanItemPriority =
        when (difficulty) {
            DifficultyLevel.HIGH -> PlanItemPriority.HIGH
            DifficultyLevel.MEDIUM -> PlanItemPriority.MEDIUM
            DifficultyLevel.LOW -> PlanItemPriority.LOW
        }

    private fun normalize(value: String): String = value.trim().lowercase()

    private data class MatchedSubject(
        val subject: PlanGenerationSubjectRequest,
        val scope: SubjectScope,
        val difficulty: DifficultyLevel,
    )
}
