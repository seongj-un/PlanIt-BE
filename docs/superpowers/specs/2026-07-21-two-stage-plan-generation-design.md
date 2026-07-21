# 2단계 학습 계획 생성 설계 (범위·기간 → 학습 성향 배치)

- 작성일: 2026-07-21
- 대상: `plan` 모듈의 플랜 생성 로직
- 상태: 설계 확정, 구현 플랜 작성 전

## 1. 목적

공부할 **범위**(과목별 분량)와 **기간**(오늘 ~ 시험일)을 받아, 시험일까지의 **전체 일별 학습 계획**을 한 번에 생성한다. 계획 생성은 두 단계로 나눈다.

1. **범위 ÷ 기간** — 날짜별·과목별로 "며칠에 얼마나"를 계산한다. (결정적 코드)
2. **학습 성향 배치** — 계산된 분량을 어떤 순서로 배치할지 조정한다. (AI, 실패 시 규칙 기반 fallback)

핵심 불변식: **숫자(분량·범위·시간)는 1단계 코드에서만 결정된다. AI는 순서/배치만 바꾸고 숫자는 절대 건드리지 않는다.**

## 2. 용어

- **범위(scope)**: 시험 공부 분량. 예) 수학 1~101쪽 → 100쪽. 도메인의 `SubjectScope.remainingUnits`가 이 수치다.
- **기간(period)**: 오늘부터 시험 전날까지의 학습 가능 일수. `ExamPlan.examDate` 기준.
- **배치(arrangement)**: 하루 안에서 과목을 공부할 순서와, 각 항목의 우선순위 강조.

## 3. 현재 구조와의 차이

| | 현재 (`a883f70`) | 이 설계 |
|---|---|---|
| 생성 범위 | 오늘 하루치 `DailyPlan` 1개 | 오늘~시험 전날 **매일치 `DailyPlan` 전부** |
| 기간 사용 | 사용 안 함 (하루 시간만 배분) | `examDate`까지 남은 날로 범위를 분배 |
| 생성 방식 | 난이도 가중치로 하루 시간 배분 (단일 단계) | 2단계 (계산 → 배치) |
| AI | 없음 (`LocalRuleBasedPlanGenerationProvider`) | 배치 단계에만 AI, 모델은 후연결 |

"오늘 플랜 / 기록 / 대시보드" API는 **변경 없음** — 이미 `DailyPlan.planDate`로 날짜별 조회를 하므로, 전체 기간치를 미리 저장해두면 각 API는 해당 날짜 것을 읽기만 하면 된다.

## 4. 컴포넌트 구조

```
PlanGenerationJobProcessor
  └─ TwoStagePlanGenerationProvider (PlanGenerationProvider 구현)
       ├─ ScheduleCalculator          (1단계: 결정적)
       └─ PlanArrangementProvider     (2단계: 배치)
            ├─ RuleBasedArrangementProvider  (기본/fallback, 결정적)
            └─ AiArrangementProvider         (AI 호출)
                 └─ AiClient (인터페이스) — 모델별 구현체를 후연결
```

> **인터페이스 계약 변경**: 현재 `PlanGenerationProvider.generate(...)`는 단일 날짜 `GeneratedPlanDraft`(planDate + items)를 반환한다. 이 설계에서는 전체 기간을 반환하도록 바꾼다 — `GeneratedSchedule(days: List<GeneratedDailyPlan>)`, `GeneratedDailyPlan(planDate, items)`. `GeneratedPlanDraftItem`에는 `startUnit`, `endUnit`, `displayOrder`가 추가된다. 기존 `LocalRuleBasedPlanGenerationProvider`는 `TwoStagePlanGenerationProvider`로 대체된다(1·2단계 조합).

### 4.1 ScheduleCalculator (1단계)

- 입력: `PlanGenerationJobCreateRequest`, `ExamPlan`, `List<SubjectScope>`
- 출력: `GeneratedSchedule` — 날짜별·과목별로 완전히 숫자가 채워진 스켈레톤
- 로직:
  - 학습 가능 일수 = 오늘 ~ `examDate` **전날**까지 (시험 당일은 새 진도 없음). 최소 1일.
  - 각 과목의 `remainingUnits`를 학습 일수로 균등 분배. 나머지는 앞쪽 날짜부터 1씩 추가.
  - 각 날짜 항목에 그 과목의 **누적 유닛 위치**(`startUnit`, `endUnit`, 1-based, 남은 범위 기준)를 기록.
  - 표시용 범위 문자열(`rangeText`)은 `startUnit`~`endUnit` + `unitType` 라벨로 생성. 예) "1~5쪽", "3~4단원".
  - 각 날짜의 총 학습 시간은 `dailyMaxStudyHours × 60`분을 상한으로, 그날 항목들에 유닛 수 비례로 배분(`estimatedMinutes`).
  - **빈 날짜는 생성하지 않는다.** 항목이 1개 이상인 날짜만 `GeneratedDailyPlan`으로 만든다. `remainingUnits`가 학습 일수보다 작으면 앞쪽 날짜에만 배치되고 그 이후는 빈 날이 되는데, 이런 날은 스케줄에서 제외한다. (예: `examDate`가 수십 년 뒤여도 실제 생성 일수는 `max(remainingUnits)` 수준으로 제한됨 — 대량의 빈 `DailyPlan` 방지)
- 순수 함수에 가깝게(외부 I/O 없음) 구현해 단위 테스트로 숫자를 전부 검증한다.

### 4.2 PlanArrangementProvider (2단계)

- 입력: `GeneratedSchedule` + `ArrangementContext`(학습 성향)
  - `ArrangementContext`: `StudyProfile`(나이·학교급·선호 학습법), 과목별 `difficulty`, 어려운 과목 목록(`difficultSubjects`), `preferredStudyMethod`
- 출력: `ArrangedSchedule` — 각 날짜 항목의 **순서(`displayOrder`)와 우선순위(`priority`)만** 조정된 결과
- **숫자(units, range, minutes)는 입력 그대로 유지**한다.

#### RuleBasedArrangementProvider (기본 & fallback)
- 각 날짜 항목을 (어려운 과목 우선 → 난이도 높은 순 → 과목명) 으로 정렬해 순서를 부여.
- `priority`는 난이도에서 파생(HIGH/MEDIUM/LOW).
- 완전히 결정적. AI 미설정·실패 시 이 결과를 사용.

#### AiArrangementProvider (AI 호출)
- 프롬프트를 조립해 `AiClient.complete(prompt)` 호출 → 응답 JSON 파싱 → 검증 → 적용.
- 검증 실패·파싱 실패·타임아웃·예외 → **`RuleBasedArrangementProvider`로 위임**(fallback). job은 성공으로 끝낸다.

### 4.3 AiClient (모델 후연결 지점)

```kotlin
interface AiClient {
    /** 프롬프트를 모델에 보내고 원문 텍스트 응답을 반환. */
    fun complete(prompt: String): String
}
```

- 모델(무료 모델, 이름 미정)이 정해지면 이 인터페이스의 구현체 **하나만** 추가하면 된다.
- 프롬프트/응답 계약은 모델 독립적으로 설계한다(아래 5절).
- 구현체는 타임아웃(기본 20초)을 갖고, 인증키는 환경변수/`.env`로 주입한다. **키를 코드나 git에 넣지 않는다.**

## 5. AI 배치 계약 (모델 독립적)

### 입력 (프롬프트에 담는 JSON)
```json
{
  "student": { "age": 17, "schoolLevel": "HIGH_SCHOOL", "preferredMethod": "PROBLEM_SOLVING" },
  "subjects": [
    { "name": "수학", "difficulty": "HIGH", "isDifficult": true },
    { "name": "영어", "difficulty": "MEDIUM", "isDifficult": false }
  ],
  "days": [
    { "date": "2026-07-22", "items": [ { "subject": "수학", "units": 5, "range": "1~5쪽" }, { "subject": "영어", "units": 10, "range": "1~10쪽" } ] }
  ]
}
```

### 지시
- "각 날짜에 대해, 그날 항목들을 **공부하기 좋은 순서**로 재배열하라. 어려운 과목은 집중력이 높은 앞쪽에, 부담이 큰 과목은 기간 초반에 배치하라."
- "**숫자(units, range)는 절대 바꾸지 마라. 항목을 추가·삭제하지 마라. 순서와 priority만 결정하라.**"
- "반드시 JSON만 출력하라."

### 출력 (모델이 반환)
```json
{
  "days": [
    { "date": "2026-07-22", "order": ["수학", "영어"], "priorities": { "수학": "HIGH", "영어": "MEDIUM" } }
  ]
}
```

### 검증 (통과 못하면 fallback)
- `days`의 날짜 집합이 입력과 동일.
- 각 날짜의 `order`가 그날 과목의 **순열**(추가·누락 없음).
- `priorities` 값이 유효한 enum(HIGH/MEDIUM/LOW).
- 하나라도 어긋나면 그 생성 건 전체를 규칙 기반 배치로 대체.

## 6. 데이터 모델 변경

### DailyPlanItem (필드 추가)
- `startUnit: Int` — 그날 항목이 남은 범위에서 차지하는 시작 위치(1-based)
- `endUnit: Int` — 끝 위치
- `displayOrder: Int` — 그날 안에서의 학습 순서

기존 `rangeTextSnapshot`에는 그날 몫 문자열("1~5쪽")을 저장(현재는 전체 범위를 저장). `plannedUnits`, `estimatedMinutes`, `priority`, `manuallyAdjusted`는 유지.

### 절대 위치의 한계 (v1 결정)
`startUnit/endUnit`은 **남은 범위 기준 1-based 순번**이다. 과목 범위가 1쪽부터 시작하면 실제 페이지와 일치하지만, 101쪽부터 시작하는 범위라면 "1~5쪽"은 실제로 101~105쪽을 뜻한다. v1은 순번 기준으로 간다. 실제 시작 페이지 매핑은 `SubjectScope`에 시작 오프셋을 추가하는 별도 후속 과제로 남긴다.

### PlanGenerationJob 의미
- job은 전체 기간치 `DailyPlan`을 생성하되, `planDate`/`generatedPlanId`는 **오늘 날짜의 `DailyPlan`**을 가리킨다. (기존 "오늘 플랜" 링크·`dashboardAvailable` 동작 유지)
- 오늘 학습 분량이 0인 경우 가장 이른 학습일의 plan을 가리킨다.

## 7. 실패·재생성 정책

- **1단계**: 결정적이라 항상 성공. 입력이 불가능하면(아래 엣지) 명확한 에러로 job 실패.
- **2단계 AI 실패**: fallback(규칙 기반 배치)으로 흡수, job은 **성공**.
- **밀렸을 때**: 고정 스케줄이므로 자동 재조정 없음. 사용자가 **재생성(job 재실행)** 하면 그 시점의 `remainingUnits`와 남은 날 기준으로 다시 계산된다. (기존 재생성 흐름 재사용: 같은 날짜의 기존 `DailyPlan`/항목/완료이벤트를 정리 후 재저장)

## 8. 엣지 케이스

- `examDate <= 오늘` → `EXAM_DATE_PASSED` 에러(학습 가능 일수 0).
- 과목 `remainingUnits == 0` → 기존 검증 유지(범위 입력 필요/소진 에러).
- `remainingUnits < 학습일수` → 앞쪽 `remainingUnits`개 날짜에 1유닛씩 배치, 나머지 날짜엔 그 과목 항목 없음.
- 활성 시험 계획/과목 범위 미입력 → 기존 검증 유지.

## 9. 테스트 전략

- **ScheduleCalculator (단위)**: 균등 분배, 나머지 배분, 단일 일수, `remainingUnits < 일수`, 다과목, `examDate` 엣지 — 숫자 전부 검증.
- **RuleBasedArrangementProvider (단위)**: 정렬 순서·priority 파생.
- **AiArrangementProvider (단위, fake `AiClient`)**: 정상 JSON 적용 / 잘못된 JSON → fallback / 순열 아님 → fallback / 타임아웃·예외 → fallback. **숫자 불변 검증.**
- **통합**: job이 오늘~시험 전날 `DailyPlan` N개 생성; "오늘 플랜"이 오늘 것을 읽음; 기록이 과거/미래 것을 읽음; 재생성이 기존 것을 갈아끼움.

## 10. 설정

- `application.yaml`에 배치 provider 토글: `planit.ai.arrangement = rule-based | ai` (모델 확정 전 기본값 `rule-based`).
- AI 관련 설정(엔드포인트·모델명·타임아웃)은 프로퍼티로, 인증키는 환경변수로.

## 11. 범위 밖 (이번에 안 함)

- 실제 페이지 절대 오프셋 매핑 (`SubjectScope` 시작 오프셋).
- AI가 날짜 간 분량을 재분배하는 것(숫자 변경). v1은 **하루 안 순서 + priority**만.
- AI 학습 방법 제안(선호 학습법 기반 "어떻게" 코칭). 이번엔 순서 배치(B)만.
- 복습·반복일 자동 편성.
