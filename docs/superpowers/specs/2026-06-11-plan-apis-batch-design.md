# Today Plan, Dashboard, History API Batch Design

**Goal:** plan generation 이후 남은 핵심 조회/수정 API를 한 번에 구현해 사용자 플로우를 닫는다.

## Scope

- `GET /api/v1/plans/today`
- `PUT /api/v1/plans/today`
- `PATCH /api/v1/plans/today/items/{planItemId}`
- `POST /api/v1/plans/today/complete`
- `GET /api/v1/plans/today/progress`
- `GET /api/v1/dashboard`
- `GET /api/v1/plans/history?month=yyyy-MM`
- `GET /api/v1/plans/history/{date}`

## Architecture

- `plan` 패키지 안에 오늘 플랜/기록 API를 둔다.
- `dashboard`는 별도 패키지를 추가하지 않고 현재 배치에서는 plan 데이터를 읽어 요약하는 API로 구현한다.
- 서비스는 세 개로 분리한다.
  - `TodayPlanService`
  - `DashboardService`
  - `PlanHistoryService`

## Data Rules

- 오늘 플랜은 `DailyPlan.planDate == LocalDate.now()` 기준이다.
- 진행도는 `DailyPlanItem.status` 기준으로 집계한다.
- 새싹 수는 `CompletionEvent.sproutDelta` 누적으로 계산한다.
- 오늘 획득 새싹은 오늘 날짜 플랜 아이템에 연결된 이벤트만 합산한다.
- 출석 streak는 `DailyPlan.status == COMPLETED` 인 날짜가 오늘부터 연속된 길이로 계산한다.
- attendance calendar는 현재 조회 월 기준 `completed` 여부만 내려준다.

## Today Plan Update Rules

- `PUT /plans/today`는 전체 저장 방식이다.
- 요청의 `planItemId`가 있으면 기존 항목을 수정한다.
- `planItemId`가 없으면 신규 생성한다.
- `deletedPlanItemIds`는 현재 오늘 플랜 소유 항목만 삭제한다.
- 신규 항목은 활성 시험 계획의 `SubjectScope`를 `subjectName` 기준으로 매칭해 연결한다.
- 매칭 실패 시 `409 SUBJECT_SCOPE_NOT_FOUND`

## Completion Rules

- `PATCH /plans/today/items/{planItemId}`에서 `false -> true`일 때만 `CompletionEvent(ITEM_CHECKED, sproutDelta=1)`를 생성한다.
- `true -> false`일 때는 새싹을 회수하지 않는다.
- `POST /plans/today/complete`는 미완료 항목이 남아 있으면 `409 PLAN_NOT_FINISHED`
- 완료 성공 시 `DailyPlan.status = COMPLETED`

## Response Simplifications

- `scopeSummary`는 `subjectNameSnapshot + ": " + studyMethodSnapshot` 조합으로 만든다.
- progress의 `label`은 `rangeTextSnapshot`을 사용한다.
- history day subjects는 해당 날짜 항목의 `subjectNameSnapshot` distinct 목록으로 만든다.

## Testing

- 오늘 플랜 라이프사이클 통합 테스트
- 대시보드/기록 통합 테스트
