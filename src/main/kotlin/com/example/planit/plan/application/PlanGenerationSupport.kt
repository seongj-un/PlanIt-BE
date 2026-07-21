package com.example.planit.plan.application

import com.example.planit.common.api.CommonApiException
import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.ScopeUnitType
import com.example.planit.plan.domain.PlanItemPriority
import org.springframework.http.HttpStatus

object PlanGenerationSupport {

    fun normalize(value: String): String = value.trim().lowercase()

    fun parseDifficulty(raw: String): DifficultyLevel =
        runCatching { enumValueOf<DifficultyLevel>(raw.trim()) }
            .getOrElse {
                throw CommonApiException(HttpStatus.BAD_REQUEST, "INVALID_DIFFICULTY", "유효하지 않은 난이도입니다.")
            }

    fun difficultyRank(difficulty: DifficultyLevel): Int =
        when (difficulty) {
            DifficultyLevel.HIGH -> 3
            DifficultyLevel.MEDIUM -> 2
            DifficultyLevel.LOW -> 1
        }

    fun priorityOf(difficulty: DifficultyLevel): PlanItemPriority =
        when (difficulty) {
            DifficultyLevel.HIGH -> PlanItemPriority.HIGH
            DifficultyLevel.MEDIUM -> PlanItemPriority.MEDIUM
            DifficultyLevel.LOW -> PlanItemPriority.LOW
        }

    fun unitLabel(unitType: ScopeUnitType): String =
        when (unitType) {
            ScopeUnitType.PAGE -> "쪽"
            ScopeUnitType.CHAPTER -> "단원"
            ScopeUnitType.QUESTION -> "문제"
            ScopeUnitType.PASSAGE -> "지문"
            ScopeUnitType.TOPIC -> "주제"
            ScopeUnitType.CUSTOM -> ""
        }
}
