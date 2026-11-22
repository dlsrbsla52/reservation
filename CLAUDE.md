<!-- 지침 원본은 AGENTS.md(Codex·Antigravity 공유)다. 여기에는 Claude Code 전용 내용만 둔다. -->
@AGENTS.md

## Claude Code 전용

- 이 저장소의 `.claude/settings.json`에 검증 hook이 있다. hook을 우회하거나 끄지 않는다.
  - Kotlin·Gradle·lint 설정 파일을 고친 턴이 끝나면 Stop hook(`.claude/hooks/compile-gate.sh`)이 바뀐 모듈을 컴파일하고 `busLint`(forbiddenapis·ktlint)를 돌린다. 실패하면 오류가 전달되니 코드를 고치고 다시 마친다. `[bus:*]` 위반은 메시지의 대신 할 것과 참고 파일을 따른다.
  - lint 설정(`lint/**`·`.editorconfig`·bus-lint 블록)을 고치거나 `// 예외:` 주석·`@Suppress`를 넣으면 PreToolUse hook(`.claude/hooks/lint-guard.sh`)이 사용자 확인을 받는다. lint 위반을 이 방법으로 피하지 않는다.
  - `psql` 등으로 DDL을 직접 실행하면 PreToolUse hook(`.claude/hooks/ddl-guard.sh`)이 막는다. 스키마 변경은 Liquibase changelog로 한다.
- 컴파일 게이트를 일시적으로 꺼야 하면 사용자가 `CLAUDE_SKIP_COMPILE_GATE=1`로 Claude Code를 시작하거나 `/hooks`에서 비활성화한다.
