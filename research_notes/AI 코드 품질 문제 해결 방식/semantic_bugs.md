# AI 에이전트가 만든 "그럴듯하지만 틀린" 의미 오류(semantic/logic bug)를 잡는 방법 (2024–2026)

> 조사 범위: 컴파일·lint는 통과하지만 동작이 틀린 코드를 업계·연구에서 어떻게 잡는지, 근거와 한계, Kotlin + Spring Boot 4.1 모듈러 모놀리스(이 저장소)에의 적용.
> 표기: 출처 URL을 직접 열어 확인한 수치만 적었다. 검색 요약으로만 본 수치는 "(검색 요약, 원문 미확인)"으로 표시하거나 Gaps로 보냈다. 벤더가 직접 만든 벤치마크는 "(벤더 자체 측정)"으로 표시했다. 2024년 이전 자료는 "(구 자료)"로 표시했다.

## 1. 실제로 쓰이는 접근법과 각각의 근거 (AI 리뷰어, LLM-as-judge, spec-driven, PBT, approval/snapshot, contract, mutation)

### Takeaway
2025–2026년에 실제로 배포된 의미 오류 방어선은 (a) 다중 에이전트 + 검증 단계를 둔 AI PR 리뷰어, (b) spec → property → 테스트로 이어지는 spec-driven 개발(Kiro의 PBT, GitHub Spec Kit), (c) LLM이 만든 mutant로 테스트의 의미적 강도를 확인하는 mutation-guided 테스트 생성(Meta ACH), (d) LLM 에이전트가 property를 뽑아 돌리는 agentic PBT다. 공통 결론은 "테스트 통과 ≠ 정확성"이며, LLM 판정자(judge)는 표면 단서에 쉽게 흔들리므로 단독 게이트로 쓰기 어렵다.

### Cited Findings

**AI 코드 리뷰어 — Claude Code Code Review (Anthropic, 벤더 자체 측정)**
- 2026-03-09 Team·Enterprise 대상 research preview로 발표했고, 이후 글에 GA 전환 업데이트 노트가 붙었다. PR이 열리면 여러 에이전트가 병렬로 버그를 찾고, 검증 단계(verification step)가 false positive를 거르고, 심각도로 순위를 매겨 요약 코멘트 1개와 인라인 코멘트로 남긴다. PR 크기·복잡도에 따라 에이전트 수와 깊이를 늘린다 — [Anthropic: Bringing Code Review to Claude Code](https://claude.com/blog/code-review)
- Anthropic 내부 수치: 엔지니어 1인당 코드 산출량이 전년 대비 200% 증가, "substantive review comment"를 받은 PR 비율 16% → 54%, 평균 리뷰 시간 약 20분, 1,000줄 초과 PR의 84%에서 지적이 나오고 평균 7.5건, 50줄 미만 PR은 31%에서 평균 0.5건, 엔지니어가 "incorrect"로 표시한 지적은 1% 미만, 리뷰당 평균 $15–25(토큰 과금) — [Anthropic](https://claude.com/blog/code-review)
- 한계(원문 명시): PR 승인은 하지 않는다(사람 결정). 오픈소스 Claude Code GitHub Action보다 비싸며 그 Action은 계속 제공된다 — [Anthropic](https://claude.com/blog/code-review)
- "1% 미만"은 독립 감사가 없다. 한 개인 블로그는 이 수치가 "엔지니어가 고치지 않고 명시적으로 닫은 지적"만 세는 좁은 정의라고 주장한다(검색 요약, 원문 미확인) — [DEV Community 개인 글](https://dev.to/kenimo49/i-asked-3-claude-code-sub-agents-to-review-the-same-pr-they-disagreed-on-41-of-the-comments-2m57)

**AI 코드 리뷰어 비교 벤치마크 (모두 벤더 자체 측정, 서로 모순)**
- Greptile 벤치마크(2025-07): 5개 OSS 저장소(Sentry·Cal.com·Grafana·Keycloak·Discourse) × 10건 = 실제 버그 50건을 "버그 도입 커밋"으로 역추적해 PR로 재현, 기본 설정 호스팅 플랜으로 실행. "catch"는 결함 줄을 짚고 영향을 설명한 라인 코멘트만 인정. catch rate: Greptile 82%, Cursor Bugbot 58%, Copilot 54%, CodeRabbit 44%, Graphite 6%. noise를 측정했다고 하나 false positive 수치는 공개하지 않았고 catch rate에 반영하지 않았다 — [Greptile Benchmarks](https://www.greptile.com/benchmarks.md)
  - 원문 내부 불일치: Copilot 저장소별 합계(4+6+5+4+7)는 26/50 = 52%로 표기된 54%와 다르다 — [Greptile Benchmarks](https://www.greptile.com/benchmarks.md)
- Augment 벤치마크: 같은 5개 저장소의 PR 50건, "유능한 사람 리뷰어가 잡아야 할 이슈"를 golden comment로 정의하고 Augment 팀이 golden set을 보정. Precision/Recall/F: Augment 65/55/59, Cursor Bugbot 60/41/49, Greptile 45/45/45, Codex Code Review 68/29/41, CodeRabbit 36/43/39, Claude Code 23/51/31, GitHub Copilot 20/34/25. 저자는 Augment 리뷰 에이전트 리드이고, golden set을 "도구 출력과 대조해" 보정했다고 밝혀 편향 소지가 있다. 표와 차트 값도 일부 다르다 — [Augment Code blog](https://www.augmentcode.com/blog/we-benchmarked-7-ai-code-review-tools-on-real-world-prs-here-are-the-results)
- 두 벤치마크의 순위가 엇갈린다(예: Greptile 1위 vs Augment 기준 Greptile 3위, Copilot은 54% catch rate vs F 25%) — [Greptile](https://www.greptile.com/benchmarks.md); contradicted by [Augment](https://www.augmentcode.com/blog/we-benchmarked-7-ai-code-review-tools-on-real-world-prs-here-are-the-results)

**LLM-as-judge / 2차 모델 리뷰의 한계 (연구)**
- "Don't Judge Code by Its Cover"(arXiv 2505.16222, EACL 2026 Findings): 코드 평가에서 authority, self-declared correctness, variable renaming, reverse authority, misleading task, illusory complexity 6가지 편향을 정의했고, 시험한 모든 judge가 이 편향에 크게 취약했다. 정답을 과대평가하는 양(+)의 편향과 실제 정답을 숨기는 음(-)의 편향 둘 다 나타났다(검색 요약 기반) — [arXiv 2505.16222](https://arxiv.org/html/2505.16222v1)
- "Bias in the Loop"(arXiv 2604.16790): 같은 코드를 반복 평가하면 판정이 갈리고, 의미를 보존하는 사소한 변형에도 판정이 바뀐다. 정확도와 함께 편향 민감도를 보고해야 한다고 주장(검색 요약 기반) — [arXiv 2604.16790](https://arxiv.org/abs/2604.16790)
- arXiv 2603.00539(2026-02): LLM 코드 리뷰에서 설명·수정안까지 요구하는 상세 프롬프트가 오판(misjudgment)률을 높였다(검색 요약 기반) — [arXiv 2603.00539](https://arxiv.org/html/2603.00539v1)

**Spec-driven development**
- GitHub Spec Kit(2025-09 공개): spec → technical plan → 작은 테스트 가능한 task 순서로 에이전트 작업을 구조화하는 오픈소스 템플릿·CLI·프롬프트 묶음 — [GitHub Blog](https://github.blog/ai-and-ml/github-copilot/spec-driven-development-with-ai-get-started-with-a-new-open-source-toolkit/), [Visual Studio Magazine](https://visualstudiomagazine.com/articles/2025/09/03/github-open-sources-kit-for-spec-driven-ai-development.aspx)
- 산출 파일: `constitution.md`, `spec.md`, `plan.md`, `tasks.md`. constitution은 이후 모든 명령이 참조하는 "협상 불가 원칙" 파일 — [Microsoft Learn](https://learn.microsoft.com/en-us/training/modules/spec-driven-development-github-spec-kit-enterprise-developers/3-examine-github-spec-kit), [DevOps.com](https://devops.com/githubs-spec-kit-puts-the-spec-back-in-software-development/)
- 신버전 명령은 `/speckit.specify`, `/speckit.plan`, `/speckit.tasks`(초기 버전은 `/specify` 등) — [Spec Kit 공식 사이트](https://github.github.com/spec-kit/index.html)
- Kiro(AWS): EARS 형식(WHEN/WHILE/WHERE/IF-THEN) 요구사항에서 design 단계에 property를 추출하고, property 검사 코드와 generator까지 생성한다. 각 property는 원 요구사항·구현 task와 연결된다. 위반 시 구현을 고치거나 spec/구현/테스트 중 무엇을 고칠지 선택지를 제시한다 — [Kiro docs: Correctness](https://kiro.dev/docs/specs/correctness/), [Kiro blog: Does your code match your spec?](https://kiro.dev/blog/property-based-testing/)
- Kiro 문서 스스로 PBT는 formal verification이 아니며, 외부 서비스·비결정적 동작에 크게 의존하는 요구사항은 mocking이 필요하거나 예제 기반 테스트가 더 낫다고 명시 — [Kiro docs](https://kiro.dev/docs/specs/correctness/)
- Kiro 사례 글: round-trip property(fast-check, TypeScript)가 사람이 놓쳤을 보안 버그를 잡음 — [Kiro blog: PBT caught a security bug](https://kiro.dev/blog/property-based-testing-fixed-security-bug/)

**Property-based testing을 에이전트가 수행 (연구)**
- "Agentic Property-Based Testing"(arXiv 2510.09907, Maaz·DeVoe·Hatfield-Dodds·Carlini, NeurIPS 2025): Claude Opus 4.1 에이전트가 모듈을 읽고 property를 도출해 Hypothesis 테스트를 쓰고 실행·자체 검토. 인기 Python 패키지 100개 대상. 수동 검토 결과 보고된 것 중 56%가 유효한 버그, 32%가 메인테이너에게 보고할 만한 버그. 순위 루브릭 상위 21건은 86% 유효·81% 보고 가치. 5건 보고(4건 패치 포함, NumPy 등), 3건 패치 병합 — [arXiv 2510.09907](https://arxiv.org/abs/2510.09907v1), [NeurIPS 2025](https://neurips.cc/virtual/2025/131710)

**Mutation testing을 의미 검증으로 (Meta)**
- Meta ACH(arXiv 2501.12862, FSE Companion 2025): 관심 있는 결함 유형(mutant)을 LLM으로 만들고 그 mutant를 죽이는 테스트를 LLM으로 생성. 2024-10-28 ~ 2024-12-31, Meta의 7개 플랫폼에 배포. Messenger·WhatsApp test-athon에서 엔지니어가 생성 테스트의 73%를 채택했고 36%를 privacy 관련으로 판단 — [arXiv 2501.12862](https://arxiv.org/abs/2501.12862), [Meta Engineering 2025-02](https://engineering.fb.com/2025/02/05/security/revolutionizing-software-testing-llm-powered-bug-catchers-meta-ach/)
- 라인 커버리지만 기준으로 했다면 571개 중 277개(약 49%) 테스트가 버려졌을 것 → 커버리지는 의미적 강도를 대변하지 못한다는 근거. LLM 기반 equivalent mutant 판정기 precision 0.79 / recall 0.47(간단한 전처리 후 0.95 / 0.96) — [arXiv 2501.12862](https://arxiv.org/html/2501.12862v1)
- Meta는 2025-09에 "LLM이 mutation testing과 compliance의 열쇠"라는 후속 글을 냈다(본문 미확인) — [Meta Engineering 2025-09](https://engineering.fb.com/2025/09/30/security/llms-are-the-key-to-mutation-testing-and-better-compliance/)

### Inferences
- 리뷰어 벤치마크는 전부 벤더가 만들었고 순위가 서로 뒤집힌다. 도구 선택 근거로는 약하고, "AI 리뷰어가 버그를 몇 % 잡는다"는 숫자를 일반화하면 안 된다. 공통적으로 recall은 대략 30–55%대(Augment 표), 즉 리뷰어 하나로는 의미 오류 절반 가까이를 놓친다고 보는 편이 안전하다.
- Claude Code Code Review와 Augment 표의 "Claude Code"는 다른 구성일 가능성이 높다(Augment는 구성·모델 버전을 밝히지 않음). 비교 시 주의.
- LLM judge 편향 연구(자기선언 정확성, 이름 바꾸기에 흔들림)는 "같은 에이전트가 쓴 코드를 같은 계열 모델이 검토"하는 구성의 약점을 시사한다. 리뷰어는 실행 가능한 증거(테스트·diff·스냅샷)를 근거로 판단하게 해야 한다.
- 가장 강한 공통 패턴은 "의도를 실행 가능한 형태로 고정"하는 것이다: Kiro는 요구사항 → property, Meta는 결함 유형 → mutant → 테스트, agentic PBT는 코드 → property. 이 저장소에서는 "의도"를 테스트·스냅샷으로 고정하고 Stop hook/CI가 실행하게 하는 쪽이 리뷰어 추가보다 효과가 크다.

### Gaps
- GitHub Copilot code review의 공식 정확도/수용률 데이터, GitHub ReviewBench(Copilot Balanced 구성 grounded F1 40.1%라는 수치)와 Martian 리더보드 수치는 검색 요약으로만 봤고 원문을 확인하지 못했다 — [The New Stack(미확인)](https://thenewstack.io/github-reviewbench-code-review/).
- Graphite Diamond, CodeRabbit 자체의 precision/false positive 공식 수치는 찾지 못했다.
- Claude Code Code Review가 `REVIEW.md` 등 저장소 규칙 파일을 읽는지는 발표 글에 없다(문서 미확인).
- Uber·Shopify·Stripe가 AI 생성 코드의 의미 오류를 잡는 방식에 대한 1차 엔지니어링 블로그는 이번 조사 범위(도구 호출 한도) 안에서 확인하지 못했다.
- formal/lightweight verification(TLA+, Dafny, Kotlin contracts 등)을 AI 코드 검증에 쓴 산업 사례는 조사하지 못했다.

## 2. AI 생성 코드의 버그율과 리뷰 효과에 대한 연구·벤더 데이터

### Takeaway
"테스트를 통과한 AI 패치도 상당수가 틀리다"는 것이 가장 견고한 연구 결과다(ICSE 2026: SWE-bench Verified의 plausible patch 중 29.6%가 정답 패치와 동작이 다르고, 27.3%는 정답보다 더 많은 동작을 바꿈). 벤더 보고서는 AI 공동작성 PR이 logic/correctness 이슈가 약 75% 더 많고(CodeRabbit), 보안 과제의 45%에서 취약점을 만든다(Veracode, Java는 72%)고 하지만 둘 다 방법론적 한계가 크다.

### Cited Findings

**"통과했지만 틀린" 패치 (가장 직접적인 근거)**
- Wang·Pradel·Liu, "Are 'Solved Issues' in SWE-bench Really Solved Correctly?"(arXiv 2503.15223, ICSE 2026): SWE-bench 검증은 PR이 건드린 테스트 파일만 실행하므로, 전체 패치의 7.8%가 개발자 테스트 스위트 전체로는 실패하는데도 "정답"으로 집계됐다 — [arXiv 2503.15223](https://arxiv.org/abs/2503.15223), [ICSE 2026](https://conf.researchr.org/details/icse-2026/icse-2026-research-track/59/Are-Solved-Issues-in-SWE-bench-Really-Solved-Correctly-An-Empirical-Study)
- 차등 테스트 생성기 PatchDiff로 보면 plausible patch의 평균 29.6%가 정답(oracle) 패치와 동작이 구별된다. 의심 패치 77건 수동 분석: 46.8%는 "비슷하지만 갈라지는 구현", 27.3%는 "정답 패치보다 더 많은 동작을 바꾼(adapting more behavior)" 패치. 대상은 SWE-bench Verified 500건에서 CodeStory·LearnByInteract·OpenHands — [arXiv 2503.15223v2](https://arxiv.org/html/2503.15223v2)
  - 주의: 29.6%는 "동작 차이"이고 그 전부가 오류는 아니다. 저자는 기대 동작을 위반할 때만 incorrect로 본다 — [arXiv 2503.15223v2](https://arxiv.org/html/2503.15223v2)

**AI 공동작성 PR 이슈 밀도 (CodeRabbit, 벤더 자체 측정, 2025-12-17)**
- OSS GitHub PR 470건(AI 공동작성 320, 사람 전용 150) 분석. PR당 이슈 10.83 vs 6.45(약 1.7배). critical·major 지적 약 1.4–1.7배 — [CodeRabbit report](https://coderabbit.ai/blog/state-of-ai-vs-human-code-generation-report)
- logic & correctness(비즈니스 로직 실수, 잘못된 의존성, 결함 있는 제어 흐름, 잘못된 설정) 이슈 75% 더 많음. error handling·예외 경로 누락 거의 2배. 보안 이슈 최대 2.74배(부적절한 비밀번호 처리, insecure object reference가 두드러짐). 과도한 I/O 약 8배(건수는 적음). 동시성·의존성 정확성 약 2배 — [CodeRabbit report](https://coderabbit.ai/blog/state-of-ai-vs-human-code-generation-report)
- 한계(원문 명시): 작성 주체를 직접 확인하지 못했고, AI 신호가 없는 PR을 사람 작성으로 "가정"했다. 범주별 원자료·신뢰구간 미공개. 분류 자체를 CodeRabbit의 taxonomy(자사 리뷰어)로 했다 — [CodeRabbit report](https://coderabbit.ai/blog/state-of-ai-vs-human-code-generation-report)

**보안 결함 (Veracode 2025 GenAI Code Security Report, 벤더)**
- 100개 이상 LLM × 80개 실제 코딩 과제. AI 생성 코드가 45%의 경우 보안 취약점을 만들었다. Java가 가장 위험(보안 실패율 72%), Python·C#·JavaScript는 38–45%. XSS(CWE-80) 방어 실패 86%. 모델 크기·학습 정교함과 무관하게 보안 성능은 정체 — [Veracode press release](https://www.veracode.com/press-release/ai-generated-code-poses-major-security-risks-in-nearly-half-of-all-development-tasks-veracode-research-reveals/), [Veracode blog](https://www.veracode.com/blog/genai-code-security-report/)
  - 주의: 위 수치는 보도자료·블로그 검색 요약으로 확인했고 PDF 원문은 열지 않았다.

**LLM 생성 코드 버그 분류 (구 자료, 2024년 3월, 구세대 모델)**
- Tambon et al.(arXiv 2403.08937): CodeGen·PanGu-Coder·Codex가 만든 버그 333건 분석, 10개 범주. 프롬프트 의도에서 벗어난 "Misinterpretation"이 20.77%로 주요 범주. 실무자·연구자 설문으로 검증 — [arXiv 2403.08937](https://arxiv.org/abs/2403.08937)

**리뷰 효과 (벤더 데이터, 1절 재인용)**
- Anthropic 내부: substantive comment PR 16% → 54%, incorrect 표시 1% 미만 — [Anthropic](https://claude.com/blog/code-review)
- Augment 표 기준 recall 29–55%, precision 20–68% — [Augment](https://www.augmentcode.com/blog/we-benchmarked-7-ai-code-review-tools-on-real-world-prs-here-are-the-results)
- Meta ACH: 라인 커버리지 기준이면 유용한 테스트의 약 49%(277/571)를 버렸을 것 — [arXiv 2501.12862](https://arxiv.org/html/2501.12862v1)

### Inferences
- SWE-bench 연구의 27.3% "정답보다 더 많은 동작을 바꾼 패치"는 이 저장소가 걱정하는 "실패 범위를 조용히 넓히는 변경"(예: 쿠키가 없을 때 모든 세션 삭제)과 같은 유형이다. 기존 테스트는 바뀐 쪽만 확인하므로 넓어진 부작용을 못 잡는다. 대응은 "바뀌지 않아야 할 것"을 단언하는 테스트(불변식·스냅샷·차등 테스트)다.
- SWE-bench 7.8%의 원인("PR이 건드린 테스트만 실행")은 Stop hook이 "바뀐 모듈만 컴파일"하는 구조와 닮았다. 바뀐 모듈만 보는 빠른 게이트는 유지하되, 모듈 경계를 넘는 계약(인증 정책, API 표면)은 `:modules:app:test`나 CI 전체 실행으로 보완해야 한다.
- Java 72%(Veracode)와 CodeRabbit의 "insecure object reference" 상위권은 Spring 컨트롤러의 인가·소유권 검사 누락이 AI 코드의 대표 위험이라는 신호로 읽을 수 있다(인과 확인은 아님).

### Gaps
- Kotlin 단독, Spring Boot 단독의 AI 코드 결함률 연구는 찾지 못했다.
- Claude Code·Codex 같은 2025–2026 에이전트가 만든 코드의 logic bug율을 통제 실험으로 측정한 동료심사 연구는 찾지 못했다(벤더 보고서뿐).
- AI 리뷰어의 false positive가 개발자 신뢰·속도에 미치는 영향의 독립 연구는 확인하지 못했다.
- DORA 2025, GitClear, METR 등 생산성·품질 거시 지표는 이번 범위에서 원문을 확인하지 않았다.

## 3. "계약 드리프트" 탐지: 실패 범위를 조용히 넓히거나 권한을 조용히 좁히는 변경

### Takeaway
API 표면 변화는 OpenAPI breaking-change 탐지(oasdiff)로 잡히지만, 이 저장소처럼 인가가 springdoc 명세 밖(커스텀 인증 필터·`ApiPathPolicy`)에 있으면 "엔드포인트 삭제로 인가 판정이 바뀌는" 드리프트는 OpenAPI diff에 보이지 않는다. 그래서 (1) OpenAPI 스냅샷 + oasdiff, (2) 핸들러 매핑 전체를 열거하는 인가 매트릭스 테스트, (3) "바뀌면 안 되는 부작용"을 단언하는 불변식/property 테스트를 함께 둔다.

### Cited Findings

**OpenAPI breaking-change 탐지 — oasdiff**
- 명령: `breaking`(기존 클라이언트를 깨는 변경만), `changelog`(소비자에게 영향 있는 모든 변경), `diff`(문서 수정 포함 전체), `summary`, 그 외 `validate`, `flatten`, `breaking-files`, `checks` 등. 입력은 로컬 파일·http(s) URL·YAML/JSON, git branch/tag/commit과 비교 가능. Docker 이미지 `tufin/oasdiff` — [oasdiff README](https://github.com/oasdiff/oasdiff)
- 심각도: `ERR`(확정 breaking), `WARN`(명세만으로 확정 불가), `INFO`(비파괴). `--fail-on ERR`이면 ERR에서 종료코드 1, `--fail-on WARN`이면 ERR·WARN 모두에서 1. `changelog`는 `--fail-on INFO`도 받는다 — [oasdiff BREAKING-CHANGES.md](https://github.com/oasdiff/oasdiff/blob/main/docs/BREAKING-CHANGES.md)
- git ref 문법: `origin/main:openapi.yaml`, `HEAD~1:openapi.yaml`. 클론에 없는 커밋은 `--fetch` — [oasdiff BREAKING-CHANGES.md](https://github.com/oasdiff/oasdiff/blob/main/docs/BREAKING-CHANGES.md)
- 수용한 변경 무시: `--err-ignore`, `--warn-ignore`(파일 형식은 IGNORING-CHANGES.md). 폐기 기간: `--deprecation-days-stable`, `--deprecation-days-beta`. `--stability-level`로 draft/alpha/beta 엔드포인트는 자유 변경 허용. `--fail-on MISSING-VERSION-BUMP`는 메이저 버전 증가 시에만 breaking 허용 — [oasdiff BREAKING-CHANGES.md](https://github.com/oasdiff/oasdiff/blob/main/docs/BREAKING-CHANGES.md)
- check ID 예: `api-path-removed-without-deprecation`. 전체 목록은 `oasdiff checks changelog --severity error`(또는 `warn`/`info`), `--format json`, `--tags response,remove`, `--location <text>`로 조회 — [oasdiff CHECKS.md](https://github.com/oasdiff/oasdiff/blob/main/docs/CHECKS.md)
- GitHub Action `oasdiff/oasdiff-action`의 `breaking` 하위 action, `fail-on` 입력(`ERR`/`WARN`), 예제는 base 브랜치를 fetch한 뒤 비교(검색 요약·미러 기반, 공식 README 미확인) — [ecosyste.ms](https://awesome.ecosyste.ms/projects/github.com%2Foasdiff%2Foasdiff-action), [DEV Community](https://dev.to/hassann/how-to-diff-openapi-specs-and-block-breaking-changes-in-ci-3a4p)

**인가 표면 테스트 (Spring Security)**
- Spring Security는 기본적으로 모든 요청에 인증을 요구하지만, 메서드 보안 애너테이션을 빠뜨린 엔드포인트는 거부하지 않으므로 "deny by default" 구성을 권한다 — [Spring Security reference: Authorize HttpServletRequests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html), [Baeldung: Deny access by default](https://www.baeldung.com/spring-deny-access)
- 디스패처 수준 엔드포인트와 ERROR 디스패치도 인가 대상이므로 `authorizeHttpRequests`에 포함해야 한다 — [Spring Security reference](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- `RequestMappingHandlerMapping`으로 모든 엔드포인트를 열거해 각 엔드포인트에 보안 규칙이 있는지 확인하는 방식이 실무 글에 있다(시작 시 검사 → 테스트로 옮길 수 있음) — [Implementing Endpoint Security in Spring Boot](https://digital-garden-71g.pages.dev/Main-Notes/Implementing-Endpoint-Security-in-Spring-Boot)
- `@WithMockUser(authorities=...)` + MockMvc로 상태 코드를 단언하는 패턴 — [Baeldung: Spring OAuth access control testing](https://www.baeldung.com/spring-oauth-testing-access-control), [Diffblue](https://www.diffblue.com/resources/how-to-test-a-secured-spring-web-mvc-endpoint-with-mockmvc/)
- Spring Boot 4 마일스톤에서 `RestTestClient`가 Spring Security 설정을 무시한다는 이슈 보고가 있다 → 테스트 클라이언트가 실제 필터 체인을 타는지 확인 필요(검색 요약, 이슈 본문 미확인) — [spring-framework#35646](https://github.com/spring-projects/spring-framework/issues/35646)

**이 저장소의 현재 상태 (로컬 확인)**
- 인가는 springdoc 명세가 아니라 `app`의 인증 필터와 `ApiPathPolicy`(`isRoutable`·`isPublic`·`isAdmin`)가 결정한다. `ApiPathPolicyTest`에 "핸들러가 없던 health-check는 공개 경로가 아니다(보호 핸들러 reservation 상세에 매칭되던 예외 제거)" 테스트가 있다 — 핸들러 유무와 경로 패턴 매칭이 인가 판정에 영향을 준 실제 드리프트 사례 — `modules/app/src/test/kotlin/com/media/bus/app/security/ApiPathPolicyTest.kt`
- 아키텍처 테스트 `ModuleBoundaryTest`, `WebSurfaceArchitectureTest`(internal HTTP API 금지)가 있다 — `modules/app/src/test/kotlin/com/media/bus/app/architecture/`
- OpenAPI 명세는 git에 커밋돼 있지 않다(`git ls-files`에 openapi/api-docs 파일 없음). `org.springdoc.openapi-gradle-plugin` 1.9.0이 앱을 `openapi` 프로필(H2, `127.0.0.1:8090`)로 띄워 `/api-docs.yaml`을 받아 `modules/app/build/docs/openapi/`에 쓰고, 루트 `generateAllOpenApiDocs`가 이를 호출하며, 매번 `cleanOpenApiDocs`로 지우고 재생성한다 — `build.gradle.kts`, `modules/app/build.gradle.kts`

### Inferences
- 드리프트 유형별 탐지 수단(제안):
  - 엔드포인트 삭제·응답 필드 삭제·필수 요청 필드 추가 → oasdiff `breaking --fail-on ERR`(base: `origin/main`의 커밋된 `openapi.yaml`).
  - 엔드포인트 삭제/추가로 공개·관리자·인증 판정이 바뀜 → OpenAPI에는 이 정책이 없으므로 oasdiff로는 못 잡는다. `RequestMappingHandlerMapping`의 (method, pattern) 전체를 열거해 `ApiPathPolicy` 판정(public/admin/authenticated/404)과 함께 텍스트로 직렬화한 "인가 매트릭스 스냅샷"을 approval 파일로 커밋하면, 엔드포인트 하나가 사라져 다른 핸들러 패턴이 그 경로를 흡수하는 경우도 diff로 드러난다. 매트릭스에 없는 새 엔드포인트는 실패시킨다(deny-by-default의 테스트판).
  - 실패 범위 확대(예: 쿠키 없음 → 전체 세션 삭제) → 스펙·인가 표면에는 안 보이는 순수 동작 변화다. "입력이 결손일 때 다른 사용자/다른 세션 상태는 변하지 않는다" 같은 불변식을 예제 테스트 + property 테스트로 단언하고, mutation testing으로 "범위를 넓히는 mutant"가 살아남는지 본다. LLM 리뷰어에는 "삭제·무효화·일괄 처리의 대상 집합이 넓어졌는가"를 체크리스트로 준다.
- OpenAPI에 `security` 요구사항을 정확히 기술하면(springdoc `@SecurityRequirement` 등) oasdiff가 보안 요구사항 변화도 볼 수 있을 가능성이 있으나, 이 저장소는 필터 기반 정책이라 명세와 실제 정책이 어긋날 수 있다. 정책의 단일 진실 공급원은 매트릭스 테스트로 두는 편이 안전하다.
- 응답 래퍼 `ApiResponse<T>`와 Result enum 코드는 클라이언트 계약의 일부다. Result enum 코드 목록을 approval 스냅샷으로 두면 코드 삭제·의미 변경을 리뷰 단계에서 드러낼 수 있다.

### Gaps
- oasdiff가 보안 요구사항(`security`, scope) 추가·삭제를 어떤 check ID와 레벨로 잡는지 CHECKS.md 본문에서 확인하지 못했다(페이지에 카탈로그가 렌더링되지 않음). `oasdiff checks changelog --location security`로 직접 확인 필요.
- openapi-diff(OpenAPITools) 등 대안 도구와 oasdiff의 탐지 범위 비교 자료는 확인하지 못했다.
- "인가 매트릭스 스냅샷"을 표준 관행으로 정리한 1차 엔지니어링 블로그(대형 기업)는 찾지 못했다. 위 설계는 조각(핸들러 열거, MockMvc 인가 테스트, deny-by-default)을 조합한 추론이다.
- Spring REST Docs, Pact/Spring Cloud Contract를 모듈러 모놀리스 내부(같은 JVM) 계약에 쓰는 사례는 조사하지 못했다.

## 4. Kotlin + Spring Boot 4 + Gradle에서의 구체 도구와 연결 방법

### Takeaway
즉시 붙일 수 있는 것은 (1) `io.kotest:kotest-property`(Kotest 러너 없이 JUnit 5에서도 사용 가능), (2) ApprovalTests.Java(`Approvals.verify`, `.approved.*` 파일 커밋), (3) oasdiff CLI/Docker를 CI에서 `openapi.yaml` 스냅샷과 비교, (4) PIT(`info.solidsoft.pitest` Gradle 플러그인)다. 단 Kotlin 친화 mutation은 상용 ArcMutate 플러그인이 필요하고, Gradle 9.9·JDK 25 호환성은 직접 확인해야 한다.

### Cited Findings

**Property-based testing**
- Kotest property 모듈: Gradle 좌표 `io.kotest:kotest-property:$version`(Maven은 `kotest-property-jvm`). "Kotest를 테스트 프레임워크로 쓰지 않아도 property 지원을 쓸 수 있다"고 명시. 반복 횟수(iterations), seed 지정, shrinking, generator(custom generator 포함) 문서가 별도 페이지로 있다 — [Kotest docs: Property-based testing](https://kotest.io/docs/proptest/property-based-testing.html)

**Approval / snapshot**
- ApprovalTests.Java: 테스트마다 `Approvals.verify(...)` 한 줄, 승인 파일 이름은 테스트 클래스·메서드 이름을 따른다. JSON 검증 예시는 `.approved.json`, 일반 객체는 `.approved.txt`를 만든다. `*.approved.*` 파일은 소스 관리에 커밋해야 한다. 기대값을 테스트 안에 두는 inline 방식(`Options().inline(expected)`)도 있다. Maven Central 아티팩트 `approvaltests` — [ApprovalTests.Java README](https://www.github.com/approvals/ApprovalTests.Java), [Getting Started](https://github.com/approvals/ApprovalTests.Java/blob/master/approvaltests/docs/tutorials/GettingStarted.md), [Inline approvals](https://github.com/approvals/ApprovalTests.Java/blob/master/approvaltests/docs/explanations/InlineApprovalsWorkflow.md)

**OpenAPI diff**
- oasdiff는 git ref와 파일을 직접 비교하고(`origin/main:openapi.yaml`), `--fail-on ERR`로 CI를 실패시킨다. Docker `tufin/oasdiff`로 JVM 툴체인과 무관하게 실행 가능 — [oasdiff README](https://github.com/oasdiff/oasdiff), [BREAKING-CHANGES.md](https://github.com/oasdiff/oasdiff/blob/main/docs/BREAKING-CHANGES.md)

**Mutation testing**
- Gradle 연동은 `info.solidsoft.pitest` 플러그인(gradle-pitest-plugin), README에 Kotlin DSL 예제와 1.15.0 버전 표기 — [gradle-pitest-plugin README](https://github.com/szpak/gradle-pitest-plugin/blob/master/README.md)
- 오픈소스 `pitest-kotlin`은 더 이상 유지되지 않고, README가 inline 함수 등 Kotlin 기능을 지원하는 상용 ArcMutate 플러그인으로 안내한다. `com.arcmutate:pitest-kotlin-plugin`(Maven Central 1.5.0)은 라이선스 키 구매(또는 grant)가 필요 — [Maven Central: com.arcmutate:pitest-kotlin-plugin](https://central.sonatype.com/artifact/com.arcmutate/pitest-kotlin-plugin), [pitest-kotlin](https://github.meowingcats01.workers.dev/pitest/pitest-kotlin)
- Kotlin + PIT를 무리 없이 쓴다는 커뮤니티 보고와, 초기 설정 실패 사례(2021, 구 자료)가 공존 — [kotlinlang Slack](https://slack-chats.kotlinlang.org/t/23090338/hello-everyone-has-anyone-been-able-to-setup-mutation-testin?t=v2), [pitusers 메일링(2021)](https://groups.google.com/g/pitusers/c/jxWlkeVCny0)

**AI 리뷰어 연결**
- Claude Code Code Review는 관리자가 Claude Code 설정에서 활성화하고 GitHub App을 설치해 저장소를 선택하며, 월 지출 한도·저장소별 활성화·분석 대시보드(리뷰 PR 수, 수용률, 비용)를 제공한다. 더 가벼운 오픈소스 Claude Code GitHub Action도 있다 — [Anthropic](https://claude.com/blog/code-review)

### Inferences
- 이 저장소에 맞춘 배치안(제안, 검증 전):
  1. **API 표면 스냅샷**: 현재 `generateOpenApiDocs`는 앱 전체를 8090에 띄우므로 느리다. `:modules:app:test`에 `@SpringBootTest` + MockMvc로 `/api-docs.yaml`(테스트 프로필에서 문서 노출 허용)을 받아 `Approvals.verify`로 커밋된 스냅샷과 비교하면 빠르고, 스냅샷 파일 자체가 리뷰 diff가 된다. CI에서는 그 커밋된 `openapi.yaml`을 `oasdiff breaking origin/main:<경로> <경로> --fail-on ERR`로 비교해 "의도한 breaking인지"를 강제한다(Docker `tufin/oasdiff` 사용 시 JDK 25 제약과 무관).
  2. **인가 매트릭스 스냅샷**: `BusApplicationContextTest`처럼 컨텍스트를 띄우는 테스트에서 `RequestMappingHandlerMapping.handlerMethods`를 열거해 `METHOD pattern -> public|admin|authenticated|unroutable`을 정렬된 텍스트로 만들고 approval 파일로 고정. 매트릭스에 없는 엔드포인트는 실패. `ApiPathPolicyTest`의 손으로 쓴 사례를 전수 검사로 확장하는 것.
  3. **불변식/property 테스트**: Kotest `kotest-property`를 JUnit 5 테스트 안에서 사용(러너 교체 불필요). 대상은 인증·세션·예약 상태 전이처럼 "결손 입력에서 영향 범위가 넓어지면 안 되는" 코드. API 이름 `checkAll`/`forAll`/`Arb`는 사전 지식이며 이번에 문서 본문으로는 확인하지 못했다.
  4. **mutation testing**: 커버리지가 아닌 "테스트가 의미를 단언하는가"를 보려면 PIT. Kotlin에서 쓸모 있는 mutant를 얻으려면 ArcMutate(상용) 검토가 필요하고, 전체가 아니라 `iam` 세션/토큰, `reservation` 상태 전이 패키지로 범위를 좁혀 주기적으로(야간 CI) 돌리는 편이 현실적이다. Meta ACH처럼 "우려하는 결함 유형"(예: 삭제 대상 조건 제거, 소유권 검사 제거)을 LLM에게 mutant로 만들게 하고 테스트가 죽이는지 보는 방식은 도구 없이 에이전트 작업으로도 흉내 낼 수 있다.
  5. **Stop hook과의 역할 분리**: 현재 Stop hook은 바뀐 모듈 컴파일 + `busLint`만 돌린다(CLAUDE.md). 의미 게이트(스냅샷·매트릭스·property)는 `:modules:app:test`에 넣어 CI와 "컨트롤러·보안·세션 파일을 바꾼 턴"에 한해 hook에서 실행하는 식으로 비용을 조절할 수 있다. 스냅샷 승인 파일(`*.approved.*`)을 에이전트가 임의로 갱신하지 못하게 lint-guard와 같은 PreToolUse 확인을 거는 것이 핵심이다(승인 파일 갱신 = 계약 변경 승인).
  6. **AI 리뷰어**: 리뷰어는 위 스냅샷 diff를 근거로 "이 계약 변경이 의도인가"를 묻는 보조 수단으로 둔다. 벤치마크상 recall이 절반 안팎이라 단독 게이트로 두지 않는다.
- AWS/CI 관점: oasdiff 결과와 approval diff는 GitLab MR에서 아티팩트·코멘트로 남겨 사람이 승인하게 하고, Docker 기반 oasdiff는 GitLab runner에서 JDK와 독립적으로 돌릴 수 있다. 운영계가 아직 없으므로(초기 단계) breaking 허용 정책은 느슨하게 시작하되, 인가 매트릭스는 지금부터 엄격히 두는 편이 이득이 크다.

### Gaps
- Kotest 6.x / ApprovalTests / gradle-pitest-plugin / ArcMutate가 Gradle 9.9 + JDK 25 + Kotlin 2.3에서 동작하는지 확인하지 못했다(각 릴리스 노트 확인 필요).
- jqwik(JUnit 5 엔진 기반 PBT)의 Kotlin 지원 상태는 조사하지 못했다.
- springdoc 3.x가 생성하는 명세가 실행마다 결정적(정렬·순서 안정)인지 확인하지 못했다. 비결정적이면 approval 스냅샷이 흔들리므로 정규화(정렬) 단계가 필요할 수 있다.
- ApprovalTests의 Kotlin/JUnit 5 공식 예제는 찾지 못했다(Java 라이브러리라 JVM에서 동작할 것으로 보이나 미확인).
- oasdiff-action의 현재 입력 이름·태그는 공식 README에서 확인하지 못했다(GitLab CI를 쓰므로 Docker CLI 직접 호출이 더 단순할 수 있음).
