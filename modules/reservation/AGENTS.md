# AGENTS.md — reservation

<!-- 이 모듈 지침의 원본. CLAUDE.md는 @AGENTS.md로 가져오고 GEMINI.md는 심볼릭 링크다. -->

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

## 선점(hold) 정책

- **선착순 즉시 선점** — 예약은 생성 즉시 정류소 광고면(`stop_id` + `slot_no`)을 선점한다. 광고면당 진행 중(`PENDING`/`CONSULTING`) 예약은 1건이고, 이미 선점된 광고면 요청은 즉시 거절한다(`R0007`).
- **만료** — 선점은 `reservation.hold.duration`(7일) 뒤 `EXPIRED`로 끝난다. 상담 시작(`CONSULTING`) 시 그 시점부터 다시 계산하고, 관리자가 연장할 수 있다. 정리는 `ReservationHoldExpiryScheduler`와 예약 생성 시점의 만료 확인이 함께 한다.
- **계약 독점** — 같은 광고면에서 점유 계약(`PENDING`/`ACTIVE`)의 기간은 겹칠 수 없다(`C0004`). 희망 시작일에 계약 중인 광고면은 예약 단계에서 거절한다(`R0008`).
- **정합성은 DB가 보장한다** — 부분 유니크 인덱스 `uq_reservation_active_slot`, 제외 제약 `ex_contract_slot_period`, 상태 전이의 `SELECT ... FOR UPDATE`. 이 조건은 `ReservationStatus.holdsSlot`, `ContractStatus.occupiesSlot`과 같아야 하므로 한쪽을 바꾸면 changelog도 함께 바꾼다.
- **Redis 게이트(`StopHoldGate`)는 부하 차단용** — 몰린 요청 중 1건만 DB로 보낸다. 정합성을 맡기지 않으며 Redis 장애 시 통과시킨다. Redis 연산은 트랜잭션 밖(Facade)에서만 한다.
- **현재 상태의 원본은 `reservation.status`** — `reservation_consultation`은 이력이다. 상태 변경은 `ReservationService`의 전이 메서드로만 하고 두 곳을 같은 트랜잭션에서 쓴다.
- **제약 위반 변환** — Exposed DAO는 쓰기를 커밋까지 미루므로, 제약 위반을 409로 돌려주려면 서비스 안에서 `flushTranslating`으로 먼저 내보낸다.
- **광고면 확장** — 지금은 모든 row가 `StopSlot.DEFAULT_NO`(1)이다. 면이 여러 개가 되면 요청 DTO에서 면 번호를 받아 넘긴다.

검증: 위 동작은 PostgreSQL 기능에 기대므로 `StopHoldPostgresIT`(기본 비활성, 일회용 PostgreSQL 필요)로 확인한다. 실행 방법은 그 파일의 KDoc에 있다.

## Database

- Schema: `reservation`
- ORM: Exposed DAO 패턴
- Migration: Liquibase (`src/main/resources/db/changelog/`)
