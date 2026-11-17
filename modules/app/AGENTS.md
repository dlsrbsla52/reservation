# AGENTS.md — app

<!-- 이 모듈 지침의 원본. CLAUDE.md는 @AGENTS.md로 가져오고 GEMINI.md는 심볼릭 링크다. -->

`modules/app`은 모듈러 모놀리스의 **유일한 실행 모듈**이다(포트 8080, Actuator `127.0.0.1:8081`).
iam·stop·reservation 도메인 모듈을 한 JVM에서 띄우고, 보안 필터 체인·통합 설정·모듈 간 포트 어댑터를 둔다.
도메인 로직은 두지 않는다.

Base package: `com.media.bus.app` (진입점 `com.media.bus.BusApplication`)

## Build

```bash
./gradlew :modules:app:test
./gradlew :modules:app:bootJar        # modules/app/build/libs/app.jar
./gradlew :modules:app:bootRun --args='--spring.profiles.active=local'
./gradlew generateAllOpenApiDocs      # openapi 프로필(H2, 127.0.0.1:8090)로 띄워 통합 명세 생성
```

## 구성

| 위치 | 역할 |
|---|---|
| `BusApplication` | 스캔 범위를 `app`·`iam`·`stop`·`reservation`으로 한정. `common`·`auth-contract`는 자동 구성으로만 빈 등록 |
| `security/SecurityConfig` | 단일 `SecurityFilterChain`. 인가 규칙은 `permitAll`이고 인증·경로 정책은 아래 필터가 한다. 인가(카테고리·권한)는 컨트롤러 `@Authorize` |
| `security/JwtAuthenticationFilter` | 예전 gateway 역할: 신뢰 헤더 제거, 라우트 허용 목록(밖이면 404), 공개 경로 정확 일치, Bearer 검증(401), `/api/v1/admin` 발급처 검사(403), `MemberPrincipal`·MDC `memberId` 설정 |
| `security/ApiPathPolicy` | 라우트 prefix·공개 엔드포인트(메서드+경로 정확 일치)·문서 경로 목록 |
| `security/CorsConfig` | 관리자 경로는 관리자 origin만 허용 |
| `adapter/*` | 소비 모듈 포트 구현. 제공 모듈 Service·Facade 호출 + DTO 변환 + 예외 변환(`callProvider`) |
| `resources/application*.yml` | 설정은 여기에만 둔다(기본·`local`·`openapi`) |
| `resources/db/changelog/db.changelog-master.xml` | 모듈별 master(iam → stop → reservation)를 include |

## 규칙

- **공개 경로를 추가하면** `ApiPathPolicy.PUBLIC_ENDPOINTS`에 메서드+정확한 경로로 넣는다. 접두 비교를 쓰지 않는다.
- **새 URL prefix**(`/api/v1/<new>`)를 만들면 `ApiPathPolicy.ROUTED_PREFIXES`에 넣는다. 안 넣으면 404다.
- **인증 필터를 빈으로 등록하지 않는다.** 빈이면 Spring Boot가 서블릿 필터로도 등록해 체인 밖에서 한 번 더 돈다.
- **어댑터**
  - 제공 모듈의 공개 진입점(Service·Facade)만 부른다. Facade가 있으면 Facade를 부른다(회원 재검증·정류소 결합·선점 캐시 해제 보존).
  - Repository·Exposed Entity·Table을 받지 않는다. `@Transactional`을 붙이지 않는다.
  - 호출은 `callProvider { }`로 감싼다: `BusinessException`·`StorageException`(404)은 그대로, `BulkheadFullException` → 429/`00270`, 그 밖의 `BaseException`(500) → 500/`00100`. 기준은 예외 타입이 아니라 `ExceptionAdvisor`가 매기는 HTTP 상태다 — 예전에 4xx로 응답하던 예외를 500으로 바꾸지 않는다.
  - 소비자의 문자열 코드값은 `parseCode`/`parseOptionalCode`로 enum 변환한다. `fromName`의 `null`을 필터로 넘기면 전체 조회가 되므로 반드시 400으로 바꾼다.
  - DTO는 필드별로 명시 변환하고, enum은 `.name`(예전 JSON 직렬화 값)으로 옮긴다. 기본값이 있는 필드는 어댑터 테스트로 유지 여부를 고정한다.
- **Actuator**는 관리 포트(루프백)에만 있다. 공개 8080에서는 404다.

## 테스트

| 테스트 | 고정하는 것 |
|---|---|
| `BusApplicationContextTest` | 앱 전체 기동(H2, Redis 불필요): 빈 충돌, 포트당 어댑터 1개, 보안 체인 1개에 인증 필터 포함, 실제 매핑이 라우트 허용 목록 안, 8080/8081 포트 정책, 없는 경로 404 |
| `JwtAuthenticationFilterTest` | 헤더 위조 차단, 토큰 검증, 관리자 발급처, 라우트 허용 목록 |
| `WebSurfaceArchitectureTest` | internal 매핑 0개, 관리자 핸들러 `@Authorize`, 신뢰 헤더 `@RequestHeader` 금지, OpenAPI 스키마 이름 중복 금지 |
| `ModuleBoundaryTest` | iam·stop·reservation 사이 Gradle 의존 금지 |
| `*AdapterTest` | DTO 변환(필드 누락, enum 문자열), 오류 변환 |
