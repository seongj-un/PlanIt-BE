# 2단계 학습 계획 생성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 범위(과목 분량)와 기간(오늘~시험일)으로 시험일까지 전체 일별 계획을 생성하되, 숫자는 결정적 코드가 계산하고 학습 성향에 따른 배치는 AI(실패 시 규칙 기반)가 담당하도록 플랜 생성 로직을 2단계로 재구성한다.

**Architecture:** `TwoStagePlanGenerationProvider`가 `ScheduleCalculator`(1단계, 결정적 숫자 계산)와 `PlanArrangementProvider`(2단계, 순서/우선순위 배치)를 조합한다. 배치는 기본 `RuleBasedArrangementProvider`이고, 무료 모델이 정해지면 `AiClient` 구현체 하나만 추가해 `AiArrangementProvider`로 전환한다. AI는 순서·priority만 바꾸고 숫자는 절대 건드리지 않으며, 실패 시 규칙 기반으로 fallback한다.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.14, Spring Data JPA + H2, Spring Modulith, JUnit5 + MockMvc + AssertJ, Gradle.

## Global Constraints

- Root package `com.example.planit`; 3-layer per module `{api, application, domain}` + `common`.
- 도메인 엔티티는 `BaseEntity()` 상속, JPA `@Entity`.
- API 응답 봉투: 성공 `{"data": ...}`, 실패 `{"error": {"code": ...}}`. 도메인 에러는 `CommonApiException(HttpStatus, code, message)`.
- 통합 테스트는 `@SpringBootTest @AutoConfigureMockMvc`, 단위 테스트는 순수 JUnit5.
- 테스트 실행: `./gradlew test` (특정: `./gradlew test --tests "FQCN"`).
- **불변식: 분량·범위·시간 숫자는 `ScheduleCalculator`에서만 결정된다. 배치 단계(AI 포함)는 항목의 순서와 priority만 바꾼다.**
- **비어 있는 날짜(항목 0개)는 `DailyPlan`으로 저장하지 않는다.**
- 모델명은 미정(무료 모델). `AiClient` 인터페이스 뒤로 격리한다. **인증키·시크릿을 코드나 git에 넣지 않는다.**
- 커밋은 각 태스크 끝에서. 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

---

## File Structure

**신규 (main):**
- `plan/application/PlanGenerationSupport.kt` — 공용 헬퍼(정규화, 난이도 파싱/랭크/우선순위, 단위 라벨)
- `plan/application/PlanSchedule.kt` — 2단계 파이프라인 공용 타입(`PlanSchedule`, `PlanScheduleDay`, `PlanScheduleItem`, `ArrangementContext`, `ArrangementSubject`)
- `plan/application/ScheduleCalculator.kt` — 1단계(결정적 숫자)
- `plan/application/PlanArrangementProvider.kt` — 배치 인터페이스
- `plan/application/RuleBasedArrangementProvider.kt` — 기본/fallback 배치
- `plan/application/AiClient.kt` — 모델 후연결 인터페이스
- `plan/application/AiArrangementProvider.kt` — AI 배치(프롬프트/파싱/검증/fallback)
- `plan/config/AiArrangementProperties.kt` — `@ConfigurationProperties`
- `plan/config/PlanArrangementConfig.kt` — 활성 배치 provider 선택 빈

**수정 (main):**
- `plan/domain/DailyPlanItem.kt` — `startUnit`/`endUnit`/`displayOrder` 필드 추가
- `plan/application/PlanGenerationProvider.kt` — 반환 타입을 `PlanSchedule`로 변경, 기존 `GeneratedPlanDraft*` 제거, `studyProfile` 파라미터 추가
- `plan/application/PlanGenerationJobProcessor.kt` — 다일자 persist, `StudyProfileRepository` 주입
- `plan/domain/DailyPlanRepository.kt` — `findAllByExamPlanIdAndPlanDateGreaterThanEqual` 추가
- `src/main/resources/application.yaml` — `planit.ai.arrangement` 기본값
- `TwoStagePlanGenerationProvider`로 대체하며 삭제: `plan/application/LocalRuleBasedPlanGenerationProvider.kt`

**테스트:**
- `plan/ScheduleCalculatorTest.kt`, `plan/RuleBasedArrangementProviderTest.kt`, `plan/AiArrangementProviderTest.kt` (단위)
- `plan/PlanGenerationJobIntegrationTest.kt` (기존 갱신 + 전체기간 검증 추가)

---

### Task 1: DailyPlanItem에 배치/범위 필드 추가

**Files:**
- Modify: `src/main/kotlin/com/example/planit/plan/domain/DailyPlanItem.kt`
- Test: `src/test/kotlin/com/example/planit/persistence/DomainPersistenceTest.kt` (기존, 컴파일 확인용)

**Interfaces:**
- Produces: `DailyPlanItem`에 `var startUnit: Int = 0`, `var endUnit: Int = 0`, `var displayOrder: Int = 0` (기본값 있으므로 기존 생성자 호출부 무변경).

- [ ] **Step 1: 필드 추가**

`DailyPlanItem.kt`의 `manuallyAdjusted` 파라미터 뒤(마지막 생성자 파라미터)로 추가:

```kotlin
    @Column(nullable = false)
    var manuallyAdjusted: Boolean = false,
    @Column(nullable = false)
    var startUnit: Int = 0,
    @Column(nullable = false)
    var endUnit: Int = 0,
    @Column(nullable = false)
    var displayOrder: Int = 0,
) : BaseEntity() {
```

- [ ] **Step 2: 컴파일 및 전체 테스트 통과 확인**

Run: `./gradlew test`
Expected: PASS (기본값 덕분에 기존 생성자 호출 무변경, H2 `ddl-auto: update`로 컬럼 자동 추가).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/example/planit/plan/domain/DailyPlanItem.kt
git commit -m "feat: add per-day range and order fields to DailyPlanItem

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: 공용 헬퍼 + 스케줄 타입 + ScheduleCalculator (1단계)

**Files:**
- Create: `src/main/kotlin/com/example/planit/plan/application/PlanGenerationSupport.kt`
- Create: `src/main/kotlin/com/example/planit/plan/application/PlanSchedule.kt`
- Create: `src/main/kotlin/com/example/planit/plan/application/ScheduleCalculator.kt`
- Test: `src/test/kotlin/com/example/planit/plan/ScheduleCalculatorTest.kt`

**Interfaces:**
- Produces:
  - `object PlanGenerationSupport { fun normalize(v: String): String; fun parseDifficulty(raw: String): DifficultyLevel; fun difficultyRank(d: DifficultyLevel): Int; fun priorityOf(d: DifficultyLevel): PlanItemPriority; fun unitLabel(t: ScopeUnitType): String }`
  - `data class PlanSchedule(val days: List<PlanScheduleDay>)`
  - `data class PlanScheduleDay(val planDate: LocalDate, val items: List<PlanScheduleItem>)`
  - `data class PlanScheduleItem(val scope: SubjectScope, val subjectName: String, val rangeText: String, val studyMethod: String, val priority: PlanItemPriority, val plannedUnits: Int, val estimatedMinutes: Int, val startUnit: Int, val endUnit: Int, val displayOrder: Int)`
  - `data class ArrangementSubject(val subjectName: String, val difficulty: DifficultyLevel, val isDifficult: Boolean)`
  - `data class ArrangementContext(val age: Int?, val schoolLevel: String?, val preferredStudyMethod: String, val subjects: List<ArrangementSubject>)`
  - `@Component class ScheduleCalculator { fun calculate(request: PlanGenerationJobCreateRequest, activePlan: ExamPlan, availableScopes: List<SubjectScope>): PlanSchedule }`

- [ ] **Step 1: 공용 헬퍼 작성**

Create `PlanGenerationSupport.kt`:

```kotlin
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
```

- [ ] **Step 2: 스케줄 타입 작성**

Create `PlanSchedule.kt`:

```kotlin
package com.example.planit.plan.application

import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.plan.domain.PlanItemPriority
import java.time.LocalDate

data class PlanSchedule(
    val days: List<PlanScheduleDay>,
)

data class PlanScheduleDay(
    val planDate: LocalDate,
    val items: List<PlanScheduleItem>,
)

data class PlanScheduleItem(
    val scope: SubjectScope,
    val subjectName: String,
    val rangeText: String,
    val studyMethod: String,
    val priority: PlanItemPriority,
    val plannedUnits: Int,
    val estimatedMinutes: Int,
    val startUnit: Int,
    val endUnit: Int,
    val displayOrder: Int,
)

data class ArrangementSubject(
    val subjectName: String,
    val difficulty: DifficultyLevel,
    val isDifficult: Boolean,
)

data class ArrangementContext(
    val age: Int?,
    val schoolLevel: String?,
    val preferredStudyMethod: String,
    val subjects: List<ArrangementSubject>,
)
```

- [ ] **Step 3: 실패 테스트 작성**

Create `ScheduleCalculatorTest.kt`:

```kotlin
package com.example.planit.plan

import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.exam.domain.ScopeUnitType
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.exam.domain.TargetExamType
import com.example.planit.plan.api.PlanGenerationJobCreateRequest
import com.example.planit.plan.api.PlanGenerationSubjectRequest
import com.example.planit.plan.application.ScheduleCalculator
import com.example.planit.user.domain.UserAccount
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ScheduleCalculatorTest {

    private val calculator = ScheduleCalculator()
    private val today: LocalDate = LocalDate.now()

    @Test
    fun `splits scope evenly across the period and tracks unit ranges`() {
        val exam = examPlan(today.plusDays(20))
        val scope = subjectScope(exam, "수학", ScopeUnitType.PAGE, total = 100, remaining = 100)
        val request = request(subject("수학", "HIGH"), dailyMaxStudyHours = 5)

        val schedule = calculator.calculate(request, exam, listOf(scope))

        assertThat(schedule.days).hasSize(20)
        val firstItem = schedule.days.first().items.single()
        assertThat(schedule.days.first().planDate).isEqualTo(today)
        assertThat(firstItem.plannedUnits).isEqualTo(5)
        assertThat(firstItem.startUnit).isEqualTo(1)
        assertThat(firstItem.endUnit).isEqualTo(5)
        assertThat(firstItem.rangeText).isEqualTo("1~5쪽")
        assertThat(schedule.days.last().items.single().endUnit).isEqualTo(100)
        assertThat(schedule.days.sumOf { day -> day.items.sumOf { it.plannedUnits } }).isEqualTo(100)
    }

    @Test
    fun `distributes remainder to the earliest days`() {
        val exam = examPlan(today.plusDays(3))
        val scope = subjectScope(exam, "수학", ScopeUnitType.PAGE, total = 10, remaining = 10)
        val request = request(subject("수학", "MEDIUM"), dailyMaxStudyHours = 4)

        val schedule = calculator.calculate(request, exam, listOf(scope))

        assertThat(schedule.days.map { it.items.single().plannedUnits }).containsExactly(4, 3, 3)
    }

    @Test
    fun `omits empty trailing days when units are fewer than days`() {
        val exam = examPlan(today.plusDays(30))
        val scope = subjectScope(exam, "영어", ScopeUnitType.QUESTION, total = 10, remaining = 10)
        val request = request(subject("영어", "LOW"), dailyMaxStudyHours = 2)

        val schedule = calculator.calculate(request, exam, listOf(scope))

        assertThat(schedule.days).hasSize(10)
        assertThat(schedule.days.all { it.items.single().plannedUnits == 1 }).isTrue()
    }

    @Test
    fun `caps daily minutes at daily max study hours`() {
        val exam = examPlan(today.plusDays(10))
        val scopes = listOf(
            subjectScope(examPlan(today.plusDays(10)), "수학", ScopeUnitType.PAGE, 50, 50),
            subjectScope(examPlan(today.plusDays(10)), "영어", ScopeUnitType.QUESTION, 50, 50),
        )
        val request = request(subject("수학", "HIGH"), subject("영어", "MEDIUM"), dailyMaxStudyHours = 3)

        val schedule = calculator.calculate(request, exam, scopes)

        assertThat(schedule.days.first().items.sumOf { it.estimatedMinutes }).isLessThanOrEqualTo(180)
    }

    @Test
    fun `throws when exam date is not in the future`() {
        val exam = examPlan(today)
        val scope = subjectScope(exam, "수학", ScopeUnitType.PAGE, 10, 10)
        val request = request(subject("수학", "HIGH"), dailyMaxStudyHours = 4)

        assertThatThrownBy { calculator.calculate(request, exam, listOf(scope)) }
            .hasMessageContaining("시험")
    }

    @Test
    fun `throws when a requested subject has no scope`() {
        val exam = examPlan(today.plusDays(10))
        val request = request(subject("과학", "HIGH"), dailyMaxStudyHours = 4)

        assertThatThrownBy { calculator.calculate(request, exam, emptyList()) }
            .hasMessageContaining("범위")
    }

    private fun examPlan(examDate: LocalDate): ExamPlan =
        ExamPlan(
            user = UserAccount(name = "학생", email = "s@e.com", passwordHash = "x"),
            targetExamType = TargetExamType.CSAT,
            targetExamLabel = "수능",
            examDate = examDate,
            status = ExamPlanStatus.ACTIVE,
        )

    private fun subjectScope(
        exam: ExamPlan,
        name: String,
        unitType: ScopeUnitType,
        total: Int,
        remaining: Int,
    ): SubjectScope =
        SubjectScope(
            examPlan = exam,
            subjectName = name,
            rawRangeText = "$name 범위",
            preferredMethodNote = "개념 정리",
            difficulty = DifficultyLevel.MEDIUM,
            unitType = unitType,
            totalUnits = total,
            remainingUnits = remaining,
        )

    private fun subject(name: String, difficulty: String): PlanGenerationSubjectRequest =
        PlanGenerationSubjectRequest(
            subjectName = name,
            examRange = "$name 범위",
            preferredMethodNote = "개념 정리",
            difficulty = difficulty,
        )

    private fun request(vararg subjects: PlanGenerationSubjectRequest, dailyMaxStudyHours: Int): PlanGenerationJobCreateRequest =
        PlanGenerationJobCreateRequest(
            subjects = subjects.toList(),
            preferredStudyMethod = "BALANCED",
            difficultSubjects = emptyList(),
            dailyMaxStudyHours = dailyMaxStudyHours,
        )
}
```

> 참고: `UserAccount` 생성자 인자는 실제 파일 기준으로 확인해 맞춘다(`src/main/kotlin/com/example/planit/user/domain/UserAccount.kt`). 여기 예시는 `name`/`email`/`passwordHash`를 가정한다.

- [ ] **Step 4: 테스트 실패 확인**

Run: `./gradlew test --tests "com.example.planit.plan.ScheduleCalculatorTest"`
Expected: FAIL (`ScheduleCalculator` 미구현 / 컴파일 에러).

- [ ] **Step 5: ScheduleCalculator 구현**

Create `ScheduleCalculator.kt`:

```kotlin
package com.example.planit.plan.application

import com.example.planit.common.api.CommonApiException
import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.plan.api.PlanGenerationJobCreateRequest
import com.example.planit.plan.application.PlanGenerationSupport.normalize
import com.example.planit.plan.application.PlanGenerationSupport.parseDifficulty
import com.example.planit.plan.application.PlanGenerationSupport.priorityOf
import com.example.planit.plan.application.PlanGenerationSupport.unitLabel
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

@Component
class ScheduleCalculator {

    fun calculate(
        request: PlanGenerationJobCreateRequest,
        activePlan: ExamPlan,
        availableScopes: List<SubjectScope>,
    ): PlanSchedule {
        val today = LocalDate.now()
        val studyDays = ChronoUnit.DAYS.between(today, activePlan.examDate).toInt()
        if (studyDays <= 0) {
            throw CommonApiException(HttpStatus.CONFLICT, "EXAM_DATE_PASSED", "시험일까지 학습 가능한 날이 없습니다.")
        }

        val scopesBySubject = availableScopes
            .filter { it.examPlan.id == activePlan.id }
            .filter { it.remainingUnits > 0 }
            .sortedBy { it.id }
            .groupBy { normalize(it.subjectName) }
            .mapValues { (_, scopes) -> scopes.toMutableList() }

        val capMinutes = request.dailyMaxStudyHours * 60

        // dayIndex -> mutable list of items (numbers only; priority/order default here)
        val itemsByDay = sortedMapOf<Int, MutableList<PlanScheduleItem>>()

        request.subjects.forEach { subject ->
            val candidates = scopesBySubject[normalize(subject.subjectName)]
            if (candidates.isNullOrEmpty()) {
                throw CommonApiException(
                    HttpStatus.CONFLICT,
                    "SUBJECT_SCOPE_NOT_FOUND",
                    "${subject.subjectName.trim()} 과목 범위를 먼저 입력해주세요.",
                )
            }
            val scope = candidates.removeAt(0)
            val difficulty = parseDifficulty(subject.difficulty)
            val remaining = scope.remainingUnits
            val base = remaining / studyDays
            val remainder = remaining % studyDays
            var cumulative = 0

            for (dayIndex in 0 until studyDays) {
                val units = base + if (dayIndex < remainder) 1 else 0
                if (units == 0) continue
                val start = cumulative + 1
                val end = cumulative + units
                cumulative = end
                val label = unitLabel(scope.unitType)
                val item = PlanScheduleItem(
                    scope = scope,
                    subjectName = subject.subjectName.trim(),
                    rangeText = "$start~$end$label",
                    studyMethod = subject.preferredMethodNote.trim(),
                    priority = priorityOf(difficulty),
                    plannedUnits = units,
                    estimatedMinutes = 0, // filled below
                    startUnit = start,
                    endUnit = end,
                    displayOrder = 0, // filled below
                )
                itemsByDay.getOrPut(dayIndex) { mutableListOf() }.add(item)
            }
        }

        val days = itemsByDay.map { (dayIndex, rawItems) ->
            val dayTotalUnits = rawItems.sumOf { it.plannedUnits }
            var allocated = 0
            val items = rawItems.mapIndexed { index, item ->
                val minutes = if (index == rawItems.lastIndex) {
                    max(1, capMinutes - allocated)
                } else {
                    max(1, capMinutes * item.plannedUnits / dayTotalUnits)
                }
                allocated += minutes
                item.copy(estimatedMinutes = minutes, displayOrder = index)
            }
            PlanScheduleDay(planDate = today.plusDays(dayIndex.toLong()), items = items)
        }

        return PlanSchedule(days = days)
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests "com.example.planit.plan.ScheduleCalculatorTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/example/planit/plan/application/PlanGenerationSupport.kt \
        src/main/kotlin/com/example/planit/plan/application/PlanSchedule.kt \
        src/main/kotlin/com/example/planit/plan/application/ScheduleCalculator.kt \
        src/test/kotlin/com/example/planit/plan/ScheduleCalculatorTest.kt
git commit -m "feat: add deterministic schedule calculator (stage 1)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: 배치 인터페이스 + RuleBasedArrangementProvider (2단계 기본/fallback)

**Files:**
- Create: `src/main/kotlin/com/example/planit/plan/application/PlanArrangementProvider.kt`
- Create: `src/main/kotlin/com/example/planit/plan/application/RuleBasedArrangementProvider.kt`
- Test: `src/test/kotlin/com/example/planit/plan/RuleBasedArrangementProviderTest.kt`

**Interfaces:**
- Consumes: `PlanSchedule`, `PlanScheduleDay`, `PlanScheduleItem`, `ArrangementContext`, `ArrangementSubject`, `PlanGenerationSupport` (Task 2).
- Produces:
  - `interface PlanArrangementProvider { fun arrange(schedule: PlanSchedule, context: ArrangementContext): PlanSchedule }`
  - `@Component class RuleBasedArrangementProvider : PlanArrangementProvider` — 각 날짜 항목을 (어려운 과목 우선 → 난이도 내림차순 → 과목명) 정렬, `displayOrder` 0..n 재부여, `priority`는 context 난이도에서 파생. 숫자 불변.

- [ ] **Step 1: 인터페이스 작성**

Create `PlanArrangementProvider.kt`:

```kotlin
package com.example.planit.plan.application

interface PlanArrangementProvider {
    /** 숫자(units, range, minutes)는 유지한 채, 각 날짜 항목의 순서와 priority만 조정한다. */
    fun arrange(schedule: PlanSchedule, context: ArrangementContext): PlanSchedule
}
```

- [ ] **Step 2: 실패 테스트 작성**

Create `RuleBasedArrangementProviderTest.kt`:

```kotlin
package com.example.planit.plan

import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.exam.domain.ScopeUnitType
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.exam.domain.TargetExamType
import com.example.planit.plan.application.ArrangementContext
import com.example.planit.plan.application.ArrangementSubject
import com.example.planit.plan.application.PlanSchedule
import com.example.planit.plan.application.PlanScheduleDay
import com.example.planit.plan.application.PlanScheduleItem
import com.example.planit.plan.application.RuleBasedArrangementProvider
import com.example.planit.plan.domain.PlanItemPriority
import com.example.planit.user.domain.UserAccount
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RuleBasedArrangementProviderTest {

    private val provider = RuleBasedArrangementProvider()

    @Test
    fun `orders difficult and harder subjects first and reassigns order`() {
        val schedule = PlanSchedule(
            days = listOf(
                PlanScheduleDay(
                    planDate = LocalDate.now(),
                    items = listOf(
                        item("영어", PlanItemPriority.MEDIUM, order = 0),
                        item("수학", PlanItemPriority.LOW, order = 1),
                    ),
                ),
            ),
        )
        val context = ArrangementContext(
            age = 18,
            schoolLevel = "HIGH_SCHOOL",
            preferredStudyMethod = "BALANCED",
            subjects = listOf(
                ArrangementSubject("영어", DifficultyLevel.MEDIUM, isDifficult = false),
                ArrangementSubject("수학", DifficultyLevel.HIGH, isDifficult = true),
            ),
        )

        val result = provider.arrange(schedule, context)

        val ordered = result.days.single().items.sortedBy { it.displayOrder }
        assertThat(ordered.map { it.subjectName }).containsExactly("수학", "영어")
        assertThat(ordered[0].displayOrder).isEqualTo(0)
        assertThat(ordered[1].displayOrder).isEqualTo(1)
        assertThat(ordered[0].priority).isEqualTo(PlanItemPriority.HIGH)
    }

    @Test
    fun `keeps numbers unchanged`() {
        val original = item("수학", PlanItemPriority.LOW, order = 0, units = 5, minutes = 120, start = 1, end = 5)
        val schedule = PlanSchedule(days = listOf(PlanScheduleDay(LocalDate.now(), listOf(original))))
        val context = ArrangementContext(
            null, null, "BALANCED",
            listOf(ArrangementSubject("수학", DifficultyLevel.HIGH, isDifficult = false)),
        )

        val result = provider.arrange(schedule, context).days.single().items.single()

        assertThat(result.plannedUnits).isEqualTo(5)
        assertThat(result.estimatedMinutes).isEqualTo(120)
        assertThat(result.startUnit).isEqualTo(1)
        assertThat(result.endUnit).isEqualTo(5)
        assertThat(result.rangeText).isEqualTo(original.rangeText)
    }

    private fun item(
        name: String,
        priority: PlanItemPriority,
        order: Int,
        units: Int = 1,
        minutes: Int = 60,
        start: Int = 1,
        end: Int = 1,
    ): PlanScheduleItem =
        PlanScheduleItem(
            scope = dummyScope(name),
            subjectName = name,
            rangeText = "$start~$end쪽",
            studyMethod = "개념 정리",
            priority = priority,
            plannedUnits = units,
            estimatedMinutes = minutes,
            startUnit = start,
            endUnit = end,
            displayOrder = order,
        )

    private fun dummyScope(name: String): SubjectScope =
        SubjectScope(
            examPlan = ExamPlan(
                user = UserAccount(name = "학생", email = "s@e.com", passwordHash = "x"),
                targetExamType = TargetExamType.CSAT,
                targetExamLabel = "수능",
                examDate = LocalDate.now().plusDays(10),
                status = ExamPlanStatus.ACTIVE,
            ),
            subjectName = name,
            rawRangeText = "$name 범위",
            preferredMethodNote = "개념 정리",
            difficulty = DifficultyLevel.MEDIUM,
            unitType = ScopeUnitType.PAGE,
            totalUnits = 10,
            remainingUnits = 10,
        )
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests "com.example.planit.plan.RuleBasedArrangementProviderTest"`
Expected: FAIL (`RuleBasedArrangementProvider` 미구현).

- [ ] **Step 4: 구현 작성**

Create `RuleBasedArrangementProvider.kt`:

```kotlin
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
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.example.planit.plan.RuleBasedArrangementProviderTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/example/planit/plan/application/PlanArrangementProvider.kt \
        src/main/kotlin/com/example/planit/plan/application/RuleBasedArrangementProvider.kt \
        src/test/kotlin/com/example/planit/plan/RuleBasedArrangementProviderTest.kt
git commit -m "feat: add rule-based plan arrangement (stage 2 default)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: AiClient + AiArrangementProvider + 설정 (모델 후연결)

**Files:**
- Create: `src/main/kotlin/com/example/planit/plan/application/AiClient.kt`
- Create: `src/main/kotlin/com/example/planit/plan/application/AiArrangementProvider.kt`
- Create: `src/main/kotlin/com/example/planit/plan/config/AiArrangementProperties.kt`
- Create: `src/main/kotlin/com/example/planit/plan/config/PlanArrangementConfig.kt`
- Test: `src/test/kotlin/com/example/planit/plan/AiArrangementProviderTest.kt`

**Interfaces:**
- Consumes: `PlanArrangementProvider`, `RuleBasedArrangementProvider`, `PlanSchedule`, `ArrangementContext`, `ObjectMapper`.
- Produces:
  - `interface AiClient { fun complete(prompt: String): String }`
  - `class AiArrangementProvider(aiClient: AiClient, fallback: RuleBasedArrangementProvider, objectMapper: ObjectMapper) : PlanArrangementProvider` — 프롬프트 조립 → `aiClient.complete` → JSON 파싱/검증 → 순서·priority 적용. 실패(파싱/검증/예외) 시 `fallback.arrange(...)`. **AI가 준 값으로 숫자를 만들지 않는다.**
  - `@ConfigurationProperties("planit.ai.arrangement") data class AiArrangementProperties(val mode: String = "rule-based", val timeoutSeconds: Long = 20)`
  - `@Configuration @EnableConfigurationProperties(AiArrangementProperties::class) class PlanArrangementConfig` — `@Primary @Bean fun activeArrangementProvider(...)`가 `mode == "ai"` 이고 `AiClient` 빈이 있으면 `AiArrangementProvider`, 아니면 `RuleBasedArrangementProvider`.

- [ ] **Step 1: AiClient 인터페이스 작성**

Create `AiClient.kt`:

```kotlin
package com.example.planit.plan.application

/**
 * 배치 단계에서 외부 모델을 호출하는 유일한 접점.
 * 무료 모델이 정해지면 이 인터페이스의 구현체를 @Bean 으로 추가하고
 * application.yaml 의 planit.ai.arrangement.mode 를 "ai" 로 바꾼다.
 * 인증키는 환경변수로 주입하고 코드/깃에 넣지 않는다.
 */
interface AiClient {
    fun complete(prompt: String): String
}
```

- [ ] **Step 2: 실패 테스트 작성**

Create `AiArrangementProviderTest.kt`:

```kotlin
package com.example.planit.plan

import com.example.planit.exam.domain.DifficultyLevel
import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.exam.domain.ScopeUnitType
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.exam.domain.TargetExamType
import com.example.planit.plan.application.AiArrangementProvider
import com.example.planit.plan.application.AiClient
import com.example.planit.plan.application.ArrangementContext
import com.example.planit.plan.application.ArrangementSubject
import com.example.planit.plan.application.PlanSchedule
import com.example.planit.plan.application.PlanScheduleDay
import com.example.planit.plan.application.PlanScheduleItem
import com.example.planit.plan.application.RuleBasedArrangementProvider
import com.example.planit.plan.domain.PlanItemPriority
import com.example.planit.user.domain.UserAccount
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AiArrangementProviderTest {

    private val objectMapper = ObjectMapper()
    private val fallback = RuleBasedArrangementProvider()
    private val today: LocalDate = LocalDate.now()

    @Test
    fun `applies AI order and priority without changing numbers`() {
        val client = AiClient {
            """
            { "days": [ { "date": "$today", "order": ["수학", "영어"],
              "priorities": { "수학": "HIGH", "영어": "LOW" } } ] }
            """.trimIndent()
        }
        val provider = AiArrangementProvider(client, fallback, objectMapper)

        val result = provider.arrange(twoSubjectSchedule(), context())

        val items = result.days.single().items.sortedBy { it.displayOrder }
        assertThat(items.map { it.subjectName }).containsExactly("수학", "영어")
        assertThat(items[0].priority).isEqualTo(PlanItemPriority.HIGH)
        assertThat(items.first { it.subjectName == "수학" }.plannedUnits).isEqualTo(5)
        assertThat(items.first { it.subjectName == "영어" }.plannedUnits).isEqualTo(10)
    }

    @Test
    fun `falls back to rule-based on invalid json`() {
        val client = AiClient { "not json at all" }
        val provider = AiArrangementProvider(client, fallback, objectMapper)

        val result = provider.arrange(twoSubjectSchedule(), context())

        // rule-based orders difficult 수학(HIGH, isDifficult) first
        assertThat(result.days.single().items.sortedBy { it.displayOrder }.map { it.subjectName })
            .containsExactly("수학", "영어")
    }

    @Test
    fun `falls back when order is not a permutation of the day's subjects`() {
        val client = AiClient {
            """{ "days": [ { "date": "$today", "order": ["수학"], "priorities": {} } ] }"""
        }
        val provider = AiArrangementProvider(client, fallback, objectMapper)

        val result = provider.arrange(twoSubjectSchedule(), context())

        assertThat(result.days.single().items).hasSize(2)
    }

    @Test
    fun `falls back when client throws`() {
        val client = AiClient { throw RuntimeException("timeout") }
        val provider = AiArrangementProvider(client, fallback, objectMapper)

        val result = provider.arrange(twoSubjectSchedule(), context())

        assertThat(result.days.single().items).hasSize(2)
    }

    private fun twoSubjectSchedule(): PlanSchedule =
        PlanSchedule(
            days = listOf(
                PlanScheduleDay(
                    planDate = today,
                    items = listOf(
                        item("영어", 10, order = 0),
                        item("수학", 5, order = 1),
                    ),
                ),
            ),
        )

    private fun context(): ArrangementContext =
        ArrangementContext(
            age = 18,
            schoolLevel = "HIGH_SCHOOL",
            preferredStudyMethod = "BALANCED",
            subjects = listOf(
                ArrangementSubject("수학", DifficultyLevel.HIGH, isDifficult = true),
                ArrangementSubject("영어", DifficultyLevel.MEDIUM, isDifficult = false),
            ),
        )

    private fun item(name: String, units: Int, order: Int): PlanScheduleItem =
        PlanScheduleItem(
            scope = dummyScope(name),
            subjectName = name,
            rangeText = "1~$units",
            studyMethod = "개념 정리",
            priority = PlanItemPriority.MEDIUM,
            plannedUnits = units,
            estimatedMinutes = 60,
            startUnit = 1,
            endUnit = units,
            displayOrder = order,
        )

    private fun dummyScope(name: String): SubjectScope =
        SubjectScope(
            examPlan = ExamPlan(
                user = UserAccount(name = "학생", email = "s@e.com", passwordHash = "x"),
                targetExamType = TargetExamType.CSAT,
                targetExamLabel = "수능",
                examDate = today.plusDays(10),
                status = ExamPlanStatus.ACTIVE,
            ),
            subjectName = name,
            rawRangeText = "$name 범위",
            preferredMethodNote = "개념 정리",
            difficulty = DifficultyLevel.MEDIUM,
            unitType = ScopeUnitType.PAGE,
            totalUnits = 10,
            remainingUnits = 10,
        )
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests "com.example.planit.plan.AiArrangementProviderTest"`
Expected: FAIL (`AiArrangementProvider` 미구현).

- [ ] **Step 4: AiArrangementProvider 구현**

Create `AiArrangementProvider.kt`:

```kotlin
package com.example.planit.plan.application

import com.example.planit.plan.application.PlanGenerationSupport.normalize
import com.example.planit.plan.domain.PlanItemPriority
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

class AiArrangementProvider(
    private val aiClient: AiClient,
    private val fallback: RuleBasedArrangementProvider,
    private val objectMapper: ObjectMapper,
) : PlanArrangementProvider {

    override fun arrange(schedule: PlanSchedule, context: ArrangementContext): PlanSchedule =
        runCatching { applyAi(schedule, context) }
            .getOrElse { fallback.arrange(schedule, context) }

    private fun applyAi(schedule: PlanSchedule, context: ArrangementContext): PlanSchedule {
        val prompt = buildPrompt(schedule, context)
        val raw = aiClient.complete(prompt)
        val root = objectMapper.readTree(extractJson(raw))
        val daysNode = root["days"] as? ArrayNode ?: error("missing days")

        val arrangementByDate = daysNode.associate { dayNode ->
            val date = dayNode["date"].asText()
            val order = (dayNode["order"] as ArrayNode).map { it.asText() }
            val priorities = (dayNode["priorities"] as? ObjectNode)?.let { node ->
                node.fields().asSequence().associate { it.key to it.value.asText() }
            } ?: emptyMap()
            date to DayArrangement(order, priorities)
        }

        // 날짜 집합 일치 검증
        val scheduleDates = schedule.days.map { it.planDate.toString() }.toSet()
        require(arrangementByDate.keys == scheduleDates) { "day set mismatch" }

        val days = schedule.days.map { day ->
            val arrangement = arrangementByDate.getValue(day.planDate.toString())
            val bySubject = day.items.associateBy { it.subjectName }

            // order 는 그날 과목의 순열이어야 함
            require(arrangement.order.toSet() == bySubject.keys) { "order not a permutation" }

            val ordered = arrangement.order.mapIndexed { index, subjectName ->
                val item = bySubject.getValue(subjectName)
                val priority = arrangement.priorities[subjectName]?.let { parsePriority(it) } ?: item.priority
                item.copy(displayOrder = index, priority = priority)
            }
            day.copy(items = ordered)
        }
        return schedule.copy(days = days)
    }

    private fun parsePriority(raw: String): PlanItemPriority = enumValueOf(raw.trim())

    /** 모델이 코드펜스나 잡텍스트를 섞어 보낼 수 있어 첫 { ~ 마지막 } 만 취한다. */
    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "no json object" }
        return raw.substring(start, end + 1)
    }

    private fun buildPrompt(schedule: PlanSchedule, context: ArrangementContext): String {
        val input = objectMapper.createObjectNode().apply {
            putObject("student").apply {
                context.age?.let { put("age", it) }
                context.schoolLevel?.let { put("schoolLevel", it) }
                put("preferredMethod", context.preferredStudyMethod)
            }
            putArray("subjects").apply {
                context.subjects.forEach { s ->
                    addObject().apply {
                        put("name", s.subjectName)
                        put("difficulty", s.difficulty.name)
                        put("isDifficult", s.isDifficult)
                    }
                }
            }
            putArray("days").apply {
                schedule.days.forEach { day ->
                    addObject().apply {
                        put("date", day.planDate.toString())
                        putArray("items").apply {
                            day.items.forEach { item ->
                                addObject().apply {
                                    put("subject", item.subjectName)
                                    put("units", item.plannedUnits)
                                    put("range", item.rangeText)
                                }
                            }
                        }
                    }
                }
            }
        }
        val inputJson = objectMapper.writeValueAsString(input)
        return """
            너는 학생의 하루 학습 항목을 공부하기 좋은 순서로 배열하는 도우미다.
            아래 입력의 각 날짜에 대해, 그날 항목들을 학습 순서로 재배열하라.
            어려운 과목(isDifficult=true)이나 난이도 HIGH 과목은 집중력이 높은 앞쪽에 둔다.
            규칙:
            - 숫자(units, range)를 절대 바꾸지 마라. 항목을 추가하거나 빼지 마라.
            - 순서(order)와 priority(HIGH|MEDIUM|LOW)만 결정하라.
            - 반드시 아래 형식의 JSON만 출력하라. 다른 텍스트를 붙이지 마라.
            출력 형식:
            { "days": [ { "date": "YYYY-MM-DD", "order": ["과목명", ...], "priorities": { "과목명": "HIGH" } } ] }

            입력:
            $inputJson
        """.trimIndent()
    }

    private data class DayArrangement(
        val order: List<String>,
        val priorities: Map<String, String>,
    )
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.example.planit.plan.AiArrangementProviderTest"`
Expected: PASS.

- [ ] **Step 6: 프로퍼티 + 설정 빈 작성**

Create `AiArrangementProperties.kt`:

```kotlin
package com.example.planit.plan.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "planit.ai.arrangement")
data class AiArrangementProperties(
    val mode: String = "rule-based",
    val timeoutSeconds: Long = 20,
)
```

Create `PlanArrangementConfig.kt`:

```kotlin
package com.example.planit.plan.config

import com.example.planit.plan.application.AiArrangementProvider
import com.example.planit.plan.application.AiClient
import com.example.planit.plan.application.PlanArrangementProvider
import com.example.planit.plan.application.RuleBasedArrangementProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@EnableConfigurationProperties(AiArrangementProperties::class)
class PlanArrangementConfig {

    @Bean
    @Primary
    fun activeArrangementProvider(
        properties: AiArrangementProperties,
        ruleBased: RuleBasedArrangementProvider,
        aiClientProvider: ObjectProvider<AiClient>,
        objectMapper: ObjectMapper,
    ): PlanArrangementProvider {
        val aiClient = aiClientProvider.getIfAvailable()
        return if (properties.mode == "ai" && aiClient != null) {
            AiArrangementProvider(aiClient, ruleBased, objectMapper)
        } else {
            ruleBased
        }
    }
}
```

- [ ] **Step 7: 전체 테스트 통과 확인**

Run: `./gradlew test`
Expected: PASS (AiClient 빈이 없으므로 기본 `rule-based`; 기존 동작 무변경).

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/example/planit/plan/application/AiClient.kt \
        src/main/kotlin/com/example/planit/plan/application/AiArrangementProvider.kt \
        src/main/kotlin/com/example/planit/plan/config/AiArrangementProperties.kt \
        src/main/kotlin/com/example/planit/plan/config/PlanArrangementConfig.kt \
        src/test/kotlin/com/example/planit/plan/AiArrangementProviderTest.kt
git commit -m "feat: add AI arrangement provider with rule-based fallback

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: 파이프라인 결선 — 전체 기간 생성으로 전환

기존 단일 날짜 provider/persist를 2단계·다일자로 교체한다. 인터페이스 변경이라 이 태스크 안에서 한꺼번에 컴파일이 맞아야 한다.

**Files:**
- Modify: `src/main/kotlin/com/example/planit/plan/application/PlanGenerationProvider.kt`
- Create: `src/main/kotlin/com/example/planit/plan/application/TwoStagePlanGenerationProvider.kt`
- Delete: `src/main/kotlin/com/example/planit/plan/application/LocalRuleBasedPlanGenerationProvider.kt`
- Modify: `src/main/kotlin/com/example/planit/plan/application/PlanGenerationJobProcessor.kt`
- Modify: `src/main/kotlin/com/example/planit/plan/domain/DailyPlanRepository.kt`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/kotlin/com/example/planit/plan/PlanGenerationJobIntegrationTest.kt`

**Interfaces:**
- Consumes: `ScheduleCalculator`, `PlanArrangementProvider`(=`activeArrangementProvider` 빈), `PlanSchedule`, `ArrangementContext`, `StudyProfileRepository`.
- Produces:
  - `interface PlanGenerationProvider { fun generate(request: PlanGenerationJobCreateRequest, activePlan: ExamPlan, availableScopes: List<SubjectScope>, studyProfile: StudyProfile?): PlanSchedule }`
  - `@Component class TwoStagePlanGenerationProvider(scheduleCalculator, arrangementProvider) : PlanGenerationProvider`
  - `DailyPlanRepository.findAllByExamPlanIdAndPlanDateGreaterThanEqual(examPlanId: Long, planDate: LocalDate): List<DailyPlan>`

- [ ] **Step 1: 인터페이스 교체**

`PlanGenerationProvider.kt` 전체를 아래로 교체(기존 `GeneratedPlanDraft`/`GeneratedPlanDraftItem` 삭제):

```kotlin
package com.example.planit.plan.application

import com.example.planit.exam.domain.ExamPlan
import com.example.planit.exam.domain.SubjectScope
import com.example.planit.plan.api.PlanGenerationJobCreateRequest
import com.example.planit.user.domain.StudyProfile

interface PlanGenerationProvider {
    fun generate(
        request: PlanGenerationJobCreateRequest,
        activePlan: ExamPlan,
        availableScopes: List<SubjectScope>,
        studyProfile: StudyProfile?,
    ): PlanSchedule
}
```

- [ ] **Step 2: TwoStagePlanGenerationProvider 작성**

Create `TwoStagePlanGenerationProvider.kt`:

```kotlin
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
        val context = buildContext(request, studyProfile)
        return arrangementProvider.arrange(schedule, context)
    }

    private fun buildContext(request: PlanGenerationJobCreateRequest, profile: StudyProfile?): ArrangementContext {
        val difficultSet = request.difficultSubjects.map { normalize(it) }.toSet()
        return ArrangementContext(
            age = profile?.age,
            schoolLevel = profile?.schoolLevel?.name,
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
```

- [ ] **Step 3: 기존 provider 삭제**

```bash
git rm src/main/kotlin/com/example/planit/plan/application/LocalRuleBasedPlanGenerationProvider.kt
```

- [ ] **Step 4: repository 메서드 추가**

`DailyPlanRepository.kt`에 추가:

```kotlin
    fun findAllByExamPlanIdAndPlanDateGreaterThanEqual(examPlanId: Long, planDate: LocalDate): List<DailyPlan>
```

- [ ] **Step 5: Processor 다일자 persist로 교체**

`PlanGenerationJobProcessor.kt`를 아래로 교체:

```kotlin
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
```

> `DailyPlan.id`가 `null`일 수 있으니 `existing.forEach` 안 `plan.id!!`는 저장된 엔티티라 non-null 보장. `earliestPlan`/`todayPlan`는 `var ...?`로 두고 마지막에 non-null 반환.

- [ ] **Step 6: application.yaml 기본값 추가**

`planit:` 블록 아래(예: `security:` 다음)에 추가:

```yaml
  ai:
    arrangement:
      mode: rule-based
      timeout-seconds: 20
```

- [ ] **Step 7: 기존 통합 테스트 갱신 + 전체기간 검증 추가**

`PlanGenerationJobIntegrationTest.kt`의 첫 테스트(`create plan generation job and persist today's plan`)는 그대로 통과해야 한다(오늘 계획에 수학·영어 각 1유닛 = 2항목). 아래 테스트 메서드를 클래스에 **추가**한다:

```kotlin
    @Test
    fun `generates plans across the whole period until the last allocated day`() {
        val authToken = signupAndGetAccessToken("whole-period@example.com")
        upsertStudyProfile(authToken)
        upsertActivePlan(authToken)
        replaceSubjectScopes(authToken)

        val createResponse = mockMvc.post("/api/v1/plan-generation-jobs") {
            header("Authorization", "Bearer $authToken")
            contentType = MediaType.APPLICATION_JSON
            content = validGenerationRequest()
        }.andExpect { status { isAccepted() } }.andReturn().response.contentAsString
        awaitJobCompleted(authToken, objectMapper.readTree(createResponse)["data"]["jobId"].asText())

        // 영어 10문제 → 앞쪽 10일에 1문제씩. 수학 3단원 → 앞쪽 3일. 비어있는 날은 저장 안 됨.
        val history = mockMvc.get("/api/v1/plans/history/${LocalDate.now().plusDays(9)}") {
            header("Authorization", "Bearer $authToken")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val items = objectMapper.readTree(history)["data"]["items"]
        assertThat(items.map { it["subjectName"].asText() }).containsExactly("영어")
    }
```

> `GET /api/v1/plans/history/{date}` 응답 구조는 `PlanHistoryDtos.kt`/`PlanHistoryController.kt`로 확인해 필드명을 맞춘다. 만약 응답에 `items[].subjectName`이 없으면, 해당 컨트롤러의 실제 필드명으로 assertion을 교정한다. 핵심은 "10일차에 영어 항목만 존재"를 검증하는 것.

- [ ] **Step 8: 전체 테스트 통과 확인**

Run: `./gradlew test`
Expected: PASS. 실패 시 `DashboardAndHistoryIntegrationTest`, `TodayPlanApiIntegrationTest`가 이전 단일 날짜 가정에 기대고 있는지 확인하고, "오늘 계획이 존재하고 항목이 있다" 수준으로 완화하거나 새 동작에 맞게 값 조정. (오늘 계획 자체는 여전히 생성되므로 대부분 통과 예상.)

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: generate whole-period plan via two-stage pipeline

Replace single-day LocalRuleBasedPlanGenerationProvider with
TwoStagePlanGenerationProvider (deterministic ScheduleCalculator +
arrangement). Persist every allocated day; job points at today's plan.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**1. Spec coverage:**
- 2단계(범위÷기간 → 배치): Task 2(1단계) + Task 3/4(2단계) ✓
- 전체 기간·고정 스케줄(A): Task 5 persist ✓
- 숫자 코드 / 배치 AI 분담 + 숫자 불변식: ScheduleCalculator가 숫자, arrangement가 순서/priority만; RuleBased/Ai 테스트에서 숫자 불변 검증 ✓
- AI 실패 fallback: Task 4 `arrange` runCatching → fallback, 테스트 4종 ✓
- AiClient 후연결 + mode 토글: Task 4 config + Task 5 yaml ✓
- 데이터 모델(startUnit/endUnit/displayOrder): Task 1 ✓
- 인터페이스 계약 변경(단일→전체 기간): Task 5 Step 1 ✓
- 빈 날짜 미저장: ScheduleCalculator(`continue`/`itemsByDay`) + 테스트 `omits empty trailing days` ✓
- 재생성(오늘 이후 삭제 후 재작성): Task 5 persistSchedule + 기존 회귀 테스트 ✓
- 엣지(examDate 지남, 범위 없음): ScheduleCalculator 예외 + 테스트 ✓
- 절대 위치 한계/방법 코칭/복습 편성: 범위 밖(스펙 11절) — 태스크 없음이 맞음 ✓

**2. Placeholder scan:** 모든 스텝이 완전한 코드/명령을 포함. "TBD/TODO/적절히 처리" 류 없음.

**3. Type consistency:** `PlanSchedule`/`PlanScheduleDay`/`PlanScheduleItem`/`ArrangementContext`/`ArrangementSubject` 시그니처가 Task 2 정의와 Task 3·4·5 사용처에서 일치. `generate(...)` 4-인자 시그니처(+`studyProfile`)가 Task 5 인터페이스·구현·processor 호출부에서 일치. `AiArrangementProvider(aiClient, fallback, objectMapper)` 생성자가 Task 4 config·테스트와 일치.

---

## Addendum (2026-07-21): Gemini AiClient 연결됨

무료 모델 = Google Gemini 확정. 커밋 `0abf909`:
- `plan/application/GeminiAiClient.kt` — Spring `RestClient`, `x-goog-api-key` 헤더 인증, `candidates[0].content.parts[0].text` 추출. `@ConditionalOnProperty(planit.ai.arrangement.mode=ai)`.
- `plan/config/GeminiProperties.kt` — `planit.ai.gemini` (base-url / model 기본 `gemini-2.0-flash` / api-key=`${GEMINI_API_KEY:}`).
- `GeminiAiClientTest.kt` — `MockRestServiceServer`로 헤더·파싱 검증.
- 켜기: `GEMINI_API_KEY` env + `mode=ai`. 미검증: 실제 Gemini API 라이브 호출(현재 mock만).

## Execution Handoff
```
