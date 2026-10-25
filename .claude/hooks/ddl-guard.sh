#!/bin/bash
# DDL 가드: psql 등으로 스키마를 직접 바꾸는 Bash 명령을 막는다.
# 근거: AGENTS.md 코드 규칙 "DB 마이그레이션 — Liquibase changelog만 사용, 수동 DDL 금지"
input="$(cat)"
cmd="$(printf '%s' "$input" | jq -r '.tool_input.command // empty')"
printf '%s' "$cmd" | grep -qiE 'psql|pgcli|jdbc:postgresql' || exit 0
if printf '%s' "$cmd" | grep -qiE '(create|alter|drop|truncate)[[:space:]]+(table|schema|index|sequence|view|type|extension|database)|rename[[:space:]]+column'; then
  jq -n '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: "수동 DDL 금지(AGENTS.md 코드 규칙): 스키마 변경은 해당 모듈의 src/main/resources/db/changelog 아래 Liquibase changelog로 작성하세요. 로컬 DB를 직접 바꿔야 하면 사용자에게 요청하세요."}}'
fi
exit 0
