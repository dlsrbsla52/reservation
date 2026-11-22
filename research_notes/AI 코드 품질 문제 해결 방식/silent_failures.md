# Silent failure handling (넓은 catch, 삼킨 예외, 조용한 기본값 폴백) 탐지와 예방 — Kotlin + Spring Boot, AI 생성 코드 관점

조사일: 2026-11-21. 적용 맥락: Spring Boot 4.1, Kotlin 2.3, Gradle 9.9, JDK 25, ktlint 1.8 커스텀 규칙 + forbiddenapis 사용 중, detekt 2.0 alpha는 Kotlin 컴파일러 버전 정렬 문제로 미채택. 의도적인 넓은 catch가 몇 곳 있다(Redis gate fail-open, 스케줄러 루프, 감사 로그 best-effort).

## Q1. Kotlin/JVM에 어떤 정적 분석 규칙이 있고, 허용 목록(allow-list)은 어떻게 다루나

### Takeaway
Kotlin에서 "넓은 catch / 삼킨 예외"를 직접 겨냥한 규칙이 가장 잘 갖춰진 도구는 detekt `exceptions` 규칙 세트(`TooGenericExceptionCaught`, `SwallowedException` 등)이고, 허용 목록은 catch 파라미터 이름 정규식(`allowedExceptionNameRegex`)과 `@Suppress`로 처리한다. detekt 없이 쓸 수 있는 대안은 ktlint 커스텀 규칙(PSI, 이름 기반), Semgrep(Kotlin GA), IntelliJ/Qodana FlexInspect이고, SonarKotlin은 Kotlin 전용 예외 규칙이 빈약하며 Error Prone은 Java 전용이다.

### Cited Findings

**detekt `exceptions` 규칙 세트 — 2.0.0-alpha.6 기준 15개 규칙**
- 현재 detekt.dev 기본 문서 버전은 2.0.0-alpha.6이고, 규칙 세트는 15개: `ErrorUsageWithThrowable`(비활성, 타입 해석 필요), `ExceptionRaisedInUnexpectedLocation`(활성, v1.16.0), `InstanceOfCheckForException`(활성, v1.21.0, 타입 해석 필요), `NotImplementedDeclaration`(비활성), `ObjectExtendsThrowable`(비활성, 타입 해석), `PrintStackTrace`(활성, v1.16.0), `RethrowCaughtException`(활성, v1.16.0), `ReturnFromFinally`(활성, v1.16.0, 타입 해석), `SwallowedException`(활성, v1.16.0), `ThrowingExceptionFromFinally`(활성, v1.16.0), `ThrowingExceptionInMain`(비활성), `ThrowingExceptionsWithoutMessageOrCause`(활성, v1.16.0), `ThrowingNewInstanceOfSameException`(활성, v1.16.0), `TooGenericExceptionCaught`(활성, v1.0.0), `TooGenericExceptionThrown`(활성, v1.0.0) — [detekt exceptions (2.0.0-alpha.6)](https://detekt.dev/docs/rules/exceptions)
- `ErrorUsageWithThrowable`은 `error(throwable)` 호출을 잡는다(throwable의 메시지와 스택 트레이스를 잃기 때문). 1.23.8 문서에는 이 규칙이 없다(14개) — [detekt exceptions (2.0.0-alpha.6)](https://detekt.dev/docs/rules/exceptions); [detekt exceptions (1.23.8)](https://detekt.dev/docs/1.23.8/rules/exceptions)
- 1.23.8 문서는 해당 버전이 "no longer actively maintained"라고 표시하며 최신으로 2.0.0-alpha.6을 링크한다 — [detekt exceptions (1.23.8)](https://detekt.dev/docs/1.23.8/rules/exceptions)

**주요 규칙 옵션과 기본값 (1.23.8과 2.0.0-alpha.6 동일)**
- `TooGenericExceptionCaught`: `exceptionNames` 기본값 `ArrayIndexOutOfBoundsException, Error, Exception, IllegalMonitorStateException, IndexOutOfBoundsException, NullPointerException, RuntimeException, Throwable`; `allowedExceptionNameRegex` 기본값 `_|(ignore|expected).*` — [detekt exceptions](https://detekt.dev/docs/rules/exceptions)
- `SwallowedException`: "caught exceptions that are not passed along, for example as a cause"를 잡는다. `ignoredExceptionTypes` 기본값 `InterruptedException, MalformedURLException, NumberFormatException, ParseException`; `allowedExceptionNameRegex` 기본값 `_|(ignore|expected).*` — [detekt exceptions](https://detekt.dev/docs/rules/exceptions)
- `TooGenericExceptionThrown`: `exceptionNames` 기본값 `Error, Exception, RuntimeException, Throwable` — [detekt exceptions](https://detekt.dev/docs/rules/exceptions)
- `ThrowingExceptionsWithoutMessageOrCause`: `exceptions` 기본값 9종(`Exception`, `IllegalStateException`, `RuntimeException`, `Throwable` 등) — [detekt exceptions](https://detekt.dev/docs/rules/exceptions)
- `ExceptionRaisedInUnexpectedLocation`: `methodNames` 기본값 `equals, finalize, hashCode, toString`; `ReturnFromFinally`: `ignoreLabeled` 기본값 `false` — [detekt exceptions](https://detekt.dev/docs/rules/exceptions)

**detekt `coroutines` 규칙 세트 중 예외 관련**
- `SuspendFunSwallowedCancellation`(비활성, 타입 해석 필요): "suspend functions should not be called inside runCatching's lambda block, because runCatching catches all the Exceptions." 문서는 `CancellationException`을 삼키거나 로그만 남기고 버리지 말고 즉시 재던지라고 하며, `IllegalStateException` 같은 상위 타입을 잡으면 cancellation도 암묵적으로 잡힐 수 있다고 경고한다. `CancellationException`을 재던지는 `coRunCatching` 유틸을 권장한다 — [detekt coroutines](https://detekt.dev/docs/rules/coroutines)
- `SuspendFunInFinallySection`(비활성, 타입 해석): non-cancellable 컨텍스트 없이 `finally`에서 suspend 호출을 잡는다. `CoroutineLaunchedInTestWithoutRunTest`(비활성, 타입 해석): 예외를 삼킬 수 있는 테스트 내 코루틴 launch를 잡는다 — [detekt coroutines](https://detekt.dev/docs/rules/coroutines)

**detekt 허용 목록·억제 메커니즘**
- `@Suppress`(Kotlin)와 `@SuppressWarnings`(Java)를 모두 지원하고, 둘 다 있으면 Kotlin 쪽이 우선한다. 규칙 ID 형식: `MagicNumber`, `style:MagicNumber`, `detekt:style:MagicNumber`, `detekt.style.MagicNumber`; 세트 전체는 `style`, 전체는 `all`. 일부 규칙은 `@file:Suppress(...)`로만 억제된다 — [detekt Suppressing Rules](https://detekt.dev/docs/introduction/suppressing-rules)
- 이름 기반 허용: catch 파라미터를 `_`, `ignored…`, `expected…`로 지으면 `TooGenericExceptionCaught`·`SwallowedException`이 기본 정규식으로 통과시킨다 — [detekt exceptions](https://detekt.dev/docs/rules/exceptions)

**SonarSource (SonarKotlin)**
- Kotlin 규칙 `RSPEC-1143`: `finally` 블록 안의 `break`·`continue`·`return`·`throw`가 try/catch의 예외를 가린다 — [rules.sonarsource.com/kotlin/RSPEC-1143](https://rules.sonarsource.com/kotlin/RSPEC-1143) (검색 결과로만 확인. rules.sonarsource.com은 현재 sonarsource.com/products/sonarqube/rules/로 301 리다이렉트된다)
- Java `S1166`("exception handlers should preserve the original exceptions" 계열 — 원래 예외를 로그·전달 없이 버리는 catch)는 Java 규칙으로만 확인됐고 Kotlin 판은 확인하지 못했다 — [RSPEC-1166 (Java)](https://rules.sonarsource.com/java/tag/error-handling/RSPEC-1166)
- SonarSource 커뮤니티에서 Java `S1181`(Throwable/Error catch 금지)의 Kotlin 판을 요청하자, SonarSource 직원은 Kotlin 분석기가 Slang 기반이고 Kotlin 전용 규칙이 그해 우선순위가 아니며 외부 이슈 import(예: detekt 리포트)로 메우라고 답했다. 글 날짜는 확인 못 했고 오래된 글일 수 있다 — [Sonar Community: Throwable and Error should not be caught in Kotlin](https://community.sonarsource.com/t/throwable-and-error-should-not-be-caught-in-kotlin-equivalent-of-java-rule-squid-s1181/5074)
- 다른 상용 분석기에도 Kotlin 삼킨 예외 규칙이 있다: DeepSource `KT-W1005`, Klocwork `KT.SWALLOWED_EXCEPTION`, Xygeni `kotlin.no_catch_generic_exception` (검색 결과 목록에서만 확인, 본문 미확인) — [DeepSource KT-W1005](https://deepsource.com/directory/kotlin/issues/KT-W1005); [Klocwork KT.SWALLOWED_EXCEPTION](https://help.klocwork.com/2024/en-us/reference/kt.swallowed_exception.htm); [Xygeni No Catch Generic Exception](https://detectors.xygeni.io/xydocs/quality/detectors/kotlin/kotlin.no_catch_generic_exception.html)

**Semgrep**
- Kotlin은 Semgrep Code(SAST)에서 "Generally available • Cross-file dataflow analysis • 60+ Pro rules"다. 커뮤니티 에디션과 유료 티어 구분은 해당 페이지에 명시돼 있지 않다 — [Semgrep supported languages](https://docs.semgrep.dev/supported-languages)

**IntelliJ / Qodana**
- Inspectopedia에 Kotlin용 "'catch' block may ignore exception"(`KotlinCatchMayIgnoreException`) 페이지가 검색 결과에 나오지만, 직접 접속 시 404였다. 존재·옵션은 미확인 — [검색 결과 URL](https://jetbrains.com/help/inspectopedia/KotlinCatchMayIgnoreException.html)
- Qodana FlexInspect: 프로젝트의 `inspections` 디렉터리에 Kotlin으로 쓴 `.inspection.kts` 파일을 두면 IDE가 즉석 컴파일해 실행하고, `id`로 `qodana.yaml`에서 켜고 끌 수 있다. IntelliJ IDEA Ultimate 필요 — [Qodana 2025.3 FlexInspect](https://www.jetbrains.com/help/qodana/2025.3/flexinspect.html); [Qodana 2024.1 블로그](https://blog.jetbrains.com/?p=467584/)
- 별도 경로로 커스텀 Kotlin 검사를 IntelliJ Platform 플러그인으로 패키징할 수 있고, 이 경로는 K2 지원이 필요하다(IntelliJ Platform 2025.1부터 기본) — [JetBrains Platform blog, 2026-02](https://blog.jetbrains.com/platform/2026/02/extending-qodana-adding-custom-code-inspections/)

**ktlint 커스텀 규칙**
- ktlint는 ServiceLoader로 규칙 세트를 찾으며 `META-INF/services/...RuleSetProviderV3` 파일에 구현 클래스 FQN을 둔다. 커스텀 규칙 억제 ID 형식은 `ktlint:some-custom-rule-set-id:some-custom-rule-id`, 규칙 세트 전체 토글은 `ktlint_<rule-set-id>` 속성 — [ktlint custom rule set (1.0.0)](https://pinterest.github.io/ktlint/1.0.0/api/custom-rule-set); [ktlint custom rule set (1.7.1)](https://pinterest.github.io/ktlint/1.7.1/api/custom-rule-set)
- 규칙은 `beforeVisitChildNodes(node: ASTNode, ...)`에서 AST 노드를 방문하는 형태다 — [zenn.dev 예제](https://zenn.dev/moyuru/articles/d2dbe6de008908)
- 이 저장소에는 이미 `lint/ktlint-rules`(예: `NoHardcodedExceptionMessageRule`, `CommentPolicyRule`, `AstSupport.kt`)와 forbiddenapis 시그니처 파일(`lint/forbidden-apis-main.txt` — `IllegalStateException#<init>(String,Throwable)` 등 예외 생성자 금지)이 있다 — 로컬: `lint/ktlint-rules/src/main/kotlin/com/media/bus/lint/ktlint/`, `lint/forbidden-apis-main.txt`

**Kotlin `runCatching`의 성질 (forbiddenapis 적용 가능성과 관련)**
- `runCatching`은 `inline fun <R> runCatching(block: () -> R): Result<R>`이며 "catching any Throwable exception that was thrown from the block"이다(Kotlin 1.3부터) — [Kotlin stdlib runCatching](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/run-catching.html)

**Error Prone**
- Error Prone은 javac 플러그인 기반 Java 정적 분석기로 Kotlin 소스는 분석하지 않는다 — [errorprone.info](https://errorprone.info/) (이번 세션에서 페이지 재확인은 하지 않음. 일반적으로 알려진 사실로 표기)

### Inferences
- **detekt 없이 가능한 범위 / detekt가 필요한 범위.** 이름(문자열) 매칭만으로 충분한 규칙(`TooGenericExceptionCaught`, `SwallowedException`, `PrintStackTrace`, `RethrowCaughtException`, `ThrowingExceptionFromFinally`)은 타입 해석이 필요 없으므로 ktlint 커스텀 규칙(PSI)으로 재구현이 현실적이다. 반면 `ReturnFromFinally`, `InstanceOfCheckForException`, `ErrorUsageWithThrowable`, `SuspendFunSwallowedCancellation`, `UseOrEmpty`처럼 "Requires type resolution"인 규칙은 컴파일 클래스패스가 필요해서 detekt Gradle 통합(=Kotlin 버전 정렬 문제)을 피하기 어렵다.
- **ktlint로 옮길 때의 한계.** ktlint는 단일 파일 AST만 보므로 `catch (e: Exception)`의 `Exception`이 `java.lang.Exception`인지 import alias인지 구분하지 못하고, `runCatching`도 이름으로만 식별한다. 이 저장소 규모(main 소스 기준 넓은 catch 11곳)에서는 이름 기반 오탐이 거의 문제되지 않을 것으로 본다.
- **forbiddenapis로는 `runCatching`·`getOrNull`·`getOrDefault`를 막기 어렵다.** forbiddenapis는 바이트코드의 메서드 호출 시그니처를 검사하는데, 이 함수들은 `inline`이라 호출 지점에 본문이 인라인되어 호출 명령이 남지 않을 가능성이 높다(검증 필요). 반대로 `java.lang.Throwable#printStackTrace()` 같은 non-inline Java API는 forbiddenapis로 확실히 막을 수 있다.
- **허용 목록 설계.** detekt식 "catch 파라미터 이름 정규식"은 AI 에이전트가 `e`를 `ignored`로 바꾸는 것만으로 규칙을 통과할 수 있어 우회 벡터가 된다. 이 저장소의 기존 장치(`// 예외: <이유>` 주석 + `lint-guard.sh` PreToolUse hook으로 사용자 승인)처럼, 허용은 "승인이 필요한 마커" 또는 "명시적 파일 목록(StopHoldGate, ReservationHoldExpiryScheduler, AuditLogService 등)"으로 고정하는 편이 에이전트 피드백 루프에 맞다.
- **SonarKotlin은 이 문제의 1차 도구로 부적합**해 보인다(Kotlin 전용 예외 규칙이 적고, Sonar 측도 detekt 리포트 import를 권함). Semgrep은 Kotlin GA라 패턴 규칙으로 보완 가능하지만 새 도구 도입 비용이 든다.

### Gaps
- SonarKotlin 전체 규칙 목록을 열거하지 못했다(rules.sonarsource.com 리다이렉트, `gh` 미인증으로 sonar-kotlin 저장소 checks 디렉터리 조회 실패). Kotlin용 S1166/S2221/S1181 존재 여부는 미확인.
- detekt 설정 파일의 규칙별 `excludes`/`includes` 경로 글롭과 baseline 동작은 이번에 문서로 재확인하지 못했다([Configuration](https://detekt.dev/docs/introduction/configurations), [Baseline](https://detekt.dev/docs/introduction/baseline) 페이지 확인 필요).
- detekt-cli를 타입 해석 없이(소스만) 돌리면 Kotlin 2.3 신규 문법 파싱이 되는지, 즉 Gradle 플러그인을 우회해 버전 정렬 문제를 피할 수 있는지는 확인하지 못했다.
- Semgrep 허용 목록 문법(`pattern-not-inside`, `paths.exclude`, `nosemgrep`)과 Kotlin `try/catch`·elvis 패턴 매칭 품질은 이번 세션에 문서로 재확인하지 않았다.
- IntelliJ의 Kotlin "catch may ignore exception" 검사 존재·옵션 미확인(404).
- forbiddenapis가 Kotlin inline 함수 호출을 탐지하는지에 대한 공식 문서는 찾지 못했다(위 추론은 inline 의미론에 근거).

## Q2. "조용한 기본값 폴백"(`?: 0`, `getOrNull()` 후 기본값, `getOrDefault`)은 어떻게 탐지하나

### Takeaway
`?: 0`, `?: emptyList()`, `runCatching { }.getOrNull()`을 "실패 은폐"로 보고 잡는 기성 정적 분석 규칙은 찾지 못했다. 오히려 detekt style 규칙(`UseOrEmpty`, `NullableBooleanCheck`)은 이런 폴백을 `.orEmpty()`·`!= false`로 더 짧게 바꾸라고 권해 텍스트 탐지를 어렵게 만든다. 현실적인 수단은 (1) 좁은 패턴의 커스텀 PSI 규칙, (2) 에이전트용 리뷰 체크리스트(Anthropic `silent-failure-hunter` 같은 리뷰 에이전트), (3) 근본 원인인 불필요한 nullable 줄이기다.

### Cited Findings
- detekt `UseOrEmpty`(style, 활성, v1.21.0, 타입 해석 필요): "detects `?: emptyList()` that can be replaced with `orEmpty()` call." 비준수 예시로 `x ?: emptyList()`, `s ?: ""`를 들고 준수 예시로 `x.orEmpty()`를 든다 — [detekt style](https://detekt.dev/docs/rules/style)
- detekt `NullableBooleanCheck`(비활성, 타입 해석): `value ?: true` / `value ?: false`를 `value != false` / `value == true`로 바꾸라고 한다. `CanBeNonNullable`(비활성, 타입 해석): non-null로 선언할 수 있는 nullable 선언을 잡는다 — [detekt style](https://detekt.dev/docs/rules/style)
- detekt style 문서에는 `getOrElse`/`getOrNull`을 다루는 규칙이 없다 — [detekt style](https://detekt.dev/docs/rules/style)
- detekt `SwallowedException`은 catch된 예외가 전달되지 않는 경우를 잡는 규칙으로 정의돼 있다(즉 catch 절이 대상) — [detekt exceptions](https://detekt.dev/docs/rules/exceptions)
- detekt `SuspendFunSwallowedCancellation`은 `runCatching` 람다 안의 suspend 호출만 대상으로 한다 — [detekt coroutines](https://detekt.dev/docs/rules/coroutines)
- Anthropic 공식 플러그인 `pr-review-toolkit`의 `silent-failure-hunter` 에이전트(설명: "Reviews PR code changes for silent failures, inadequate error handling, and inappropriate fallback behavior", try/catch·fallback·에러 억제 작업 뒤 proactive 실행)가 찾는 패턴: 빈 catch("absolutely forbidden"), 로그만 남기고 계속하는 catch, 에러 시 로그 없이 null/기본값 반환, 실패할 수 있는 연산을 `?.`로 건너뛰기, 이유 없는 폴백 체인, 사용자에게 알리지 않는 재시도 소진, 프로덕션 코드의 mock/stub/fake 폴백, 무관한 에러까지 숨기는 넓은 catch. 심각도: CRITICAL(조용한 실패, 넓은 catch), HIGH(나쁜 에러 메시지, 정당화되지 않은 폴백), MEDIUM(맥락 부족). 출력: 위치, 심각도, 설명, 가려질 수 있는 에러, 사용자 영향, 권고, 수정 예시. 원칙 중 "Mock/fake implementations belong only in tests", "Fallbacks must be explicit and justified" — [anthropics/claude-plugins-official silent-failure-hunter.md](https://raw.githubusercontent.com/anthropics/claude-plugins-official/main/plugins/pr-review-toolkit/agents/silent-failure-hunter.md); [PR Review Toolkit 소개](https://claude.com/plugins/pr-review-toolkit)
- 같은 에이전트는 Claude Code 자체 저장소 관례(`logForDebugging`, `logError`(Sentry), `logEvent`(Statsig), `constants/errorIds.ts`)를 전제로 쓰여 있다 — [silent-failure-hunter.md](https://raw.githubusercontent.com/anthropics/claude-plugins-official/main/plugins/pr-review-toolkit/agents/silent-failure-hunter.md)
- OpenAI Codex 프롬프팅 가이드의 시작 프롬프트: "No silent failures: do not early-return on invalid input without logging/notification consistent with repo patterns" — [OpenAI Codex Prompting Guide](https://developers.openai.com/cookbook/examples/gpt-5/codex_prompting_guide)
- 실무 보고에서 가장 흔한 형태로 "catching errors and returning an empty dictionary or hardcoded default value, with no logging"이 꼽힌다(커뮤니티 글, 일화 수준) — [검색 결과: 140 sessions using Claude Code](https://dev.to/voxcore84/i-spent-140-sessions-using-claude-code-it-lied-about-what-it-did-l18) (본문 미확인, 검색 요약 기준)

**로컬 코드 관찰 (2026-11-21, `modules/**/src/main`, 테스트 제외, grep 기준)**
- `catch (x: Exception|Throwable|RuntimeException)` 11곳: `app/security/JwtAuthenticationFilter.kt:77`, `reservation/.../ReservationFacade.kt:66`, `reservation/.../StopResolutionService.kt:31`, `reservation/.../hold/ReservationHoldExpiryScheduler.kt:33`, `reservation/.../hold/StopHoldGate.kt:43,75`, `auth-contract/.../JwtProvider.kt:284`, `common/.../BoundedConcurrencyAspect.kt:49,65(Throwable)`, `common/.../TransactionalBulkheadAspect.kt:85(Throwable)`, `iam/audit/service/AuditLogService.kt:45` — 로컬 grep
- `runCatching { UUID.fromString(memberId) }.getOrNull()` 3곳: `iam/auth/service/AuthService.kt:265,464,481` — 로컬 grep
- 리터럴 기본값 elvis(`?: 0|false|""|emptyList()` 등) 12곳(예: `stop/client/SeoulBusApiClient.kt:31` `rows ?: emptyList()`, `iam/admin/dto/StopBoundsResponse.kt:21-22` `truncated ?: false`, `limit ?: 0`, `auth-contract/.../MemberPrincipal.kt:100` `emailVerified ... ?: false`) — 로컬 grep
- main 소스에 `kotlinx.coroutines`·`suspend fun` 사용 0건 — 로컬 grep

### Inferences
- **좁고 고신호인 패턴부터 규칙화하는 것이 맞다.** 오탐이 적은 순서: (a) `runCatching { … }.getOrNull()` / `.getOrDefault(…)` / `.getOrElse { 상수 }` 체인(`runCatching`이 `Throwable` 전체를 잡으므로 `UUID.fromString` 같은 파싱은 `toUuidOrNull()` 류 좁은 헬퍼나 명시적 `try { } catch (e: IllegalArgumentException)`으로 대체 가능), (b) 본문에 rethrow도, catch 변수를 쓰는 로그 호출도 없는 넓은 catch, (c) 외부 응답 DTO 매핑에서의 `?: 0`/`?: false`. elvis 리터럴 전체 금지는 12곳 중 상당수가 정당한 표현(`(keyword?.length ?: 0) > 200`, debug 로그 인자)이라 노이즈가 크다.
- **elvis 폴백은 "어디서 쓰였나"로 판정해야 한다.** 같은 `?: false`라도 JWT 클레임(`emailVerified`)에서의 기본값은 보안 의미가 있는 결정이고, 로그 인자에서의 `?: 0`은 무해하다. 따라서 elvis는 lint보다 리뷰 체크리스트(에이전트 리뷰 단계)에 두고, lint는 (a)(b)에 집중하는 분업이 현실적이다.
- **detekt `UseOrEmpty`는 이 목표와 반대 방향**이다. 도입하면 `?: emptyList()`가 `.orEmpty()`로 바뀌어 "폴백" 자체는 남고 grep/PSI 탐지 표면만 바뀐다. detekt를 나중에 도입하더라도 이 규칙의 의미를 알고 켜야 한다.
- **근본 원인 공략:** 외부 API DTO의 nullable 필드가 기본값 폴백을 낳는다. 경계(외부 API 역직렬화)에서 필수 필드는 non-null로 선언해 역직렬화 실패로 fail-fast시키고, 진짜 선택 필드만 nullable로 두면 내부 코드의 elvis가 줄어든다(detekt `CanBeNonNullable`이 이 방향의 규칙이지만 타입 해석 필요).
- **`silent-failure-hunter`는 그대로 쓰기보다 이식해야** 한다(TypeScript·Sentry·Statsig 전제). 이 저장소용으로는 `ExceptionAdvisor`·`BusinessException(XxxResult.Y)`·허용된 넓은 catch 목록을 기준으로 다시 써야 한다.

### Gaps
- `?: 0`, `getOrNull()` 후 기본값을 "실패 은폐"로 판정하는 기성 규칙(detekt, Sonar, Semgrep registry, IntelliJ)은 찾지 못했다. Semgrep registry의 Kotlin 품질 규칙 목록은 직접 열람하지 못했다.
- 이런 폴백을 데이터 흐름(예: nullable의 출처가 외부 호출 실패인지)으로 구분하는 도구는 확인하지 못했다(Semgrep cross-file dataflow로 가능할 수 있으나 미검증).

## Q3. AI 에이전트가 넓은 try/catch나 방어적 폴백을 과도하게 만든다는 증거

### Takeaway
양대 벤더(Anthropic, OpenAI)의 공식 프롬프팅 가이드가 모두 이 패턴을 명시적으로 억제하는 문구를 제공하고, Anthropic은 이를 잡는 리뷰 에이전트를 공식 플러그인으로 배포한다. 다만 정량 증거는 약하다: 학술 연구는 오히려 LLM 코드의 "검사·예외 처리 부족"을 보고하며, "과잉 폴백"은 실무자 보고와 벤더 가이드 수준의 증거다.

### Cited Findings

**벤더 공식 가이드**
- Anthropic "Prompting best practices"의 "Overeagerness" 절: "Claude Opus 4.5 and Claude Opus 4.6 have a tendency to overengineer by creating extra files, adding unnecessary abstractions, or building in flexibility that wasn't requested." 예시 프롬프트에 "Defensive coding: Don't add error handling, fallbacks, or validation for scenarios that can't happen. Trust internal code and framework guarantees. Only validate at system boundaries (user input, external APIs)."가 들어 있다 — [Anthropic Prompting best practices](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices)
- 같은 문서의 "Avoid focusing on passing tests and hardcoding" 절: 테스트 통과에 과도하게 집중하거나 워크어라운드를 쓰는 경향을 언급하고 "Do not hard-code values or create solutions that only work for specific test inputs"라는 예시 프롬프트를 준다 — [Anthropic Prompting best practices](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices)
- OpenAI Codex 프롬프팅 가이드(대상 `gpt-5.3-codex`, 시작 프롬프트는 GPT-5.1-Codex-Max 프롬프트 기반) "Code Implementation" 항목: "Tight error handling: No broad catches or silent defaults: do not add broad try/catch blocks or success-shaped fallbacks; propagate or surface errors explicitly rather than swallowing them." 페이지 날짜 표기 없음 — [OpenAI Codex Prompting Guide](https://developers.openai.com/cookbook/examples/gpt-5/codex_prompting_guide)
- Anthropic은 공식 플러그인 `pr-review-toolkit`에 조용한 실패 전담 리뷰 에이전트(`silent-failure-hunter`)를 포함한다(Q2 참고) — [claude.com/plugins/pr-review-toolkit](https://claude.com/plugins/pr-review-toolkit)

**실무자 보고 (일화·의견)**
- Sean Goedecke, "AI coding agents rely too much on fallbacks"(2025-09-27): "at least in Claude Code, Codex and Copilot" 자동 폴백을 만든다고 관찰. 예: Louvain 군집화를 요청했는데 알파벳순 그룹핑 폴백을 같이 만들어 "you might not even know if the Louvain method is working", 추론 실패 시 하드코딩 응답, API 데이터 일부를 placeholder로 대체. 원인에 대해선 "best guess is that it's a reinforcement learning artifact"(code with fallbacks is more likely to succeed)라고 추정(검증되지 않은 가설). 프로덕션 PR에서는 제출 전에 걷어낼 수 있지만 프로토타이핑·벤치마크에서는 "you can't be confident that you're actually evaluating your latest idea" — [seangoedecke.com/agents-and-fallbacks](https://seangoedecke.com/agents-and-fallbacks/)
- GitHub `anthropics/claude-code#31890`(2026-03-07, 라벨 `area:model`, `bug`): "Claude Code adds non-sensical fallbacks and swallows errors very frequently even having CLAUDE.md explicitly forbidding this behaviour and asking for error returns. For example, just now Claude Code tried to handle an encryption fail by trying to send the data PLAINTEXT." 2026-04-04 비활동으로 "not planned" 자동 종료. 댓글에서 다른 사용자가 같은 현상과 관련 이슈 #26533을 언급 — [anthropics/claude-code#31890](https://github.com/anthropics/claude-code/issues/31890)
- 실무 커뮤니티의 공통 결론: CLAUDE.md/AGENTS.md 지시는 모델이 무시할 수 있는 advisory 맥락이고 hook·CI 검사는 결정적으로 실패하므로 검사를 CI나 실행 하네스로 옮기라는 주장(검색 요약 기준, 본문 미확인) — [daily.dev: I audited my AI agent's guardrails](https://daily.dev/posts/i-audited-my-ai-agent-s-guardrails-most-of-them-weren-t-running--iejklp8mp)

**Sonar의 LLM 코드 분석 (2025-08)**
- Sonar "coding personalities" 보고서: Claude Sonnet 4·3.7, GPT-4o, Llama 3.2 90B, OpenCoder-8B를 Java 과제 4,442개로 평가. Claude Sonnet 4에 대해 "verbose and highly complex, as it consistently attempts to implement sophisticated safeguards and error handling"이라고 서술. 코드 스멜 하위 범주 중 exception-handling 스멜 비중은 모델별 약 0.02–0.08%로 작다(검색 요약 기준, PDF 원문 미확인) — [Sonar blog: LLM coding personality traits](https://www.sonarsource.com/blog/llm-coding-personality-traits/); [Sonar: Claude 모델 personality](https://www.sonarsource.com/sem/the-coding-personalities-of-leading-llms/claude/); [The Register, 2025-08-13](https://www.theregister.com/2025/08/13/ai_model_personalities_shape_the/)

**학술 연구 (방향이 반대인 증거 포함)**
- arXiv 2503.20197 "A Preliminary Study on the Robustness of Code Generation by Large Language Models"(v1 2025-03-26, v4 2025-09-23): CoderEval에서 코드 LLM 4종의 출력 중 "35.2% of their outputs are less robust than human-written code", 결함의 90% 이상이 "missing conditional checks". 즉 과잉이 아니라 **검사 부족**. (검색 요약에는 37.8%로 나온 판본이 있어 버전에 따라 수치가 다름) — [arXiv 2503.20197](https://arxiv.org/abs/2503.20197)
- arXiv 2410.06949 "Towards Exception Safety Code Generation with Intermediate Representation Agents Framework"(v1 2024-10-09, v3 2025-07-07, 비동료심사 프리프린트): "Large Language Models (LLMs) often struggle with robust exception handling in generated code", "leading to fragile programs that are prone to runtime errors" — [arXiv 2410.06949](https://arxiv.org/abs/2410.06949)
- arXiv 2412.18989 CodeSmellEval: LLM의 코드 스멜 생성 경향을 Pylint 기준으로 측정하는 벤치마크(빈 catch를 따로 다루지는 않음, 검색 요약 기준) — [arXiv 2412.18989](https://arxiv.org/pdf/2412.18989v2)

### Inferences
- **증거의 성격이 갈린다.** 벤더 가이드 두 곳과 Anthropic의 전용 리뷰 에이전트는 "과잉 방어·조용한 폴백"이 벤더도 인정하는 반복 패턴임을 보여주지만 빈도 수치는 없다. 학술 연구는 "필요한 검사·예외 처리의 부족"을 정량화한다. 둘은 모순이라기보다 "잘못된 위치의 처리"(필요한 경계 검사는 빠지고, 내부에는 성공처럼 보이는 폴백이 들어감)로 함께 설명된다.
- **지시만으로는 부족하다는 근거가 있다.** #31890은 CLAUDE.md 금지 문구가 있어도 폴백이 반복된다는 사용자 보고다. 이 저장소가 이미 택한 "규칙은 hook·lint로 강제"(compile-gate의 `busLint`) 방향이 이 문제에도 맞다. 지침 문구는 Anthropic 예시 프롬프트의 "Trust internal code and framework guarantees. Only validate at system boundaries" 문장을 거의 그대로 AGENTS.md에 옮기는 것이 근거가 가장 강하다.
- Goedecke의 RL 아티팩트 가설은 그럴듯하지만 검증된 사실이 아니다. 보고서에서는 "추정"으로만 인용해야 한다.

### Gaps
- LLM 생성 코드에서 넓은 catch·빈 catch·조용한 기본값의 **빈도**를 사람 코드와 비교한 연구는 찾지 못했다. Kotlin 대상 연구는 전무.
- Seeker(2410.06949 v1)의 "세 가지 실패 패턴(fragile code 미탐지, 잘못된 예외 타입 포착, 부실한 handling block)" 주장은 검색 스니펫에만 있었고 현재 v3 초록에는 없어 검증하지 못했다.
- Sonar 보고서의 exception-handling 수치와 "GPT-5 reasoning 모드에서 inadequate I/O error-handling 증가" 주장은 2차 보도·검색 요약으로만 확인했다.
- CatchAll(arXiv 2601.01271, 2026-01, 저장소 인식 try-catch 생성)은 검색 목록에서만 확인했다.

## Q4. Spring Boot의 fail-fast·에러 처리 정책 패턴 (글로벌 핸들러, Result/Either, runCatching 함정)

### Takeaway
Spring의 표준은 컨트롤러에서 예외를 잡지 않고 `@ControllerAdvice`(+ RFC 9457 `ProblemDetail`)로 모으는 것이고, Kotlin 쪽 함정은 `runCatching`이 `Throwable` 전체(코루틴 `CancellationException`, `Error` 포함)를 잡는다는 점이다. Arrow는 `catch`가 치명적 예외와 `CancellationException`을 자동으로 제외하고, "도메인 실패는 타입으로, 시스템 실패는 예외로"라는 경계를 제시한다.

### Cited Findings

**Spring MVC 에러 응답**
- Spring Framework는 RFC 9457 "Problem Details for HTTP APIs"를 지원한다. `ProblemDetail`(표준·비표준 필드 컨테이너), `ErrorResponse`("All Spring MVC exceptions implement this"), `ErrorResponseException`, `ResponseEntityExceptionHandler`("convenient base class for an @ControllerAdvice that handles all Spring MVC exceptions ... and renders an error response with a body") — [Spring MVC Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- "In Spring Boot, the `spring.mvc.problemdetails.enabled` property autoconfigures a `ResponseEntityExceptionHandler` that handles built-in exceptions with problem details." 자체 advice를 함께 쓰면 Boot가 등록한 order 0 핸들러보다 앞서도록 순서를 지정해야 한다. 7.1부터 `ProblemDetail`의 `instance` 필드가 경로로 자동 설정되지 않는다 — [Spring MVC Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- 이 저장소의 정책: Controller 내 try-catch 금지, `common`의 `ExceptionAdvisor`에 위임, 예외 메시지 대신 모듈 Result enum + `BusinessException`(4xx)/`ServiceException`(5xx), `error()`·`require {}`·`check {}` 금지 — 로컬: `AGENTS.md` "코드 규칙"

**Kotlin `runCatching`과 코루틴 cancellation**
- `runCatching`은 블록에서 던져진 "any Throwable exception"을 잡아 `Result` 실패로 감싼다 — [Kotlin stdlib runCatching](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/run-catching.html)
- kotlinx.coroutines 이슈 #1814 "Provide a `runCatching` that does not handle a `CancellationException` but re-throws it instead."(2020-02-18 개설, 현재 Open). 메인테이너 qwwdfsad는 "Could you please elaborate why is it unexpected behaviour?", elizarov는 "What are the use-cases for `runCatching` in an application code?"라고 되물었고 수용·거절 결론은 없다. 커뮤니티 대안으로 `runSuspendCatching`, `cooperativeCatch`, catch 블록에서 `coroutineContext.ensureActive()` 호출 등이 논의됨 — [kotlinx.coroutines#1814](https://github.com/Kotlin/kotlinx.coroutines/issues/1814)
- detekt 문서는 `CancellationException`을 즉시 재던지라고 하고, 상위 타입(`IllegalStateException`) catch도 cancellation을 잡을 수 있다고 경고하며 `coRunCatching` 유틸과 `SuspendFunSwallowedCancellation` 활성화를 권한다 — [detekt coroutines](https://detekt.dev/docs/rules/coroutines)
- `runCatching`이 구조적 동시성을 깨뜨린다는 사실을 문서화해 달라는 요청 KT-55480이 있다(검색 요약 기준, 미열람) — [검색 결과 출처: kotlinlang Slack 아카이브](https://slack-chats.kotlinlang.org/t/489958/how-to-avoid-accidentally-catching-cancellationexception-for)

**Arrow (typed errors)**
- "The `catch` DSL allows us to wrap foreign functions and capture any `Throwable` or `T: Throwable` that might be thrown." "It automatically avoids capturing fatal exceptions such as `OutOfMemoryError`, or Kotlin's `CancellationException`." 예상한 예외(`SQLException`)만 잡고 나머지 `Throwable`은 재던지는 예시와 `Either.catchOrThrow` + `mapLeft` 패턴을 보여준다 — [Arrow: Working with typed errors](https://arrow-kt.io/learn/typed-errors/working-with-typed-errors/)
- "Logical failures indicate problems within the domain, and those should be handled as part of the usual domain logic." "Exceptions indicate problems which affect the system's ability to continue working." — [Arrow: Working with typed errors](https://arrow-kt.io/learn/typed-errors/working-with-typed-errors/)
- "Typed errors should be used for logical errors"; "database connection suddenly dropped" 같은 실패는 예외로, "perhaps combined with resilience" — [Arrow: From Either to Raise](https://arrow-kt.io/learn/typed-errors/from-either-to-raise/)

### Inferences
- **이 저장소에서 `CancellationException` 위험은 현재 낮다**(main 소스에 코루틴 0건, Virtual Threads 사용). 대신 같은 구조의 위험은 `InterruptedException`이다: `runCatching`·`catch (e: Exception)`은 `InterruptedException`도 잡아 인터럽트 신호를 지운다. 그런데 detekt `SwallowedException`의 기본 `ignoredExceptionTypes`에 `InterruptedException`이 들어 있어, detekt 기본 설정은 이 경우를 오히려 통과시킨다. 커스텀 규칙을 만든다면 이 기본값을 따르지 않는 것이 Virtual Threads 환경에 맞다(스케줄러 루프·`BoundedConcurrencyAspect`의 `Throwable` catch 검토 대상).
- **fail-fast 정책을 "어디서 잡을 수 있나"로 정의하면 lint로 옮기기 쉽다.** 예: (1) 넓은 catch는 승인된 위치 목록(Redis gate fail-open, 스케줄러 루프 최상단, 감사 로그 best-effort, AOP 동시성 제한)에서만 허용, (2) 허용된 곳도 catch 본문에 catch 변수를 포함한 로그 호출(+ 가능하면 메트릭) 필수, (3) 그 외는 `ExceptionAdvisor`까지 전파. 이는 Anthropic 가이드의 "Only validate at system boundaries"와 Arrow의 "도메인 실패는 타입, 시스템 실패는 예외" 구분과도 맞는다.
- **Arrow 도입은 이 문제의 필요조건이 아니다.** 이 저장소는 이미 `BusinessException(XxxResult.Y)`로 도메인 실패를 코드화하고 있어, Arrow를 넣지 않고도 "예상 가능한 실패는 Result 코드 예외로, 나머지는 전파"라는 같은 경계를 표현할 수 있다. Arrow의 실질적 이점(`catch`가 fatal·cancellation 자동 제외)은 코루틴이 없는 현재 구조에서 작다.
- **AWS·운영 관점:** fail-open(Redis gate)처럼 의도적으로 실패를 삼키는 곳은 로그만으로는 운영에서 "조용한 실패"와 구분되지 않는다. 허용 목록의 각 catch에 카운터 메트릭(예: Micrometer)을 붙여 CloudWatch/LGTM 대시보드·알람에서 폴백 발생률을 볼 수 있게 하는 것이 best-effort 설계의 짝이다(일반적 관측성 원칙에 근거한 추론).

### Gaps
- Spring Boot 4.1에서 `spring.mvc.problemdetails.enabled`의 기본값은 이번에 확인하지 못했다(Spring Framework 문서는 기본값을 명시하지 않음).
- KT-55480의 현재 상태와 JetBrains 공식 입장은 직접 확인하지 못했다.
- `Result`/`runCatching` 사용을 공식적으로 "애플리케이션 코드에서 지양"하라는 Kotlin 팀 문서는 찾지 못했다(#1814에서 elizarov가 애플리케이션 코드의 사용 사례를 되물은 정도).
