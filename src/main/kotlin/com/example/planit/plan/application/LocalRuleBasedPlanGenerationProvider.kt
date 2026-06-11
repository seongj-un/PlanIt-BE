package com.example.planit.plan.application

import com.example.planit.common.api.CommonApiException
import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.plan.api.PlanGenerationJobCreateRequest
import com.example.planit.plan.api.PlanGenerationSubjectRequest
import com.example.planit.plan.domain.PlanItemPriority
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

@Component
class LocalRuleBasedPlanGenerationProvider : PlanGenerationProvider {

    override fun generate(
        request: PlanGenerationJobCreateRequest,
        activePlan: ExamPlan,
        availableScopes: List<SubjectScope>,
    ): GeneratedPlanDraft {
        val scopesBySubject = availableScopes
            .filter { it.examPlan.id == activePlan.id }
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

        val difficultSubjects = request.difficultSubjects.map { normalize(it) }.toSet()
        val totalMinutes = request.dailyMaxStudyHours * 60
        val totalWeight = matchedSubjects.sumOf { weightOf(it.difficulty, difficultSubjects.contains(normalize(it.subject.subjectName))) }
        var allocatedMinutes = 0

        return GeneratedPlanDraft(
            planDate = LocalDate.now(),
            items = matchedSubjects.mapIndexed { index, matched ->
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

                GeneratedPlanDraftItem(
                    scope = matched.scope,
                    subjectName = matched.subject.subjectName.trim(),
                    examRange = matched.subject.examRange.trim(),
                    studyMethod = matched.subject.preferredMethodNote.trim(),
                    priority = priorityOf(matched.difficulty),
                    plannedUnits = min(remainingUnits, max(1, estimatedMinutes / 60)),
                    estimatedMinutes = estimatedMinutes,
                )
            },
        )
    }

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
