package com.example.planit.plan.application

import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.common.api.CommonApiException
import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.exam.domain.ExamPlanRepository
import com.example.planit.exam.domain.SubjectScopeRepository
import com.example.planit.plan.api.PlanGenerationJobCreateRequest
import com.example.planit.plan.api.PlanGenerationJobCreateResponse
import com.example.planit.plan.api.PlanGenerationJobStatusResponse
import com.example.planit.plan.api.PlanGenerationSubjectRequest
import com.example.planit.plan.domain.PlanGenerationJob
import com.example.planit.plan.domain.PlanGenerationJobRepository
import com.example.planit.plan.domain.PlanGenerationJobStatus
import com.example.planit.user.domain.UserAccountRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

@Service
class PlanGenerationService(
    private val userAccountRepository: UserAccountRepository,
    private val examPlanRepository: ExamPlanRepository,
    private val subjectScopeRepository: SubjectScopeRepository,
    private val planGenerationJobRepository: PlanGenerationJobRepository,
    private val planGenerationJobProcessor: PlanGenerationJobProcessor,
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
        prevalidateSubjectScopes(activePlan.id!!, request.subjects)

        val job = planGenerationJobRepository.save(
            PlanGenerationJob(
                jobId = generateJobId(),
                user = user,
                status = PlanGenerationJobStatus.PENDING,
                progressPercent = 0,
                message = "오늘 플랜 생성을 준비하고 있어요.",
            ),
        )

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    planGenerationJobProcessor.processRequested(job.id!!, principal.id, request)
                }
            },
        )

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

    private fun prevalidateSubjectScopes(examPlanId: Long, subjects: List<PlanGenerationSubjectRequest>) {
        val scopesBySubject = subjectScopeRepository.findAllByExamPlanId(examPlanId)
            .filter { it.remainingUnits > 0 }
            .sortedBy { it.id }
            .groupBy { normalize(it.subjectName) }
            .mapValues { (_, scopes) -> scopes.toMutableList() }

        subjects.forEach { subject ->
            val candidates = scopesBySubject[normalize(subject.subjectName)]
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
            candidates.removeAt(0)
        }
    }

    private fun generateJobId(): String =
        "plan-job-${UUID.randomUUID().toString().replace("-", "").take(12)}"

    private fun parseDifficulty(raw: String): DifficultyLevel =
        runCatching { enumValueOf<DifficultyLevel>(raw.trim()) }
            .getOrElse {
                throw CommonApiException(HttpStatus.BAD_REQUEST, "INVALID_DIFFICULTY", "유효하지 않은 난이도입니다.")
            }

    private fun normalize(value: String): String = value.trim().lowercase()
}
