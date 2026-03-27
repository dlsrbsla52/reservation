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

## 현재 상태

초기 구조. `ReservationController`, `ReservationServiceHealthCheck`만 존재.

## 확장 시 준수 사항

신규 기능 추가 시 아래 구조를 따른다:

```
modules/reservation/
  controller/
  service/           # 서비스
  entity/            # DateBaseEntity 상속, 정적 팩토리 메서드
    enums/           # BaseEnum 구현
  repository/        # JpaRepository 직접 주입
  guard/
  dto/
    request/
    response/
```

## Database

- Schema: `reservation`
- Migration: Liquibase (`src/main/resources/db/changelog/`)
- stop 서비스의 정류소 데이터가 필요하면 Repository 직접 참조 금지 → stop-service API 경유