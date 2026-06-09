# Plan Generation Jobs MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 오늘 플랜 생성을 위한 job API와 동기 생성 MVP를 추가한다.

**Architecture:** `plan` 모듈 안에 job 엔티티, API DTO, 컨트롤러, 서비스 를 추가한다. POST는 즉시 플랜을 생성하지만 외부에는 job 생성 API처럼 보이도록 하고, GET은 저장된 job 상태를 반환한다.

**Tech Stack:** Spring Boot, Kotlin, Spring MVC, Spring Data JPA, H2, MockMvc

---

### Task 1: Design And Test Skeleton

**Files:**
- Create: `src/test/kotlin/com/example/planit/plan/PlanGenerationJobIntegrationTest.kt`
- Modify: `agent-notes.md`

- [ ] Add failing integration tests for successful job creation and missing-scope failure.
- [ ] Run the new test class and confirm it fails because the endpoint is missing.

### Task 2: Job Persistence And API

**Files:**
- Create: `src/main/kotlin/com/example/planit/plan/domain/PlanGenerationJob.kt`
- Create: `src/main/kotlin/com/example/planit/plan/domain/PlanGenerationJobRepository.kt`
- Create: `src/main/kotlin/com/example/planit/plan/domain/PlanGenerationJobStatus.kt`
- Create: `src/main/kotlin/com/example/planit/plan/api/PlanGenerationDtos.kt`
- Create: `src/main/kotlin/com/example/planit/plan/api/PlanGenerationController.kt`

- [ ] Add the job entity and repository.
- [ ] Add POST and GET endpoints with DTOs.

### Task 3: Synchronous Generation Service

**Files:**
- Create: `src/main/kotlin/com/example/planit/plan/application/PlanGenerationService.kt`
- Modify: `src/main/kotlin/com/example/planit/plan/domain/DailyPlanItemRepository.kt`

- [ ] Implement validation, scope matching, same-day plan replacement, item generation, and job state persistence.
- [ ] Re-run the test class and confirm it passes.

### Task 4: Verification And Notes

**Files:**
- Modify: `agent-notes.md`

- [ ] Run `./gradlew test`
- [ ] Update notes with the new MVP behavior and remaining gaps.
- [ ] Commit the implementation.
