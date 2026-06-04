package com.example.planit.exam.application

import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.common.api.CommonApiException
import com.example.planit.exam.api.ActiveExamPlanRequest
import com.example.planit.exam.api.ActiveExamPlanResponse
import com.example.planit.exam.api.SubjectScopeRequest
import com.example.planit.exam.api.SubjectScopeResponse
import com.example.planit.exam.api.SubjectScopesReplaceRequest
import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.ExamPlanRepository
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.exam.domain.ScopeUnitType
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.exam.domain.SubjectScopeRepository
import com.example.planit.exam.domain.TargetExamType
import com.example.planit.user.domain.UserAccountRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class ActiveExamPlanService(
    private val userAccountRepository: UserAccountRepository,
    private val examPlanRepository: ExamPlanRepository,
    private val subjectScopeRepository: SubjectScopeRepository,
) {

    @Transactional(readOnly = true)
    fun getActivePlan(principal: AuthenticatedUser): ActiveExamPlanResponse =
        examPlanRepository.findByUserIdAndStatus(principal.id, ExamPlanStatus.ACTIVE)
            .map { it.toResponse() }
            .orElseThrow {
                CommonApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "활성 시험 계획이 없습니다.")
            }

    @Transactional
    fun upsertActivePlan(principal: AuthenticatedUser, request: ActiveExamPlanRequest): ActiveExamPlanResponse {
        require(request.targetExamLabel.isNotBlank()) { "시험 이름은 비어 있을 수 없습니다." }
        require(!request.examDate.isBefore(LocalDate.now())) { "시험 날짜는 오늘 이후여야 합니다." }

        val user = userAccountRepository.findById(principal.id)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.") }
        val examType = enumValueOf<TargetExamType>(request.targetExamType)

        val plan = examPlanRepository.findByUserIdAndStatus(user.id!!, ExamPlanStatus.ACTIVE)
            .orElseGet {
                ExamPlan(
                    user = user,
                    targetExamType = examType,
                    targetExamLabel = request.targetExamLabel.trim(),
                    examDate = request.examDate,
                    status = ExamPlanStatus.ACTIVE,
                )
            }

        plan.targetExamType = examType
        plan.targetExamLabel = request.targetExamLabel.trim()
        plan.examDate = request.examDate

        return examPlanRepository.save(plan).toResponse()
    }

    @Transactional(readOnly = true)
    fun getActiveScopes(principal: AuthenticatedUser): List<SubjectScopeResponse> =
        subjectScopeRepository.findAllByExamPlanId(activePlan(principal).id!!)
            .sortedBy { it.id }
            .map { it.toResponse() }

    @Transactional
    fun replaceActiveScopes(
        principal: AuthenticatedUser,
        request: SubjectScopesReplaceRequest,
    ): List<SubjectScopeResponse> {
        require(request.scopes.isNotEmpty()) { "과목 범위는 최소 1개 이상이어야 합니다." }

        val plan = activePlan(principal)
        val existingScopes = subjectScopeRepository.findAllByExamPlanId(plan.id!!)
        if (existingScopes.isNotEmpty()) {
            subjectScopeRepository.deleteAll(existingScopes)
        }

        val savedScopes = request.scopes.map { scopeRequest ->
            validateScope(scopeRequest)
            subjectScopeRepository.save(
                SubjectScope(
                    examPlan = plan,
                    subjectName = scopeRequest.subjectName.trim(),
                    rawRangeText = scopeRequest.rawRangeText.trim(),
                    preferredMethodNote = scopeRequest.preferredMethodNote.trim(),
                    difficulty = enumValueOf<DifficultyLevel>(scopeRequest.difficulty),
                    unitType = enumValueOf<ScopeUnitType>(scopeRequest.unitType),
                    totalUnits = scopeRequest.totalUnits,
                    remainingUnits = scopeRequest.remainingUnits,
                ),
            )
        }

        if (!plan.user.onboardingCompleted) {
            plan.user.onboardingCompleted = true
        }

        return savedScopes.map { it.toResponse() }
    }

    private fun activePlan(principal: AuthenticatedUser): ExamPlan =
        examPlanRepository.findByUserIdAndStatus(principal.id, ExamPlanStatus.ACTIVE)
            .orElseThrow {
                CommonApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "활성 시험 계획이 없습니다.")
            }

    private fun validateScope(request: SubjectScopeRequest) {
        require(request.subjectName.isNotBlank()) { "과목명은 비어 있을 수 없습니다." }
        require(request.rawRangeText.isNotBlank()) { "시험 범위는 비어 있을 수 없습니다." }
        require(request.preferredMethodNote.isNotBlank()) { "공부 방법 메모는 비어 있을 수 없습니다." }
        require(request.totalUnits > 0) { "전체 단위 수는 1 이상이어야 합니다." }
        require(request.remainingUnits in 0..request.totalUnits) { "남은 단위 수는 0 이상 전체 단위 이하이어야 합니다." }
    }

    private fun ExamPlan.toResponse() = ActiveExamPlanResponse(
        id = id!!,
        targetExamType = targetExamType.name,
        targetExamLabel = targetExamLabel,
        examDate = examDate,
        status = status.name,
    )

    private fun SubjectScope.toResponse() = SubjectScopeResponse(
        id = id!!,
        subjectName = subjectName,
        rawRangeText = rawRangeText,
        preferredMethodNote = preferredMethodNote,
        difficulty = difficulty.name,
        unitType = unitType.name,
        totalUnits = totalUnits,
        remainingUnits = remainingUnits,
    )
}
