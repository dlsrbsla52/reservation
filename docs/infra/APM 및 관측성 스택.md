# APM 및 관측성(Observability) 스택

이 문서는 `bus` MSA의 APM/관측성 아키텍처, 도입 배경, 켜고 끄는 법, 단일 EC2 사양·최적화를 정리한다.
로깅 정책은 형제 문서 [`로깅 정책 강화.md`](./로깅%20정책%20강화.md)를 함께 참고한다.

---

## 1. 개요 — 한 줄 결론

**에이전트리스 OpenTelemetry**(Micrometer Observation + OTLP export) 방식으로 트레이스/메트릭을 수집하고,
백엔드는 셀프호스트 **Grafana LGTM**(Loki=로그 / Tempo=트레이스 / Prometheus=메트릭 / Grafana=UI)을 쓴다.
수집 게이트웨이는 **Grafana Alloy** 하나로 통합한다.

```
                                  ┌──────────► Tempo      (트레이스)
  4× Spring Boot ──OTLP(4318)──► Alloy ─────► Prometheus (메트릭, remote_write)
  (gateway/iam/stop/reservation)   │  └──────► (Tempo가 service-graph/span 메트릭도 Prometheus로)
                                   └─Docker log─► Loki    (로그, JSON stdout tail)
                                                   │
                                                   └──► Grafana (localhost:3300) ─ 3개 데이터소스 상관관계
```

---

## 2. 왜 이 방식인가 — VT-pinning 안전성 (핵심 근거)

이 프로젝트는 **Java 25 + Virtual Threads**를 쓴다. APM 선택 시 가장 중요한 기준은 "VT를 carrier 스레드에 pinning하지 않는가"였다.

- **JEP 491 (JDK 24부터, Java 25 포함)**: `synchronized`/`Object.wait()`로 인한 VT pinning이 JVM 레벨에서 제거됐다.
  과거 APM 바이트코드 에이전트가 VT에서 위험했던 주 원인이 사라졌다. **남은 pinning 원인은 native(JNI/FFM) 프레임뿐.**
- 따라서 안전 기준은 **"요청 hot path에 native 계측을 넣지 않는 순수 Java 계측인가"** 로 좁혀진다.
- **에이전트리스(Micrometer Observation)** 는 `-javaagent`를 붙이지 않는다 → 에이전트가 carrier를 pinning할 여지 자체가 없다.
  컨텍스트 전파는 `io.micrometer:context-propagation`(VT·Reactor 인지)이 담당한다. Spring Boot 4.0 공식 권장 방식이기도 하다.

### 배제한 대안
| 대안 | 배제 이유 |
|---|---|
| OTel Java **에이전트**(`-javaagent`) | 과거 VT pinning 이슈 + VT 경계 컨텍스트 유실 이력, 버전매칭 리스크. 여기선 불필요한 복잡성 |
| **Pinpoint** | 무거운 바이트코드 에이전트 + 자체 ThreadLocal 전파, SB4/WebFlux·VT 정합성 미검증, **백엔드에 HBase 필요** → 단일 EC2 부적합 |
| **Scouter** | Java 8세대 설계, VT 미인지 |

> 참고: 로그의 `traceId`/`spanId`는 Micrometer Tracing이 MDC에 주입한다. VT/새 스레드 전파는 `mdcTaskDecorator` + `context-propagation`로 이미 처리된다(pinning과 별개). `OTEL_ENABLED=false`로 트레이싱을 꺼도 `requestId`(MdcLoggingFilter)는 유지되어 로그 상관관계는 가능하다.

---

## 3. 구성 요소 & 파일 위치

| 구성 | 이미지/의존성 | 설정 파일 |
|---|---|---|
| 트레이싱 자동구성(**Boot 4.0 필수**) | `org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry` | `modules/common/build.gradle.kts` |
| OTLP 익스포터(앱) | `io.micrometer:micrometer-tracing-bridge-otel`, `io.opentelemetry:opentelemetry-exporter-otlp`, `io.micrometer:micrometer-registry-otlp` | `modules/common/build.gradle.kts` |
| 앱 트레이싱/메트릭 설정 | (위 의존성) | 각 모듈 `application.yml`의 `management.tracing` / `management.opentelemetry.tracing` / `management.otlp.metrics` |
| Alloy(수집) | `grafana/alloy` | `observability/alloy/config.alloy` |
| Tempo(트레이스) | `grafana/tempo` | `observability/tempo/tempo.yml` |
| Prometheus(메트릭) | `prom/prometheus` | `observability/prometheus/prometheus.yml` |
| Loki(로그) | `grafana/loki` | `observability/loki-config.yml` |
| Grafana(UI) | `grafana/grafana` | `observability/grafana/provisioning/datasources/*.yml` |

버전은 앱 OTLP 의존성의 경우 **Spring Boot 4.0 BOM**(OpenTelemetry BOM 포함)이 관리하므로 명시하지 않는다.

> ⚠️ **Spring Boot 4.0 함정**: actuator 자동구성이 모듈로 분리됐다. `micrometer-tracing-bridge-otel`만 있으면 관측(observation)/메트릭은 되지만 **Tracer가 생성되지 않아 스팬이 안 만들어진다**(로그 `traceId`가 빈값). 트레이싱 OTLP 자동구성(`OpenTelemetryTracingAutoConfiguration`, `OtlpTracingAutoConfiguration`)을 제공하는 **`spring-boot-micrometer-tracing-opentelemetry` 모듈이 반드시 필요**하다.

### 포트 맵 (로컬)
| 서비스 | 호스트 포트 |
|---|---|
| Grafana | `3300` |
| Prometheus | `9090` |
| Tempo (쿼리 API) | `3200` |
| Alloy OTLP | `4317`(gRPC) / `4318`(HTTP) |
| Loki | (호스트 미노출, Grafana가 프록시) |
| gateway / iam / stop / reservation | `8080 / 8181 / 8182 / 8183` |

---

## 4. 앱 설정 키

각 서비스 `application.yml`:
```yaml
spring:
  application:
    name: iam-service   # OTel service.name + 로그 service 라벨 (compose 서비스명과 일치)
management:
  tracing:
    enabled: ${OTEL_ENABLED:true}                 # 트레이싱 on/off
    sampling:
      probability: ${OTLP_TRACE_SAMPLING:1.0}     # 운영 0.1 권장
  opentelemetry:                                  # ⚠️ Boot 4.0: 트레이스 OTLP 엔드포인트 경로 변경
    tracing:
      export:
        otlp:
          endpoint: ${OTLP_TRACES_ENDPOINT:http://localhost:4318/v1/traces}
  otlp:
    metrics:                                      # 메트릭은 Boot 3.x와 동일 경로
      export:
        enabled: ${OTEL_ENABLED:true}
        url: ${OTLP_METRICS_ENDPOINT:http://localhost:4318/v1/metrics}
        step: 30s
```

> 속성 경로 주의: 트레이스는 **`management.opentelemetry.tracing.export.otlp.endpoint`**(Boot 4.0 신규), 메트릭은 **`management.otlp.metrics.export.url`**(3.x와 동일). Boot 3.x의 `management.otlp.tracing.endpoint`는 4.0에서 동작하지 않는다.

주요 환경변수:
| 변수 | 기본값(로컬/운영) | 용도 |
|---|---|---|
| `OTEL_ENABLED` | `true` | 앱의 트레이스·메트릭 export 전체 스위치 |
| `OTLP_TRACE_SAMPLING` | `1.0` / `0.1` | 트레이스 샘플링 비율 |
| `OTLP_TRACES_ENDPOINT` | `http://alloy:4318/v1/traces` | 트레이스 수집 대상 |
| `OTLP_METRICS_ENDPOINT` | `http://alloy:4318/v1/metrics` | 메트릭 수집 대상 |

---

## 5. 켜고 끄기 (프로파일 토글)

관측성 5종은 docker-compose `profiles: ["observability"]` 로 **opt-in**이다. 기본 `up`에서는 뜨지 않는다.

```bash
# 전체 켜기 (트레이스+메트릭+로그)
docker compose -f docker-compose-local.yml --profile observability up -d

# 로그만 (Phase 0) — tempo/prometheus를 끌어오지 않음
docker compose -f docker-compose-local.yml up -d loki alloy grafana

# 끄기 — 컨테이너 정지로 RAM 회수 (볼륨/데이터 유지)
docker compose -f docker-compose-local.yml stop grafana alloy tempo prometheus loki

# 완전 제거 (데이터 볼륨까지: -v)
docker compose -f docker-compose-local.yml --profile observability down
```

> 운영 compose(`docker-compose.yml`)도 동일한 프로파일/명령을 쓴다. `--profile observability` 없이 `up`하면 앱+DB만 뜬다.

### 단계적 도입 권장
- **Phase 0 — 로그**(Loki+Alloy+Grafana): 가성비 최고. 구조화 로그 + 검색 + requestId 상관관계.
- **Phase 1 — 트레이스**(+Tempo): 서비스 간 지연 디버깅이 필요할 때.
- **Phase 2 — 메트릭**(+Prometheus): 대시보드/알람 + APM 서비스 그래프가 필요할 때.

앱은 코드 변경 없이 **어느 백엔드를 켜느냐 + `OTEL_ENABLED`** 로만 단계를 오간다.

---

## 6. 로컬에서 보기

1. 관측성 스택 기동: `docker compose -f docker-compose-local.yml --profile observability up -d`
2. 앱 기동(도커 또는 IntelliJ). 호스트 실행 시 Alloy가 `localhost:4318`로 노출돼 있어 그대로 OTLP push 가능.
3. API 호출로 트래픽 발생(게이트웨이 `http://localhost:8080` 경유).
4. **Grafana 접속**: http://localhost:3300 → 좌측 **Explore**
   - **Tempo**: 트레이스 검색 / TraceID 조회. 스팬 클릭 → 관련 로그(Loki)로 이동.
   - **Prometheus**: `traces_spanmetrics_calls_total`, `http_server_requests_seconds_*` 등 PromQL. Service Graph 확인.
   - **Loki**: `{service="iam-service"} | json` 로 구조화 로그. 로그의 traceId → Tempo로 점프.

> Grafana 최초 로그인은 compose의 `GRAFANA_USER/PASSWORD`(기본 admin/admin). 기존 볼륨의 비번을 잊었다면:
> `docker exec -it msa-grafana grafana cli admin reset-admin-password <새비번>`

---

## 7. 단일 EC2 사양 & 최적화

Next.js까지 한 대에 올릴 때의 RAM 추정과 컨테이너 상한(`deploy.resources.limits.memory`, 운영 compose 적용):

| 구성 | mem_limit | 비고 |
|---|---|---|
| Spring Boot ×4 | 각 768M | `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` → heap ~560M |
| PostgreSQL | 1024M | **운영은 Aurora 권장(오프로드)** |
| Valkey | 256M | 운영은 ElastiCache 권장 |
| Tempo / Prometheus | 각 768M | 성장분 캡 |
| Loki / Alloy / Grafana | 각 512M | |

- **상한 합계**: 풀 스택 ≈ 7.25 GB / 관측성 OFF(lean) ≈ 4.25 GB
- **인스턴스 추천**: 관측성 상시 ON = **16 GB**(Graviton `m7g.xlarge`/`r7g.large`), lean = **8 GB**(`t4g.large`). 모든 이미지·런타임 arm64 지원 → Graviton 권장.

### 최적화 체크리스트
- **상태ful 오프로드**(Aurora/ElastiCache) — 효과 1순위. 프로드 EC2에서 PG/Valkey 제거 → ~1–1.5 GB 절감.
- **컨테이너 mem_limit + JVM MaxRAMPercentage** — 호스트 OOM 방지(적용됨).
- **Hikari 풀 20→10**(`DB_POOL_SIZE`, 적용됨) — 커넥션/메모리 절약.
- **보존기간**: Prometheus 7d, Tempo 7d, Loki 7d(적용됨).
- **트레이스 샘플링** 운영 10%(적용됨).
- 초기엔 **Tempo `metrics_generator` OFF** 가능(서비스 그래프 불필요 시 메모리 절약).
- **스왑 2~4 GB** + EBS gp3(관측성 백엔드 쓰기).

---

## 8. 운영 주의사항
- Actuator 엔드포인트(`health, loggers, metrics`)는 방화벽/리버스프록시로 보호. host publish 서비스 주의.
- 프로드 트레이스는 샘플링 필수(성능/비용). `traceparent` 헤더로 게이트웨이→하위 서비스 분산 트레이스가 자동 연결된다.
- 관측성 백엔드가 꺼져 있어도 앱은 정상 동작한다(OTLP export는 best-effort). 완전 무소음을 원하면 `OTEL_ENABLED=false`.

---

## 9. Fargate/ECS 이전 체크리스트

각 서비스를 별도 서버(ECS Fargate)로 분리할 때의 이식성 가이드. **핵심 결론: 앱 계측(에이전트리스 OTLP)은 그대로 이식되고, 인프라 배관(수집·백엔드·디스커버리)만 Fargate식으로 교체하면 된다.** 이게 에이전트리스 OTLP를 선택한 이식성상의 이유다.

### 이식성 감사 — 무엇이 살고 무엇이 깨지나

| 요소 | Fargate | 이유 / 대응 |
|---|---|---|
| 앱 OTLP export (트레이스·메트릭) | ✅ 그대로 | `OTLP_*_ENDPOINT` env만 변경, 재빌드 불필요. VT-safe 유지 |
| `traceparent` 분산 전파 | ✅ 그대로 | HTTP 헤더 기반 → 토폴로지 무관 |
| JSON 구조화 로그(stdout) | ✅ 그대로 | 수집 경로만 교체(아래) |
| **Alloy의 Docker 소켓 로그 수집** | ❌ 불가 | Fargate는 `/var/run/docker.sock` 접근 불가 → **FireLens(Fluent Bit)** 또는 **awslogs** 드라이버로 대체 |
| **서비스명 DNS**(`alloy:4318`, `iam-service:8181`) | ❌ 불가 | awsvpc 모드 → **Cloud Map 서비스 디스커버리** / **사이드카(localhost)** / ALB |
| **로컬 볼륨**(tempo/prometheus/loki/pg_data) | ⚠️ 영속성 소실 | 태스크 재시작 시 소실 → **EFS**(TSDB엔 느림) 또는 **S3 백엔드 / 관리형** |
| `deploy.resources.limits` | ⚠️ 부분 | ECS 태스크 CPU/메모리 discrete 조합으로 매핑 |
| `observability` 프로파일 토글 | ⚠️ 개념만 | ECS는 서비스 배포/미배포 또는 `desiredCount=0`으로 토글 |
| PG / Valkey 컨테이너 | 권장 제거 | Fargate에 stateful 지양 → **Aurora / ElastiCache** |

### 권장 아키텍처

- **수집(핵심)**: OTel Collector(**ADOT**)나 Alloy를 **각 태스크의 사이드카**로 배치 → 앱은 `localhost:4318`로 push(awsvpc에서 태스크 내 컨테이너는 네트워크 네임스페이스 공유 → 서비스 디스커버리 불필요). 사이드카가 백엔드로 팬아웃.
- **트레이스**: Tempo(ECS + **S3 백엔드**) 또는 **AWS X-Ray**
- **메트릭**: **Amazon Managed Prometheus(AMP)** remote-write (현재 remote_write 구조 유지, 인증만 SigV4 추가)
- **로그**: **FireLens(Fluent Bit) → Loki(S3)** 또는 **CloudWatch Logs**
- **UI**: **Amazon Managed Grafana** 또는 Grafana on ECS
- **가장 단순**: 전면 관리형(**AMP + Managed Grafana + X-Ray/ADOT**) → Fargate에 stateful 컨테이너 0

### 이미 이식성이 확보된 부분 ✓
- OTLP 엔드포인트가 이미 **env 주입식**(`OTLP_TRACES_ENDPOINT`/`OTLP_METRICS_ENDPOINT`), 앱 **기본값이 `localhost:4318`** → **Fargate 사이드카 패턴에 즉시 적합**(env 미설정 시 기본값 사용)
- 로그가 이미 **JSON stdout** → FireLens/CloudWatch에 그대로 태움
- 서비스 간 URL도 이미 env(`IAM_SERVICE_URL` 등) → Cloud Map/ALB 엔드포인트로 교체만

> 요약: "단일 EC2 → Fargate" 이전 시 **앱 이미지·코드는 손대지 않고**, ① 로그 수집(FireLens), ② 수집기(사이드카 ADOT/Alloy), ③ 백엔드(S3/관리형), ④ 디스커버리(Cloud Map) 4가지 배관만 교체한다.
