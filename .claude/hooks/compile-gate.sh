#!/bin/bash
# 컴파일 게이트
#   record: PostToolUse(Edit|Write)에서 바뀐 Kotlin·Gradle 파일 경로를 세션별로 기록한다.
#   check : Stop에서 기록된 파일이 속한 모듈만 컴파일한다. 실패하면 decision:block으로 오류를 돌려줘
#           Claude가 고치고 다시 마치게 한다. 공유 모듈(common, auth-contract)이나 루트 빌드 스크립트가
#           바뀌면 전체 모듈을 컴파일한다.
# 끄기: CLAUDE_SKIP_COMPILE_GATE=1 로 Claude Code를 시작하거나 /hooks에서 비활성화한다.
set -u
mode="${1:-}"
input="$(cat)"
[ "${CLAUDE_SKIP_COMPILE_GATE:-0}" = "1" ] && exit 0

project_dir="${CLAUDE_PROJECT_DIR:-$(pwd)}"
session_id="$(printf '%s' "$input" | jq -r '.session_id // "nosession"')"
state_dir="${TMPDIR:-/tmp}/claude-compile-gate"
mkdir -p "$state_dir"
touched="$state_dir/${session_id}.touched"
fails="$state_dir/${session_id}.fails"

case "$mode" in
  record)
    f="$(printf '%s' "$input" | jq -r '.tool_input.file_path // .tool_response.filePath // empty')"
    case "$f" in
      "$project_dir"/*.kt|"$project_dir"/*.kts) printf '%s\n' "$f" >> "$touched" ;;
    esac
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
        modules/*/*) m="${rel#modules/}"; tasks="$tasks :modules:${m%%/*}:testClasses" ;;
        *) full=1 ;;
      esac
    done < <(sort -u "$touched")
    [ "$full" = "1" ] && tasks="testClasses"
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
      jq -n --arg m "컴파일 게이트: ${tasks}컴파일이 3회 연속 실패해 더 막지 않습니다. 직접 확인해 주세요." '{systemMessage: $m}'
      exit 0
    fi
    errors="$(printf '%s\n' "$out" | grep -E '^e: |error:|What went wrong|^> ' | head -40)"
    [ -z "$errors" ] && errors="$(printf '%s\n' "$out" | tail -40)"
    jq -n --arg r "컴파일 게이트 실패 (${tasks% }). 아래 오류를 고친 뒤 다시 마치세요:
${errors}" '{decision: "block", reason: $r}'
    exit 0
    ;;
esac
exit 0
