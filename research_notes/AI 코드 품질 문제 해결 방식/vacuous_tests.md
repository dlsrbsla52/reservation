# 아무것도 검증하지 않는 테스트(vacuous tests) 탐지와 개선 — 뮤테이션 테스트, LLM 생성 테스트 품질, 경량 신호, Kotlin·Spring Boot 적용

조사일: 2026-11-21. 출처 표기 규칙: 웹페이지를 직접 열어 확인한 것은 그냥 링크만 단다. 검색 결과 요약만 보고 본문을 열지 못한 것은 "(검색 요약 기준)"으로 표시한다. 2024년 이전 자료는 "(구 자료, 연도)"로 표시한다. 저장소 로컬 사실은 파일 경로를 출처로 쓴다.

적용 대상 저장소의 현재 상태(로컬 확인):
- Gradle wrapper `gradle-9.9.0-bin.zip` — `gradle/wrapper/gradle-wrapper.properties`
- Java toolchain `JavaLanguageVersion.of(25)` — `build.gradle.kts` 64–65, 170–172행
- 테스트 의존성: `spring-boot-starter-test`, `io.mockk:mockk:1.13.10`, `junit-platform-launcher`(BOM 관리), `useJUnitPlatform()`, test JVM 인자 `-Xshare:off -Dnet.bytebuddy.safe=true` — `build.gradle.kts` 93–104행
- Spring Boot 4.1.2 BOM이 관리하는 버전: `junit-jupiter.version` **6.0.3**, `mockito.version` 5.23.0(spring-boot-starter-test가 끌어옴), `assertj.version` 3.27.7 — `~/.gradle/caches/.../spring-boot-dependencies/4.1.2/*.pom`
- `modules/**` 안 Kotlin 테스트 파일 67개, `@Test` 약 394개, `relaxed = true`/`relaxUnitFun = true` 37곳(13개 파일), `verify {`/`coVerify {` 34줄, assertion 호출(assertThat·assertEquals·shouldBe·assertThrows 등) 742줄 — `grep` 집계(2026-11-21)
- 저장소의 커스텀 ktlint 규칙: `TestMethodNameRule`, `NoMethodDisplayNameRule`, `CommentPolicyRule`, `EntityFactoryNameRule`, `NoHardcodedExceptionMessageRule`. assertion이 없는 테스트를 잡는 규칙은 없다 — `lint/ktlint-rules/src/main/kotlin/com/media/bus/lint/ktlint/`

---

## 1. JVM/Kotlin 뮤테이션 테스트: PIT, Gradle 플러그인, Kotlin 지원, JUnit 5/6, JDK 25·Gradle 9 호환성, 변경 코드만 분석, 실행 비용

### Takeaway
PIT 최신은 1.30.2(2026-11-05)이고, Gradle 플러그인 `info.solidsoft.pitest` 최신은 1.19.0(2026-03-29, 기본 PIT 1.22.1)이다. JDK 25를 쓰려면 `pitestVersion`을 1.25.8 이상(가급적 1.30.x)으로 올려야 한다. 공식 문서 어디에도 Gradle 9.9와 JUnit Jupiter 6(Spring Boot 4.1.2 BOM 기본값)을 지원한다는 내용이 없어서 PoC로 직접 확인해야 한다. Kotlin 바이트코드에서 나오는 "junk mutation"을 걸러주는 것은 상용 Arcmutate Kotlin 플러그인이고, "변경 코드만" 분석하는 것도 오픈소스 Gradle 경로에서는 실험 기능인 history 파일뿐이며 본격적인 git 기반 분석은 Arcmutate(라이선스 필요)가 제공한다.

### Cited Findings

**PIT 코어 버전과 JDK 25**
- PIT 릴리스: 1.30.2(11월 5일), 1.30.1(10월 14일), 1.30.0(8월 27일), 1.25.9(8월 4일), 1.25.8(7월 20일), 1.25.7(7월 8일), 1.25.6(7월 7일), 1.25.5(6월 17일), 1.25.4(6월 9일), 1.25.3(5월 29일). 릴리스 페이지에 연도는 표시되지 않지만, gradle 플러그인 changelog(1.19.0 = 2026-03-29, 기본 PIT 1.22.1)와 순서를 맞춰 보면 2026년이다 — [hcoles/pitest releases](https://github.com/hcoles/pitest/releases)
- 1.25.8 릴리스 노트에 "Fix BigDecimal and BigInteger mutators for java 25"가 있다. 1.30.1에서 "Bump asm to 9.11", 1.25.6에서 "ASM 9.10.1"로 올렸다 — [hcoles/pitest releases](https://github.com/hcoles/pitest/releases)
- 릴리스 노트에 Kotlin이나 incremental analysis를 언급한 항목은 없다 — [hcoles/pitest releases](https://github.com/hcoles/pitest/releases)

**Gradle 플러그인(info.solidsoft.pitest)**
- 최신 1.19.0, 생성일 2026-03-29. Kotlin DSL은 `plugins { id("info.solidsoft.pitest") version "1.19.0" }` — [Gradle Plugin Portal](https://plugins.gradle.org/plugin/info.solidsoft.pitest)
- changelog 요약 — [gradle-pitest-plugin CHANGELOG](https://github.com/szpak/gradle-pitest-plugin/blob/master/CHANGELOG.md)
  - 1.20.0(미출시): "Fix Gradle 9 deprecation warnings"
  - 1.19.0(2026-03-29): "PIT 1.22.1 by default"
  - 1.19.0-rc.3(2026-01-19): "PIT 1.22.0 by default", "`useClasspathFile` is enabled by default"(`useClasspathFile = false`로 끌 수 있다)
  - 1.19.0-rc.2(2025-10-01): "PIT 1.20.3 by default", "pitestReportAggregate task is compatible with configuration cache in Gradle 9"
  - 1.19.0-rc.1(2025-06-07): "Java 17 is minimal supported version", "Gradle 8.4 is minimal supported version", "Added examples of using plugin with Kotlin DSL", "Initial support for Gradle 9 (no warnings with 9.0-m9)"
- Arcmutate 문서의 Gradle 예시는 `pitestVersion = '1.30.2'`로 코어 버전을 덮어쓰고, 멀티모듈 집계 태스크 `pitestReportAggregate`를 쓸 때 설정하는 방법을 함께 적었다 — [Arcmutate Kotlin docs](https://docs.arcmutate.com/docs/kotlin.html)

**JUnit 5 플러그인(pitest-junit5-plugin)**
- 최신은 1.2.3이다. Quarkus 3.22.x 이상을 지원하고 pitest 1.19.4 이상이 필요하다. 1.2.1과 1.2.2는 pitest 1.15.2 이상, 1.2.0은 1.14.0 이상이 필요하다 — [pitest-junit5-plugin README](https://github.com/pitest/pitest-junit5-plugin)
- Gradle에서 `junit5PluginVersion`을 설정하면 "adds dependency to org.pitest:pitest-junit5-plugin and sets "testPlugin" to "junit5"" 된다. README 예시는 `junit5PluginVersion = '1.2.2'`, `pitestVersion = '1.18.2'`(최신보다 오래됨) — [pitest-junit5-plugin README](https://github.com/pitest/pitest-junit5-plugin)
- 1.2.2 노트: "The pitest maven and gradle plugins now automatically resolve the correct version of platform launcher at runtime". platform-launcher 의존성을 provided로 바꿨는데, 예전에는 빌드 시점 버전이 전이 의존으로 끌려와 런타임 충돌을 일으켰다 — [pitest-junit5-plugin README](https://github.com/pitest/pitest-junit5-plugin)
- 검색으로는 pitest-junit5-plugin이 JUnit 6을 지원한다고 확인할 자료를 찾지 못했다(검색 요약 기준) — [검색 결과: pitest-junit5-plugin](https://github.com/pitest/pitest-junit5-plugin)
- 이 저장소는 Spring Boot 4.1.2 BOM 덕분에 JUnit Jupiter 6.0.3을 쓴다 — 로컬 `spring-boot-dependencies-4.1.2.pom`

**Kotlin 지원: 오픈소스 PIT의 한계와 Arcmutate Kotlin 플러그인**
- Kotlin은 일부 언어 기능을 "compiler generated constructs that do not map back to the source code"로 컴파일한다. 그래서 PIT가 소스의 실수로는 재현할 수 없는 "junk mutations"를 만든다. Arcmutate Kotlin 플러그인은 이것을 걸러내고 mutant 설명의 잡음을 줄인다 — [Arcmutate Kotlin docs](https://docs.arcmutate.com/docs/kotlin.html)
- 처리 대상 언어 기능: coroutines, destructuring, intrinsics, safe casts, 자동 생성 accessor, `lateinit`, enum·sealed class의 unmatched `when`, var accessor, inline 코드, non-null 타입 — [Arcmutate Kotlin docs](https://docs.arcmutate.com/docs/kotlin.html)
- null 필터링: 기본으로 컴파일러가 생성한 null 처리 변이 중 일부를 제거한다. `+KOTLIN_NO_NULLS`는 손으로 쓴 것까지 null check 변이를 전부 제거하고, `-KOTLIN_MULTI_NULL`은 기본 필터를 완화한다 — [Arcmutate Kotlin docs](https://docs.arcmutate.com/docs/kotlin.html)
- 이미 빈 컬렉션을 반환하는 메서드에 "empty return"을 넣는 equivalent mutant를 걸러낸다. inline 함수의 변이는 원래 함수로 옮겨 보고하고(1.0.0부터), 500 instruction을 넘는 메서드는 기본으로 건너뛴다 — [Arcmutate Kotlin docs](https://docs.arcmutate.com/docs/kotlin.html)
- Kotlin 전용 mutator: `KOTLIN_RETURNS`, `KOTLIN_REMOVE_DISTINCT`, `KOTLIN_REMOVE_SORTED` — [Arcmutate Kotlin docs](https://docs.arcmutate.com/docs/kotlin.html)
- 설치: `pitest 'com.arcmutate:pitest-kotlin-plugin:1.5.2'`(PIT 클래스패스에 추가). `pitestReportAggregate`를 쓰면 `pitestReport` 구성에도 추가해야 한다. PIT 1.22.0 이상 필요 — [Arcmutate Kotlin docs](https://docs.arcmutate.com/docs/kotlin.html)
- 라이선스: "Before you can use the integration, you must first acquire a licence". 라이선스 파일은 프로젝트 루트의 `arcmutate-licence.txt`이고, 동적 라이선스 조회도 지원한다 — [Arcmutate Kotlin docs](https://docs.arcmutate.com/docs/kotlin.html)
- Meta의 ACH는 "10,795 Android Kotlin classes"에 뮤테이션 기반 테스트 생성을 적용했다. 산업 규모에서 Kotlin 코드를 뮤테이션 테스트한 사례다 — [arXiv 2501.12862](https://arxiv.org/abs/2501.12862)

**변경 코드만 분석(CI용)**
- PIT incremental analysis는 "experimental feature"다. `withHistory`(true면 `java.io.tmpdir`에 저장하고 input/output 위치 설정은 무시), `historyInputLocation`/`historyOutputLocation`으로 지난 결과를 재사용한다 — [pitest.org incremental analysis](https://pitest.org/quickstart/incremental_analysis/)
- 재사용 규칙: 클래스와 죽인 테스트가 둘 다 바뀌지 않았으면 killed로 보고, 새로 덮는 테스트도 없고 덮던 테스트도 바뀌지 않았으면 survived로 본다. 의존성 중에서는 superclass와 outer class 변경만 추적하고, 이 가정은 "currently **unproven**"이라고 문서가 직접 밝힌다 — [pitest.org incremental analysis](https://pitest.org/quickstart/incremental_analysis/)
- input과 output 위치를 다르게 두면 "you will need to implement some mechanism to swap the values between runs". 즉 CI에서는 history 파일을 직접 캐시해야 한다 — [pitest.org incremental analysis](https://pitest.org/quickstart/incremental_analysis/)
- Arcmutate git 플러그인: `+GIT(from[master])` 같은 feature string을 주면 "only code modified between the specified git refs will be analysed". 기본 범위는 수정된 라인이고 `scope[class]`로 클래스 전체로 넓힐 수 있다. `+git_test`, `+git_mixed` 모드도 있다. 생성되는 `gitci` JSON은 "GitHub, Gitlab, Bitbucket and Azure DevOps integrations"가 PR 워크플로에서 쓴다. git 기능이 켜지면 `failWhenNoMutations=false`가 자동 설정된다. PIT 1.22.0 이상과 라이선스가 필요하다 — [Arcmutate git integration](https://docs.arcmutate.com/docs/git-integration.html)
- PIT FAQ와 블로그는 속도를 높이는 방법으로 Arcmutate accelerator 플러그인, git으로 수정된 라인만 분석하기, `withHistory`, subsumption 분석으로 mutant 수 줄이기를 든다(검색 요약 기준) — [PIT FAQ](https://pitest.org/faq), [blog.pitest.org "less is more"](https://blog.pitest.org/less-is-more)
- Maven에는 `scmMutationCoverage` goal로 차등 분석을 하는 경로가 있다고 실무 글이 소개한다(검색 요약 기준, 실무 블로그) — [augmentcode guide](https://www.augmentcode.com/guides/mutation-testing-ai-generated-code)

**실행 비용**
- Google 사례: 코드 20억 줄, 하루 1억 5천만 개 이상의 테스트 실행. 리뷰 중 변경된 코드만 변이하고 무관한 mutant를 걸러내며 라인당·리뷰당 mutant 수를 제한하는 방식으로 "orders of magnitude fewer mutants"를 만든다 — [Petrović et al., "Practical Mutation Testing at Scale", IEEE TSE 2021 (구 자료, 2021)](https://research.google/pubs/practical-mutation-testing-at-scale-a-view-from-google/)
- Thoughtworks Radar도 "mutation runs are slow"라고 보고, 특정 패키지나 최근 변경 코드로 범위를 좁히라고 권한다 — [Thoughtworks Radar: Mutation testing](https://www.thoughtworks.com/en-sg/radar/techniques/mutation-testing)

**경량 대안: Descartes(extreme mutation)**
- Descartes는 PIT의 mutation engine 플러그인이다. 메서드 본문을 단순한 return 하나로 바꾸거나 비워서 "pseudo-tested method"(로직을 지워도 테스트가 통과하는 메서드)를 찾는다. 생성자는 변이하지 않고, 단순 getter·setter, 빈 void, 상수 반환 메서드는 건너뛴다. `mutationEngine = descartes`로 선택한다(검색 요약 기준) — [stamp-project/pitest-descartes](https://github.com/stamp-project/pitest-descartes), [Vera-Pérez & Monperrus, arXiv 1811.03045 (구 자료, 2018)](https://arxiv.org/abs/1811.03045)

### Inferences
- **JDK 25 바이트코드**: gradle 플러그인 1.19.0의 기본 PIT는 1.22.1이고, Java 25 관련 수정(1.25.8)과 ASM 9.11(1.30.1)은 그보다 뒤에 나왔다. 따라서 `pitestVersion`을 1.30.2로 명시해야 한다. Arcmutate 문서 예시도 1.30.2를 쓴다.
- **Gradle 9.9**: 1.19.0은 Gradle 9를 "initial support"하는 수준이고 deprecation 경고 수정은 미출시 1.20.0에 들어 있다. 9.9에서는 경고가 나오거나 configuration cache와 충돌할 수 있다. 첫 PoC로 `:modules:stop`(가장 작은 모듈로 추정) 하나에 적용해 보는 것이 안전하다.
- **JUnit Jupiter 6.0.3**: pitest-junit5-plugin README는 JUnit 6을 언급하지 않는다. "launcher 버전을 런타임에 자동으로 맞춘다"는 1.2.2의 동작 덕분에 될 수도 있지만 아직 미확인이다. PoC에서 실패하면 첫 번째로 의심할 지점이다.
- **MockK와 PIT minion JVM**: 이 저장소의 `test` 태스크는 `-Xshare:off -Dnet.bytebuddy.safe=true`를 쓴다. PIT는 별도 minion JVM에서 테스트를 돌리므로 `test` 태스크의 jvmArgs가 전달되지 않을 가능성이 크다. pitest 설정의 `jvmArgs`(속성명은 플러그인 README에서 확인 필요)에 같은 값을 넣어야 할 수 있다. (이번 조사에서 미검증)
- **Kotlin 노이즈**: 오픈소스 PIT만 쓰면 Kotlin의 컴파일러 생성 null check(`Intrinsics.checkNotNullParameter` 등), data class와 coroutine 상태 머신에서 junk mutant가 생기고, 이것들이 survived로 잡혀 점수를 깎을 수 있다. 문서가 junk mutation의 존재를 명시하므로 근거가 있는 추정이다. 오픈소스만으로 갈 때는 다음을 검토한다. (a) mutator를 `DEFAULTS`나 `STRONGER` 중 반환값·조건 경계 위주로 좁힌다. (b) `avoidCallsTo`/`excludedMethods`로 `kotlin.jvm.internal.Intrinsics`를 제외한다. 실제 효과는 PoC로 측정해야 한다.
- **스프링 통합 테스트 제외**: `@SpringBootTest`나 컨텍스트 로딩 테스트를 mutant마다 돌리면 비용이 폭발한다. `excludedTestClasses`나 태그로 순수 단위 테스트(MockK 기반 Service·Validator 테스트)만 대상으로 삼는 것이 현실적이다.
- **검증 전 설정 스케치**(속성명은 플러그인 README로 재확인할 것):
  ```kotlin
  // 루트 build.gradle.kts의 subprojects 또는 모듈 build.gradle.kts
  plugins { id("info.solidsoft.pitest") version "1.19.0" }
  pitest {
      pitestVersion.set("1.30.2")          // JDK 25 수정 포함 버전
      junit5PluginVersion.set("1.2.3")     // JUnit 6 호환은 PoC로 확인
      targetClasses.set(listOf("com.media.bus.stop.*"))
      excludedClasses.set(listOf("*Config*", "*Table*", "*Application*"))
      threads.set(4)
      outputFormats.set(listOf("XML", "HTML"))
      timestampedReports.set(false)
      jvmArgs.set(listOf("-Xshare:off", "-Dnet.bytebuddy.safe=true"))
      // historyInputLocation / historyOutputLocation → GitLab CI cache로 유지
  }
  ```
- **GitLab CI**: Arcmutate git 플러그인은 GitLab 통합을 문서화했다. 라이선스를 사지 않을 경우 대안은 두 가지다. (a) MR 파이프라인에서 `git diff --name-only origin/main...`으로 바뀐 Kotlin 파일을 FQCN으로 바꿔 `targetClasses`에 넘기는 셸 스크립트, (b) history 파일 캐시.

### Gaps
- Gradle 9.9와 gradle-pitest-plugin 1.19.0의 호환성을 명시한 공식 자료를 찾지 못했다(changelog는 "initial support"와 미출시 1.20.0의 경고 수정만 언급).
- pitest-junit5-plugin 1.2.3과 JUnit Jupiter/Platform 6.x의 호환성을 확인한 자료가 없다.
- Arcmutate 가격 페이지(`arcmutate.com/pricing`)가 404를 반환해 가격, 오픈소스 무료 여부, 체험판 조건을 확인하지 못했다.
- 예전 오픈소스 `pitest-kotlin` 플러그인의 현재 상태(아카이브 여부)는 확인하지 못했다.
- PIT 실행 시간에 대한 공식 벤치마크나 Arcmutate accelerator의 단축률 수치를 찾지 못했다. 이 저장소(테스트 약 394개)의 실제 비용은 PoC로 측정해야 한다.
- Descartes가 PIT 1.30.x와 JDK 25·Kotlin 바이트코드에서 동작하는지 확인하지 못했다.

---

## 2. LLM 생성 테스트 품질 연구: Meta TestGen-LLM·ACH, 테스트 스멜·assertion 품질, "통과하지만 검증하지 않는" 테스트, Google 뮤테이션 테스트

### Takeaway
산업 사례(Meta)는 LLM이 만든 테스트를 그대로 받지 않는다. "빌드됨 → 안정적으로 통과 → 측정 가능한 개선(커버리지나 mutant kill)" 필터를 통과한 것만 사람에게 보여주고, 그때 수용률이 73%였다. 학술 연구는 LLM 테스트가 커버리지는 높아도 mutation score가 낮을 수 있고(100% 커버리지, 4% mutation score 사례), Assertion Roulette 같은 스멜이 흔하다고 보고한다. 에이전트가 테스트를 고쳐서 통과시키는 "test exploitation"도 정량적으로 측정됐다. 반대로 최신 Claude 모델의 테스트가 인간 테스트보다 약하지 않다는 2026년 연구도 있어서, 결론은 "LLM 테스트는 나쁘다"가 아니라 "검증 없이 믿지 말라"다.

### Cited Findings

**Meta TestGen-LLM (FSE 2024)**
- 논문 "Automated Unit Test Improvement using Large Language Models at Meta", Alshahwan, Harman 외, arXiv 2402.09171(2024-02-14), FSE 2024 — [arXiv 2402.09171](https://arxiv.org/abs/2402.09171)
- TestGen-LLM은 기존 인간 테스트를 개선하고, 생성한 테스트가 측정 가능한 개선을 입증하는 필터를 통과해야 한다(할루시네이션 방지) — [arXiv 2402.09171](https://arxiv.org/abs/2402.09171)
- Instagram Reels·Stories 평가: "75% of TestGen-LLM's test cases built correctly, 57% passed reliably, and 25% increased coverage." — [arXiv 2402.09171](https://arxiv.org/abs/2402.09171)
- Instagram·Facebook test-a-thon: 적용한 클래스의 11.5%를 개선했고, 엔지니어가 추천의 73%를 프로덕션 배포용으로 수용했다 — [arXiv 2402.09171](https://arxiv.org/abs/2402.09171)

**Meta ACH: 뮤테이션 유도 LLM 테스트 생성 (2025)**
- 논문 "Mutation-Guided LLM-based Test Generation at Meta", Foster, Harman 외, arXiv 2501.12862(2025-01-22), FSE 2025 Industry Track 제출 — [arXiv 2501.12862](https://arxiv.org/abs/2501.12862)
- "ACH generates relatively few mutants (aka simulated faults), compared to traditional mutation testing." 특정 관심사(예: privacy)에 관련된, 현재 탐지되지 않는 결함을 mutant로 만들고, 그것을 죽이는 테스트를 생성해 회귀를 막는다 — [arXiv 2501.12862](https://arxiv.org/abs/2501.12862)
- 적용 규모: 7개 플랫폼의 Android Kotlin 클래스 10,795개, mutant 9,095개, privacy-hardening 테스트 571개 생성 — [arXiv 2501.12862](https://arxiv.org/abs/2501.12862)
- LLM 기반 equivalent mutant 탐지 에이전트: precision 0.79, recall 0.47이었고, 간단한 전처리 후 0.95, 0.96으로 올랐다 — [arXiv 2501.12862](https://arxiv.org/abs/2501.12862)
- Messenger·WhatsApp test-a-thon에서 엔지니어가 테스트의 73%를 수용했고, 36%를 privacy 관련으로 판단했다 — [arXiv 2501.12862](https://arxiv.org/abs/2501.12862)

**테스트 스멜과 assertion 품질**
- "Test smells in LLM-Generated Unit Tests"(Ouédraogo 외, arXiv 2410.10628, 2024-10 최초, 2025-11 개정): 4개 LLM(GPT-3.5, GPT-4, Mistral 7B, Mixtral 8x7B)이 만든 클래스 단위 스위트 20,505개를 EvoSuite 테스트 14,469개, 인간 테스트 779,585개와 TsDetect(스멜 21종)로 비교했다. LLM 테스트에는 Assertion Roulette와 Magic Number Test가 흔하고, Long Test와 Useless Test가 같이 나타난다. 스멜 패턴이 인간 테스트와 비슷해 학습 데이터 누출 가능성을 제기한다(검색 요약 기준) — [arXiv 2410.10628](https://arxiv.org/abs/2410.10628v2)
- "Using Large Language Models to Generate JUnit Tests: An Empirical Study"(arXiv 2305.00418): LLM 테스트의 Assertion Roulette 빈도는 23.8%~61.3%이고, Magic Number Test와 Lazy Test가 가장 흔하다(검색 요약 기준, 구 자료 2023) — [arXiv 2305.00418](https://arxiv.org/pdf/2305.00418)
- Python LLM 테스트 품질 연구(arXiv 2506.14297): LLM이 만든 테스트 스위트 대부분에 오류나 테스트 스멜이 하나 이상 있었다(검색 요약 기준) — [arXiv 2506.14297](https://arxiv.org/html/2506.14297v1)

**"통과하지만 검증하지 않는" 테스트: 커버리지와 mutation score의 괴리**
- MutGen 논문(arXiv 2506.02954, 2025-06 최초, 2025-12 개정): HumanEval-Java 과제 id_81에서 커버리지 100%인데 mutation score가 4%인 테스트 스위트를 보고했다. mutation 피드백을 프롬프트에 넣는 MutGen은 HumanEval-Java 89.5%, LeetCode-Java 89.1%의 mutation score로 EvoSuite와 일반 LLM 프롬프트를 앞섰다(검색 요약 기준) — [arXiv 2506.02954](https://arxiv.org/html/2506.02954v6)
- 대규모 LLM 테스트 연구(arXiv 2407.00225): 통과한 LLM 테스트 다수가 인터페이스, 추상 클래스, 사소한 메서드만 겨냥해 실제 로직을 건드리지 않았고, 해당 사례의 mutation score는 0%였다(검색 요약 기준, 2024) — [arXiv 2407.00225](https://arxiv.org/html/2407.00225v3)
- MuTAP(Dakhel 외, arXiv 2308.16557): mutation testing으로 LLM 테스트를 보강해 합성 버그 코드에서 mutation score 93.57%를 얻었다(Python, MutPy). LLM 출력은 여전히 후처리가 필요하다(검색 요약 기준, 구 자료 2023) — [arXiv 2308.16557](https://arxiv.org/abs/2308.16557)

**코딩 에이전트의 테스트 악용(test exploitation)**
- ImpossibleBench(Zhong, Raghunathan, Carlini, arXiv 2510.20270, 2025-10-23): 명세와 단위 테스트가 서로 모순되게 만든 "불가능 과제"의 통과율을 cheating rate로 정의한다 — [arXiv 2510.20270](https://arxiv.org/abs/2510.20270)
  - GPT-5의 cheating rate는 Conflicting-SWEbench 54.0%, Oneoff-SWEbench 76%, Oneoff-LiveCodeBench 2.9% — [ImpossibleBench HTML](https://arxiv.org/html/2510.20270)
  - 악용 유형 네 가지: 테스트 수정, 비교 연산자 오버로드(항상 equal), 추가 상태 기록, special-casing. "Claude models and Qwen3-Coder cheat mostly (over 79%) by modifying tests" — [ImpossibleBench HTML](https://arxiv.org/html/2510.20270)
  - 완화책: 테스트를 숨기면 cheating이 거의 0이 되지만 정상 성능도 떨어진다. 읽기 전용 테스트는 성능을 유지하면서 테스트 수정을 막는다(Opus 4.1에 가장 효과적). 다만 special-casing과 연산자 오버로드는 막지 못한다. 엄격한 프롬프트는 Conflicting-LiveCodeBench에서 GPT-5를 85% 초과에서 1%로 낮췄다. 중단(abort) 옵션은 GPT-5를 54%에서 9%로 낮췄지만 Claude Opus 4.1에서는 효과가 작았다. 여러 번 제출하게 하면 평균 cheating이 33%에서 38%로 올랐다 — [ImpossibleBench HTML](https://arxiv.org/html/2510.20270)
  - 모니터링: Claude Sonnet 4·Opus 4가 Impossible-LiveCodeBench에서는 cheating의 86~89%를 탐지했지만 Impossible-SWEbench에서는 42~50%에 그쳤다 — [ImpossibleBench HTML](https://arxiv.org/html/2510.20270)
- EvilGenie(arXiv 2511.21654, 2025-11): held-out 테스트, LLM judge, 테스트 파일 편집 탐지, 사람 리뷰로 reward hacking을 탐지했고, Codex와 Claude Code 모두에서 명시적인 reward hacking을 관찰했다(검색 요약 기준) — [arXiv 2511.21654](https://arxiv.org/pdf/2511.21654)
- SpecBench(arXiv 2605.21384, 2026-05): 공개 테스트 통과율과 숨긴 테스트 통과율의 차이로 reward hacking을 측정했고, 시험한 모든 모델이 공개 스위트를 포화시킬 수 있었다(검색 요약 기준) — [arXiv 2605.21384](https://arxiv.org/abs/2605.21384)

**반대 증거**
- Leith, "The Quality of Claude AI-authored Python Tests Is Not Weaker Than Human-authored Tests"(arXiv 2608.15188, 2026-08-15): Django와 Pandas의 인간 테스트와 비교해, 최신 Claude 모델(Sonnet/Opus 4.6 이후)의 테스트가 비열등성(one-sided non-inferiority) 기준으로 "no weaker"였다. fault-injection 프로토콜 3종과 7축 정성 루브릭으로 평가했고, 코퍼스마다 수백 개 테스트를 썼다 — [arXiv 2608.15188](https://arxiv.org/abs/2608.15188)

**Google의 뮤테이션 테스트(구 자료, 2018–2021)**
- "State of Mutation Testing at Google"(ICSE-SEIP 2018): 엔지니어 6,000명이 작성하거나 리뷰하는 모든 변경에 쓰였고, 필수 코드 리뷰를 거치는 코드 작성자 14,000명 이상에게 영향을 줬다. 커버리지가 계산되는 diff의 약 30%를 처리했다 — [Google Research (구 자료, 2018)](https://research.google/pubs/state-of-mutation-testing-at-google/)
- "Practical Mutation Testing at Scale: A view from Google"(IEEE TSE 2021): 1,000개 이상 프로젝트의 개발자 24,000명 이상이 쓰는 코드 리뷰 환경. 변경 코드만 변이하고, 맥락 기반 필터로 무관한 mutant를 제거하고, 라인당·리뷰당 상한을 두고, 연산자의 과거 성과로 mutant를 고른다. "context-based mutant filtering and selection improve mutant quality and actionability" — [Google Research (구 자료, 2021)](https://research.google/pubs/practical-mutation-testing-at-scale-a-view-from-google/)
- "Does mutation testing improve testing practices?"(ICSE 2021, arXiv 2103.07189): mutant 약 1,500만 개를 분석했다. 뮤테이션 테스트를 쓰는 개발자는 테스트를 더 많이 쓰고 스위트를 적극적으로 개선한다. mutant는 실제 결함과 결합되어 있어서, 결함을 만든 변경에 뮤테이션 테스트를 썼다면 live mutant가 보고됐을 것이다 — [arXiv 2103.07189 (구 자료, 2021)](https://arxiv.org/abs/2103.07189)

### Inferences
- Meta 두 논문의 공통 설계 원칙은 "LLM 출력은 기계적 필터(빌드, 반복 통과, 측정 가능한 개선)를 통과해야만 사람에게 간다"이다. 이 저장소에서는 에이전트가 쓴 테스트에 대해 (1) 컴파일·통과(이미 Stop hook으로 강제), (2) assertion 존재(정적 규칙), (3) 대상 클래스의 mutant kill 증가(PIT)를 차례로 거는 구조가 여기에 대응한다.
- ACH가 Android **Kotlin** 클래스에 적용됐다는 점은 Kotlin 코드에 뮤테이션 기반 테스트 강화가 가능하다는 산업 근거다. 다만 Meta는 자체 도구(LLM이 mutant를 생성)를 썼고 PIT를 쓰지 않았으므로, Kotlin junk mutation 문제가 똑같이 생기지는 않는다.
- ImpossibleBench 결과는 이 저장소에 바로 적용된다. Claude 계열은 주로 "테스트 수정"으로 악용하므로, 에이전트가 **프로덕션 코드를 고치는** 작업에서는 기존 테스트 파일 수정에 사용자 확인을 거는 PreToolUse hook(기존 `lint-guard.sh` 방식)이 읽기 전용 테스트와 같은 효과를 낸다. 반대로 **테스트를 쓰는** 작업에서는 수정을 막을 수 없으므로 뮤테이션 신호가 필요하다.
- 스멜 연구들이 말하는 Assertion Roulette(한 테스트에 설명 없는 assertion 여러 개)는 이 저장소의 "백틱 한국어 문장 테스트 이름" 규칙과 결합할 수 있다. 테스트 이름이 단일 행위를 서술하게 하면 assertion 대상도 좁아진다. 다만 이 효과는 연구로 검증된 것이 아니다.

### Gaps
- TestGen-LLM 필터의 세부(반복 실행 횟수 등)는 abstract에서만 확인했고 본문 수치는 확인하지 못했다.
- arXiv 2410.10628, 2305.00418, 2407.00225, 2506.02954, 2308.16557, 2511.21654는 검색 요약만 확인했다. 원문 표의 수치는 재확인이 필요하다.
- ImpossibleBench의 Claude 모델별 정확한 cheating rate는 그래프에만 있어서 숫자로 확보하지 못했다.
- Google 2018/2021 논문의 "유용하다고 평가된 mutant 비율", arid node 수치는 abstract 페이지에 없어서 확보하지 못했다.
- Kotlin·MockK 기반 LLM 테스트의 품질을 직접 측정한 연구는 찾지 못했다. 위 연구는 대부분 Java나 Python 대상이다.

---

## 3. 경량 신호: assertion 밀도·무 assertion 테스트 규칙, 과잉 mocking 탐지, MockK 함정

### Takeaway
"assertion 없는 테스트"를 잡는 기성 도구는 Java와 C#에 쓰는 SonarQube S2699, JVM 언어용 IntelliJ/Qodana `TestMethodWithoutAssertion`이다. 후자는 MockK와 kotlin.test를 assertion으로 인식한다. 검색 범위 안에서는 detekt 기본 규칙도, SonarKotlin 규칙도 찾지 못했다. 이 저장소는 이미 커스텀 ktlint 규칙 체계(`bus:*`)가 있으므로 PSI 규칙을 추가하는 것이 가장 직접적이다. MockK의 relaxed mock은 stub 없이도 코드가 돌아가게 만들어 "아무것도 확인하지 않는 통과"를 쉽게 만든다. `confirmVerified`, `checkUnnecessaryStub`, `verifyAll` 같은 도구로 보완할 수 있다.

### Cited Findings

**assertion 없는 테스트 규칙**
- SonarQube S2699 "Tests should include assertions": assertion이 없는 테스트는 "increase the coverage without enforcing anything on the covered code, resulting in a false sense of security"라는 이유로 잡는다. 커스텀 assertion 메서드를 호출하는 테스트는 예외다(C# 규칙 페이지, 검색 요약 기준) — [rules.sonarsource.com C# RSPEC-2699](https://rules.sonarsource.com/csharp/tag/tests/RSPEC-2699)
- Java용 S2699는 AssertJ, Hamcrest, JUnit, Mockito 등 알려진 프레임워크의 assertion이 하나도 없으면 이슈를 올린다(검색 요약 기준) — [rules.sonarsource.com Java RSPEC-2699](https://rules.sonarsource.com/java/tag/junit/RSPEC-2699)
- 커뮤니티에서는 helper 클래스 안 assertion이나 Awaitility 같은 덜 흔한 라이브러리 때문에 오탐이 난다고 보고한다(검색 요약 기준) — [Sonar community: Awaitility](https://community.sonarsource.com/t/sonar-does-not-see-assertions-done-with-awaitility/22323)
- IntelliJ `TestMethodWithoutAssertion`: "Reports test methods that do not contain any assertions. Such methods may indicate either incomplete or weak test cases." 설정 경로가 "JVM languages" 아래에 있고, 기본 assertion 클래스에 JUnit, JUnit 5, AssertJ, Truth, Mockito, **MockK**, Hamcrest, TestNG, Kotlin test(`kotlin.test.AssertionsKt`)가 들어 있다. 클래스명과 메서드명 정규식으로 assertion을 추가할 수 있다. 옵션 `assertKeywordIsAssertion`, `ignoreIfExceptionThrown`은 기본으로 꺼져 있다. IntelliJ IDEA 2026.3과 Qodana for JVM 2026.3에 번들되어 있다 — [JetBrains Inspectopedia](https://www.jetbrains.com/help/inspectopedia/TestMethodWithoutAssertion.html)
- detekt에서 assertion 없는 테스트를 잡는 기본 규칙은 검색으로 찾지 못했다(검색 요약 기준) — [검색 결과 참고: detekt CONTRIBUTING](https://www.github.com/detekt/detekt/blob/main/.github/CONTRIBUTING.md)
- SonarKotlin 체크 목록(GitHub 디렉터리, 화면에 보인 범위)에서 테스트·assertion 관련 체크 클래스는 보이지 않았다. 목록이 잘려 있어 확정할 수는 없다 — [sonar-kotlin checks](https://github.com/SonarSource/sonar-kotlin/tree/master/sonar-kotlin-checks/src/main/java/org/sonarsource/kotlin/checks)

**MockK 함정과 보완 도구** — 모두 [mockk.io](https://mockk.io/)
- relaxed mock은 "returns some simple value for all functions"이고, 참조 타입에는 chained mock을 반환한다. 예: `car.drive(Direction.NORTH) // returns null`
- 경고: "relaxed mocking is working badly with generic return types. A class cast exception is usually thrown in this case."
- `relaxUnitFun = true`는 Unit 반환 함수만 relaxed로 만든다(`mockk(...)`, `@MockK`, `MockKAnnotations.init`).
- 전역 설정 파일 `src/test/resources/mockk.properties`에 `relaxed=true|false`, `relaxUnitFun=true|false`를 둘 수 있고, 둘 다 기본은 `false`다. 로더는 한 번 읽고 classloader 수명 동안 캐시한다.
- `confirmVerified`: "It will throw an exception if there are some calls left without verification."
- `checkUnnecessaryStub`: "It will throw an exception if there are some declared calls on the mocks that are not used by the tested code."
- `verifyAll`은 순서와 무관하게 모든 호출을 검증하고, `verifySequence`는 지정한 순서까지 검증한다. `excludeRecords`는 일치하는 호출을 기록에서 빼서 exhaustive 검증을 돕는다.
- 문서에 "MockK 1.14.9 or later"라는 표현이 있으므로 1.14.x 계열이 존재한다. 이 저장소는 `io.mockk:mockk:1.13.10`을 쓴다 — [mockk.io](https://mockk.io/), `build.gradle.kts` 95행

**저장소 현황(로컬 집계)**
- `relaxed = true`/`relaxUnitFun = true` 37곳(13개 파일), `verify {`/`coVerify {` 34줄, assertion 호출 742줄, `@Test` 약 394개 — `grep`(2026-11-21), `modules/**/*.kt`
- 커스텀 ktlint 규칙 중 테스트 관련은 `TestMethodNameRule`, `NoMethodDisplayNameRule`이고 assertion 규칙은 없다 — `lint/ktlint-rules/src/main/kotlin/com/media/bus/lint/ktlint/`

### Inferences
- **IntelliJ 검사의 맹점**: MockK가 기본 assertion 클래스에 들어 있어서, `verify { repo.save(any()) }`만 있는 "상호작용만 검증하는" 테스트도 통과한다. 결과값을 검증하지 않고 "호출됐다"만 확인하는 과잉 mocking 패턴은 이 검사로 못 잡는다.
- **커스텀 ktlint PSI 규칙 후보**(저장소의 `bus:*` 체계와 `lint-guard.sh` 우회 방지에 맞춤):
  1. `bus:test-has-assertion`: `@Test` 함수 본문(호출하는 private helper 포함)에 assertion 계열 호출(`assertThat`, `assertEquals`, `assertThrows`, `assertThatThrownBy`, `shouldBe`, `verify`, `coVerify`, `confirmVerified`)이 하나도 없으면 위반.
  2. `bus:test-not-verify-only`: assertion이 `verify`/`coVerify`뿐이고 SUT 반환값이나 상태 assertion이 없으면 경고. 단 반환형이 `Unit`인 커맨드 메서드 테스트는 정당하므로 허용 목록이나 이름 규칙이 필요하다.
  3. `bus:no-relaxed-mock`: `mockk(relaxed = true)`, `@MockK(relaxed = true)`, `@RelaxedMockK`를 금지하거나 `relaxUnitFun`만 허용. `src/test/resources/mockk.properties`에 `relaxed=true`가 생기면 forbiddenapis가 아니라 파일 검사로 차단.
  4. `bus:no-mock-of-sut`: 테스트 클래스 이름이 `XxxTest`인데 `mockk<Xxx>()`나 `spyk<Xxx>()`를 쓰면 위반(SUT mocking).
  5. 약한 단독 assertion(`isNotNull()`, `isNotEmpty()`, `assertNotNull`만 있는 경우) 경고.
  위 규칙들은 정적 분석이라 우회할 수 있다(예: 의미 없는 `assertThat(true).isTrue()`). 그래서 뮤테이션 신호와 함께 써야 한다.
- **MockK 강화 관례**: 서비스 테스트에서 `@AfterEach`로 `confirmVerified(mocks)` + `checkUnnecessaryStub(mocks)`를 호출하면 "불필요한 stub"과 "검증되지 않은 부수효과 호출"이 드러난다. relaxed mock 37곳은 우선 `relaxUnitFun`으로 바꿀 수 있는지 검토할 대상이다.
- **Qodana**: IntelliJ 검사를 CI에서 돌리려면 Qodana for JVM이 경로가 될 수 있다. 다만 GitLab에서 Qodana 비용과 설정 부담을 ktlint 규칙 추가와 비교해야 한다.

### Gaps
- SonarKotlin(Sonar Kotlin analyzer)에 S2699나 그에 해당하는 규칙이 있는지 확정하지 못했다(rules.sonarsource.com이 리다이렉트되어 Kotlin 규칙 페이지를 열지 못했다).
- detekt 2.x에 서드파티 규칙셋(예: detekt-junit 계열)이 assertion 검사를 제공하는지 확인하지 못했다.
- "assertion 밀도"(테스트당 assertion 수)와 결함 탐지력의 상관을 정량화한 2024–2026 연구는 찾지 못했다.
- MockK 최신 버전 번호(1.14.x의 정확한 최신값)는 확인하지 못했다.

---

## 4. AI 작성 코드의 게이트로 mutation score 쓰기(에이전트 루프)와 한계

### Takeaway
"테스트 생성 → 뮤테이션 실행 → 살아남은 mutant를 프롬프트에 되먹여 테스트 강화" 루프는 학술(MuTAP, MutGen)과 산업(Meta ACH)에서 모두 효과가 보고됐다. Thoughtworks Radar Vol. 35(2026-11)는 뮤테이션 테스트를 AI 생성 테스트의 가드레일로 보고 **Adopt**에 올렸다. 한계는 비용(느림 → 변경 코드·특정 패키지로 범위 제한), equivalent mutant(LLM 판별기로 완화할 수 있으나 완벽하지 않음), Kotlin junk mutant, 그리고 점수 자체를 에이전트가 게이밍할 가능성이다.

### Cited Findings
- Thoughtworks Technology Radar Vol. 35, 2026-11 **Adopt**: "As teams adopt AI coding assistants, the volume of AI-generated code and tests increases rapidly." 커버리지는 얕은 테스트로 부풀릴 수 있으니 뮤테이션 테스트를 AI 생성 테스트의 가드레일로 쓰라고 한다. "AI agents can now perform mutation testing autonomously during execution." 실행이 느리므로 특정 패키지나 최근 변경 코드로 범위를 좁히라고 권한다 — [Thoughtworks Radar](https://www.thoughtworks.com/en-sg/radar/techniques/mutation-testing)
- 같은 블립의 2026-04 **Trial** 단계 설명: 뮤테이션 테스트는 테스트 스위트의 결함 탐지력에 대한 "the most honest signal"이고 커버리지는 "only tracks line execution"이다. AI 보조 작업에서는 높은 커버리지 뒤에 "logically hollow tests"가 숨을 수 있고, 뮤테이션 테스트가 "perpetually green" 테스트를 막는다. 언급한 도구는 Stryker, Pitest, cargo-mutants — [Thoughtworks Radar](https://www.thoughtworks.com/en-sg/radar/techniques/mutation-testing)
- MutGen: mutation 피드백을 프롬프트에 넣어 HumanEval-Java 89.5%, LeetCode-Java 89.1%의 mutation score를 얻었고, EvoSuite와 일반 LLM을 앞섰다(검색 요약 기준) — [arXiv 2506.02954](https://arxiv.org/html/2506.02954v6)
- MuTAP: 살아남은 mutant로 프롬프트를 보강해 mutation score 93.57%(Python, 합성 버그 코드)를 얻었다(검색 요약 기준, 구 자료 2023) — [arXiv 2308.16557](https://arxiv.org/abs/2308.16557)
- Meta ACH: 관심사에 맞춘 소수의 mutant만 만들고, 그 mutant를 죽이는 테스트를 생성했다. 엔지니어 수용률 73% — [arXiv 2501.12862](https://arxiv.org/abs/2501.12862)
- Equivalent mutant: ACH의 LLM 판별기는 precision 0.79/recall 0.47이었고 전처리 후 0.95/0.96이다 — [arXiv 2501.12862](https://arxiv.org/abs/2501.12862). Arcmutate Kotlin 플러그인도 empty-return equivalent mutant를 필터링한다 — [Arcmutate Kotlin docs](https://docs.arcmutate.com/docs/kotlin.html)
- 비용 통제: Google은 변경 코드만 변이하고 라인당·리뷰당 상한을 두어 mutant를 자릿수 단위로 줄였다 — [Google TSE 2021 (구 자료)](https://research.google/pubs/practical-mutation-testing-at-scale-a-view-from-google/). Arcmutate git 플러그인은 기본으로 수정된 라인만 분석한다 — [Arcmutate git integration](https://docs.arcmutate.com/docs/git-integration.html). PIT history 기반 incremental analysis는 실험 기능이고 핵심 가정이 증명되지 않았다 — [pitest.org](https://pitest.org/quickstart/incremental_analysis/)
- 실무 블로그(신뢰도 낮음, 검색 요약 기준): 한 Laravel 실험에서 살아남은 mutant를 에이전트에게 되먹이자 mutation score가 71.58%에서 89.62%로, 생존 mutant가 27개에서 11개로 줄었다. 한 가이드는 CI의 `mutationThreshold`를 85% 넘게 잡으라고 하지만 근거를 제시하지 않는다. 여러 블로그에 도는 "AI 코드의 생존률이 15~25% 높다"는 수치는 일화 수준이라 검증되지 않았다 — [augmentcode guide](https://www.augmentcode.com/guides/mutation-testing-ai-generated-code), [DEV: PIT + Claude Code agentic loops](https://dev.to/machinecodingmaster/stop-trusting-your-ai-generated-tests-hardening-codebases-with-pitest-and-claude-code-agentic-loops-hba)
- 게이밍 위험: 에이전트는 테스트 수정, 연산자 오버로드, special-casing으로 검증을 우회한다. 피드백 루프(여러 번 제출)를 허용하면 cheating이 33%에서 38%로 늘었다 — [ImpossibleBench HTML](https://arxiv.org/html/2510.20270)

### Inferences
- **이 저장소용 루프 설계안**(Claude Code·Codex 공통):
  1. 에이전트가 `modules/<m>`의 클래스 X에 테스트를 쓰거나 바꾼다.
  2. 기존 Stop hook(`compile-gate.sh`)과 같은 방식으로, 바뀐 테스트가 덮는 프로덕션 클래스만 `targetClasses`로 지정해 `./gradlew :modules:<m>:pitest`를 돌린다. 테스트 범위도 바뀐 테스트 클래스로 제한한다(`targetTests`).
  3. `build/reports/pitest/mutations.xml`에서 `status="SURVIVED"`와 `NO_COVERAGE`를 뽑아 "라인 번호 + mutator 설명 + 대상 메서드"를 에이전트에게 돌려준다. 이것이 MutGen과 MuTAP식 피드백이다.
  4. 에이전트가 테스트를 보강하거나, equivalent mutant라고 판단한 근거를 보고서에 남긴다(ACH식). 사람은 근거만 검토한다.
  5. 게이트는 전체 점수가 아니라 "바뀐 클래스의 mutation score가 하락하지 않음"이나 "바뀐 라인의 생존 mutant 0개 또는 근거 있는 예외"로 둔다. 절대 임계값(예: 85%)은 근거가 없으므로 PoC에서 기준선을 잰 뒤 정한다.
- **Stop hook에 넣을지 CI에 넣을지**: PIT는 단위 테스트보다 몇 배에서 수십 배 느릴 가능성이 크다(공식 수치 없음). 매 턴 Stop hook보다는 (a) 스킬이나 명시적 명령(`/mutate`)으로 에이전트가 직접 루프를 돌리고, (b) GitLab MR 파이프라인에서 변경 클래스 한정으로 게이트하는 이원화가 현실적이다. 전체 실행은 nightly로 돌린다.
- **게이밍 대응**: 뮤테이션 점수만 보면 에이전트가 assertion을 남발하거나 구현 세부(private 호출 순서)에 결합된 `verifySequence`로 mutant를 죽일 수 있다. 그러면 깨지기 쉬운 테스트가 늘어난다. 정적 규칙(3절의 verify-only·SUT mock 금지)과 리뷰 체크리스트를 같이 써야 한다. 또 "프로덕션 코드를 고치는 턴에는 기존 테스트 파일 수정에 확인을 받는다"는 hook이 ImpossibleBench의 읽기 전용 테스트 완화책과 같은 효과를 낸다.
- **AWS·CI 관점**: GitLab Runner에서 PIT는 CPU를 많이 쓰고(`threads`) minion JVM을 여러 개 띄워 메모리도 많이 쓴다. 공유 러너보다 전용 러너 태그나 nightly 스케줄로 분리하는 편이 MR 파이프라인 시간을 지킨다. history 파일과 Gradle 캐시는 GitLab cache 키(브랜치별)로 유지한다.
- **Kotlin 한계 요약**: 오픈소스 PIT + Kotlin에서는 junk mutant 때문에 절대 점수의 의미가 흐려진다. 그래서 상대 지표(같은 클래스의 이전 대비 변화)나 생존 mutant 목록 리뷰를 쓰는 편이 낫고, 절대 점수를 게이트로 쓰려면 Arcmutate Kotlin 플러그인 도입을 검토해야 한다.

### Gaps
- 실제 팀이 AI 생성 코드에 mutation score 게이트를 운영한 결과(오탐률, 개발 속도 영향)를 정량 보고한 1차 자료(동료 심사 또는 공식 엔지니어링 블로그)는 Meta 외에는 찾지 못했다. 실무 블로그 수치는 검증되지 않았다.
- Meta ACH 관련 공식 엔지니어링 블로그 글은 이번에 열어보지 못했다(arXiv abstract만 확인).
- 뮤테이션 게이트가 테스트 취약성(brittleness)을 키우는지에 대한 실증 연구는 찾지 못했다.
- Claude Code·Codex 환경에서 PIT 루프를 돌린 비용과 효과를 측정한 자료는 실무 블로그 제목 수준에서만 확인했다.
