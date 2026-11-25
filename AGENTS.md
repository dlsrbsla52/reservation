# AGENTS.md — reservation (bus)

<!-- 지침 원본. Claude Code는 CLAUDE.md의 @AGENTS.md import로, Gemini·Antigravity는 GEMINI.md 심볼릭 링크로 이 파일을 읽는다. 지침은 이 파일과 modules/*/AGENTS.md에만 쓴다. -->

## 응답 규칙

- 모든 설명, 주석, 커밋 메시지는 한국어로 작성한다.
- 코드 주석은 `app`·`iam`·`stop`·`reservation`·`content`에서 쓰지 않는다(함수·클래스의 한 줄 KDoc만 허용, 진짜 예외 케이스는 `// 예외: <이유>`이고 사용자 승인이 필요하다). `common`·`auth-contract`는 설명 주석을 둔다. 근거·범위는 `docs/agents/rules.md`.
- 구현 배경, 설계 의도, 잠재적 사이드 이펙트는 코드 주석이 아니라 작업 보고와 커밋 메시지로 설명한다. 작업을 마치면 영향 범위와 검증 결과를 함께 보고한다.
- AWS 환경(Aurora, Secrets Manager)과 CI/CD(GitLab, Docker) 파이프라인을 고려한 아키텍처 관점의 조언을 병행한다.

## 개요

Spring Boot 4.1.2 모듈러 모놀리스 (root: `bus`), Kotlin 2.3.20, 7개 Gradle 모듈. ORM: Exposed 1.6.1. 실행 모듈: `modules/app`(JVM 1개, 포트 8080). 공통 인프라: `modules/common`, 인증 계약: `modules/auth-contract`.

- 2026-11 MSA(gateway + 서비스 3개)에서 전환했다. 배경과 단계는 `docs/modular-monolith-migration-plan.md`, 모듈 간 호출 대응표는 `docs/modular-monolith-s2s-mapping.md`.

## 빌드와 실행

```bash
./gradlew clean build                                         # 전체 빌드
./gradlew :modules:app:bootJar                                # 실행 JAR (modules/app/build/libs/app.jar)
./gradlew :modules:app:bootRun --args='--spring.profiles.active=local'
./gradlew :modules:<module>:test                              # 단일 모듈 테스트
./gradlew :modules:<module>:test --tests "패키지.클래스.메서드"
./gradlew generateAllOpenApiDocs                              # 통합 OpenAPI (modules/app/build/docs/openapi/openapi.yaml)

# Docker (운영)
docker compose up --build -d
docker compose up -d --build --no-deps app

# Docker (로컬 개발)
docker compose -f docker-compose-local.yml up --build -d
docker compose -f docker-compose-local.yml up -d database valkey   # 앱은 IDE에서 실행할 때
```

## 모듈

모듈 코드를 수정하기 전에 해당 모듈의 `AGENTS.md`를 먼저 읽는다.

| 모듈 | 역할 | 지침 |
|---|---|---|
| `app` | 실행 모듈(8080, Actuator 127.0.0.1:8081). 보안 필터 체인, 통합 설정, 모듈 간 포트 어댑터 | `modules/app/AGENTS.md` |
| `iam` | JWT 발급·갱신·회원 관리, 관리자 API | `modules/iam/AGENTS.md` |
| `stop` | 버스 정류소 CRUD | `modules/stop/AGENTS.md` |
| `reservation` | 예약 핵심 비즈니스 | `modules/reservation/AGENTS.md` |
| `content` | 홈페이지 콘텐츠(포트폴리오·공지·FAQ·온라인 문의), 이미지 저장소(R2) | `modules/content/AGENTS.md` |
| `auth-contract` | JWT/인증 계약 공유 라이브러리 | `modules/auth-contract/AGENTS.md` |
| `common` | 공통 인프라 라이브러리 | `modules/common/AGENTS.md` |

인프라: PostgreSQL 18.3 (port **15433**), Valkey 8.1.6 / Redis (port **6379**)

공개 경로: `/api/v1/{auth,member,admin,stop,reservation,contract,content}/**`만 8080에서 받는다. 그 밖의 경로(`/actuator`, `/error`, swagger 등)는 `app`의 인증 필터가 404로 끝낸다(로컬 프로필은 swagger 허용).
- **모듈 간 호출은 포트·어댑터로만 한다.** 소비 모듈이 포트 인터페이스를 정의하고(`iam.port.*`, `reservation.*.port.*`), `app`의 어댑터(`com.media.bus.app.adapter`)가 제공 모듈의 Service·Facade를 호출해 구현한다. HTTP internal API(`/api/v1/internal/**`)는 만들지 않는다 — `WebSurfaceArchitectureTest`가 막는다.
- 어댑터는 제공 모듈의 공개 진입점(Service·Facade)만 부른다. Repository·Exposed Entity를 받지 않고, `@Transactional`을 붙이지 않는다. 입력 검증은 제공 모듈 진입점에 둔다(`InputValidator`).
- `/api/v1/admin/**`는 인증 필터가 관리자 사이트 토큰(`aud=ADMIN`) + 관리자 회원 유형만 통과시킨다. 관리자 화면이 쓰는 기능은 서비스 공개 API를 직접 부르지 말고 iam admin API → 포트로 위임한다.
- 회원 정보는 `@CurrentMember MemberPrincipal`로만 받는다. `X-User-*` 헤더를 읽지 않는다(인증 필터가 모든 요청에서 제거한다).

## 코드 규칙 (반드시 지킨다)

- **Kotlin** — 순수 Kotlin 프로젝트. `@JvmStatic`, `@JvmField`, `java.util.Optional` 사용 금지. nullable 타입(`T?`) 사용
- **Virtual Threads 활성화** — `ThreadLocal` 금지, `ScopedValue` 사용. `synchronized` 대신 `ReentrantLock`
- **Enum** — 모든 코드성 Enum은 `BaseEnum` 구현 (`displayName` 필드). companion object에 `fromName()` 정의
- **Entity** — Exposed DAO 패턴. Table object + Entity class 분리. 정적 팩토리 메서드(`companion object`)로 생성 강제. 팩토리 이름은 `create`(또는 `createXxx`)
- **모듈 경계** — 기존 아키텍처와 모듈 경계를 우선한다. `iam`/`stop`/`reservation`/`content`는 서로 Gradle 의존하지 않는다(`ModuleBoundaryTest`). 타 모듈 호출은 소비 모듈의 포트 → `app` 어댑터 경유
- **응답** — `common`의 `ApiResponse<T>` 래퍼 필수
- **예외** — Controller 내 try-catch 금지. `common` 글로벌 핸들러(`ExceptionAdvisor`)에 위임. 예외 클래스는 Kotlin 기본 인수 주생성자 사용
- **예외 메시지** — 문자열로 쓰지 않는다. 모듈 Result enum(`CommonResult`·`AuthResult`·`StopResult`·`ReservationResult`·`ContractResult`·`ContentResult`)에 코드를 추가하고 `BusinessException(XxxResult.Y)`(4xx)나 `ServiceException(XxxResult.Y)`(5xx)를 던진다. 예외 클래스는 `message` 파라미터가 없다. `error()`·`require {}`·`check {}`도 쓰지 않는다. 디버깅용 값은 log로 남긴다
- **KDoc** — 함수·클래스에만 한 줄 요약(`/** 요약 */`)으로 쓴다. `common`·`auth-contract`는 여러 줄 마크다운 KDoc을 유지한다
- **DB 마이그레이션** — Liquibase changelog만 사용, 수동 DDL 금지. 모듈별 `db/changelog/{iam,stop,reservation,content}/`에 두고 `app`의 루트 master가 include한다. 새 changelog 파일의 `logicalFilePath`는 모듈 경로(`db/changelog/{module}/<파일명>.xml`)로 지정한다(V001 등 전환 전 파일은 예전 이력 경로 `db/changelog/<파일명>.xml`을 유지한다)
- **테스트** — Repository: `@Mock`, Guard/Validator: 익명 Stub. MockK 사용 권장. 테스트 메서드 이름은 백틱 한국어 문장이고 메서드에 `@DisplayName`을 달지 않는다(클래스는 허용)
- **스키마 격리** — DataSource는 하나지만 모듈별 schema를 쓴다(`auth`·`audit` iam / `stop` / `reservation` / `content`). Exposed Table은 스키마를 명시한다(`"auth.member"`). 다른 모듈 스키마의 테이블을 조인하지 않는다

## 검증

- 바꾼 모듈마다 `./gradlew :modules:<module>:test`를 실행한다. `:modules:app:test`(아키텍처·보안·어댑터 테스트)와 `:modules:app:bootJar`도 확인한다.
- 여러 모듈이나 공유 모듈(`common`, `auth-contract`)을 바꿨으면 `./gradlew clean build`로 전체를 확인한다.
- lint는 `check`에 묶여 있다(`./gradlew check`). 모듈 하나만 볼 때는 `./gradlew :modules:<module>:busLint`, 자동 수정은 `./gradlew ktlintFormat`. 규칙·메시지·우회 방지 장치는 `docs/agents/rules.md`에 있다. lint 위반은 코드를 고쳐 통과시키고 `lint/`·`.editorconfig`·`@Suppress`로 피하지 않는다.
- Claude Code(`.claude/settings.json`)와 Codex(`.codex/hooks.json`, 이 맥 전용) 모두 같은 hook 스크립트(`.claude/hooks/`)로 편집 후 턴 종료 시 `busLint`를 돌리고 lint 우회를 막는다. Codex는 사용자 확인(ask)을 지원하지 않아 우회 시도는 거절된다.

## 비밀값

- 비밀값(`JWT_SECRET`, `SEOUL_API_KEY` 등)은 **프로젝트 루트 `.env`에만** 둔다. `.env`는 git·docker 빌드 컨텍스트에서 제외돼 있고, `.env.example`이 템플릿이다.
- 읽는 경로: docker compose는 `.env`를 자동으로 읽어 `${VAR}`를 치환하고, 로컬 실행은 `modules/app`의 `application.yml` `spring.config.import`가 읽는다. 환경변수가 `.env`보다 우선한다. 설정 파일(`application*.yml`)은 `modules/app`에만 둔다.
- yml·compose·테스트에 실제 값이나 운영에 쓰일 수 있는 기본값을 넣지 않는다. 테스트는 명백한 더미 값을 쓴다.
- IntelliJ HTTP Client의 계정 비밀번호는 `HTTP/env/http-client.private.env.json`(git 제외)에 두고 `.http`에서는 `{{password}}`로 참조한다.
- `local/*`, `backup/*` 브랜치는 비밀값 정리 전 원본 이력을 담은 로컬 전용 브랜치다. `.git/hooks/pre-push`가 push를 막는다.

## 주의할 파일

- `.claude/**`는 Claude Code 설정(hooks, skills)이고, `.codex/`와 `.omx/`는 Codex 설정과 상태다. 사용자가 요청한 경우에만 수정한다.
- `GEMINI.md`는 이 파일을 가리키는 로컬 심볼릭 링크다(git 제외). 따로 편집하지 않는다.
- `.agents/workflows/reservation.md`는 초기 마이그레이션 프롬프트 기록이라 현재 구조와 다르다. 최신 기준은 이 파일과 모듈별 `AGENTS.md`다.
