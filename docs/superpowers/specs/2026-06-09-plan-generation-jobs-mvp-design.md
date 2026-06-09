# Plan Generation Jobs MVP Design

**Goal:** 온보딩 이후 오늘 플랜을 생성할 수 있는 `plan-generation-jobs` API를 추가한다.

## Scope

- `POST /api/v1/plan-generation-jobs`
- `GET /api/v1/plan-generation-jobs/{jobId}`
- 동기 생성이지만 외부 계약은 job API처럼 유지
- 생성 결과는 `DailyPlan` 1개와 `DailyPlanItem` 여러 개로 저장

## Non-goals

- 실제 비동기 큐 처리
- Anthropic API 연동
- 중간 진행률 상태 전이
- 복잡한 분량 분배 알고리즘

## API Contract

### POST

- 응답은 `202 Accepted`
- 응답 본문은 `jobId`, `status`, `estimatedSeconds`
- MVP에서는 내부적으로 같은 요청 안에서 플랜 생성까지 끝낸다.
- 외부 계약 유지를 위해 POST 응답의 상태는 `PENDING`으로 둔다.

### GET

- 저장된 job 상태를 반환
- MVP에서는 성공 시 즉시 `COMPLETED` 상태가 조회된다.

## Persistence

새 엔티티 `PlanGenerationJob`:

- `jobId: String`
- `user: UserAccount`
- `status: PlanGenerationJobStatus`
- `progressPercent: Int`
- `message: String`
- `planDate: LocalDate?`
- `generatedPlanId: Long?`

## Generation Rules

- 활성 시험 계획이 있어야 한다.
- 활성 시험 계획의 `SubjectScope`가 있어야 한다.
- 요청 subject는 활성 scope와 `subjectName` 기준으로 매칭한다.
- 같은 과목명이 중복될 수 있으므로 같은 scope는 한 번만 사용한다.
- 매칭 실패 시 `409 Conflict`
- `DailyPlan`은 오늘 날짜 기준으로 생성한다.
- 같은 날짜 플랜이 이미 있으면 기존 아이템을 지우고 같은 플랜 ID에 재생성한다.
- `DailyPlanItem` 생성 값:
  - `subjectNameSnapshot`: 요청 subject name
  - `rangeTextSnapshot`: 요청 exam range
  - `studyMethodSnapshot`: 요청 preferred method note
  - `priority`: difficulty 기준 `HIGH/MEDIUM/LOW`
  - `status`: `PENDING`
  - `plannedUnits`: 최소 1, 최대 scope `remainingUnits`
  - `estimatedMinutes`: `dailyMaxStudyHours`를 difficulty weight로 비례 배분
  - `manuallyAdjusted`: `false`

## Validation

- `subjects`는 비어 있으면 안 된다.
- `dailyMaxStudyHours`는 1 이상 24 이하여야 한다.
- 매칭된 scope의 `remainingUnits`는 1 이상이어야 한다.

## Future Swap Point

현재 생성 알고리즘은 서비스 내부 계산이다.
나중에 Anthropic Haiku를 붙일 때는 이 계산 부분만 별도 provider로 분리해 교체한다.
