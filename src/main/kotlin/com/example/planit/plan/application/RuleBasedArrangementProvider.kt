package com.example.planit.plan.application

import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.plan.application.PlanGenerationSupport.difficultyRank
import com.example.planit.plan.application.PlanGenerationSupport.normalize
import com.example.planit.plan.application.PlanGenerationSupport.priorityOf
import org.springframework.stereotype.Component

@Component
class RuleBasedArrangementProvider : PlanArrangementProvider {

    override fun arrange(schedule: PlanSchedule, context: ArrangementContext): PlanSchedule {
        val bySubject = context.subjects.associateBy { normalize(it.subjectName) }

        val days = schedule.days.map { day ->
            val ordered = day.items
                .sortedWith(
                    compareByDescending<PlanScheduleItem> { subjectOf(bySubject, it)?.isDifficult ?: false }
                        .thenByDescending { difficultyRank(difficultyOf(bySubject, it)) }
                        .thenBy { it.subjectName },
                )
                .mapIndexed { index, item ->
                    item.copy(
                        displayOrder = index,
                        priority = priorityOf(difficultyOf(bySubject, item)),
                    )
                }
            day.copy(items = ordered)
        }
        return schedule.copy(days = days)
    }

    private fun subjectOf(bySubject: Map<String, ArrangementSubject>, item: PlanScheduleItem): ArrangementSubject? =
        bySubject[normalize(item.subjectName)]

    private fun difficultyOf(bySubject: Map<String, ArrangementSubject>, item: PlanScheduleItem): DifficultyLevel =
        subjectOf(bySubject, item)?.difficulty ?: item.priorityToDifficulty()
}

private fun PlanScheduleItem.priorityToDifficulty(): DifficultyLevel =
    when (priority) {
        com.example.planit.plan.domain.PlanItemPriority.HIGH -> DifficultyLevel.HIGH
        com.example.planit.plan.domain.PlanItemPriority.MEDIUM -> DifficultyLevel.MEDIUM
        com.example.planit.plan.domain.PlanItemPriority.LOW -> DifficultyLevel.LOW
    }
