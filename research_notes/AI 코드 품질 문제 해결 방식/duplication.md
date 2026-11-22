# AI 코딩 에이전트가 만드는 코드 중복(기존 공유 함수 재사용 대신 재구현)과 탐지·예방 방법

조사일: 2026-11-21. 적용 대상: Spring Boot 4.1 + Kotlin 2.3 모듈러 모놀리스(Gradle 모듈 6개), Gradle 9.9, JDK 25, Claude Code·Codex + Stop hook(컴파일 + forbiddenapis·ktlint 커스텀 규칙) + 기존 아키텍처 테스트.

출처 표기 규칙: GitClear 사이트(gitclear.com)는 WebFetch에 403을 돌려줘 원문을 직접 열지 못했다. GitClear 수치는 모두 2차 보도(DevClass, LeadDev, DEV Community)나 검색 요약에서 왔고, 항목마다 그렇게 적었다.

## 1. AI 보조 코딩이 중복을 늘리고 재사용·리팩터링을 줄인다는 증거

### Takeaway
가장 큰 데이터(GitClear, 2020~2026년 변경 라인 1.5억~6.2억)는 AI 도입 이후 "복사·붙여넣기/중복 블록"은 늘고 "moved(리팩터링 신호)"는 크게 줄었다고 일관되게 보고한다. 학술 연구(MSR '26)도 에이전트 PR이 사람 PR보다 의미적 중복(Type-4)이 약 1.87배라고 보고한다. 다만 GitClear는 분석 도구를 파는 벤더이고, DORA는 반대 방향 결과도 냈다.

### Cited Findings

**GitClear 2024 보고서 (2023 데이터, 2024-01 발표)**
- 제목은 "Coding on Copilot: 2023 Data Shows Downward Pressure on Code Quality, Plus Projections for 2024"이고 저자는 William Harding, Matthew Kloster다(PDF 메타데이터로 확인, 본문은 추출하지 못함). — [gwern 사본 PDF](https://gwern.net/doc/ai/nn/transformer/gpt/codex/2024-harding.pdf), [GitClear 원문 URL](https://www.gitclear.com/coding_on_copilot_data_shows_ais_downward_pressure_on_code_quality)
- 코드 1억 5,300만 줄을 분석했고, code churn이 2021년(AI 이전) 기준 대비 2024년에 두 배가 될 것으로 예측했다(2차 보도). — [arc.dev 요약](https://arc.dev/developer-blog/impact-of-ai-on-code/), [DevClass 2024-01-24](https://www.devclass.com/ai-ml/2024/01/24/ai-assistance-is-leading-to-lower-code-quality-claim-researchers/1630032)

**GitClear 2025 보고서 (2024 데이터, 2025-02 발표)**
- 변경 라인 2억 1,100만 줄. 2024년에 5줄 이상 중복 코드 블록 빈도가 8배로 늘었고, moved 라인이 39.9% 줄었으며, 2024년은 copy/paste 라인이 moved 라인을 처음으로 넘어선 해다. — [DevClass 2025-02-20](https://www.devclass.com/ai-ml/2025/02/20/ai-is-eroding-code-quality-states-new-in-depth-report/1626250)
- 2020→2024 비교: copy/paste 라인 비중 8.3%→12.3%, moved 라인 24.1%→9.5%, 2주 안에 다시 고친 코드 3.1%→5.7%(DEV Community 글이 GitClear 수치를 옮긴 것으로, 2차 출처). — [DEV Community "The Year Duplicated Code Rose 8x"](https://dev.to/brandincanfield/the-year-duplicated-code-rose-8x-43n0)
- GitClear 원문 페이지 URL: https://www.gitclear.com/ai_assistant_code_quality_2025_research (403으로 직접 확인 못 함)

**GitClear 2026 보고서 "The Maintainability Gap" (2023~2026 데이터)**
- 실제 코드 변경 6억 2,300만 건(2023~2026)을 분석했다. 중복("5줄 이상 연속으로 반복되는 의미 있는 라인")은 AI 이전 대비 81% 늘었고, "Move (refactor)"는 70% 줄었다. 12개월 넘게 손대지 않은 코드를 고치는 legacy refactoring은 2023년보다 74% 줄었고, functional connectivity는 35% 줄었다. 오류 은폐 구문(rescue/catch 블록, safe-navigation, stub 메서드)은 기준 연도보다 47% 늘었다. AI 보조 커밋은 전체 커밋의 약 4분의 1이다. — [LeadDev 2026-07-07](https://leaddev.com/ai/code-maintainability-plummets-in-the-ai-coding-era)
- Harding 인용: "Every time you want something, AI creates a new package for it", "AI strongly prefers to write code that won't be labeled as a defect". — [LeadDev](https://leaddev.com/ai/code-maintainability-plummets-in-the-ai-coding-era)
- 검색 요약에만 나온 세부 수치(원문 미확인): 블록 중복이 백만 변경 라인당 40.3→73.0(+81%), 커밋 안 copy/paste +41%, moved 코드 비중 2022년 21%→2026년 3.8%. — [GitClear "The Maintainability Gap"](https://gitclear.com/the_ai_code_quality_maintainability_gap) (403), [The New Stack](https://thenewstack.io/ai-coding-duplication-rose/) (403)
- GitClear에는 "Write-Only Mode: AI Code Quality in 2026"이라는 별도 페이지도 있으나 내용은 확인하지 못했다. — [GitClear](https://www.gitclear.com/write_only_mode_ai_research)

**반론과 맥락**
- GitClear는 코드 리뷰 분석 도구를 파는 회사라 이해관계가 있다는 지적이 있다. Google DORA 2024는 AI 도입이 늘면 코드 품질이 3.4% 좋아지지만 delivery stability는 7.2% 떨어진다고 추정했다. — [DevClass 2025-02-20](https://www.devclass.com/ai-ml/2025/02/20/ai-is-eroding-code-quality-states-new-in-depth-report/1626250)

**학술·산업 연구**
- "More Code, Less Reuse" (Huang 외, arXiv:2601.21276, 2026-01-29, MSR '26): AIDev 데이터셋에서 에이전트 PR과 사람 PR을 비교했다. 중복 분석은 crewAI 저장소 PR 617건(Dataset B)으로 했고, CodeSage-Large 임베딩으로 의미적 유사도를 쟀으며, PyRef로 Move/Rename Method 리팩터링을 제외했다. 지표 Average Max Redundancy는 에이전트 0.2867 대 사람 0.1532("nearly 1.87x increase", Mann-Whitney p<0.001)였다. 순환 복잡도 같은 전통 지표는 "minimal differences"였다(변경 쌍 22,311개 중 18,968개(85.02%)가 0). 리뷰어 감정은 에이전트 PR에 더 중립적이거나 긍정적이었다. 논문은 이 중복이 리뷰어 감정에 드러나지 않는 "silent technical debt"라고 본다. — [arXiv 2601.21276](https://arxiv.org/html/2601.21276v1)
- Wu 외, "An Empirical Study of Code Clones from Commercial AI Code Generators" (Proc. ACM Softw. Eng. 2(FSE), 2025, pp. 2874–2896, doi 10.1145/3729397): 상용 AI 코드 생성기 3종에서 Type-1+Type-2 클론 비율이 최대 7.50%였다. 이 연구는 저장소 내부 재사용 실패가 아니라 기존 코드·학습 데이터를 복제하는 문제(저작권·취약점 전파)를 다룬다. — [FSE 2025 페이지](https://conf.researchr.org/details/fse-2025/fse-2025-research-papers/111/An-Empirical-Study-of-Code-Clones-from-Commercial-AI-Code-Generators), [researchr](https://researchr.org/publication/WuHFQHLZL25)
- OX Security "Army of Juniors" (2025-10): AI 생성 코드의 안티패턴 10가지 가운데 "avoidance of refactors"가 AI 생성 코드의 80–90%에서 나타났다고 보고한다. 표본은 오픈소스 저장소 300여 개이고, 그중 AI 코딩 도구를 쓴 저장소는 50개라고 보도됐다. 분모는 원문 PDF로 확인하지 못했다. — [OX Security](https://www.ox.security/army-of-juniors), [Help Net Security 2025-10-27](https://www.helpnetsecurity.com/2025/10/27/ai-code-security-risks-report/), [InfoQ 2025-11](https://www.infoq.com/news/2025/11/ai-code-technical-debt)
- arXiv:2607.01867 "An Exploratory Study on LLM-Generated Code and Comments in Code Repositories"는 LLM 생성으로 보이는 코드의 저장소 내부 파일 단위 클론이 대부분 70%를 넘는다고 보고한다는 검색 요약이 있다. 원문은 확인하지 못했다. — [arXiv PDF](https://arxiv.org/pdf/2607.01867)

### Inferences
- GitClear 3개 보고서(2024·2025·2026)는 "중복 증가 + moved 감소" 방향이 같다. 특히 moved 감소는 "기존 코드를 공통 모듈로 옮겨 재사용"하는 행동이 줄었다는 신호로, 이 저장소의 "client IP 추출 이중 구현" 같은 사례와 같은 현상이다.
- MSR '26 논문에서 에이전트 PR 중복은 텍스트가 아닌 임베딩 유사도(Type-4)로 측정됐다. 토큰 기반 도구(CPD, Sonar)만으로는 이 증가분을 놓칠 수 있다는 근거가 된다(4번 질문).
- 수치의 신뢰도: GitClear는 벤더 데이터이고 정의(5줄 이상 연속 중복)가 독자적이다. 보고서에 "AI 때문에 X% 늘었다"고 쓰기보다 "AI 보급 시기와 함께 X% 늘었다"로 쓰는 것이 정확하다.

### Gaps
- GitClear 원문(2025·2026) 표와 방법론을 직접 확인하지 못했다(403). 2026 세부 수치(40.3→73.0, 21%→3.8%)는 검색 요약에서만 확인된다.
- Claude Code·Codex 같은 "에이전트" 단위로 나눈 중복 수치는 찾지 못했다. LeadDev 기사도 도구별 데이터는 없다고 밝힌다.
- MSR '26 논문은 Python(crewAI)만 다뤄 Kotlin/JVM에 그대로 옮길 수 있는지는 검증되지 않았다.
- 2024 GitClear PDF는 OCR 텍스트를 추출할 도구(pdftotext)가 없어 연도별 operation 표를 읽지 못했다.

## 2. Kotlin을 지원하는 탐지 도구와 CI 게이트 구성

### Takeaway
Kotlin 중복 탐지는 PMD CPD(토큰 기반, Gradle 플러그인 있음), jscpd v5(Kotlin Type-3/Type-4 실험 모드, 기준선 대비 "새 클론만 실패", MCP 서버), SonarQube(새 코드 중복률 3.0% 게이트), IntelliJ/Qodana `DuplicatedCode` 검사로 할 수 있다. detekt에는 일반 코드 블록 중복 규칙이 없다. "기존 중복은 허용하고 새 중복만 막는" 방식이 jscpd(`--baseline-from-ref`), Sonar(new code 조건), ArchUnit(`FreezingArchRule`)에 공통으로 있다.

### Cited Findings

**PMD CPD**
- CPD는 Java, Kotlin 등을 지원한다("CPD works with Java, JSP, C/C++, C#, Go, Kotlin, Ruby, Swift and many more languages"). `--minimum-tokens`는 필수 옵션이다. — [PMD CPD 문서](https://pmd.github.io/pmd/pmd_userdocs_cpd.html)
- `--ignore-identifiers`·`--ignore-literals`는 Java·C++ 전용이고 `--ignore-annotations`는 C#·Java 전용이라 Kotlin에는 적용되지 않는다. Kotlin은 `CPD-OFF`/`CPD-ON` 주석 억제를 지원한다. 중복이 있으면 CLI 종료 코드 4(`--no-fail-on-violation`으로 끔), PMD 7.3.0부터 복구 가능 오류는 종료 코드 5다. — [PMD CPD 문서](https://pmd.github.io/pmd/pmd_userdocs_cpd.html)
- Gradle 플러그인 `de.aaschmid.cpd`: v3.5는 Gradle 7.4 이상과 PMD 7.4.0 이상을 지원한다. `minimumTokenCount`의 기본값은 50이고, `failOnViolation`·`failOnError`의 기본값은 true다(v3.5부터). 기본 소스는 Java source set이고, Kotlin DSL 예제는 `language = "kotlin"`으로 `cpdKotlin` 태스크를 따로 등록한다. Kotlin은 PMD 6.10.0부터 지원한다. 릴리스 날짜는 페이지에서 확인되지 않았다(커밋 299개, 스타 104개). — [gradle-cpd-plugin](https://github.com/aaschmid/gradle-cpd-plugin)

**jscpd (v5, Rust 단일 바이너리)**
- 형식 224개를 지원한다. Kotlin은 `--similarity`(함수 단위 near-miss) 언어 목록과 `--semantic`(Type-4, 실험) 언어 목록에 모두 들어 있다. `--max-gap-lines N`은 몇 줄을 끼워 넣거나 바꾼 복사본을 하나의 `similar` 클론으로 묶는다. — [jscpd README](https://raw.githubusercontent.com/kucherenko/jscpd/master/README.md)
- CI 게이트: "`--baseline .jscpd-baseline.json --fail-on-new-clones[=N]` tolerates legacy clones and fails the build on regressions", "`--baseline-from-ref origin/main` does the same without a committed file"(체크아웃에 base ref가 필요, `fetch-depth: 0`). `--threshold`는 트리 전체 중복률을 비교한다. GitHub Action `kucherenko/jscpd@v5`는 SARIF를 Code Scanning에 올린다. Docker, pre-commit, Super Linter/MegaLinter 연동도 있다. — [jscpd README](https://raw.githubusercontent.com/kucherenko/jscpd/master/README.md), [jscpd GitHub](https://github.com/kucherenko/jscpd)
- 버전: README 인용 블록은 5.3.0이고, 다른 본문은 5.4.2를 언급한다. — [jscpd README](https://raw.githubusercontent.com/kucherenko/jscpd/master/README.md)

**SonarQube / SonarCloud**
- 기본 품질 게이트 "Sonar way"에는 "Duplication in the new code is less than or equal to 3.0%"가 들어 있다. PR의 new code는 대상 브랜치 대비 바뀐 부분이다. fudge factor는 새 라인이 20줄 이상이 될 때까지 중복 조건을 무시하며, 기본으로 켜져 있다. — [SonarQube 품질 게이트](https://docs.sonarsource.com/sonarqube-server/latest/quality-standards-administration/managing-quality-gates/introduction-to-quality-gates/)
- 탐지 기준: Java는 "at least 10 successive and duplicated statements"이고, 그 밖의 언어는 "at least 100 successive and duplicated tokens"가 "10 lines of code for other languages"(COBOL 30, ABAP 20)에 걸쳐 있어야 한다. "Differences in indentation and in string literals are ignored". 지표는 `duplicated_lines_density`·`duplicated_lines`·`duplicated_blocks`·`duplicated_files`와 각각의 new code 버전이다. — [SonarQube 지표 정의](https://docs.sonarsource.com/sonarqube-server/latest/user-guide/code-metrics/metrics-definition/)

**IntelliJ IDEA / Qodana**
- `DuplicatedCode`("Duplicated code fragment") 검사는 보고할 중복 범위(옵션 ID `restrictedDuplicateScope`)와 중복으로 볼 언어 구문의 최소 크기를 설정할 수 있다. Qodana 설정 파일에서 켜고 끌 수 있고, Qodana for JVM 2026.1에 기본으로 들어 있다. — [Inspectopedia DuplicatedCode](https://jetbrains.com/help/inspectopedia/DuplicatedCode.html)
- `DuplicateExpressions`는 별도 검사다(`complexityThreshold` 기본값 70). — [Inspectopedia DuplicateExpressions](https://www.jetbrains.com/help/inspectopedia/DuplicateExpressions.html)
- (오래된 자료, 2020) Qodana Clone Finder EAP는 PHP·Java·Kotlin을 지원한다고 발표했다. — [JetBrains 블로그](https://blog.jetbrains.com/?p=143055/)

**detekt**
- 현재 style 규칙 페이지에는 일반 코드 블록 중복 규칙이 없다. 중복 대신 금지 경로를 정할 수 있는 규칙은 두 가지다. `ForbiddenMethodCall`("Reports all method or constructor invocations that are forbidden"): 기본 비활성, type resolution 필요, 설정 `methods`. `ForbiddenImport`: 기본 비활성, 설정 `forbiddenImports`·`allowedImports`(glob). — [detekt style 규칙](https://detekt.dev/docs/rules/style/)

**이 저장소 현황 (로컬 확인)**
- 루트 `build.gradle.kts`는 `de.thetaphi.forbiddenapis` 3.11을 쓰고, `suppressAnnotations = emptySet()`으로 어노테이션 우회를 막는다. `busLint`는 `forbiddenApis`에 의존하고, Stop hook이 바뀐 모듈마다 `busLint`를 부른다. 중복 탐지 도구(CPD·jscpd·Sonar)는 없다. — 로컬 파일 `/Users/hig/IdeaProjects/reservation/build.gradle.kts` 105–126행

### Inferences
- **Kotlin + CPD의 한계**: 식별자·리터럴 정규화 옵션이 Kotlin에 없으므로, CPD는 Kotlin에서 공백·주석만 다른 거의 그대로의 복사(Type-1)만 잡는다. 변수 이름만 바꾼 Type-2 클론도 놓칠 수 있다. 에이전트가 같은 로직을 새로 쓰면 보통 이름이 달라서, CPD만으로는 이 저장소의 실제 사고 유형을 막기 어렵다.
- **Sonar 기준과 짧은 헬퍼**: Kotlin은 Sonar 문서의 "other languages"(100토큰 + 10줄)에 해당할 것이다(Kotlin이 명시되지는 않음). `ClientIpResolver.resolve`처럼 한 줄짜리 헬퍼를 재구현한 경우는 이 기준과 fudge factor(20줄)에 모두 걸리지 않는다. 토큰 기반 게이트는 "큰 복붙"용이지 "작은 공통 함수 재구현"용이 아니다.
- **이 프로젝트 권장 구성**:
  1. `busLint`에 넣을 후보로는 jscpd v5(`--baseline-from-ref origin/main --fail-on-new-clones 0`, Kotlin `--similarity`)가 CPD보다 낫다. 기존 중복은 허용하고 에이전트가 이번 턴에 만든 새 클론만 실패시키므로 Stop hook 피드백 루프와 맞는다. 다만 바이너리 설치(시스템 의존)가 필요하고 Gradle 밖 도구다.
  2. Gradle 안에서 끝내려면 `de.aaschmid.cpd` + `language = "kotlin"` 태스크를 쓴다. 이 경우 새 코드만 보는 기능이 없으므로 `minimumTokenCount`를 높게 잡거나 처음에는 `ignoreFailures`로 보고만 받는다.
  3. 공용 PR 게이트(GitLab CI)에는 SonarQube의 new code 중복률 3.0% 조건이 표준이다. 짧은 재구현은 1번과 3번 질문의 아키텍처 테스트로 보완한다.
- detekt는 이미 롤백한 경험이 있으므로(메모리상 11-17), 중복 탐지만 위해 다시 들일 이유는 약하다. detekt의 `ForbiddenMethodCall`과 같은 역할을 forbiddenapis가 이미 하고 있다.

### Gaps
- `de.aaschmid.cpd` v3.5가 Gradle 9.9 + PMD 7.x 최신판에서 동작하는지, 최근 릴리스 날짜가 언제인지 확인하지 못했다.
- jscpd의 기본 `--min-tokens`/`--min-lines` 값과 `--threshold` 초과 시 종료 코드는 README에서 확인되지 않았다.
- Sonar의 Kotlin 전용 중복 파라미터(`sonar.cpd.exclusions`, 언어별 minimumTokens)는 이번 조사에서 공식 문서로 확인하지 못했다.
- Qodana `DuplicatedCode`를 qodana.yaml에 넣는 공식 예제, 그리고 Qodana Community(무료) 린터에 이 검사가 들어 있는지는 확인하지 못했다.
- detekt `StringLiteralDuplication` 규칙은 현재 style 페이지에서 보이지 않았다. detekt 2.0에서 바뀌었는지는 확인하지 않았다.

## 3. 에이전트용 예방책: 작성 전 검색 강제, 단일 소유자 아키텍처 테스트, 금지 API로 공통 헬퍼 유도

### Takeaway
공식 Claude Code 가이드는 CLAUDE.md/AGENTS.md 지시를 모델이 무시할 수 있는 "non-deterministic" 수단으로, hook·deny 규칙·CI를 강제 수단으로 나눈다. 따라서 "작성 전에 기존 헬퍼를 찾아라"는 지시는 출발점일 뿐이고, 실제로 막는 수단은 세 가지다. ① 한 관심사의 소유자를 고정하는 아키텍처 테스트(ArchUnit `callMethod`, Konsist `hasImport`/`hasTextContaining`), ② 공통 헬퍼로 안내하는 메시지를 단 금지 API 규칙(forbiddenapis `@defaultMessage`), ③ 변경분에 대한 reuse 리뷰 패스(Claude Code `/simplify`, jscpd MCP)다.

### Cited Findings

**지시(non-deterministic)와 강제(deterministic)의 구분 — Claude Code 공식 가이드**
- "Non-deterministic: you write the instruction, and Claude (the model) reads it. This can be your prompt, `CLAUDE.md`, or a skill. … there's no *guarantee* that it'll follow your instructions." / "Deterministic: you configure the instruction, and Claude Code (the harness) *has* to execute this. This can be a hook, a deny rule, or a managed setting." — [Claude Code Best practices](https://code.claude.com/docs/en/best-practices)
- "If something has to happen every time, make it a hook, a deny rule, or a managed setting. `CLAUDE.md` is for guidelines, and Claude is allowed to decide a guideline doesn't apply." — [Claude Code Best practices](https://code.claude.com/docs/en/best-practices)
- "If you keep adding emphasis to a `CLAUDE.md` line and Claude still misses it, that's a sign the line belongs in a hook." — [Claude Code Best practices](https://code.claude.com/docs/en/best-practices)
- Stop hook은 exit code 2로 종료를 막고, stderr 내용이 Claude에게 계속 일해야 하는 이유로 전달된다. hook이 도구 호출 없이 연속 8번 종료를 막으면 Claude Code가 다음 차단을 무시하고 턴을 끝낸다(`CLAUDE_CODE_STOP_HOOK_BLOCK_CAP`으로 조정). `prompt`/`agent` hook은 다른 모델이 판단하므로 결정적이지 않다. 실제 강제에는 `command`·`http`·`mcp_tool` hook을 쓴다. — [Claude Code Best practices](https://code.claude.com/docs/en/best-practices)
- "Broad codebase searches go to the built-in Explore agent." — [Claude Code Best practices](https://code.claude.com/docs/en/best-practices)
- 강제 수단은 적게 유지하라: "If you ever find yourself at forty of them, most are probably preferences". — [Claude Code Best practices](https://code.claude.com/docs/en/best-practices)

**Reuse 리뷰 패스**
- `/simplify`는 변경된 코드를 Code Reuse·Code Quality·Efficiency 세 에이전트가 병렬로 검토하고, reuse 에이전트가 새로 추가된 코드를 대신할 기존 유틸리티를 찾으며, 검토가 끝나면 수정을 적용한다고 소개된다(서드파티 설명). — [MindStudio](https://www.mindstudio.ai/blog/claude-code-simplify-batch-commands), [Pasquale Pillitteri 가이드](https://pasqualepillitteri.it/en/news/331/claude-code-simplify-batch-complete-guide)
- 공식 skills 문서는 `/simplify`를 bundled skill로 언급한다. 같은 이름의 프로젝트 스킬은 bundled 명령을 대체하지만 alias는 대체하지 않는다(예: project `code-review` 스킬은 `/code-review`를 대체하지만 `/review` alias는 대체하지 않음). `disableBundledSkills`로 끌 수 있다. — [Claude Code Skills](https://code.claude.com/docs/en/skills.md)
- 상충 정보: 2026-05 GitHub 이슈는 v2.1.146에서 `/simplify`가 `/code-review`로 바뀌었다고 주장한다. — [claudeissues 61030](https://claudeissues.com/issue/61030-docs-bundled-skill-docs-still-use-simplify-and-omit-code-review-effort), [gtapps/simplify](https://github.com/gtapps/simplify). 반면 이 세션(2026-11-21)의 Claude Code 스킬 목록에는 `simplify`("Review the changed code for reuse, simplification, efficiency, and altitude cleanups, then apply the fixes. Quality only")와 `code-review`가 따로 있다. — 로컬 관찰(현재 세션 스킬 목록)

**에이전트가 작성 전에 쓰는 중복 검색 도구**
- jscpd `--mcp`는 stdio MCP 서버를 띄운다. 어시스턴트는 이를 통해 "check a snippet against the codebase before writing it, list a file's clones", 다시 스캔, 두 폴더를 함수 단위로 비교할 수 있다. `--reporters ai`는 콘솔 리포터보다 토큰을 약 79% 적게 쓴다. 설치형 에이전트 스킬 5개(`jscpd`, `dry-refactoring`, `codebase-refactoring` 등)가 있다. — [jscpd GitHub](https://github.com/kucherenko/jscpd), [jscpd README](https://raw.githubusercontent.com/kucherenko/jscpd/master/README.md)

**단일 소유자 아키텍처 테스트**
- ArchUnit은 Java 바이트코드를 분석한다. Kotlin 테스트 클래스 예제(`@AnalyzeClasses(packagesOf = [MyArchitectureTest::class])`)가 있다. 미리 정의된 조건 `ArchConditions.callMethod(Object.class, "equals", Object.class)`, 접근 규칙 `noClasses().that().resideInAPackage("..service..").should().accessClassesThat().resideInAPackage("..controller..")`, `onlyBeAccessed().byAnyPackage(...)`, `dependOnClassesThat()`를 쓸 수 있다. `FreezingArchRule.freeze(rule)`은 첫 실행의 위반을 저장하고 이후에는 새 위반만 보고한다(`freeze.refreeze=true`로 다시 기록). — [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html)
- `noClasses().should().callMethod(LocalDateTime.class, "now")` 형태로 특정 메서드 호출을 금지한 사례가 있다. — [검색 결과: Baeldung/Reflectoring 등 ArchUnit 소개](https://www.baeldung.com/java-archunit-intro)
- Konsist는 "like ArchUnit, but for Kotlin language"인 구조 linter다(JUnit4/5·Kotest, `Konsist.scopeFromProject()`, `assertTrue`, `assertArchitecture { Layer(...) }`). 1.0을 앞두고 있다. — [Konsist 문서](https://docs.konsist.lemonappdev.com/)
- Konsist 일반 스니펫: `.assertFalse { it.hasImport { import -> import.hasNameStartingWith("java.util.logging") } }`, `.assertFalse { it.hasTextContaining("forbiddenString") }`. — [Konsist General Snippets](https://docs.konsist.lemonappdev.com/inspiration/snippets/general-snippets)

**금지 API로 공통 헬퍼 유도**
- forbiddenapis 시그니처 문법: `클래스#메서드(파라미터)`(파라미터 타입은 완전한 이름), 생성자는 `<init>`, `시그니처 @ 메시지`, 이어지는 시그니처에 같은 메시지를 다는 `@defaultMessage`. — [forbidden-apis Signatures Syntax](https://github.com/policeman-tools/forbidden-apis/wiki/SignaturesSyntax)
- Gradle 확장의 `signaturesFiles`, `bundledSignatures`, `ignoreFailures`. — [forbidden-apis Gradle Usage](https://github.com/policeman-tools/forbidden-apis/wiki/GradleUsage)

**이 저장소의 실제 사례 (로컬 확인)**
- 현재 IP 추출은 `modules/iam/src/main/kotlin/com/media/bus/iam/support/ClientIpResolver.kt`의 `object ClientIpResolver { fun resolve(request: HttpServletRequest?): String? = request?.remoteAddr }` 하나로 모여 있다. `AuthCookieSupport.extractClientIp`와 `AuditLogService.record`는 이를 위임 호출한다. 기존 아키텍처 테스트(`ModuleBoundaryTest`, `WebSurfaceArchitectureTest`)는 ArchUnit·Konsist 없이 Spring `ClassPathScanningCandidateComponentProvider`와 리플렉션으로 작성돼 있다. — 로컬 파일

### Inferences
- **IP 사고에 맞는 규칙 배치**:
  - forbiddenapis로 `jakarta.servlet.ServletRequest#getRemoteAddr() @ [bus:SingleClientIpResolver] ClientIpResolver.resolve(request)를 쓴다. 참고: modules/iam/.../support/ClientIpResolver.kt`를 금지하면, 메시지가 Stop hook 출력으로 바로 에이전트에게 간다. 이 저장소의 `[bus:*]` 메시지 관례와도 맞는다. 다만 `suppressAnnotations = emptySet()`이라 소유자 클래스 `ClientIpResolver` 자신도 걸린다. 따라서 소유자 예외는 forbiddenapis가 아닌 다른 곳에 둬야 한다. 예: `forbiddenApisMain`에서 해당 클래스 파일을 빼는 설정(가능 여부는 확인 필요), ktlint 커스텀 규칙에 경로 allowlist, 아키텍처 테스트.
  - 가장 깔끔한 것은 아키텍처 테스트다. ArchUnit `noClasses().that().doNotHaveFullyQualifiedName("com.media.bus.iam.support.ClientIpResolver").should().callMethod(ServletRequest::class.java, "getRemoteAddr")`에 X-Forwarded-For 헤더 문자열 사용 금지를 더하는 식이다(문법은 직접 검증 필요). 이렇게 하면 "소유자 1개"가 코드로 고정된다. 현재 테스트처럼 리플렉션만으로는 메서드 본문의 호출을 보기 어렵다. 그러니 바이트코드를 보는 ArchUnit이나 소스 텍스트를 보는 Konsist `hasTextContaining("remoteAddr")` 중 하나가 필요하다.
  - Konsist는 Kotlin 소스 수준이라 `request.remoteAddr` 같은 프로퍼티 접근을 import로는 잡지 못하고 텍스트 매칭에 기대야 한다. ArchUnit은 컴파일된 `getRemoteAddr()` 호출을 정확히 잡는다. 따라서 "호출 소유자 고정"에는 ArchUnit이 더 정밀하다.
- **지시 계층**: AGENTS.md에는 "공통 관심사 소유자 표"(예: client IP → `ClientIpResolver`, 예외 → Result enum)를 짧게 두고 "새 헬퍼를 만들기 전에 이 표와 `support` 패키지를 검색"하라고 쓴다. 강제는 위 규칙이 맡는다. 공식 가이드의 구분(지시는 출발점, hook·테스트가 강제)을 그대로 따르는 구성이다.
- **Reuse 패스**: `/simplify`(reuse 에이전트)나 jscpd MCP의 "작성 전 스니펫 검사"는 아직 소유자 규칙이 없는 새 관심사를 잡는 보완책이다. 모델 판단이라 결정적이지 않으므로 Stop hook이 아니라 커밋 전 수동 단계나 PR 리뷰 단계에 둔다.
- 강제 규칙은 "같은 교정을 두 번 받은" 관심사에만 추가해 수를 적게 유지한다. 공식 가이드의 "forty of them" 경고 및 사용자 공통 지침과 같은 방향이다.

### Gaps
- forbiddenapis Gradle 태스크에서 특정 클래스만 검사에서 빼는 패턴 필터가 지원되는지는 공식 위키에서 확인하지 못했다(위키에 `suppressAnnotations`·exclude 설명 없음).
- ArchUnit의 `callMethod`가 Kotlin 프로퍼티 접근(`request.remoteAddr` → `getRemoteAddr()`)이나 `HttpServletRequest`(하위 인터페이스) 경유 호출을 `ServletRequest` 대상으로 매칭하는지는 직접 테스트해야 한다.
- 대규모로 AI 에이전트를 쓰는 팀이 "단일 소유자" 아키텍처 테스트로 중복을 줄였다는 정량 사례(엔지니어링 블로그)는 찾지 못했다.
- Codex/AGENTS.md 쪽의 "작성 전 검색" 공식 권장 문구는 이번 조사에서 확인하지 않았다.

## 4. 한계: 텍스트가 다른 의미적 중복(Type-4)과 대응 방법

### Takeaway
토큰 기반 도구(CPD, Sonar)는 Type-1~2(일부 Type-3)용이다. 에이전트가 만드는 중복은 이름과 구조가 다른 Type-4 비중이 높다는 연구가 있다. 대응은 ① 임베딩 기반 함수 유사도(jscpd `--semantic`, CodeSage·CodeRankEmbed 계열), ② LLM 리뷰·판정(연구상 F1이 높지만 데이터셋에 따라 크게 떨어짐), ③ 의미 단위 소유자 규칙(아키텍처 테스트)으로 정리된다. ③만 결정적이다.

### Cited Findings
- 고전 클론 탐지 도구 9종을 GPTCloneBench(GPT-3 생성 클론)로 평가한 결과, 도구들은 AI 생성 클론에도 "retain considerable effectiveness"를 보였다. 일부 도구는 기존 벤치마크보다 성능 차이가 컸고, 정규화가 좋은 도구가 더 나았다. 고전 도구는 역사적으로 Type 1–3과 "limited semantic clones"에 맞춰져 있다(Alam 외, arXiv:2509.25754, ICSME 2025). — [arXiv 2509.25754](https://arxiv.org/abs/2509.25754)
- LLM 기반 클론 탐지: LLM 5종 × 프롬프트 4종 × 데이터셋 7종으로 평가했다. CodeNet 기반에서는 o3-mini가 F1 0.943이었지만 BigCloneBench 기반에서는 성능이 크게 떨어졌다. 5회 제출 간 판단 일치율은 90% 이상이고 F1 변동은 0.03 미만이었다(Zhu 외, arXiv:2511.01176, 2025-11-03). — [arXiv 2511.01176](https://arxiv.org/abs/2511.01176)
- GPT-3.5·GPT-4는 사람이 만든 클론보다 LLM이 만든 클론을 더 정확히 탐지했다(arXiv:2407.02402, 2024, 검색 요약). — [arXiv 2407.02402](https://arxiv.org/pdf/2407.02402)
- 임베딩 기반 측정 사례: MSR '26 논문은 CodeSage-Large 임베딩으로 PR별 Max Redundancy Score를 계산했다. 에이전트 PR의 AMR은 0.2867, 사람 PR은 0.1532였다. — [arXiv 2601.21276](https://arxiv.org/html/2601.21276v1)
- 도구 사례: jscpd `--semantic`은 "Type-4 semantic clones (experimental)"이고, 기본 모델은 CodeRankEmbed(MIT)이며 jina-embeddings-v2-base-code도 쓸 수 있다. Java·Kotlin 함수를 비교한다. — [jscpd README](https://raw.githubusercontent.com/kucherenko/jscpd/master/README.md)
- 리뷰어는 에이전트 PR의 중복에 감정적으로 반응하지 않았다(중립·긍정 비율이 더 높음). 사람 리뷰만으로는 이 중복을 걸러내기 어렵다는 근거다. — [arXiv 2601.21276](https://arxiv.org/html/2601.21276v1)

### Inferences
- 이 저장소의 IP 사고는 "텍스트는 짧고 다르지만 의미는 같은" 유형이다. CPD·Sonar 기준(10줄, 100토큰)에 안 걸리고, 임베딩 방식도 한 줄 함수에서는 신호가 약할 것이다. 따라서 이미 알려진 관심사는 소유자 규칙(아키텍처 테스트·금지 API)으로 막는다. 아직 알려지지 않은 관심사는 임베딩·LLM 리뷰(`/simplify`, jscpd `--semantic`)로 찾아서 규칙으로 승격한다. 이 2단계 구성이 증거와 맞는다.
- 임베딩·LLM 판정은 확률적이다(F1이 데이터셋에 따라 크게 달라짐). Stop hook의 차단 조건으로 쓰면 오탐 때문에 턴이 반복 차단될 수 있으므로(연속 8회 차단 상한), 보고용이나 PR 단계에 두는 것이 안전하다.
- jscpd `--semantic`은 임베딩 모델을 로컬에서 돌리므로 CI 러너(GitLab, Docker)에 모델 크기와 실행 시간 비용이 붙는다. 처음에는 정기 작업(야간 전체 스캔)으로 두고 결과를 사람이 판단하는 편이 낫다.

### Gaps
- jscpd `--semantic`의 Kotlin 정밀도·재현율 공개 벤치마크는 찾지 못했다. 실험 기능이다.
- Kotlin/JVM 대상의 Type-4 클론 탐지 벤치마크(에이전트 생성 코드 기준)는 찾지 못했다. 공개 연구는 Python·Java 위주다.
- arXiv 2509.25754의 도구별 수치(어떤 도구가 Type-3/4에서 약했는지)는 초록에 없어 확인하지 못했다.
- LLM 리뷰 패스(`/simplify` 등)가 중복을 실제로 얼마나 줄이는지에 대한 정량 평가는 찾지 못했다.
