# observability/ — 관측성 스택 설정

`bus` MSA의 APM/관측성(로그·트레이스·메트릭) 백엔드 설정 모음.
**에이전트리스 OpenTelemetry + Grafana LGTM**(Loki/Tempo/Prometheus/Grafana), 수집은 Alloy 하나로 통합.

| 파일 | 역할 |
|---|---|
| `alloy/config.alloy` | 수집 게이트웨이 — Docker 로그 → Loki, 앱 OTLP → Tempo/Prometheus |
| `tempo/tempo.yml` | 트레이스 백엔드(+service-graph/span 메트릭 → Prometheus) |
| `prometheus/prometheus.yml` | 메트릭 백엔드(remote-write receiver) |
| `loki-config.yml` | 로그 백엔드(single-binary) |
| `grafana/provisioning/datasources/*.yml` | Loki/Tempo/Prometheus 데이터소스 + 상관관계 |

## 빠른 사용
```bash
# 켜기 (전체)
docker compose -f docker-compose-local.yml --profile observability up -d
# 로그만
docker compose -f docker-compose-local.yml up -d loki alloy grafana
# 끄기 (RAM 회수, 데이터 유지)
docker compose -f docker-compose-local.yml stop grafana alloy tempo prometheus loki
```
Grafana: http://localhost:3300 → Explore. (Tempo 3200 / Prometheus 9090 / Alloy OTLP 4317·4318)

> 아키텍처·VT 안전 근거·EC2 사양·단계적 도입 등 상세는 [`docs/infra/APM 및 관측성 스택.md`](../docs/infra/APM%20및%20관측성%20스택.md) 참고.
