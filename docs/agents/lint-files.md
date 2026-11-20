# lint 구성 파일 안내

2026-11-21 lint 작업으로 새로 생긴 파일 24개를 설명한다. 규칙 자체(무엇을 왜 막는지)는 [`rules.md`](rules.md)에 있고, 이 문서는 파일 기준으로 "이게 뭐고, 언제 손대는지"를 적는다.

## 한눈에 보기

```
코드를 저장한다 ─ IntelliJ ─────────────▶ .editorconfig + ktlint 플러그인(규칙 jar) → 저장 시 자동 정리
Claude가 파일을 고친다 ─ PreToolUse ────▶ .claude/hooks/lint-guard.sh → lint 설정·예외 주석 변경이면 사용자 확인
Claude가 턴을 끝낸다 ─ Stop ─────────────▶ compile-gate.sh → :modules:<m>:busLint → forbiddenapis + ktlint
빌드·CI ─ ./gradlew check ──────────────▶ 같은 검사 + 테스트
```

| 묶음 | 파일 수 | 직접 고칠 일 |
|---|---|---|
| 스타일·규칙 설정 | 4 | 규칙을 바꿀 때만 |
| ktlint 커스텀 규칙 모듈 | 16 | 새 규칙을 만들 때만 |
| 빌드 환경 | 2 | 거의 없음 |
| Claude hook | 1 | 거의 없음 |
| 코드 | 1 | 정류소 오류 코드를 추가할 때 |
| 문서 | 2(이 파일 포함) | 규칙이 바뀌면 |

## 1. 스타일·규칙 설정

| 파일 | 무엇 | 언제 고치나 |
|---|---|---|
| `.editorconfig` | 코드 스타일의 단일 원본. IntelliJ의 Reformat Code·Optimize Imports와 ktlint가 같이 읽는다. 들여쓰기·import 와일드카드 기준(IntelliJ 기본값)·끈 ktlint 규칙·bus 규칙의 적용 범위(어느 모듈·main/test)가 여기 있다 | 스타일을 바꾸거나 규칙 범위를 바꿀 때 |
| `lint/forbidden-apis.txt` | 컴파일된 코드에서 쓰면 안 되는 JDK API 목록(main·test 공통). 지금은 `ThreadLocal`·`InheritableThreadLocal` | 전 모듈에서 금지할 API가 생길 때 |
| `lint/forbidden-apis-main.txt` | main 코드 전용 금지 목록. 문자열로 예외를 만드는 생성자(`IllegalStateException("…")` 등). `error()`·`require {}`도 여기에 걸린다 | 거의 없음 |
| `lint/forbidden-apis-test.txt` | test 코드 전용 금지 목록. Mockito(MockK만 쓴다) | 테스트에서 금지할 라이브러리가 생길 때 |

각 줄의 `@` 뒤 문장이 위반 시 출력되는 메시지다. AI가 읽고 바로 고칠 수 있게 "대신 할 것 + 참고 파일" 형식으로 쓴다.

## 2. ktlint 커스텀 규칙 모듈 (`lint/ktlint-rules/`)

이 저장소 전용 ktlint 규칙(`bus:*`)을 만드는 작은 Gradle 빌드다. 메인 빌드가 `settings.gradle.kts`의 `includeBuild`로 가져와 jar로 만든 뒤 ktlint에 넣는다. IntelliJ ktlint 플러그인도 이 jar(`build/libs/ktlint-rules-1.0.0.jar`)를 읽는다.

메인 빌드의 하위 모듈로 두지 않은 이유: 루트 `subprojects` 블록이 Spring BOM을 걸어 Kotlin 라이브러리 버전을 바꾸는데, ktlint는 자기가 기대하는 버전이 따로 있다.

| 파일 | 무엇 |
|---|---|
| `settings.gradle.kts`, `build.gradle.kts` | 이 빌드의 설정. Kotlin 2.3.20, JDK 25, ktlint 1.8.0 API |
| `gradle/gradle-daemon-jvm.properties` | 이 폴더에서 단독으로 빌드해도 JDK 25 데몬을 쓰게 한다 |
| `src/main/.../BusRuleSetProvider.kt` | 규칙 묶음 등록(ktlint가 찾는 입구) |
| `src/main/.../BusRule.kt` | 규칙 공통 바탕(규칙 ID `bus:<이름>`) |
| `src/main/.../AstSupport.kt` | 주석을 줄째 지우기, KDoc을 한 줄로 바꾸기, 요약 문장 뽑기 |
| `src/main/.../CommentPolicyRule.kt` | `bus:comment-policy` — 주석 금지, KDoc 한 줄. 자동 수정 |
| `src/main/.../TestMethodNameRule.kt` | `bus:test-method-name` — 테스트 이름은 백틱 한국어 |
| `src/main/.../NoMethodDisplayNameRule.kt` | `bus:no-method-display-name` — 메서드 `@DisplayName` 금지. 자동 수정 |
| `src/main/.../EntityFactoryNameRule.kt` | `bus:entity-factory-name` — Entity 팩토리는 `create` |
| `src/main/.../NoHardcodedExceptionMessageRule.kt` | `bus:no-hardcoded-exception-message` — 외부 라이브러리 예외까지 문자열 메시지 금지 |
| `src/main/resources/META-INF/services/...RuleSetProviderV3` | ktlint가 규칙 묶음을 찾게 하는 등록 파일(한 줄) |
| `src/test/.../*Test.kt` (4개) | 규칙 테스트 23개. 위반 예제가 실제로 잡히고 자동 수정 결과가 맞는지 확인한다 |

새 규칙을 만들 때는 [`rules.md`의 규칙 추가 절차](rules.md#규칙-추가-절차)를 따른다. 규칙을 고친 뒤 IntelliJ에 반영하려면 `./gradlew -p lint/ktlint-rules jar`로 jar를 다시 만든다(Gradle 빌드와 hook은 알아서 다시 만든다).

## 3. 빌드 환경

| 파일 | 무엇 |
|---|---|
| `gradle/gradle-daemon-jvm.properties` | Gradle 데몬을 JDK 25로 고정한다. 셸의 `JAVA_HOME`이 무엇이든 CLI·IntelliJ·Claude hook이 같은 JDK로 빌드한다 |
| `lint/ktlint-rules/gradle/gradle-daemon-jvm.properties` | 위와 같은 내용(규칙 모듈 단독 빌드용) |

## 4. Claude hook

| 파일 | 무엇 |
|---|---|
| `.claude/hooks/lint-guard.sh` | Claude가 lint를 우회하려는 변경을 하면 사용자 확인을 받는다. 대상: `lint/**`·`.editorconfig`·루트 bus-lint 블록 수정, `// 예외:` 주석이나 `@Suppress` 추가, 셸로 lint 파일 수정. `.claude/settings.json`에 등록돼 있고 Claude Code를 다시 시작하면 적용된다 |
| `.codex/hooks.json` | Codex용 hook 등록. 위 스크립트와 `compile-gate.sh`·`ddl-guard.sh`를 Codex에서도 부른다. `.git/info/exclude`로 git에서 빠지는 이 맥 전용 파일이라 목록(24개)에 없다. Codex에서 `/hooks`로 한 번 승인해야 실행된다 |

기존 파일 중 hook 관련 변경: `compile-gate.sh`가 컴파일 뒤 `busLint`도 돌리고, 위반 줄을 Claude에게 돌려준다.

## 5. 코드

| 파일 | 무엇 |
|---|---|
| `modules/stop/.../result/StopResult.kt` | 정류소 모듈의 오류 코드 enum(`S0001`~`S0017`). 예외 메시지를 문자열 대신 Result 코드로 바꾸면서 stop 모듈에 처음 생겼다. 다른 모듈의 `AuthResult`(A)·`ReservationResult`(R)·`ContractResult`(C)와 같은 형태다 |

## 6. 문서

| 파일 | 무엇 |
|---|---|
| `docs/agents/rules.md` | 규칙 대장. 규칙별 판정 기준·범위·강제 수단, 결정 기록, 우회 방지, IntelliJ 설정, 규칙 추가 절차, 원복 방법 |
| `docs/agents/lint-files.md` | 이 문서 |

## 커밋하지 않는 것

- `lint/ktlint-rules/build/`, `lint/ktlint-rules/.gradle/` — 빌드 산출물. `.gitignore`에 걸려 있다.
- `docs/feature/hosting-setup.md` — 이번 작업 전부터 스테이징돼 있던 파일이다. lint 작업과 무관하다.

## 기존 파일 중 바뀐 것 (요약)

새 파일 외에 기존 파일 261개가 바뀌었다. 대부분은 규칙을 지금 코드에 적용한 결과다.

- 설정: `build.gradle.kts`·`settings.gradle.kts`의 `// >>> bus-lint` 블록, `.claude/settings.json`, `.claude/hooks/compile-gate.sh`
- 지침: `AGENTS.md`, `CLAUDE.md`, `modules/common/AGENTS.md`, `modules/reservation/AGENTS.md`
- 코드: 예외 메시지 → Result 코드(35곳), 테스트 이름 한국어화(56개), Entity 팩토리 `of` → `create`(4개), 주석 정리·한 줄 KDoc(app·iam·stop·reservation), 포맷 정리
