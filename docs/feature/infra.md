# 인프라/운영 (Infrastructure) 기능 목록

현재 프로젝트의 인프라·운영 측면에서 보강이 필요한 기능 목록.

---

## 현재 구현 상태

- [x] Docker Compose (로컬/운영 분리)
- [x] Spring Cloud Gateway 라우팅
- [x] Liquibase 스키마 버전 관리
- [x] Valkey(Redis) — Refresh Token, 이메일 인증 토큰 저장
- [x] Virtual Threads 활성화 (IO 바운드 처리)
- [x] S2S RestClient (내부 서비스 통신)
- [ ] 스케줄러 없음 (계약 만료·갱신 자동화 부재)
- [ ] 모니터링/알림 인프라 없음
- [ ] 테스트 커버리지 미비

---

## 추가 기능 목록

### 1. 스케줄러 (우선순위: 높음)

| 기능 | 설명 |
|------|------|
| 계약 만료 처리 | 매일 자정: `contract_end_date` 도달한 `ACTIVE` 계약 → `EXPIRED` 전환 |
| 자동 갱신 실행 | 매일 자정: `auto_renewal=true` + 만료 N일 전 계약 자동 갱신 생성 |
| 갱신 알림 발송 | 만료 30일/7일/1일 전: 갱신 안내 이메일 발송 + `renewal_notified_at` 기록 |
| 연체 처리 | 매일: 납부 기한 초과 `UNPAID` → `OVERDUE` 전환 |
| 공공 API 동기화 | 주기적 서울시 버스 정류소 데이터 갱신 |

> **구현 고려사항:** `@Scheduled` + S2S 토큰(`S2STokenFilter`) 조합, Virtual Thread 기반 비동기 실행

### 2. 테스트 (우선순위: 높음)

| 기능 | 설명 |
|------|------|
| Repository 단위 테스트 | MockK `@Mock` 기반 Exposed Repository 테스트 |
| Service/Facade 단위 테스트 | MockK Stub으로 S2S 클라이언트 격리 |
| API 통합 테스트 | Testcontainers로 PostgreSQL 실제 연결, JWT 인증 포함 E2E |
| JaCoCo 커버리지 | 모듈별 커버리지 리포트 (`./gradlew jacocoTestReport`) |

### 3. 모니터링/관찰 가능성 (우선순위: 중간)

| 기능 | 설명 |
|------|------|
| Spring Boot Actuator | `/actuator/health`, `/actuator/metrics` 엔드포인트 활성화 |
| Micrometer + Prometheus | 요청 수, 레이턴시, JVM 메트릭 수집 |
| 분산 추적 | Micrometer Tracing (Brave/OpenTelemetry) — MSA 간 요청 추적 |
| 구조화 로깅 | MDC 기반 `traceId`, `userId`, `module` 필드 포함 JSON 로그 |
| 로그 수집 | CloudWatch Logs 또는 ELK 스택 연동 |

### 4. AWS 인프라 연동 (우선순위: 중간)

| 기능 | 설명 |
|------|------|
| AWS SES 이메일 발송 | `AuthService` TODO 구현 — Spring Cloud AWS SES |
| AWS Secrets Manager | DB 비밀번호, JWT 시크릿 등 민감 설정 관리 |
| AWS Aurora PostgreSQL | 운영 DB 전환 (로컬: Docker PostgreSQL 18.3 유지) |
| AWS CodeBuild CI/CD | `buildspec-dev.yml`, `buildspec-prod.yml` 파이프라인 검증 |

### 5. API 문서화 (우선순위: 중간)

| 기능 | 설명 |
|------|------|
| SpringDoc OpenAPI | `springdoc-openapi-starter-webmvc-ui` 의존성 추가, Swagger UI 제공 |
| Gateway 통합 문서 | 게이트웨이에서 각 모듈의 OpenAPI 스펙 집계 |
| 요청/응답 예시 | `@Schema` 어노테이션으로 DTO 문서화 |

### 6. 성능/안정성 (우선순위: 낮음)

| 기능 | 설명 |
|------|------|
| Resilience4j Circuit Breaker | S2S 호출 실패 시 서킷 브레이커로 장애 전파 방지 |
| Resilience4j Bulkhead | 모듈별 커넥션 풀 소진 방지 |
| Valkey 캐싱 | 자주 조회되는 정류소 정보, 역할-권한 매핑 캐싱 |
| Exposed 쿼리 성능 튜닝 | N+1 탐지, 인덱스 전략 검토 |
