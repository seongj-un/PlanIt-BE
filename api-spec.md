# PlanIt API 명세서

`project.md`에 있는 현재 필요한 화면/기능 기준 초안입니다.

## 1. 범위

포함 기능

- 회원가입
- 로그인 / 로그아웃
- 사용자 기본 정보 입력
- 시험 범위 사전 조사
- 플랜 생성 요청 / 생성 상태 조회
- 홈 대시보드 조회
- 오늘 플랜 조회
- 오늘 플랜 수정
- 플랜 항목 체크 / 체크 해제
- 오늘 플랜 완료 처리
- 진행도 조회
- 과거 플랜 기록 조회
- 새싹 / 출석 정보 조회
- 마이페이지 프로필 조회

제외 기능

- 앞으로의 예상 플랜 보기
- 복습 문제 출제

## 2. 공통 규칙

- Base URL: `/api/v1`
- 인증 방식: `Authorization: Bearer <accessToken>`
- Content-Type: `application/json`
- 날짜 형식: `yyyy-MM-dd`
- 시간대 기준: `Asia/Seoul`
- 플랜 생성은 로딩 화면이 있으므로 비동기 처리

## 3. 공통 응답 형식

성공

```json
{
  "success": true,
  "data": {},
  "meta": {}
}
```

실패

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "입력값을 확인해주세요.",
    "fieldErrors": [
      {
        "field": "email",
        "reason": "이미 사용 중인 이메일입니다."
      }
    ]
  }
}
```

## 4. 주요 Enum

| 이름 | 값 |
| --- | --- |
| `schoolLevel` | `MIDDLE_SCHOOL`, `HIGH_SCHOOL`, `RETAKER`, `ETC` |
| `targetExamType` | `SCHOOL_EXAM`, `MOCK_EXAM`, `CSAT`, `CERTIFICATE`, `ETC` |
| `preferredStudyMethod` | `CONCEPT_FIRST`, `PROBLEM_FIRST`, `BALANCED` |
| `priority` | `HIGH`, `MEDIUM`, `LOW` |
| `generationStatus` | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` |
| `planItemStatus` | `PENDING`, `COMPLETED` |

## 5. 화면-API 매핑

| 화면 | 용도 | API |
| --- | --- | --- |
| Splash / Welcome | 시작 진입 | 별도 API 없음 |
| Login | 로그인 | `POST /auth/login` |
| Sign Up | 회원가입 | `POST /auth/signup` |
| User Info Collection | 기본 정보 저장 | `PUT /users/me/study-profile` |
| Pre-study Survey | 범위/조건 입력 후 생성 요청 | `POST /plan-generation-jobs` |
| Plan Generation Loading | 생성 상태 조회 | `GET /plan-generation-jobs/{jobId}` |
| Home / Dashboard | 요약 조회 | `GET /dashboard` |
| Plan Detail | 오늘 플랜 상세 조회 | `GET /plans/today` |
| Plan Edit | 오늘 플랜 수정 | `PUT /plans/today` |
| Plan Check / Progress | 오늘 진행도 조회 | `GET /plans/today/progress` |
| Past Plans / History | 기록 조회 | `GET /plans/history` |
| My Page | 프로필/설정 조회 | `GET /users/me`, `POST /auth/logout` |

## 6. 인증 API

### 6.1 회원가입

`POST /auth/signup`

Request

```json
{
  "name": "김민지",
  "email": "minji@example.com",
  "password": "P@ssw0rd!"
}
```

Response `201 Created`

```json
{
  "success": true,
  "data": {
    "user": {
      "id": 1,
      "name": "김민지",
      "email": "minji@example.com",
      "onboardingCompleted": false
    },
    "tokens": {
      "accessToken": "jwt-access-token",
      "refreshToken": "jwt-refresh-token",
      "expiresIn": 3600
    }
  }
}
```

### 6.2 로그인

`POST /auth/login`

Request

```json
{
  "email": "minji@example.com",
  "password": "P@ssw0rd!"
}
```

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "user": {
      "id": 1,
      "name": "김민지",
      "onboardingCompleted": true
    },
    "tokens": {
      "accessToken": "jwt-access-token",
      "refreshToken": "jwt-refresh-token",
      "expiresIn": 3600
    }
  }
}
```

### 6.3 토큰 재발급

`POST /auth/refresh`

Request

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "accessToken": "new-jwt-access-token",
    "expiresIn": 3600
  }
}
```

### 6.4 로그아웃

`POST /auth/logout`

Request

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

Response `204 No Content`

## 7. 사용자 / 온보딩 API

### 7.1 내 정보 조회

`GET /users/me`

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "김민지",
    "email": "minji@example.com",
    "age": 18,
    "schoolLevel": "HIGH_SCHOOL",
    "targetExamType": "CSAT",
    "targetExamLabel": "수능",
    "examDate": "2026-06-12",
    "usualStudyHoursPerDay": 4,
    "preferredStudyMethod": "BALANCED",
    "sproutCount": 12,
    "attendanceStreakDays": 7,
    "onboardingCompleted": true
  }
}
```

### 7.2 기본 정보 저장 / 수정

`PUT /users/me/study-profile`

Request

```json
{
  "age": 18,
  "schoolLevel": "HIGH_SCHOOL",
  "usualStudyHoursPerDay": 4,
  "preferredStudyMethod": "BALANCED"
}
```

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "김민지",
    "email": "minji@example.com",
    "age": 18,
    "schoolLevel": "HIGH_SCHOOL",
    "targetExamType": null,
    "targetExamLabel": null,
    "examDate": null,
    "usualStudyHoursPerDay": 4,
    "preferredStudyMethod": "BALANCED",
    "sproutCount": 0,
    "attendanceStreakDays": 0,
    "onboardingCompleted": false
  }
}
```

시험 계획과 시험 범위 입력은 별도 API로 분리되어 있다.

- `PUT /exam-plans/active`
- `PUT /exam-plans/active/scopes`

## 8. 플랜 생성 API

### 8.1 플랜 생성 요청

`POST /plan-generation-jobs`

설명

- 시험 범위 입력 화면에서 호출
- 요청이 들어오면 비동기 생성 Job을 만들고 `202 Accepted` 반환

Request

```json
{
  "subjects": [
    {
      "subjectName": "수학",
      "examRange": "수열과 극한 1~3단원",
      "preferredMethodNote": "개념 정리 후 대표 문제 15문제",
      "difficulty": "HIGH"
    },
    {
      "subjectName": "영어",
      "examRange": "빈칸 추론, 순서 배열",
      "preferredMethodNote": "근거 문장 표시",
      "difficulty": "MEDIUM"
    },
    {
      "subjectName": "국어",
      "examRange": "독서 지문 10개",
      "preferredMethodNote": "문학/독서 균형",
      "difficulty": "MEDIUM"
    }
  ],
  "preferredStudyMethod": "BALANCED",
  "difficultSubjects": [
    "수학"
  ],
  "dailyMaxStudyHours": 5
}
```

Response `202 Accepted`

```json
{
  "success": true,
  "data": {
    "jobId": "plan-job-01JX123ABC",
    "status": "PENDING",
    "estimatedSeconds": 0
  }
}
```

### 8.2 플랜 생성 상태 조회

`GET /plan-generation-jobs/{jobId}`

Response `200 OK` - 진행 중

```json
{
  "success": true,
  "data": {
    "jobId": "plan-job-01JX123ABC",
    "status": "RUNNING",
    "progressPercent": 65,
    "message": "입력한 범위로 오늘 할 분량을 계산하고 있어요."
  }
}
```

Response `200 OK` - 완료

```json
{
  "success": true,
  "data": {
    "jobId": "plan-job-01JX123ABC",
    "status": "COMPLETED",
    "planDate": "2026-06-04",
    "planId": 101,
    "dashboardAvailable": true
  }
}
```

Response `200 OK` - 실패

```json
{
  "success": true,
  "data": {
    "jobId": "plan-job-01JX123ABC",
    "status": "FAILED",
    "progressPercent": 100,
    "message": "수학 과목 범위를 먼저 입력해주세요.",
    "dashboardAvailable": false
  }
}
```

## 9. 대시보드 API

### 9.1 홈 대시보드 조회

`GET /dashboard`

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "userName": "민지",
    "nextExam": {
      "label": "수능",
      "date": "2026-06-12",
      "dDay": 23
    },
    "todayPlan": {
      "planDate": "2026-06-04",
      "completedCount": 2,
      "totalCount": 4,
      "progressPercent": 50,
      "items": [
        {
          "planItemId": 9001,
          "subjectName": "수학",
          "scopeSummary": "수열과 극한: 개념 정리 + 문제 15문제",
          "completed": false
        },
        {
          "planItemId": 9002,
          "subjectName": "국어",
          "scopeSummary": "독해 유형 20문제",
          "completed": true
        }
      ]
    },
    "rewards": {
      "sproutCount": 12,
      "earnedToday": 2
    },
    "attendance": {
      "streakDays": 7,
      "calendar": [
        {
          "date": "2026-06-01",
          "completed": true
        },
        {
          "date": "2026-06-02",
          "completed": true
        }
      ]
    }
  }
}
```

## 10. 오늘 플랜 API

### 10.1 오늘 플랜 조회

`GET /plans/today`

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "planId": 101,
    "planDate": "2026-06-04",
    "status": "PENDING",
    "items": [
      {
        "planItemId": 9001,
        "subjectName": "수학",
        "examRange": "수열과 극한 3장 이상",
        "studyMethod": "개념 정리 후 대표 문제 15문제",
        "priority": "HIGH",
        "status": "PENDING",
        "estimatedMinutes": 70
      },
      {
        "planItemId": 9002,
        "subjectName": "영어",
        "examRange": "빈칸 추론 유형 15문제",
        "studyMethod": "근거 문장 밑줄 표시",
        "priority": "HIGH",
        "status": "PENDING",
        "estimatedMinutes": 50
      }
    ]
  }
}
```

### 10.2 오늘 플랜 일괄 수정

`PUT /plans/today`

설명

- 플랜 수정 화면에서 전체 항목을 저장
- 없는 `planItemId`는 신규 생성, `deletedPlanItemIds`는 삭제

Request

```json
{
  "items": [
    {
      "planItemId": 9001,
      "subjectName": "수학",
      "examRange": "수열과 극한 3장 이상",
      "studyMethod": "개념 정리 후 대표 문제 15문제",
      "priority": "HIGH"
    },
    {
      "subjectName": "영어",
      "examRange": "빈칸 추론 유형 15문제",
      "studyMethod": "근거 문장 밑줄 표시",
      "priority": "MEDIUM"
    }
  ],
  "deletedPlanItemIds": [
    9005
  ]
}
```

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "planId": 101,
    "updated": true,
    "itemCount": 2
  }
}
```

### 10.3 플랜 항목 체크 / 체크 해제

`PATCH /plans/today/items/{planItemId}`

Request

```json
{
  "completed": true
}
```

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "planItemId": 9001,
    "completed": true,
    "completedCount": 3,
    "totalCount": 4,
    "sproutAwarded": 1
  }
}
```

규칙

- `false -> true` 전환일 때만 새싹 1개 지급
- `true -> false` 전환 시 새싹 1개 회수
- 응답의 `sproutAwarded`는 변화량을 의미하며 `1`, `0`, `-1`이 될 수 있음

### 10.4 오늘 플랜 완료 처리

`POST /plans/today/complete`

설명

- 사용자가 `오늘 플랜 완료하기` 버튼을 눌렀을 때 호출
- 미완료 항목이 남아 있으면 `409 Conflict` 반환

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "planId": 101,
    "completed": true,
    "attendanceRecorded": true,
    "streakDays": 7
  }
}
```

### 10.5 오늘 진행도 조회

`GET /plans/today/progress`

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "completedCount": 2,
    "totalCount": 4,
    "completedItems": [
      {
        "planItemId": 9002,
        "subjectName": "국어",
        "label": "독해 유형 20문제"
      }
    ],
    "remainingItems": [
      {
        "planItemId": 9001,
        "subjectName": "수학",
        "label": "수열과 극한 3장 이상"
      }
    ],
    "sproutCount": 12,
    "sproutPerPlanItem": 1
  }
}
```

## 11. 기록 API

### 11.1 월별 기록 조회

`GET /plans/history?month=2026-06`

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "month": "2026-06",
    "stats": {
      "completedPlans": 42,
      "incompletePlans": 8
    },
    "days": [
      {
        "date": "2026-06-01",
        "completedCount": 4,
        "totalCount": 5,
        "completed": true,
        "subjects": [
          "수학",
          "영어",
          "국어",
          "과학"
        ]
      },
      {
        "date": "2026-06-02",
        "completedCount": 2,
        "totalCount": 4,
        "completed": false,
        "subjects": [
          "국어",
          "과학"
        ]
      }
    ]
  }
}
```

### 11.2 특정 날짜 기록 상세 조회

`GET /plans/history/{date}`

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "date": "2026-06-01",
    "completedCount": 4,
    "totalCount": 5,
    "items": [
      {
        "subjectName": "수학",
        "examRange": "미분과 적분 1단원",
        "studyMethod": "개념 정리 후 10문제",
        "completed": true
      }
    ]
  }
}
```

## 12. 마이페이지 / 설정 API

### 12.1 공부 설정 수정

`PATCH /users/me/study-settings`

Request

```json
{
  "usualStudyHoursPerDay": 5,
  "preferredStudyMethod": "CONCEPT_FIRST"
}
```

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "updated": true
  }
}
```

### 12.2 알림 설정 수정

`PATCH /users/me/notification-settings`

Request

```json
{
  "dailyReminderEnabled": true,
  "dailyReminderTime": "20:00"
}
```

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "updated": true
  }
}
```

### 12.3 계정 설정 수정

`PATCH /users/me/account`

Request

```json
{
  "name": "김민지",
  "password": "NewP@ssw0rd!"
}
```

Response `200 OK`

```json
{
  "success": true,
  "data": {
    "updated": true
  }
}
```

## 13. 에러 코드 초안

| code | 설명 |
| --- | --- |
| `VALIDATION_ERROR` | 요청값 검증 실패 |
| `UNAUTHORIZED` | 인증 실패 |
| `FORBIDDEN` | 권한 없음 |
| `USER_NOT_FOUND` | 사용자 없음 |
| `EMAIL_ALREADY_EXISTS` | 중복 이메일 |
| `PLAN_GENERATION_FAILED` | 플랜 생성 실패 |
| `PLAN_NOT_FOUND` | 플랜 없음 |
| `PLAN_ITEM_NOT_FOUND` | 플랜 항목 없음 |
| `PLAN_NOT_COMPLETABLE` | 미완료 항목이 남아 완료 처리 불가 |

## 14. 구현 메모

- 플랜 생성 결과는 현재 기능 범위상 `오늘 플랜` 중심으로 노출
- 미래 예상 플랜 API는 일부러 분리했고 이번 범위에서 만들지 않음
- `새싹`, `출석`, `진행도`는 대시보드와 상세 조회에서 중복 노출 가능
- 체크 해제 시 새싹 회수 정책은 확정 필요
