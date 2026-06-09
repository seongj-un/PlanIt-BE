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
- 플랜 도메인 엔티티는 존재함: daily plan, daily plan item, completion event.
- 오늘 플랜, 대시보드, 기록, 플랜 생성 job API는 아직 미구현.

## Spec Gaps To Resolve

- `api-spec.md`의 `PUT /users/me/study-profile` 설명은 현재 구현과 다르다.
- 실제 구현은 프로필 입력과 시험 계획 입력이 분리되어 있다.
- 이후 기능 추가 전 명세와 현재 API 구조를 한번 맞추는 편이 안전하다.

## Pending Decisions

- 플랜 생성 AI의 정확한 Anthropic API 연동 방식.
- 프롬프트 입력 스키마.
- 모델 응답 JSON shape.
- 플랜 생성 job을 실제 비동기로 처리할지, 우선 동기 생성 후 job 상태만 흉내 낼지.
- 완료 체크 해제 시 새싹 회수 정책.

## Next Candidate Work

1. `api-spec.md`와 현재 온보딩 API 구조를 정리한다.
2. `plan-generation-jobs` MVP를 만든다.
3. 생성 결과로 `DailyPlan`, `DailyPlanItem`을 저장한다.
4. `GET /plans/today`와 `GET /plans/today/progress`를 만든다.
5. 항목 체크, 완료 처리, 기록 조회, 대시보드 API로 확장한다.
