# CLAUDE.md — reservation

`modules/reservation`은 버스 예약 핵심 비즈니스를 담당하는 Spring Boot 서비스 (port **8183**).
Base package: `com.media.bus.reservation`

## Build

```bash
./gradlew :modules:reservation:bootJar
./gradlew :modules:reservation:bootRun
./gradlew :modules:reservation:test
```

## Dependencies

- `modules/common` — 공통 인프라
- `modules/auth-contract` — `JwtProvider`, `MemberPrincipal`, `MemberType`

## 패키지 구조

```
modules/reservation/src/main/kotlin/com/media/bus/reservation/
  contract/              # 계약 도메인
    controller/ContractController   # @Authorize(ADMIN, WRITE) 보호
    service/ContractFacade          # S2S 호출(트랜잭션 외부) + DB 저장(트랜잭션 내부) 분리
    service/ContractService         # @Transactional DB 저장
    service/MemberVerificationService  # IAM S2S 회원 재검증
    client/IamServiceClient         # IAM 서비스 HTTP 클라이언트
    entity/                         # ContractEntity, ContractDetailEntity (Exposed DAO)
    dto/
  reservation/           # 예약 도메인
    controller/ReservationController
    service/ReservationFacade
    service/StopResolutionService   # stop 서비스 S2S 정류소 검증
    client/StopServiceClient        # stop 서비스 HTTP 클라이언트
    entity/                         # ReservationEntity (Exposed DAO)
    dto/
```

## 설계 패턴

- **Facade 패턴** — S2S 호출(트랜잭션 외부)과 DB 저장(트랜잭션 내부)을 명시적으로 분리
- **IAM 재검증** — Gateway 헤더만 신뢰하지 않고, 원본 JWT로 IAM DB에서 회원 재검증
- **모듈 경계** — stop 서비스 데이터는 Repository 직접 참조 금지 → `StopServiceClient` API 경유

## Database

- Schema: `reservation`
- ORM: Exposed DAO 패턴
- Migration: Liquibase (`src/main/resources/db/changelog/`)
