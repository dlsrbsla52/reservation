# AGENTS.md

## 언어와 응답

- 사용자 응답, 설명, 커밋 메시지는 한국어로 작성한다.
- 코드 주석은 필요한 경우에만 작성하되, 작성 시 한국어로 작성한다.
- 구현 시 설계 의도, 영향 범위, 검증 결과를 함께 보고한다.

## 프로젝트 개요

이 프로젝트는 Spring Boot 4, Kotlin, Gradle 멀티 모듈 MSA 프로젝트다.

루트 규칙은 `CLAUDE.md`를 따른다.
모듈을 수정할 때는 반드시 해당 모듈의 `CLAUDE.md`도 먼저 확인한다.

- `modules/common/CLAUDE.md`
- `modules/auth-contract/CLAUDE.md`
- `modules/gateway/CLAUDE.md`
- `modules/iam/CLAUDE.md`
- `modules/stop/CLAUDE.md`
- `modules/reservation/CLAUDE.md`

## 작업 규칙

- 기존 아키텍처와 모듈 경계를 우선한다.
- 타 모듈 Repository 직접 참조 금지. Service 또는 API 경유.
- Controller 내부 try-catch 금지. 공통 예외 처리에 위임한다.
- 응답은 `ApiResponse<T>`를 사용한다.
- DB 변경은 Liquibase changelog로만 처리한다.
- Exposed DAO 패턴을 따른다.
- Kotlin 프로젝트이므로 `java.util.Optional`, 불필요한 `@JvmStatic`, `@JvmField` 사용을 피한다.
- Virtual Thread 환경을 고려해 `ThreadLocal`, `synchronized` 사용을 피한다.

## 검증 명령

전체 검증:

```bash
./gradlew clean build
```

모듈별 검증:

```bash
./gradlew :modules:<module>:test
./gradlew :modules:<module>:bootJar
```

예시:

```bash
./gradlew :modules:reservation:test
./gradlew :modules:iam:test
```

## 주의할 파일

- `.claude/**`와 `.omc/**`는 Claude/OMC 전용 설정이다. Codex 작업 중에는 사용자가 요청한 경우에만 수정한다.
- `.agents/workflows/reservation.md`는 현재 프로젝트 상태와 일부 내용이 다를 수 있으므로, 최신 기준은 루트 `CLAUDE.md`와 모듈별 `CLAUDE.md`를 우선한다.
