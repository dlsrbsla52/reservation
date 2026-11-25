# 규칙 대장 (lint·hook)

AGENTS.md의 규칙 중 기계가 판정할 수 있는 것을 빌드와 hook으로 강제한다. 목적은 AI가 문장 규칙을 해석하지 않고 빌드 실패 메시지를 보고 바로 고치게 하는 것이다. 그래서 위반 메시지는 규칙 ID, 대신 할 것, 참고 파일 순으로 쓴다.

## 장치

| 층 | 도구 | 위치 | 실행 |
|---|---|---|---|
| 컴파일러 | 예외 클래스에 `message` 파라미터가 없다 | `modules/common/.../exceptions` | `compileKotlin` |
| 바이트코드 | forbiddenapis 3.11 | `lint/forbidden-apis*.txt` | `forbiddenApisMain`·`forbiddenApisTest` (`check`에 포함) |
| 소스 | ktlint 1.8.0 + bus 규칙 | `lint/ktlint-rules`, `.editorconfig` | `ktlintCheck-<module>` (`check`에 포함), `ktlintFormat` |
| 구조 | 아키텍처 테스트 | `modules/app/src/test/.../architecture`, `AdminControllerAuthorizeCoverageTest` | `test` |
| hook | Stop·PreToolUse·PostToolUse | `.claude/hooks/` (Claude `.claude/settings.json`, Codex `.codex/hooks.json`) | Claude Code, Codex |

모듈 하나의 lint 묶음은 `./gradlew :modules:<module>:busLint`다. Stop hook(`compile-gate.sh`)이 바뀐 모듈마다 이 태스크를 부르므로, 도구를 늘릴 때는 루트 `build.gradle.kts`의 `busLint`에만 추가한다.

## 규칙

| ID | 판정 기준 | 범위 | 강제 | 자동 수정 |
|---|---|---|---|---|
| `bus:NoThreadLocal` | `ThreadLocal`·`InheritableThreadLocal` 참조(전체 경로·typealias 포함) | 전 모듈 main·test | forbiddenapis | 없음 |
| `bus:NoHardcodedExceptionMessage` | 예외를 문자열로 생성. `IllegalStateException`·`IllegalArgumentException`·`RuntimeException`·`Exception`·`NoSuchElementException`의 String 생성자. `error()`·`require {}`·`check {}`·`requireNotNull`·`checkNotNull`도 바이트코드에 이 생성자로 남는다 | 전 모듈 main | 컴파일러 + forbiddenapis | 없음 |
| `bus:no-hardcoded-exception-message` | 위와 같은 판정을 소스에서 한다. forbiddenapis가 모르는 외부 예외(`MalformedJwtException("…")`)까지 잡는다 | 전 모듈 main | ktlint | 없음 |
| `bus:NoMockito` | `org.mockito.**`, `@MockitoBean`·`@MockitoSpyBean` | 전 모듈 test | forbiddenapis | 없음 |
| `bus:comment-policy` | `//`·`/* */` 주석 금지(`// 예외:`·`language=`·`noinspection`만 허용). KDoc은 함수·클래스·object·보조 생성자·typealias에 한 줄만 | `app`·`iam`·`stop`·`reservation`·`content` main·test | ktlint | 주석 삭제, 여러 줄 KDoc은 첫 문장(제목이면 제목)으로 줄임 |
| `bus:test-method-name` | `@Test` 계열 메서드 이름이 백틱 + 한글 | 전 모듈 test | ktlint | 없음 |
| `bus:no-method-display-name` | 메서드의 `@DisplayName`(클래스는 허용) | 전 모듈 test | ktlint | 어노테이션 삭제 |
| `bus:entity-factory-name` | `*EntityClass` companion의 `of`·`from`·`newInstance` | 전 모듈 | ktlint | 없음 |
| ktlint 표준 | IntelliJ 스타일(`intellij_idea`). 끈 규칙과 이유는 `.editorconfig`에 있다 | 전 모듈 | ktlint | 있음 |

예외 메시지는 모듈 Result enum에 둔다: `CommonResult`(00000–00299), `AuthResult`(A), `StopResult`(S), `ReservationResult`(R), `ContractResult`(C), `ContentResult`(W). 디버깅에 필요한 값은 예외가 아니라 log로 남긴다.

## 결정 기록

- `BaseEnum` 대상에서 `Result` 구현 enum(결과 코드)은 뺀다. (2026-11-21)
- `app` 어댑터는 제공 모듈의 `entity.enums` import를 허용한다. 금지 대상은 `..repository..` 패키지와 Exposed `Table`·`Entity` 하위 타입이다. (2026-11-21)
- 포맷 기준은 지금 코드의 다수 스타일이고, 이는 IntelliJ 기본 Kotlin 스타일과 같다. IntelliJ Reformat이 재현하지 못하는 ktlint 규칙(줄 길이 기준 줄바꿈·합치기, trailing comma 강제 등)은 끈다. import 와일드카드는 IntelliJ Optimize Imports(같은 패키지 5개 이상, `java.util.*`)가 정한다.
- 주석 정책 도입 시(2026-11-21) 지운 주석은 git 이력에 있다. 운영 절차였던 내용(`StopHoldPostgresIT` 실행 방법)은 `modules/reservation/AGENTS.md`로 옮겼다.

## AI 우회 방지

- `lint-guard.sh`(PreToolUse): `lint/**`·`.editorconfig`·루트 `build.gradle.kts`의 bus-lint 블록 수정, `// 예외:` 주석이나 `@Suppress` 추가 시 사용자 확인을 받는다. 셸로 lint 파일을 고치는 명령도 같다.
  - Codex는 PreToolUse의 `ask`를 지원하지 않아 같은 경우를 거절(`deny`)하고 사용자에게 요청하라고 안내한다(`lint-guard.sh codex`). Codex 편집은 `apply_patch` 패치 텍스트에서 파일과 추가 줄을 읽는다.
- forbiddenapis는 `suppressAnnotations`를 비워 `@SuppressForbidden`류 우회를 막는다.
- Stop hook의 차단 메시지는 규칙·설정 파일을 고쳐 통과시키지 말라고 지시한다.

## IntelliJ

- Reformat Code·Optimize Imports는 `.editorconfig`의 `ij_kotlin_*` 값을 따른다(Settings > Editor > Code Style > "Enable EditorConfig support"가 켜져 있어야 한다).
- bus 규칙과 ktlint 표준 규칙을 편집기에서 보고 코드 정리로 고치려면 Marketplace의 "Ktlint" 플러그인을 설치하고 Settings > Tools > KtLint에서 다음을 지정한다.
  - Mode: Distract free(저장 시 자동 수정) 또는 Manual
  - External rule set JARs: `lint/ktlint-rules/build/libs/ktlint-rules-1.0.0.jar` (`./gradlew -p lint/ktlint-rules jar`로 만든다)
- 규칙 jar는 JDK 25 대상이다. IntelliJ 2026.3은 JBR 25에서 돌아 그대로 읽힌다.

## 규칙 추가 절차

1. 리뷰에서 같은 지적을 두 번 하면 이 표에 후보로 적는다.
2. 판정 기준을 기계가 판정할 수 있는 문장으로 쓴다. 모호하면 사용자에게 결정을 받는다.
3. 강제 위치를 고른다: JDK·라이브러리 API 사용은 forbiddenapis, 파일 하나의 구문은 ktlint bus 규칙, 여러 파일·리플렉션은 아키텍처 테스트.
4. 메시지를 "규칙 ID, 대신 할 것, 참고 파일"로 쓰고 위반 예제로 테스트한다(`lint/ktlint-rules/src/test`).
5. 기존 코드 위반은 묵인 목록(baseline)에 넣지 않고 고친다. AI는 주변 코드를 따라 쓴다.

후보: `BaseEnum`+`fromName` 아키텍처 테스트, 엔드포인트·권한 스냅샷 테스트, 로거 선언 형태(`private val log = LoggerFactory.getLogger(javaClass)`), compose 비밀값 기본값 금지.

## 원복

루트 `build.gradle.kts`·`settings.gradle.kts`의 `// >>> bus-lint` ~ `// <<< bus-lint` 블록, `lint/`, `.editorconfig`, `gradle/gradle-daemon-jvm.properties`, `.claude/hooks/lint-guard.sh`와 `.claude/settings.json`의 등록, `compile-gate.sh`의 `busLint` 호출을 되돌리면 도구가 빠진다. 코드 변경(예외 Result화, 주석 정리, 테스트 이름)은 별도 커밋으로 둔다.
