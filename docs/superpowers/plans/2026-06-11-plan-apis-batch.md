# Today Plan, Dashboard, History API Batch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 오늘 플랜 생성 이후 남은 조회/수정/집계 API를 한 번에 구현한다.

**Architecture:** 오늘 플랜 조작, 대시보드 집계, 기록 조회를 별도 서비스로 나누고, 각 엔드포인트는 얇은 컨트롤러로 유지한다. 집계는 기존 `DailyPlan`, `DailyPlanItem`, `CompletionEvent`, `ExamPlan` 데이터를 조합해 계산한다.

**Tech Stack:** Spring Boot, Kotlin, Spring MVC, Spring Data JPA, H2, MockMvc

---

### Task 1: Red Tests For Today Plan Lifecycle

**Files:**
- Create: `src/test/kotlin/com/example/planit/plan/TodayPlanApiIntegrationTest.kt`
- Test: `src/test/kotlin/com/example/planit/plan/TodayPlanApiIntegrationTest.kt`

- [ ] Add failing tests for `GET /plans/today`, `GET /plans/today/progress`, `PUT /plans/today`, `PATCH /plans/today/items/{planItemId}`, and `POST /plans/today/complete`.
- [ ] Run the new test class and confirm it fails before implementation.

### Task 2: Red Tests For Dashboard And History

**Files:**
- Create: `src/test/kotlin/com/example/planit/plan/DashboardAndHistoryIntegrationTest.kt`
- Test: `src/test/kotlin/com/example/planit/plan/DashboardAndHistoryIntegrationTest.kt`

- [ ] Add failing tests for `GET /dashboard`, `GET /plans/history`, and `GET /plans/history/{date}`.
- [ ] Run the new test class and confirm it fails before implementation.

### Task 3: Implement Today Plan APIs

**Files:**
- Create: `src/main/kotlin/com/example/planit/plan/api/TodayPlanController.kt`
- Create: `src/main/kotlin/com/example/planit/plan/api/TodayPlanDtos.kt`
- Create: `src/main/kotlin/com/example/planit/plan/application/TodayPlanService.kt`
- Modify: `src/main/kotlin/com/example/planit/plan/domain/DailyPlanRepository.kt`
- Modify: `src/main/kotlin/com/example/planit/plan/domain/DailyPlanItemRepository.kt`
- Modify: `src/main/kotlin/com/example/planit/plan/domain/CompletionEventRepository.kt`
- Modify: `src/main/kotlin/com/example/planit/exam/domain/SubjectScopeRepository.kt`

- [ ] Implement fetch/update/toggle/complete/progress behavior.
- [ ] Re-run the today-plan test class and confirm it passes.

### Task 4: Implement Dashboard And History APIs

**Files:**
- Create: `src/main/kotlin/com/example/planit/plan/api/DashboardController.kt`
- Create: `src/main/kotlin/com/example/planit/plan/api/DashboardDtos.kt`
- Create: `src/main/kotlin/com/example/planit/plan/api/PlanHistoryController.kt`
- Create: `src/main/kotlin/com/example/planit/plan/api/PlanHistoryDtos.kt`
- Create: `src/main/kotlin/com/example/planit/plan/application/DashboardService.kt`
- Create: `src/main/kotlin/com/example/planit/plan/application/PlanHistoryService.kt`

- [ ] Implement dashboard summary and monthly/daily history queries.
- [ ] Re-run the dashboard/history test class and confirm it passes.

### Task 5: Verification And Notes

**Files:**
- Modify: `agent-notes.md`

- [ ] Run `./gradlew test`
- [ ] Update notes with the new API coverage and remaining gaps.
- [ ] Commit the batch.
