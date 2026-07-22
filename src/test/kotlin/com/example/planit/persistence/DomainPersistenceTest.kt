package com.example.planit.persistence

import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.ExamPlanRepository
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.exam.domain.ScopeUnitType
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.exam.domain.SubjectScopeRepository
import com.example.planit.exam.domain.TargetExamType
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
import com.example.planit.user.domain.PreferredStudyMethod
import com.example.planit.user.domain.StudyProfile
import com.example.planit.user.domain.StudyProfileRepository
import com.example.planit.user.domain.UserAccount
import com.example.planit.user.domain.UserAccountRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.time.LocalDate

@DataJpaTest
class DomainPersistenceTest(
    @Autowired private val userAccountRepository: UserAccountRepository,
    @Autowired private val studyProfileRepository: StudyProfileRepository,
    @Autowired private val examPlanRepository: ExamPlanRepository,
    @Autowired private val subjectScopeRepository: SubjectScopeRepository,
    @Autowired private val dailyPlanRepository: DailyPlanRepository,
    @Autowired private val dailyPlanItemRepository: DailyPlanItemRepository,
    @Autowired private val completionEventRepository: CompletionEventRepository,
) {

    @Test
    fun `persist core anti-cram domain graph`() {
        val user = userAccountRepository.save(
            UserAccount(
                name = "김민지",
                email = "minji@example.com",
                passwordHash = "{noop}password",
                onboardingCompleted = true,
            ),
        )

        val profile = studyProfileRepository.save(
            StudyProfile(
                user = user,
                usualStudyHoursPerDay = 4,
                preferredStudyMethod = PreferredStudyMethod.BALANCED,
            ),
        )

        val examPlan = examPlanRepository.save(
            ExamPlan(
                user = user,
                targetExamType = TargetExamType.CSAT,
                targetExamLabel = "수능",
                examDate = LocalDate.of(2026, 6, 12),
                status = ExamPlanStatus.ACTIVE,
            ),
        )

        val subjectScope = subjectScopeRepository.save(
            SubjectScope(
                examPlan = examPlan,
                subjectName = "수학",
                rawRangeText = "수열과 극한 1~3단원",
                preferredMethodNote = "개념 정리 후 대표 문제 15문제",
                difficulty = DifficultyLevel.HIGH,
                unitType = ScopeUnitType.CHAPTER,
                totalUnits = 3,
                remainingUnits = 3,
            ),
        )

        val dailyPlan = dailyPlanRepository.save(
            DailyPlan(
                examPlan = examPlan,
                planDate = LocalDate.of(2026, 6, 4),
                status = DailyPlanStatus.PENDING,
            ),
        )

        val dailyPlanItem = dailyPlanItemRepository.save(
            DailyPlanItem(
                dailyPlan = dailyPlan,
                subjectScope = subjectScope,
                subjectNameSnapshot = "수학",
                rangeTextSnapshot = "수열과 극한 1단원",
                studyMethodSnapshot = "개념 정리 후 대표 문제 15문제",
                priority = PlanItemPriority.HIGH,
                status = PlanItemStatus.COMPLETED,
                plannedUnits = 1,
                estimatedMinutes = 70,
                manuallyAdjusted = true,
            ),
        )

        val completionEvent = completionEventRepository.save(
            CompletionEvent(
                dailyPlanItem = dailyPlanItem,
                eventType = CompletionEventType.ITEM_CHECKED,
                sproutDelta = 1,
            ),
        )

        assertThat(profile.id).isNotNull
        val savedScope = subjectScopeRepository.findAllByExamPlanId(examPlan.id!!).single()
        assertThat(savedScope.remainingUnits).isEqualTo(3)
        assertThat(savedScope.unitType).isEqualTo(ScopeUnitType.CHAPTER)
        assertThat(dailyPlanRepository.findByExamPlanIdAndPlanDate(examPlan.id!!, LocalDate.of(2026, 6, 4)))
            .hasValueSatisfying {
                assertThat(it.status).isEqualTo(DailyPlanStatus.PENDING)
            }
        val savedPlanItem = dailyPlanItemRepository.findAllByDailyPlanId(dailyPlan.id!!).single()
        assertThat(savedPlanItem.subjectScope.id).isEqualTo(subjectScope.id)
        assertThat(savedPlanItem.manuallyAdjusted).isTrue()

        val savedEvent = completionEventRepository.findAllByDailyPlanItemId(dailyPlanItem.id!!).single()
        assertThat(savedEvent.id).isEqualTo(completionEvent.id)
        assertThat(savedEvent.sproutDelta).isEqualTo(1)
    }
}
