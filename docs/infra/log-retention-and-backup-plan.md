# 로그 보존 및 백업 계획

이 문서는 단일 EC2 Docker Compose 운영 기준에서 애플리케이션 로그를 어떻게 보존하고, 향후 S3 장기 보관을 어떻게 붙일지 정리한다. DB 백업과 달리 로그는 서비스 복구의 필수 데이터가 아니므로, 초기에는 디스크 보호와 조회 가능 기간을 우선한다.

## 현재 로그 흐름

현재 로그 파이프라인은 다음 구조다.

```text
애플리케이션 stdout
  -> Docker json-file 로그
  -> Alloy
  -> Loki
  -> Grafana
```

역할은 다음과 같다.

| 컴포넌트 | 역할 |
|----------|------|
| Docker json-file | 컨테이너 stdout 로그의 단기 로컬 버퍼 |
| Alloy | Docker 컨테이너 로그 수집 및 Loki 전송 |
| Loki | 로컬 디스크 기반 로그 저장소 |
| Grafana | 로그 조회 UI |

## 현재 보존 정책

운영 compose의 Docker 로그는 컨테이너별로 다음 제한을 둔다.

```yaml
logging:
  driver: json-file
  options:
    max-size: "50m"
    max-file: "5"
```

컨테이너 1개당 최대 약 250MB까지만 Docker 로그 파일을 보관한다. 이 로그는 장기 보관 목적이 아니라 Alloy가 Loki로 전송하기 전후의 단기 버퍼다.

Loki는 `observability/loki-config.yml` 기준으로 7일 보존한다.

```yaml
limits_config:
  retention_period: 168h

compactor:
  retention_enabled: true
```

## 초기 운영 정책

AWS 계정과 S3 버킷이 준비되기 전까지는 로그를 별도로 백업하지 않는다.

초기 기준:

| 항목 | 기준 |
|------|------|
| 실시간 조회 | Grafana + Loki |
| 로컬 로그 보존 | Loki 7일 |
| Docker 로그 보존 | 컨테이너당 50MB x 5 |
| 장기 보관 | 없음 |
| 우선순위 | 디스크 보호 |

트래픽이 적고 admin 중심으로 시작하는 단계에서는 이 구성이 충분하다. 로그를 무기한 보관하려고 하기보다, 디스크가 차서 서비스가 죽지 않도록 제한하는 것이 더 중요하다.

## S3 장기 보관 방향

AWS 계정, S3 버킷, IAM Role이 준비되면 로그 장기 보관을 추가할 수 있다. 다만 모든 로그를 S3에 무조건 보관하지 않고, 필요한 로그만 선별한다.

추천 정책:

| 로그 종류 | S3 장기 보관 여부 | 이유 |
|-----------|-------------------|------|
| 일반 INFO 요청 로그 | 기본 미보관 | 비용과 저장량 대비 가치가 낮음 |
| ERROR 로그 | 보관 권장 | 장애 분석과 운영 이력에 필요 |
| 보안/인증 실패 로그 | 보관 권장 | 계정 공격, 권한 문제 추적에 필요 |
| 결제/예약/계약 감사 로그 | 별도 감사 로그로 보관 권장 | 비즈니스 증빙 가능성이 있음 |
| 디버그 로그 | 미보관 | 운영에서 장기 보관 가치가 낮음 |

## 권장 S3 저장 구조

향후 S3 보관을 붙이면 날짜와 서비스 기준으로 prefix를 나눈다.

```text
s3://<bucket-name>/logs/application/year=2026/month=06/day=12/service=gateway/
s3://<bucket-name>/logs/application/year=2026/month=06/day=12/service=iam/
s3://<bucket-name>/logs/audit/year=2026/month=06/day=12/
```

장기 보관 대상은 gzip 압축된 JSON Lines 형식을 권장한다.

```text
gateway_2026-06-12_00.jsonl.gz
iam_2026-06-12_00.jsonl.gz
```

## 구현 후보

### 1. Loki 로컬 보존 + 주기적 export

초기 확장안으로 가장 단순하다.

```text
cron 또는 log-export 컨테이너
  -> Loki Query API로 ERROR/보안 로그 조회
  -> jsonl.gz 파일 생성
  -> S3 업로드
```

장점:

- 현재 구조를 크게 바꾸지 않는다.
- 필요한 로그만 선별해 S3 비용을 줄일 수 있다.
- S3 연동 실패가 애플리케이션 로그 수집 경로를 막지 않는다.

단점:

- export 스크립트와 중복 방지 로직이 필요하다.
- Loki 보존 기간 내에 export가 반드시 성공해야 한다.

### 2. Alloy에서 S3 또는 외부 저장소로 이중 전송

Alloy에서 Loki 외에 다른 저장소로도 전송하는 방식이다.

장점:

- 수집 시점에 바로 장기 저장소로 보낼 수 있다.

단점:

- 설정 복잡도가 올라간다.
- S3 직접 저장보다는 중간 저장소나 별도 파이프라인이 필요할 수 있다.
- 초기 서비스 규모에는 과하다.

### 3. 애플리케이션에서 감사 로그를 별도 테이블/S3로 기록

예약, 계약, 회원 권한 변경처럼 증빙이 필요한 이벤트는 일반 로그와 분리하는 것이 좋다.

장점:

- 비즈니스 감사 로그를 일반 운영 로그와 분리할 수 있다.
- 보존 기간과 조회 요구사항을 별도로 설계할 수 있다.

단점:

- 애플리케이션 레벨의 이벤트 모델링이 필요하다.

## 추천 단계

현재 프로젝트에는 다음 순서가 적합하다.

1. 지금은 Docker log rotation + Loki 7일 보존으로 운영한다.
2. AWS 계정과 S3 버킷을 만든 뒤 DB 백업을 먼저 붙인다.
3. 그 다음 ERROR/보안 로그만 Loki에서 S3로 export하는 `log-export` 작업을 추가한다.
4. 예약/계약/권한 변경 같은 핵심 이벤트는 별도 감사 로그 설계를 검토한다.

## 운영 모니터링 기준

단일 서버에서는 로그 자체보다 디스크 사용량 모니터링이 더 중요하다.

확인 대상:

```bash
docker system df
docker volume ls
df -h
du -sh /var/lib/docker
```

알림 기준 초안:

| 항목 | 경고 | 위험 |
|------|------|------|
| 루트 디스크 사용률 | 70% | 85% |
| Loki volume 증가 | 평소 대비 급증 | 7일 보존인데도 계속 증가 |
| Docker log 사용량 | 수 GB 이상 | rotation 미적용 의심 |

## S3 Lifecycle 초안

S3 장기 보관을 붙인 뒤에는 Lifecycle로 비용을 제한한다.

| 대상 | 보관 정책 |
|------|-----------|
| ERROR 로그 | 90일 후 Glacier Instant Retrieval 또는 삭제 |
| 보안/인증 실패 로그 | 180일 보관 |
| 감사 로그 | 1년 이상 보관 검토 |
| 일반 INFO 로그 | 원칙적으로 S3 미보관 |

## 주의사항

- requestId, traceId 같은 고유값은 Loki label로 올리지 않는다. 현재 Alloy 설정처럼 JSON 본문으로 남기고 쿼리 시 파싱한다.
- 운영에서 DEBUG 로그를 장시간 켜두지 않는다.
- 개인정보, 토큰, 쿠키, Authorization 헤더는 로그에 남기지 않는다.
- 로그 백업은 DB 백업보다 우선순위가 낮다. S3 연동은 DB 백업이 먼저 검증된 뒤 진행한다.
