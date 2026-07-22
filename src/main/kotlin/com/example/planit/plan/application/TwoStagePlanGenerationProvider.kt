package com.example.planit.plan.application

import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.plan.api.PlanGenerationJobCreateRequest
import com.example.planit.plan.application.PlanGenerationSupport.normalize
import com.example.planit.plan.application.PlanGenerationSupport.parseDifficulty
import com.example.planit.user.domain.StudyProfile
import org.springframework.stereotype.Component

@Component
class TwoStagePlanGenerationProvider(
    private val scheduleCalculator: ScheduleCalculator,
    private val arrangementProvider: PlanArrangementProvider,
) : PlanGenerationProvider {

    override fun generate(
        request: PlanGenerationJobCreateRequest,
        activePlan: ExamPlan,
        availableScopes: List<SubjectScope>,
        studyProfile: StudyProfile?,
    ): PlanSchedule {
        val schedule = scheduleCalculator.calculate(request, activePlan, availableScopes)
        val context = buildContext(request)
        return arrangementProvider.arrange(schedule, context)
    }

    private fun buildContext(request: PlanGenerationJobCreateRequest): ArrangementContext {
        val difficultSet = request.difficultSubjects.map { normalize(it) }.toSet()
        return ArrangementContext(
            preferredStudyMethod = request.preferredStudyMethod,
            subjects = request.subjects.map { subject ->
                ArrangementSubject(
                    subjectName = subject.subjectName.trim(),
                    difficulty = parseDifficulty(subject.difficulty),
                    isDifficult = difficultSet.contains(normalize(subject.subjectName)),
                )
            },
        )
    }
}
