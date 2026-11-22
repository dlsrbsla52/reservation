# AGENTS.md — reservation

<!-- 이 모듈 지침의 원본. CLAUDE.md는 @AGENTS.md로 가져오고 GEMINI.md는 심볼릭 링크다. -->

`modules/reservation`은 버스 예약 핵심 비즈니스를 담당하는 도메인 라이브러리 모듈이다. 실행은 `modules/app`이 한다.
Base package: `com.media.bus.reservation`

## Build

```bash
./gradlew :modules:reservation:test
./gradlew :modules:app:bootRun --args='--spring.profiles.active=local'   # 실행은 app 모듈
```

## Dependencies

- `modules/common` — 공통 인프라
- `modules/auth-contract` — `JwtProvider`, `MemberPrincipal`, `MemberType`

## 패키지 구조

```
modules/reservation/src/main/kotlin/com/media/bus/reservation/
  contract/              # 계약 도메인
    controller/ContractController   # 회원 본인 계약 조회 (관리자 생성·조회는 InternalContractFacade — iam admin → app 어댑터 경유)
    service/ContractFacade          # 타 모듈 조회(트랜잭션 외부) + DB 저장(트랜잭션 내부) 분리
    service/InternalContractFacade  # 관리자 계약 진입점. 입력 검증·페이지 보정 포함
    service/ContractService         # @Transactional DB 저장
    service/MemberVerificationService  # 회원 재검증 (MemberLookupPort 경유)
    port/MemberLookupPort           # iam 회원 조회 포트. 구현은 app 어댑터
    entity/                         # ContractEntity, ContractDetailEntity (Exposed DAO)
    dto/
  reservation/           # 예약 도메인
    controller/ReservationController
    service/ReservationFacade
    service/StopResolutionService   # 정류소 검증·결합 (StopLookupPort 경유)
    port/StopLookupPort             # stop 정류소 조회 포트. 구현은 app 어댑터
    entity/                         # ReservationEntity (Exposed DAO)
    dto/
```

## 설계 패턴

- **Facade 패턴** — 타 모듈 포트 호출(트랜잭션 외부)과 DB 저장(트랜잭션 내부)을 명시적으로 분리. 관리자 진입점(`ReservationFacade`의 admin 메서드, `InternalContractFacade`)이 입력 검증을 맡는다
- **IAM 재검증** — 계약 생성 시 `memberId`가 있으면 `MemberLookupPort`로 iam DB에서 회원을 다시 확인한다
- **모듈 경계** — stop·iam 데이터는 Repository 직접 참조 금지 → 포트(`StopLookupPort`, `MemberLookupPort`) 경유. 이 모듈은 다른 도메인 모듈에 Gradle 의존하지 않는다

## 선점(hold) 정책

- **선착순 즉시 선점** — 예약은 생성 즉시 정류소 광고면(`stop_id` + `slot_no`)을 선점한다. 광고면당 진행 중(`PENDING`/`CONSULTING`) 예약은 1건이고, 이미 선점된 광고면 요청은 즉시 거절한다(`R0007`).
- **만료** — 선점은 `reservation.hold.duration`(7일) 뒤 `EXPIRED`로 끝난다. 상담 시작(`CONSULTING`) 시 그 시점부터 다시 계산하고, 관리자가 연장할 수 있다. 정리는 `ReservationHoldExpiryScheduler`와 예약 생성 시점의 만료 확인이 함께 한다.
- **계약 독점** — 같은 광고면에서 점유 계약(`PENDING`/`ACTIVE`)의 기간은 겹칠 수 없다(`C0004`). 희망 시작일에 계약 중인 광고면은 예약 단계에서 거절한다(`R0008`).
- **희망 계약 시작일은 필수** — `desiredContractStartDate`는 컬럼이 V001부터 NOT NULL이고 요청 DTO도 non-null이다. 빠지면 역직렬화 단계에서 400/`00260`이다.
- **요청 본문의 null 원소** — MVC의 Jackson은 Kotlin nullability를 검사하지 않는다(`Set<UUID>`에 `""`·`null`이 null 원소로 들어온다). 원소 위치의 `@NotNull`은 Kotlin이 바이트코드에 남기지 않아 동작하지 않으므로, DTO `init`에서 막는다(`CreateStopReservationRequest`).
- **정합성은 DB가 보장한다** — 부분 유니크 인덱스 `uq_reservation_active_slot`, 제외 제약 `ex_contract_slot_period`, 상태 전이의 `SELECT ... FOR UPDATE`. 이 조건은 `ReservationStatus.holdsSlot`, `ContractStatus.occupiesSlot`과 같아야 하므로 한쪽을 바꾸면 changelog도 함께 바꾼다.
- **Redis 게이트(`StopHoldGate`)는 부하 차단용** — 몰린 요청 중 1건만 DB로 보낸다. 정합성을 맡기지 않으며 Redis 장애 시 통과시킨다. Redis 연산은 트랜잭션 밖(Facade)에서만 한다.
- **현재 상태의 원본은 `reservation.status`** — `reservation_consultation`은 이력이다. 상태 변경은 `ReservationService`의 전이 메서드로만 하고 두 곳을 같은 트랜잭션에서 쓴다.
- **제약 위반 변환** — Exposed DAO는 쓰기를 커밋까지 미루므로, 제약 위반을 409로 돌려주려면 서비스 안에서 `flushTranslating`으로 먼저 내보낸다.
- **광고면 확장** — 지금은 모든 row가 `StopSlot.DEFAULT_NO`(1)이다. 면이 여러 개가 되면 요청 DTO에서 면 번호를 받아 넘긴다.

검증: 위 동작은 PostgreSQL 기능(부분 유니크 인덱스, 제외 제약, 행 잠금)에 기대므로 목이나 H2로는 확인할 수 없다. `StopHoldPostgresIT`(기본 비활성)가 비어 있는 일회용 PostgreSQL에 Liquibase changelog를 적용하고 실제 서비스를 여러 스레드에서 동시에 호출한다. Spring 컨텍스트 없이 서비스를 직접 만들므로 `@Transactional` 대신 호출마다 Exposed `transaction {}`으로 감싼다.

```bash
docker run -d --rm --name reservation-it-pg -e POSTGRES_PASSWORD=it -p 25433:5432 postgres:18.3
RESERVATION_IT_JDBC_URL=jdbc:postgresql://localhost:25433/postgres \
  ./gradlew :modules:reservation:test --tests "*StopHoldPostgresIT"
docker stop reservation-it-pg
```

접속 계정은 `RESERVATION_IT_DB_USER` / `RESERVATION_IT_DB_PASSWORD`로 바꾼다(기본 postgres / it). 스키마를 만들고 데이터를 쓰므로 개발·운영 DB를 가리키지 않는다.

## Database

- Schema: `reservation`
- ORM: Exposed DAO 패턴
- Migration: Liquibase (`src/main/resources/db/changelog/`)
