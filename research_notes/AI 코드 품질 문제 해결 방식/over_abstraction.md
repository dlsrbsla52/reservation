# AI 에이전트 생성 코드의 과설계·불필요한 추상화와 대응 방식

조사 시점: 2026-11-21. 적용 맥락: Spring Boot 4.1 + Kotlin 2.3 모듈러 모놀리스(포트·어댑터), Gradle 9.9, JDK 25, ktlint 1.8 커스텀 규칙(`lint/ktlint-rules`: CommentPolicyRule, EntityFactoryNameRule, NoHardcodedExceptionMessageRule 등), forbiddenapis, Claude Code·Codex Stop hook(`compile-gate.sh`), 순수 JUnit 아키텍처 테스트(`ModuleBoundaryTest`, `WebSurfaceArchitectureTest`: Spring `ClassPathScanningCandidateComponentProvider` + 리플렉션 방식). ArchUnit·Konsist·detekt는 현재 쓰지 않는다.

## 1. LLM 에이전트가 불필요한 추상화·장황함을 더한다는 근거

### Takeaway
모델 벤더(Anthropic)가 공식 문서에서 특정 모델(Opus 4.5/4.6)의 과설계 경향을 명시하고 대응 프롬프트를 제공하며, 2025–2026 실증 연구(MSR '26 등)는 AI 도구·에이전트 도입 후 저장소의 정적 분석 경고와 인지 복잡도가 지속적으로 증가함을 인과 추정으로 보고한다. 다만 "불필요한 추상화" 자체를 직접 측정한 연구는 없고, 복잡도·스멜·중복·PR 크기 같은 대리 지표로만 확인된다. 또한 경향은 모델 버전마다 달라서(과설계 → 범위 확장 → 보수적 최소 구현) 지침도 모델별로 재점검해야 한다.

### Cited Findings

벤더 문서 (Anthropic)
- Anthropic 공식 프롬프팅 가이드 "Overeagerness" 절: "Claude Opus 4.5 and Claude Opus 4.6 have a tendency to overengineer by creating extra files, adding unnecessary abstractions, or building in flexibility that wasn't requested." 이런 행동이 보이면 "add specific guidance to keep solutions minimal"을 권한다 — [Claude Prompting best practices](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices)
- 같은 문서의 샘플 프롬프트 "Avoid over-engineering. Only make changes that are directly requested or clearly necessary." 4개 항목: Scope("A bug fix doesn't need surrounding code cleaned up. A simple feature doesn't need extra configurability."), Documentation("Don't add docstrings, comments, or type annotations to code you didn't change."), Defensive coding("Don't add error handling, fallbacks, or validation for scenarios that can't happen. Trust internal code and framework guarantees. Only validate at system boundaries"), Abstractions("Don't create helpers, utilities, or abstractions for one-time operations. Don't design for hypothetical future requirements. The right amount of complexity is the minimum needed for the current task.") — [Claude Prompting best practices](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices)
- 같은 문서: 에이전트 코딩에서 모델이 임시 파일·스크립트를 "temporary scratchpad"로 만들 수 있으며, 남기지 않으려면 "clean up these files by removing them at the end of the task"라고 지시하라고 한다. 또 테스트 통과에 과도하게 집중해 하드코딩하거나 "helper scripts for complex refactoring" 같은 우회책을 쓸 수 있다고 명시한다 — [Claude Prompting best practices](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices)
- Opus 4.6은 "a strong tendency to use subagents ... where a simpler, direct approach would suffice"라고 같은 문서가 기술한다(도구 사용 차원의 과잉) — [Claude Prompting best practices](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices)
- Claude Opus 5 전용 가이드: "Claude Opus 5 can also expand the scope of a task, adding steps that weren't requested or applying its own judgment about what the task should be." 좁은 작업용 샘플: "Deliver what was asked, at the scope intended. ... say so in a sentence and continue with the task as asked rather than quietly narrowing, widening, or transforming it." 또한 디스크에 쓰는 문서가 이전 모델보다 길어지는 경향이 있어 "do not pad with filler sections, redundant summaries, or boilerplate"를 권한다 — [Prompting Claude Opus 5](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-opus-5)
- 반대 방향의 공식 진술: Claude Code 모범 사례 문서는 "Claude tends to read a request conservatively and build the smallest thing that satisfies it"라며 더 큰 범위를 원할 때 `be ambitious`라고 말하라고 한다(이 문서 기준 기본 모델은 Opus 5.5 medium effort) — [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices). 즉 Anthropic 문서 안에서도 모델별로 "과설계(Opus 4.5/4.6)", "범위 확장(Opus 5)", "보수적 최소 구현(Claude Code 기본)" 서술이 공존한다.
- Claude Code 문서: 리뷰어에게 문제를 찾으라고 하면 "will always find *something*. If you fix every finding, you end up with extra abstraction layers and tests for cases that can't happen."(리뷰 루프 자체가 추상화를 늘리는 원인이 될 수 있음) — [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices)

벤더 문서 (OpenAI)
- GPT-5.3-Codex 가이드에는 "over-engineering"이나 "unnecessary abstraction"을 직접 다룬 문장이 없다. 가장 가까운 문장: "Act as a discerning engineer: optimize for correctness, clarity, and reliability over speed;", "avoid risky shortcuts, speculative changes, and messy hacks just to get the code to work;", "Reuse: DRY/search first: before adding new helpers or logic, search for prior art and reuse or extract a shared helper instead of duplicating.", "Tight error handling: No broad catches or silent defaults: do not add broad try/catch blocks or success-shaped fallbacks;", "Avoid committing to tests/broad refactors unless you will do them now.", 주석은 "Usage of these comments should be rare." — [Using GPT-5.3-Codex](https://developers.openai.com/api/docs/guides/latest-model/gpt-5.3-codex.md)
- 같은 가이드: Codex CLI는 AGENTS.md를 자동으로 대화에 주입하며 "the model has been trained to closely adhere to these instructions." — [Using GPT-5.3-Codex](https://developers.openai.com/api/docs/guides/latest-model/gpt-5.3-codex.md)
- GPT-5-Codex 프롬프팅 가이드의 핵심 원칙은 "less is more"(최소 프롬프트에서 시작해 필요한 지침만 추가) — [GPT-5-Codex Prompting Guide](https://cookbook.openai.com/examples/gpt-5-codex_prompting_guide) (검색 요약으로 확인, 본문 미열람)

실증 연구 (2025–2026)
- He, Miller, Agarwal, Kästner, Vasilescu, "Speed at the Cost of Quality: How Cursor AI Increases Short-Term Velocity and Long-Term Complexity in Open-Source Projects", MSR '26: Cursor 도입 GitHub 프로젝트와 매칭 대조군의 staggered DiD. Borusyak et al. 추정치로 도입 후 정적 분석 경고 +30.26%(±6.66), 코드 복잡도 +41.64%(±7.62). 속도 증가는 "large, but transient", 경고·복잡도 증가는 "substantial and persistent" — [arXiv 2511.04427 abs](https://arxiv.org/abs/2511.04427), [v3 HTML](https://arxiv.org/html/2511.04427v3)
  - 측정 방식: SonarQube Community로 reliability·maintainability·security 이슈 총수, 복잡도는 코드베이스 전체 인지 복잡도(Campbell 2018). 논문 Discussion(5.1.2, 5.2)에는 복잡도 증가가 25.1%/25%로 적혀 Table 2(41.64%)와 불일치한다 — [v3 HTML](https://arxiv.org/html/2511.04427v3)
  - MSR '26 Distinguished Paper Award 수상, 806–807개 저장소(버전마다 다름) — [검색 결과: arXiv/hehao98](https://hehao98.github.io/publication/2026-cursor) (검색 요약)
- Agarwal, He, Vasilescu, "AI IDEs or Autonomous Agents? Measuring the Impact of Coding Agents on Software Development"(2026-01): AIDev 데이터셋, 첫 에이전트 생성 PR을 도입 시점으로 한 staggered DiD. "static-analysis warnings and cognitive complexity rising by roughly 18% and 39%", "sustained agent-induced technical debt even when velocity advantages fade." 속도 이득은 에이전트가 프로젝트의 첫 AI 도구일 때만 크게 나타남 — [arXiv 2601.13597](https://arxiv.org/abs/2601.13597) (검색 요약은 35%로 표기했으나 abstract 원문은 39%)
- Sonar, "The Coding Personalities of Leading LLMs"(2025-08-13, 벤더 연구): 6개 모델이 4,400여 개 Java 과제를 풀게 하고 SonarQube로 분석. Claude Sonnet 4는 OpenCoder-8B보다 "more than three times the lines of code"를 생성. Sonnet 4는 3.7 대비 벤치마크 통과율 +6.3%였으나 "the severity of bugs increased by 93%". GPT-5(minimal)는 "creates verbose code", 정교한 해법 시도가 "far higher rate of code smells"로 이어진다고 서술. 모든 모델에서 코드 스멜이 이슈의 90% 이상 — [Sonar blog](https://www.sonarsource.com/blog/the-coding-personalities-of-leading-llms)
  - 지역화 페이지에는 Sonnet 4 370,816 LOC vs OpenCoder-8B 120,288 LOC 수치가 있다고 검색 요약이 전함(영문 원문에는 개별 수치 없음) — [Sonar blog (ko 등)](https://www.sonarsource.com/ko/blog/llm-coding-personality-traits)
- arXiv 2508.14727 "Assessing the Quality and Security of AI-Generated Code: A Quantitative Analysis": 4,442개 Java 과제, 코드 스멜이 결함의 92% 이상. 방어적 에러 처리가 문맥을 추론하지 못하면 "unnecessary logical complexity"를 만들 수 있고, 참조되지 않는 코드가 "codebase bloat"에 기여한다고 서술 — [arXiv 2508.14727](https://arxiv.org/html/2508.14727v1) (검색 요약으로만 확인. 과제 수가 Sonar 보고서와 같아 같은 데이터셋으로 보이나 저자 관계는 미확인)
- Ghosh Paul, Zhu, Bayley(Oxford Brookes), "Investigating The Smells of LLM-generated Code"(Information and Software Technology 확장판, arXiv 2510.03029): ScenEval 1,000개 Java 과제, PMD·Checkstyle·DesigniteJava로 측정. 사람 참조 해답(평균 19.378 위반) 대비 스멜 평균 +63.34%(Falcon 42.28%, Gemini Pro 62.07%, ChatGPT 64.05%, Codex 84.97%). implementation smell +73.35%, design smell +21.42%. "the increase in code smells is greater for more complex coding tasks" — [arXiv 2510.03029v2](https://arxiv.org/html/2510.03029v2). **주의: 대상 모델이 GPT-3.5·Codex(GPT-3)·Gemini Pro 1.0·Falcon-7B로 구세대다. ChatGPT 수치는 abstract(65.05%)와 표(64.05%)가 불일치.**
- GitClear 2025 보고서(2024년 데이터, 2억 1,100만 변경 라인): 5줄 이상 중복 블록이 2024년에 8배 증가, "moved" 라인(리팩터링 대리 지표) 39.9% 감소, 2024년이 복붙 라인이 이동 라인을 처음 넘은 해. GitClear는 원인으로 Tab 한 번으로 블록을 넣는 편의성과 제한된 컨텍스트로 기존 함수 재사용 제안이 적은 점을 든다 — [DevClass, 2025-02-20](https://www.devclass.com/ai-ml/2025/02/20/ai-is-eroding-code-quality-states-new-in-depth-report/1626250). 같은 기사는 반대 근거로 Google 2024 DORA의 "AI 도입 시 코드 품질 +3.4%, 전달 안정성 −7.2%"를 함께 소개한다.
- GitClear·GitKraken "Maintainability Gap" 보고서(2023–2026, 6억 2,300만 변경): 블록 중복 백만 변경 라인당 40.3 → 73.0(+81%), moved code 비중 2022년 21% → 2026년 3.8%, 레거시 리팩터링 74% 감소 — [LeadDev](https://leaddev.com/software-quality/how-ai-generated-code-accelerates-technical-debt), [The New Stack](https://thenewstack.io/ai-coding-duplication-rose/) (검색 요약 기반 2차 보도, 원문 미열람)
- 실무자 관찰: Andrej Karpathy(2026-01 X 게시물)가 "They really like to overcomplicate code and APIs, bloat abstractions, don't clean up dead code...", "implement a bloated construction over 1000 lines when 100 would do."라고 썼다 — [forrestchang/andrej-karpathy-skills README가 인용](https://raw.githubusercontent.com/multica-ai/andrej-karpathy-skills/HEAD/README.md), 원 게시물 [x.com/karpathy/status/2015883857489522876](https://x.com/karpathy/status/2015883857489522876) (X 원문은 직접 열람하지 않음)

에이전트 PR 연구 (AIDev 데이터셋, 2026)
- Ehsani 외, "Where Do AI Coding Agents Fail?"(arXiv 2601.15195): 33,596개 에이전트 PR(Codex 21,799, Copilot 4,970, Devin 4,827, Cursor 1,541, Claude Code 459). 머지 안 된 PR은 "tend to introduce larger code changes, touch more files"(라인 Cliff's δ≈0.17, 파일 δ≈0.10). 로지스틱 회귀에서 변경 라인 1줄당 머지 odds 약 1% 감소. 거절 PR 562건 수동 분류에서 "Unwanted feature" 4%(과도·불필요한 변경 포함), 유지보수자 코멘트 "This is a LOT to review, would really prefer smaller granular PRs." — [arXiv 2601.15195](https://arxiv.org/html/2601.15195v1)
- Nakashima 외, "Why Agentic-PRs Get Rejected"(arXiv 2602.04226, 2026-02): 거절 PR 654건(Claude Code, Codex, Devin, Copilot, Cursor, 사람). 에이전트 PR에만 나타난 거절 사유: Too large 1.5%, No added value 1.1%, More complex("more complex than warranted") 0.2%, No AI confidence 0.2% 등. 단 거절 PR의 67.9%는 리뷰어 피드백이 없어 사유 불명 — [arXiv 2602.04226](https://arxiv.org/html/2602.04226v1)

### Inferences
- 근거의 층위가 다르다. "불필요한 추상화"를 직접 지목한 1차 자료는 Anthropic 문서(Opus 4.5/4.6)와 실무자 관찰(Karpathy)이고, 실증 연구는 인지 복잡도·스멜·중복·PR 크기라는 대리 지표로 같은 방향을 가리킨다. 보고서에서 "AI가 추상화를 과하게 만든다는 정량 증거"라고 단정하면 과장이다.
- He 외 연구의 복잡도는 코드베이스 **전체** 인지 복잡도라서 코드량 증가(장황함)와 분리되지 않는다. "메서드 단위로 더 복잡해졌다"가 아니라 "복잡도 총량이 늘었다"로 인용해야 정확하다.
- 벤더 문서가 모델 세대마다 반대 방향의 경향(과설계 ↔ 보수적)을 적고 있으므로, AGENTS.md의 과설계 억제 문구는 모델이 바뀔 때 효과를 다시 확인해야 한다. 이 저장소는 Claude Code(현재 기본 Opus 5.5)와 Codex를 같이 쓰므로, 공유 AGENTS.md에는 모델 중립적인 "범위·추상화 기준"을 두고 모델별 튜닝은 CLAUDE.md 쪽에 두는 편이 맞다.
- Anthropic 샘플의 Documentation·Defensive coding 항목은 이 저장소가 이미 강제하는 규칙(코드 주석 금지 + `// 예외:` 승인, `error()`/`require{}` 금지, 입력 검증은 제공 모듈 진입점의 `InputValidator`)과 거의 겹친다. 빠진 것은 Scope와 Abstractions 항목이다.

### Gaps
- Kotlin·Spring 코드베이스를 대상으로 AI 생성 코드의 추상화 수준(인터페이스 수, 레이어 수, 단일 구현 인터페이스 비율)을 측정한 연구는 찾지 못했다.
- Opus 5/5.5, GPT-5.x-Codex 같은 2026년 모델 세대를 대상으로 한 독립적인 코드 크기·복잡도 비교 연구는 찾지 못했다(Sonar 보고서는 Sonnet 4/GPT-5 minimal까지).
- Google·Microsoft 엔지니어링 리서치 중 과설계를 직접 다룬 자료는 이번 조사에서 확인하지 못했다(DORA 2024는 2차 보도로만 확인).
- GitClear "Maintainability Gap" 원문과 Karpathy X 원문은 직접 열람하지 못했다.

## 2. 측정 신호와 도구 (Kotlin 기준) 및 신규 코드 한정 게이트

### Takeaway
Kotlin에서 쓸 수 있는 표준 신호는 detekt `complexity` 규칙 세트(CognitiveComplexMethod, CyclomaticComplexMethod, LongMethod, LargeClass, TooManyFunctions, ComplexInterface 등)와 `style` 규칙 세트의 불필요 구조 탐지 규칙(AbstractClassCanBeConcreteClass, UnnecessaryInheritance, UnusedParameter 등), 그리고 SonarQube Kotlin 분석기의 인지 복잡도 규칙(S3776, 기본 15)이다. 팀들은 기존 부채가 아니라 신규 코드에만 조건을 거는 방식(Sonar의 new code 조건, PR에서는 new code 조건만 적용)을 쓴다. 단 이 지표들은 크기·분기·중첩을 재지, "이 인터페이스가 필요한가"는 재지 못한다.

### Cited Findings

detekt complexity 규칙 세트 (문서 기준 detekt 2.0.0-alpha.6)
- CognitiveComplexMethod: SonarSource 인지 복잡도로 메서드 복잡도 제한, `allowedComplexity` 15, **기본 비활성** — [detekt complexity](https://detekt.dev/docs/rules/complexity)
- CyclomaticComplexMethod: McCabe 순환 복잡도, `allowedComplexity` 14, 기본 활성(`ignoreSimpleWhenEntries` 옵션 있음) — [detekt complexity](https://detekt.dev/docs/rules/complexity)
- LongMethod `allowedLines` 60(활성), LargeClass `allowedLines` 600(활성), TooManyFunctions 파일·클래스·인터페이스·object·enum별 11(활성), LongParameterList 함수 5·생성자 6(활성, 타입 해석 필요, `ignoreDataClasses` 옵션), NestedBlockDepth 4(활성), ComplexCondition 3(활성) — [detekt complexity](https://detekt.dev/docs/rules/complexity)
- ComplexInterface `allowedDefinitions` 10(비활성), NestedScopeFunctions 깊이 1(비활성, 타입 해석 필요), MethodOverloading 6(비활성), NamedArguments 3(비활성), StringLiteralDuplication(비활성), LabeledExpression(비활성), ReplaceSafeCallChainWithRun(비활성) — [detekt complexity](https://detekt.dev/docs/rules/complexity)
- detekt style 규칙 세트에서 불필요 구조와 관련된 규칙: AbstractClassCanBeConcreteClass(활성, 추상 멤버 없는 추상 클래스), AbstractClassCanBeInterface(활성), UnnecessaryInheritance(활성), UnusedPrivateFunction·UnusedPrivateProperty·UnusedParameter(활성), OptionalAbstractKeyword(활성), UnnecessaryApply(활성), UnnecessaryLet·UnnecessaryInnerClass·UseDataClass·DataClassContainsFunctions(비활성). `UnnecessaryAbstractClass`·`UnusedPrivateMember`라는 이름은 이 버전 문서에 없다(분리·개명됨) — [detekt style](https://detekt.dev/docs/rules/style)

SonarQube
- 인지 복잡도는 측정값(measure)이 아니라 규칙 S3776으로 검사하며, 메서드가 15를 넘지 않는지 본다(커뮤니티 답변). Kotlin 분석기에서도 인지 복잡도가 활성화돼 있다 — [Sonar Community: Cognitive Complexity metric vs rule](https://community.sonarsource.com/t/cognitive-complexity-metric-vs-rule/76444), [sonar-kotlin GitHub](https://github.com/sonarsource/sonar-kotlin)
- 복잡도 측정값은 전체 코드와 신규 코드에서 다르게 계산될 수 있고, 커뮤니티 답변은 new code 품질 게이트에 조건을 추가해 "no new overly-complex functions"를 막으라고 권한다 — [Sonar metrics definition](https://docs.sonarsource.com/sonarqube-server/user-guide/code-metrics/metrics-definition), [Sonar Community: Custom Quality Gates](https://community.sonarsource.com/t/custom-quality-gates/10887)
- 기본 "Sonar way" 게이트 조건 4개: "No new issues are introduced", "All new Security Hotspots are reviewed", 신규 코드 커버리지 ≥ 80.0%, 신규 코드 중복 ≤ 3.0%. 브랜치에서는 overall·new code 조건이 모두 적용되고, **PR에서는 new code 조건만** 적용된다 — [SonarQube Server: Introduction to quality gates](https://docs.sonarsource.com/sonarqube-server/quality-standards-administration/managing-quality-gates/introduction-to-quality-gates)
- 같은 문서: "Sonar recognizes that AI-generated code requires additional quality standards." AI 코드가 있으면 AI Code Assurance 자격 게이트를 쓰라고 하며, 내장 게이트 이름은 "Sonar way for agentic AI"(에이전트 중심 개발용) — [SonarQube Server: Introduction to quality gates](https://docs.sonarsource.com/sonarqube-server/quality-standards-administration/managing-quality-gates/introduction-to-quality-gates)

연구에서 쓰인 지표
- He 외(MSR '26)는 SonarQube Community의 이슈 총수와 코드베이스 전체 인지 복잡도를, Agarwal 외는 정적 분석 경고·인지 복잡도·중복·주석 밀도를 품질 지표로 썼다 — [arXiv 2511.04427v3](https://arxiv.org/html/2511.04427v3), [arXiv 2601.13597](https://arxiv.org/abs/2601.13597)
- GitClear는 중복 블록(5줄 이상)과 "moved" 라인(리팩터링·재사용 모듈화의 대리 지표)을 쓴다 — [DevClass](https://www.devclass.com/ai-ml/2025/02/20/ai-is-eroding-code-quality-states-new-in-depth-report/1626250)
- Ghosh Paul 외는 PMD·Checkstyle·DesigniteJava로 implementation/design smell 위반 수를 셌다 — [arXiv 2510.03029v2](https://arxiv.org/html/2510.03029v2)
- 에이전트 PR 연구는 변경 라인 수·파일 수를 머지 실패의 유의미한 예측 변수로 보고한다(라인당 odds 약 1% 감소) — [arXiv 2601.15195](https://arxiv.org/html/2601.15195v1)

### Inferences
- 이 저장소 적용 관점: 저장소 메모리에 따르면 detekt 2.0 커스텀 규칙 시도는 11-17에 롤백됐고, 문서상 detekt 2.0은 아직 alpha.6 표기다. 따라서 detekt를 다시 넣는다면 커스텀 규칙 없이 **내장 complexity/style 규칙 세트 + baseline 파일**만 쓰는 것이 위험이 가장 작다. Kotlin 2.3·Gradle 9.9·JDK 25 호환성은 확인하지 못했으므로 도입 전 검증이 필요하다(아래 Gaps).
- "신규 코드에만 게이트"는 두 가지로 구현할 수 있다. (a) detekt baseline으로 기존 위반을 동결하고 새 위반만 실패시키기, (b) SonarQube(또는 SonarQube Cloud)의 new code 조건과 PR 분석. 운영계가 없고 단일 EC2 + GitLab CI 구성이라면 (a)가 인프라 추가 없이 Stop hook(`compile-gate.sh`)과 `busLint`에 바로 붙는다. (b)는 SonarQube 서버 운영 비용이 생긴다.
- 과설계 탐지에 쓸 만한 조합: TooManyFunctions·ComplexInterface(인터페이스 비대화), AbstractClassCanBeConcreteClass·UnnecessaryInheritance(불필요한 상속 계층), UnusedParameter·UnusedPrivate*(추측성 확장 지점의 흔적), LongParameterList(설정 가능성 과잉). 이들은 크기·형태 신호일 뿐이라 "단일 구현 인터페이스"나 "한 번만 쓰는 헬퍼"는 잡지 못한다. 그건 3절의 아키텍처 테스트로 보완해야 한다.
- He 외 연구가 저장소 전체 인지 복잡도를 지표로 쓴 점을 따르면, 이 저장소도 `./gradlew check` 때 모듈별 복잡도 총합과 LOC를 기록해 커밋 간 추세를 보는 것이 PR 단위 임계값보다 AI 도입 효과를 보기에 적합하다(추세 관찰용, 게이트 아님).
- ktlint는 포맷·스타일 규칙 엔진이라 메서드 복잡도 같은 지표 계산에는 맞지 않는다. 복잡도 임계값을 ktlint 커스텀 규칙으로 재구현하는 것은 detekt 기능을 다시 만드는 셈이다.

### Gaps
- detekt 2.0 정식 릴리스 여부와 Kotlin 2.3/Gradle 9.9/JDK 25 호환성은 이번 조사에서 확인하지 못했다(문서 페이지는 2.0.0-alpha.6 표기).
- NOM(메서드 수)·LCOM(응집도 결여) 같은 클래스 응집도 지표를 Kotlin에서 계산해 주는 도구(SonarKotlin, detekt 모두)는 확인하지 못했다. detekt 문서의 complexity 규칙 세트에는 LCOM 규칙이 없다.
- churn(변경 빈도) 지표를 게이트로 쓰는 Kotlin 팀 사례는 찾지 못했다. CodeScene 같은 행동 분석 도구는 조사하지 않았다.
- "Sonar way for agentic AI" 게이트의 구체 조건은 확인하지 못했다.
- SonarKotlin S3776 규칙 페이지는 리다이렉트로 직접 열람하지 못했다(기본 15는 커뮤니티 답변 기준).

## 3. 에이전트 측 대응책 (지침, 워크플로, 리뷰 패스, 아키텍처 테스트)

### Takeaway
2024–2026 대응책은 네 층으로 정리된다. (1) 지침: Anthropic 샘플 프롬프트와 Karpathy 계열 CLAUDE.md처럼 "범위·추상화·방어 코드·문서"를 명시한 YAGNI 문구, (2) 워크플로: 스펙 인터뷰 → 새 세션에서 구현 → 스펙 대비 리뷰, (3) 자동 단순화 리뷰: Claude Code `/simplify`(재사용·단순화·효율·추상화 수준 4개 병렬 에이전트), (4) 결정적 강제: 절대 예외 없는 규칙만 hook·아키텍처 테스트로. Anthropic 문서는 지침은 "가이드라인"이고 반드시 지켜야 하는 것만 hook으로 옮기라고 한다.

### Cited Findings

지침 문구 (AGENTS.md/CLAUDE.md)
- Anthropic 샘플 "Avoid over-engineering" 프롬프트(Scope·Documentation·Defensive coding·Abstractions 4항목, 1절 참조) — [Claude Prompting best practices](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices)
- Opus 5용 범위 고정 문구 "Deliver what was asked, at the scope intended. ... stop short of actions that are clearly beyond what was asked." — [Prompting Claude Opus 5](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-opus-5)
- forrestchang/andrej-karpathy-skills CLAUDE.md(MIT, Karpathy 2026-01 게시물 기반 커뮤니티 파일, 검색 결과상 수만~약 9.8만 스타로 보도되나 수치는 출처마다 다름): "Simplicity First — Minimum code that solves the problem. Nothing speculative." 항목: "No features beyond what was asked", "No abstractions for single-use code", "No "flexibility" or "configurability" that wasn't requested", "No error handling for impossible scenarios", "If 200 lines could be 50, rewrite it". 판정 기준 "Would a senior engineer say this is overcomplicated? If yes, simplify." — [README](https://raw.githubusercontent.com/multica-ai/andrej-karpathy-skills/HEAD/README.md)
- 같은 파일 "Surgical Changes — Touch only what you must. Clean up only your own mess.": "Don't "improve" adjacent code, comments, or formatting", "Don't refactor things that aren't broken", "Match existing style", "If you notice unrelated dead code, mention it — don't delete it", "Remove imports/variables/functions that YOUR changes made unused". 기준 "Every changed line should trace directly to the user's request." 단서: "These guidelines bias toward caution over speed." 사소한 작업은 판단에 맡김 — [README](https://raw.githubusercontent.com/multica-ai/andrej-karpathy-skills/HEAD/README.md)
- Dev.to 비판: Simplicity First가 과교정될 수 있고, 모델이 필요한 추상화와 과설계를 항상 구분하지는 못한다 — [DEV Community](https://dev.to/evan-dong/why-your-ai-coding-assistant-keeps-overengineering-4-rules-that-actually-fix-it-d8a) (검색 요약)
- OpenAI 쪽 대응 문구는 "speculative changes" 회피, "DRY/search first", "No broad catches", "Avoid committing to tests/broad refactors unless you will do them now"이고, Codex는 AGENTS.md를 자동 주입해 강하게 따르도록 학습됐다 — [Using GPT-5.3-Codex](https://developers.openai.com/api/docs/guides/latest-model/gpt-5.3-codex.md)

지침 배치 원칙
- Claude Code 문서: CLAUDE.md는 매 세션 로드되므로 코드에서 알아낼 수 없는 것만 두고 짧게 유지. "If something has to happen every time, make it a hook, a deny rule, or a managed setting. `CLAUDE.md` is for guidelines, and Claude is allowed to decide a guideline doesn't apply." 강조를 계속 덧붙여도 안 지켜지면 hook으로 옮길 신호이고, 반대로 선호에 불과한 hook은 지우라고 한다. "a hook is for a rule with *zero* exceptions" — [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices)
- 같은 문서: 강제 세트는 작게 유지하라. "If you ever find yourself at forty of them, most are probably preferences" — [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices)

계획 → 구현 워크플로
- Claude Code 문서: 요구가 불명확하면 "Interview me until you could write the spec, then write it to SPEC.md."로 범위 밖 항목까지 확정한 뒤 "start a fresh session to build from it, so the build begins with a clean context and a written definition of done." SPEC.md는 이후 리뷰 기준이 된다 — [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices)
- 스펙 대비 리뷰 프롬프트 예: "use a subagent to review the rate limiter diff against SPEC.md, check every requirement is implemented ... report gaps that affect correctness and skip style". `skip style`을 빼면 리뷰가 추상화 층을 늘리는 쪽으로 흐른다는 경고 포함 — [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices)

자동 단순화·리뷰 패스
- Claude Code `/simplify [target]`(번들 스킬): "Review the changed code for cleanup opportunities and apply the fixes. Four review agents run in parallel, covering reuse of existing helpers, simplification, efficiency, and whether the change is at the right level of abstraction. The review doesn't look for correctness bugs." 버그는 `/code-review`로 — [Claude Code Commands](https://code.claude.com/docs/en/commands)
  - 상충 정보: 커뮤니티 저장소는 번들 `/simplify`가 v2.1.146에서 제거됐다고 주장하나 [gtapps/simplify](https://github.com/gtapps/simplify), 현재 공식 명령어 문서는 `/simplify`를 번들 스킬로 싣고 있다 — [Claude Code Commands](https://code.claude.com/docs/en/commands). (이 세션의 스킬 목록에도 "reuse, simplification, efficiency, and altitude cleanups"로 노출된다 — 관찰, URL 없음)
- `/code-review`는 "fresh subagent that never saw your conversation"에서 diff를 검토해 작성 맥락에 끌려가지 않는다. Stop hook은 exit code 2로 턴 종료를 막고 stderr를 Claude에게 돌려준다(연속 8회 차단 후 해제, `CLAUDE_CODE_STOP_HOOK_BLOCK_CAP`로 조정) — [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices)
- Opus 5는 리뷰 프롬프트가 "be conservative"면 문자 그대로 적게 보고하므로 "report everything and filter in a separate pass"를 권한다 — [Prompting Claude Opus 5](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-opus-5)

diff 크기 근거
- 에이전트 PR은 클수록 머지 확률이 낮고(라인당 odds 약 1%↓), 유지보수자들이 "smaller granular PRs"를 요구하며, 무관한 수정을 섞은 PR을 꺼린다 — [arXiv 2601.15195](https://arxiv.org/html/2601.15195v1); "Too large"는 에이전트 PR에만 나타난 거절 사유 — [arXiv 2602.04226](https://arxiv.org/html/2602.04226v1)

아키텍처 테스트
- Konsist: Kotlin 전용 아키텍처·선언 검사 라이브러리로 JUnit/Kotest 테스트로 실행하며 `Konsist.scopeFromProject()`/`scopeFromModule(":app")`로 범위를 잡는다. 레이어 API 예: `Layer("Data", "com.myapp.data..")`, `presentation.dependsOn(data)`, `data.dependsOnNothing()`, `a.doesNotDependOn(b)`. 선언 검사 예: `@RestController` 클래스가 `..controller..` 패키지에 있는지, `UseCase` 접미사 클래스의 public 함수가 정확히 1개인지 — [Konsist docs llms-full.txt](https://docs.konsist.lemonappdev.com/llms-full.txt)
- Konsist 문서에는 "인터페이스 구현체 수" 검사 스니펫이 없고, Kotlin Slack에서 메인테이너가 "클래스가 구체 구현이 아닌 인터페이스에만 의존하는지" 검사에 깔끔한 방법이 아직 없다고 답했다(부모 선언 조회 개선은 백로그) — [Konsist docs](https://docs.konsist.lemonappdev.com/llms-full.txt), [kotlinlang Slack 스레드](https://slack-chats.kotlinlang.org/t/15740615/question-is-it-possible-to-assert-that-all-classes-of-a-pack) (검색 요약)

### Inferences
이 저장소에 바로 적용할 수 있는 형태(모두 추론이며 검증 전 제안):
- **AGENTS.md(공유) 추가 후보**: Anthropic 샘플 중 이 저장소에 아직 없는 Scope·Abstractions 두 항목만 모듈 경계 규칙에 맞게 옮긴다. 예: "요청 범위 밖의 리팩터링·설정 옵션·확장 지점을 만들지 않는다", "한 곳에서만 쓰는 헬퍼·유틸·인터페이스를 만들지 않는다. 단 `*.port.*` 포트 인터페이스는 모듈 경계용이라 예외", "변경으로 생긴 미사용 import·함수만 지우고 기존 dead code는 보고만 한다". Documentation·Defensive coding 항목은 이미 CommentPolicyRule, NoHardcodedExceptionMessageRule, `InputValidator` 규칙으로 덮여 있어 중복 기재할 필요가 없다.
- **모델별 튜닝은 CLAUDE.md에**: Opus 5 계열의 범위 확장 억제 문구("Deliver what was asked, at the scope intended")는 Claude 전용이므로 CLAUDE.md의 "Claude Code 전용" 절에 둔다. Codex는 AGENTS.md를 학습상 강하게 따르므로 공유 문구는 짧고 구체적이어야 한다.
- **결정적 강제는 순수 JUnit 아키텍처 테스트로**: 기존 `WebSurfaceArchitectureTest`처럼 `ClassPathScanningCandidateComponentProvider` + `AnnotatedElementUtils`로 다음을 검사할 수 있다(ArchUnit·Konsist 없이).
  - "`com.media.bus.app.adapter` 아래 클래스·메서드에 `@Transactional`이 없다" — AGENTS.md에 이미 있는 문장 규칙을 테스트로 승격(예외 없는 규칙이므로 hook/test 대상 조건에 맞음).
  - "어댑터 생성자 파라미터 타입이 제공 모듈의 Repository·Exposed Entity가 아니다" — 어댑터가 공개 진입점만 부른다는 규칙의 승격.
  - "`iam`/`stop`/`reservation` 내부(포트 패키지 제외)에서 구현체가 정확히 1개인 인터페이스를 새로 만들지 않는다" — 단, 포트·어댑터 구조에서는 단일 구현 인터페이스가 의도된 설계이므로 `*.port.*`는 반드시 제외해야 한다. 일반적인 "단일 구현 인터페이스 금지" 규칙을 그대로 가져오면 이 아키텍처와 충돌한다. 기존 위반이 있으면 허용 목록(baseline)으로 동결하고 신규만 막는다.
  - "`*Impl` 접미사 클래스 금지"나 "`*Helper`/`*Util` 신규 클래스 금지" 같은 이름 규칙은 예외가 많아(Claude Code 문서의 "a hook is for a rule with zero exceptions" 기준 미달) 지침에 두는 편이 맞다.
- **Stop hook에 diff 크기 신호 추가**: `compile-gate.sh`가 이미 바뀐 모듈을 알고 있으므로 `git diff --stat` 합계가 기준(예: 수백 줄)을 넘으면 차단이 아니라 "범위 확인" 메시지를 남기는 방식이 근거(PR 크기와 머지 실패의 상관)에 맞는다. 임계값은 근거가 없으므로 팀이 정해야 한다.
- **단순화 패스 운용**: 기능 구현 후 `/simplify`(추상화 수준 리뷰 포함)를 돌리되, Claude Code 문서 경고대로 리뷰 결과를 전부 반영하지 말고 SPEC 대비·correctness 위주로 거른다. `/code-review`와 역할이 다르다(버그 vs 품질).
- **계획 단계**: 모듈 경계를 넘는 기능(새 포트 추가)은 SPEC.md 인터뷰 → 새 세션 구현 순서를 쓰면 "포트를 새로 만들어야 하는가"를 구현 전에 사람이 판단할 수 있다.

### Gaps
- `/simplify` 같은 자동 단순화 패스가 실제로 코드 크기·복잡도를 줄인다는 정량 평가는 찾지 못했다.
- YAGNI 지침 문구(Anthropic 샘플, Karpathy 파일)의 효과를 측정한 공개 평가는 찾지 못했다. 스타 수는 인기 지표일 뿐 효과 근거가 아니다.
- ArchUnit의 Kotlin 사용 사례·`@Transactional` 금지 규칙 예시는 이번 조사에서 직접 확인하지 못했다.
- 팀 차원의 diff 크기 상한(예: N줄)을 에이전트에 적용한 실무 사례와 그 효과 수치는 찾지 못했다.

## 4. 한계: 지표가 판단하지 못하는 것과 이를 메우는 리뷰 관행

### Takeaway
복잡도 지표와 스멜 규칙은 크기·분기·중첩·중복을 재지만, 추상화가 정당한지(변경 축이 실제로 있는지, 경계용 포트인지)는 판단하지 못한다. 벤더 문서와 연구는 이 간극을 (1) 작성 맥락에서 분리된 리뷰어(새 서브에이전트, 사람), (2) 스펙 대비 리뷰, (3) "시니어 엔지니어가 과하다고 할까", "모든 변경 줄이 요청으로 추적되는가" 같은 판정 질문, (4) 최종 승인권을 사람에게 두는 방식으로 메운다. 또한 리뷰 자체가 과설계를 부를 수 있다는 점(모든 지적 반영 시 추상화 증가)이 명시돼 있다.

### Cited Findings
- 리뷰어는 항상 무언가를 찾으며 모든 지적을 반영하면 "extra abstraction layers and tests for cases that can't happen"이 생긴다 — [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices)
- 결정적 장치와 비결정적 장치의 구분표: "When it's done"은 요청된 증거·`/goal`·`/code-review`(비결정) vs 테스트·타입체크·Stop hook·CI(결정), "Who has the last word"는 Claude의 요약 vs "Branch protection, a person who approves the merge" — [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices)
- 같은 문서: "Almost every guideline has exceptions, and current models are good at reading the code in front of them and judging whether a guideline fits the situation." 즉 판단이 필요한 규칙(추상화 필요성 포함)은 지침으로 두고 모델·사람의 판단에 맡기는 것이 공식 권장 — [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices)
- 판정 질문: "Would a senior engineer say this is overcomplicated?", "Every changed line should trace directly to the user's request." — [andrej-karpathy-skills README](https://raw.githubusercontent.com/multica-ai/andrej-karpathy-skills/HEAD/README.md)
- Simplicity 지침은 과교정될 수 있으며 모델이 필요한 추상화와 과설계를 항상 구분하지 못한다는 비판 — [DEV Community](https://dev.to/evan-dong/why-your-ai-coding-assistant-keeps-overengineering-4-rules-that-actually-fix-it-d8a) (검색 요약)
- Anthropic은 같은 문서에서 테스트 통과용 하드코딩을 막으려면 "robust, maintainable, and extendable" 해법을 요구하라고도 쓴다. 과설계 억제("minimum needed")와 일반화 요구("extendable")가 한 문서에 공존한다 — [Claude Prompting best practices](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices)
- Sonar 연구: Sonnet 4는 통과율이 올랐지만 버그 심각도가 93% 증가 — 기능 테스트 통과(정확성 지표)가 품질·복잡도 문제를 가리지 못한다는 근거 — [Sonar blog](https://www.sonarsource.com/blog/the-coding-personalities-of-leading-llms)
- 에이전트 거절 PR의 67.9%는 리뷰어 피드백이 없어 사유를 알 수 없다. "No confidence in AI-generated code"(사람이 충분히 검토·이해하지 않은 코드)가 별도 거절 사유로 존재하며, 한 유지보수자는 "SLOP badge"로 표시하겠다고 했다 — [arXiv 2602.04226](https://arxiv.org/html/2602.04226v1)
- "Non-optimal design"("architecturally unsound") 거절 비율은 오히려 사람 PR(5.5%)이 에이전트 PR(0.9%)보다 높다 — 설계 판단 실패가 에이전트만의 문제는 아니다 — [arXiv 2602.04226](https://arxiv.org/html/2602.04226v1)
- Opus 5는 리뷰 정밀도·재현율이 높지만 "be conservative" 같은 지시를 문자 그대로 따라 덜 보고하므로, 전부 보고하게 한 뒤 별도 패스에서 거르라고 권한다 — [Prompting Claude Opus 5](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-opus-5)

### Inferences
- 이 저장소에서 "추상화가 정당한가"의 판단 기준은 이미 아키텍처에 내장돼 있다. 모듈 간 호출은 포트·어댑터(단일 구현 인터페이스가 정당), 모듈 내부는 Service·Facade·Repository(추가 인터페이스는 대체로 불필요). 이 기준을 리뷰 체크리스트 한 줄("새 인터페이스·레이어가 모듈 경계용인가, 두 번째 구현이 실제로 있는가")로 AGENTS.md나 리뷰 스킬에 두면 지표가 못 하는 판단을 사람·리뷰 에이전트가 같은 기준으로 할 수 있다.
- 지표 게이트(복잡도·LOC)는 "신규 코드에서 악화 금지" 수준으로만 쓰고, 추상화 필요성은 PR 설명(작업 보고)에서 "왜 새 타입이 필요한가"를 쓰게 하는 관행으로 보완하는 것이 이 저장소의 기존 규칙("구현 배경, 설계 의도는 작업 보고와 커밋 메시지로")과 맞는다.
- 리뷰 패스를 여러 겹 쌓을수록(simplify → code-review → 사람) 지적 반영이 누적돼 추상화가 늘 수 있으므로, 리뷰 프롬프트에 "correctness와 스펙 누락만, 스타일 제외"를 명시하고 단순화 패스는 "제거·인라인 방향 제안만" 받는 식으로 방향을 고정하는 편이 안전하다.
- AWS·CI 관점: GitLab CI에서 PR(MR) 단위로 new code 조건만 거는 구조(Sonar 방식 또는 detekt baseline)가 단일 EC2 운영 비용 대비 합리적이다. SonarQube 서버를 따로 띄우면 Aurora·Valkey와 별개로 상시 리소스가 늘어난다.

### Gaps
- 사람 리뷰어가 AI 생성 코드의 과설계를 얼마나 잡아내는지, 또는 리뷰 시간이 얼마나 늘어나는지에 대한 2025–2026 정량 연구는 찾지 못했다.
- "추상화의 정당성"을 자동 판정하려는 도구(예: 변경 이력 기반으로 두 번째 구현 등장 여부를 추적)는 찾지 못했다.
- Google·Microsoft의 내부 코드 리뷰 관행 중 AI 생성 코드의 과설계를 다룬 공개 자료는 확인하지 못했다.
