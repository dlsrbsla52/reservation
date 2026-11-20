#!/bin/bash
# lint 가드: AI가 lint 규칙을 우회하는 변경을 하려 하면 막는다.
#   - lint 설정 변경: lint/**, .editorconfig, 루트 build.gradle.kts의 bus-lint 블록
#   - 예외 표시 주석 `// 예외:` 새로 추가 (진짜 예외 케이스는 사용자만 승인한다)
#   - @Suppress / @file:Suppress 새로 추가
# 사용: lint-guard.sh [claude|codex]
#   claude(기본): 사용자 확인(ask)을 받는다.
#   codex: Codex PreToolUse는 ask를 지원하지 않아 거절(deny)하고 사용자에게 요청하라고 안내한다.
# 근거: docs/agents/rules.md "AI 우회 방지"
agent="${1:-claude}"
input="$(cat)"
tool="$(printf '%s' "$input" | jq -r '.tool_name // empty')"
cwd="$(printf '%s' "$input" | jq -r '.cwd // empty')"
[ -n "$cwd" ] || cwd="$(pwd)"
project_dir="${CLAUDE_PROJECT_DIR:-$(git -C "$cwd" rev-parse --show-toplevel 2>/dev/null || printf '%s' "$cwd")}"

decide() {
  if [ "$agent" = "codex" ]; then
    jq -n --arg r "lint 가드: $1 사용자 승인이 필요한 변경이라 막았다. 직접 우회하지 말고, 꼭 필요하면 이유와 함께 사용자에게 요청하라. lint 위반이라면 코드를 규칙에 맞게 고쳐라." \
      '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $r}}'
  else
    jq -n --arg r "lint 가드: $1 규칙 위반을 고치려고 규칙·설정을 바꾸는 것이라면 거절하고, 코드를 규칙에 맞게 고치세요." \
      '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "ask", permissionDecisionReason: $r}}'
  fi
  exit 0
}

markers='//[[:space:]]*예외:|@(file:)?Suppress\('
guarded_keywords='bus-lint|busLint|forbiddenapis|detekt|spotless|ktlint'

# 한 파일에 대한 변경(old·new 텍스트)을 검사한다.
check_change() {
  local path="$1" old="$2" new="$3" rel added
  rel="${path#"$project_dir"/}"
  case "$rel" in
    lint/*|.editorconfig) decide "lint 설정 파일($rel)을 바꾸려 한다." ;;
  esac
  if [ "$rel" = "build.gradle.kts" ] && printf '%s\n%s' "$old" "$new" | grep -qE "$guarded_keywords"; then
    decide "루트 build.gradle.kts의 lint 설정(bus-lint 블록)을 바꾸려 한다."
  fi
  case "$rel" in
    *.kt|*.kts)
      added="$(comm -13 <(printf '%s\n' "$old" | grep -E "$markers" | sed -E 's/^[[:space:]]+//' | sort -u) \
                        <(printf '%s\n' "$new" | grep -E "$markers" | sed -E 's/^[[:space:]]+//' | sort -u))"
      [ -n "$added" ] && decide "$rel 에 예외 주석 또는 @Suppress를 새로 넣으려 한다:
$added
"
      ;;
  esac
}

abs() { case "$1" in /*) printf '%s' "$1" ;; *) printf '%s' "$cwd/$1" ;; esac; }

case "$tool" in
  Edit|Write|MultiEdit)
    f="$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')"
    new="$(printf '%s' "$input" | jq -r '[.tool_input.new_string, .tool_input.content, (.tool_input.edits // [] | map(.new_string) | join("\n"))] | map(select(. != null)) | join("\n")')"
    if [ "$tool" = "Write" ] && [ -f "$f" ]; then
      old="$(cat "$f")"
    else
      old="$(printf '%s' "$input" | jq -r '[.tool_input.old_string, (.tool_input.edits // [] | map(.old_string) | join("\n"))] | map(select(. != null)) | join("\n")')"
    fi
    check_change "$f" "$old" "$new"
    ;;
  apply_patch)
    # Codex 패치: 파일별로 '+' 줄을 새 내용, '-' 줄을 옛 내용으로 본다(문맥 줄은 양쪽에 같으니 제외해도 된다).
    patch="$(printf '%s' "$input" | jq -r '.tool_input.command // .tool_input.input // .tool_input.patch // empty | strings')"
    current="" old="" new=""
    flush() { [ -n "$current" ] && check_change "$(abs "$current")" "$old" "$new"; }
    while IFS= read -r line; do
      case "$line" in
        '*** Add File: '*|'*** Update File: '*|'*** Delete File: '*)
          flush; current="${line#*: }"; old=""; new="" ;;
        '*** Move to: '*) flush; current="${line#*: }"; old=""; new="" ;;
        '*** '*) ;;
        +*) new="$new${line#+}"$'\n' ;;
        -*) old="$old${line#-}"$'\n' ;;
      esac
    done <<< "$patch"
    flush
    ;;
  Bash)
    cmd="$(printf '%s' "$input" | jq -r '.tool_input.command // empty | if type == "array" then join(" ") else . end')"
    if printf '%s' "$cmd" | grep -qE '(^|[[:space:]"/])(lint/|\.editorconfig)' \
      && printf '%s' "$cmd" | grep -qE 'sed -i|perl -[a-zA-Z0-9]*i|>[^&]|tee |mv |rm |cp |git (checkout|restore)|apply_patch'; then
      decide "셸 명령으로 lint 설정 파일을 바꾸려 한다."
    fi
    ;;
esac
exit 0
