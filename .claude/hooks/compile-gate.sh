#!/bin/bash
# 컴파일·lint 게이트
#   record: PostToolUse(Edit|Write)에서 바뀐 Kotlin·Gradle·lint 설정 파일 경로를 세션별로 기록한다.
#   check : Stop에서 기록된 파일이 속한 모듈만 컴파일하고 busLint(루트 build.gradle.kts의 lint 묶음)를 돌린다. 실패하면 decision:block으로 오류를 돌려줘
#           Claude가 고치고 다시 마치게 한다. 공유 모듈(common, auth-contract), 루트 빌드 스크립트, lint 설정이
#           바뀌면 전체 모듈을 검사한다. 검사 도구 목록은 busLint가 정하므로 도구를 늘려도 이 파일은 그대로 둔다.
# Claude Code(.claude/settings.json)와 Codex(.codex/hooks.json)가 같이 쓴다. 입력 형식 차이는 아래에서 흡수한다.
#   Claude: tool_input.file_path / Codex: apply_patch의 tool_input.command(패치 텍스트) 안 "*** Update File: <경로>"
# 끄기: CLAUDE_SKIP_COMPILE_GATE=1 로 에이전트를 시작하거나 /hooks에서 비활성화한다.
set -u
mode="${1:-}"
input="$(cat)"
[ "${CLAUDE_SKIP_COMPILE_GATE:-0}" = "1" ] && exit 0

cwd="$(printf '%s' "$input" | jq -r '.cwd // empty')"
[ -n "$cwd" ] || cwd="$(pwd)"
project_dir="${CLAUDE_PROJECT_DIR:-$(git -C "$cwd" rev-parse --show-toplevel 2>/dev/null || printf '%s' "$cwd")}"
session_id="$(printf '%s' "$input" | jq -r '.session_id // "nosession"')"

# 편집된 파일의 절대 경로를 한 줄씩 낸다(Claude Edit·Write, Codex apply_patch 모두).
edited_files() {
  printf '%s' "$input" | jq -r '.tool_input.file_path // .tool_response.filePath // empty'
  printf '%s' "$input" | jq -r '.tool_input.command // .tool_input.input // .tool_input.patch // empty | strings' \
    | sed -nE 's/^\*\*\* (Add File|Update File|Delete File|Move to): (.+)$/\2/p' \
    | while IFS= read -r p; do case "$p" in /*) printf '%s\n' "$p" ;; *) printf '%s\n' "$cwd/$p" ;; esac; done
}
state_dir="${TMPDIR:-/tmp}/claude-compile-gate"
mkdir -p "$state_dir"
touched="$state_dir/${session_id}.touched"
fails="$state_dir/${session_id}.fails"

case "$mode" in
  record)
    edited_files | while IFS= read -r f; do
      case "$f" in
        "$project_dir"/*.kt|"$project_dir"/*.kts|"$project_dir"/lint/*|"$project_dir"/.editorconfig) printf '%s\n' "$f" >> "$touched" ;;
      esac
    done
    exit 0
    ;;
  check)
    [ -s "$touched" ] || exit 0
    tasks=""
    full=0
    while IFS= read -r f; do
      rel="${f#"$project_dir"/}"
      case "$rel" in
        modules/common/*|modules/auth-contract/*) full=1 ;;
        modules/*/*) m="${rel#modules/}"; tasks="$tasks :modules:${m%%/*}:testClasses :modules:${m%%/*}:busLint" ;;
        *) full=1 ;;
      esac
    done < <(sort -u "$touched")
    [ "$full" = "1" ] && tasks="testClasses busLint"
    tasks="$(printf '%s\n' $tasks | sort -u | tr '\n' ' ')"

    cd "$project_dir" || exit 0
    out="$(./gradlew $tasks -q --console=plain 2>&1)"
    if [ $? -eq 0 ]; then
      rm -f "$touched" "$fails"
      exit 0
    fi

    # 같은 게이트가 3번 연속 실패하면 더 막지 않고 사용자에게 넘긴다(무한 반복 방지)
    n=$(( $(cat "$fails" 2>/dev/null || echo 0) + 1 ))
    echo "$n" > "$fails"
    if [ "$n" -ge 3 ]; then
      rm -f "$fails"
      jq -n --arg m "컴파일·lint 게이트: ${tasks}검사가 3회 연속 실패해 더 막지 않습니다. 직접 확인해 주세요." '{systemMessage: $m}'
      exit 0
    fi
    errors="$(printf '%s\n' "$out" | grep -E '^e: |error:|What went wrong|^> |^Forbidden |^  in |^[^ ]+\.kts?:[0-9]+:[0-9]+: |\[bus:' | head -60)"
    [ -z "$errors" ] && errors="$(printf '%s\n' "$out" | tail -40)"
    jq -n --arg r "컴파일·lint 게이트 실패 (${tasks% }). 아래 오류를 고친 뒤 다시 마치세요. [bus:*] 위반은 메시지의 '대신 할 것'과 참고 파일을 따르고, 규칙·설정 파일을 고쳐 통과시키지 않는다:
${errors}" '{decision: "block", reason: $r}'
    exit 0
    ;;
esac
exit 0
