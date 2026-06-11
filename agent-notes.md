# Agent Notes

이 파일은 Codex 작업 메모다.
사용자 요청, 구현 결정, 보류 중인 정보, 다음 작업 후보를 기록한다.
필요하면 이후 작업 중 자율적으로 갱신한다.

## Operating Rules

- 이 파일은 에이전트 전용 작업 노트처럼 사용한다.
- 다음 요청 정리, 사용자 선호 기록, 보류 중인 결정 사항 기록에 사용한다.
- 컨텍스트가 부족할 때 우선 이 파일을 참고해 작업 맥락을 복원한다.
- 최종 확정되지 않은 내용은 추정이 아니라 보류 항목으로 적는다.

## User Preferences

- 작업이 끝나면 실행한 작업을 정리한다.
- 가능하면 작업이 끝난 뒤 커밋과 푸시까지 진행한다.
- 계획 생성 AI는 현재 사용자 생각 기준으로 Anthropic Haiku를 사용할 가능성이 높다.
- Anthropic Haiku 관련 구체 구현 방식, 입력값, 출력 포맷은 나중에 사용자에게 다시 확인받는다.
- 이 파일은 내가 자율적으로 메모하고 사용하는 용도로 계속 유지해도 된다.

## Current Implementation Snapshot

- 인증 API 구현 완료: signup, login, refresh, logout.
- 온보딩 입력 API 구현 완료: study profile, active exam plan, subject scopes.
- 플랜 생성 job 구현 완료: provider 분리 + 실제 비동기 생성 + job 상태 조회 방식.
- 오늘 플랜 API 구현 완료: 조회, 일괄 수정, 체크/체크 해제, 완료 처리, 진행도 조회.
- 대시보드 API 구현 완료.
- 기록 API 구현 완료: 월별 기록, 특정 날짜 상세.
- 마이페이지 설정 API 구현 완료: study-settings, notification-settings, account.
- `GET /users/me` 확장 완료: 시험 정보, 누적 새싹, 출석 streak 포함.
- 플랜 도메인 엔티티와 집계 이벤트는 현재 API 흐름에 연결됨.
- 회귀 수정 반영 완료: 플랜 재생성 시 stale completion event 정리, today plan update blank validation, dashboard scopeSummary 정합성.
- 새싹 정책 확정 완료: 체크 해제 시 1개 회수, 재체크 시 다시 1개 지급.

## Spec Gaps To Resolve

- `api-spec.md`의 온보딩 구조는 실제 구현처럼 프로필 입력과 시험 계획 입력이 분리되어 있다.
- 문서와 실제 응답 필드는 이번 배치 기준으로 다시 맞췄다.

## Pending Decisions

- 플랜 생성 AI의 정확한 Anthropic API 연동 방식.
- 프롬프트 입력 스키마.
- 모델 응답 JSON shape.

## Next Candidate Work

1. `LocalRuleBasedPlanGenerationProvider` 자리에 Anthropic Haiku provider를 연결한다.
2. 프롬프트 입력 스키마와 모델 응답 JSON shape를 사용자와 확정한다.
3. AI 실패 시 재시도 정책과 job timeout 정책을 정한다.
4. 필요하면 `GET /users/me`에 알림 설정까지 포함할지 결정한다.
