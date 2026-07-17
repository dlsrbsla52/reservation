# 로그 보존 및 백업 계획

이 문서는 단일 EC2 Docker Compose 운영에서 애플리케이션 로그를 단순하고 안전하게 보존하는 기준을 정리한다. 현재는 Loki·Alloy·Grafana 등 별도 로그 스택을 운영하지 않는다.

## 현재 로그 흐름

```text
애플리케이션 Pattern stdout
  -> Docker json-file 로그
  -> Docker 로테이션
```

운영 Compose는 컨테이너당 `50m x 5`, 로컬 Compose는 `20m x 3`으로 제한한다. 로그는 다음처럼 조회한다.

```bash
docker compose logs -f --tail=200 gateway-service
docker compose logs -f --tail=200 iam-service
docker compose logs --since=30m stop-service reservation-service
```

## 초기 운영 정책

| 항목 | 기준 |
|------|------|
| 실시간 조회 | `docker compose logs` |
| 운영 로그 보존 | 컨테이너당 최대 약 250MB |
| 로컬 로그 보존 | 컨테이너당 최대 약 60MB |
| 장기 보관 | 없음 |
| 우선순위 | 디스크 고갈 방지 |

로그는 서비스 복구의 필수 데이터가 아니므로 DB 백업보다 우선순위를 낮게 둔다. 장애 전후 로그를 보존해야 할 때는 `docker compose logs --since ...` 출력을 별도 파일로 보관한다.

## 향후 외부 보관

트래픽과 장애 분석 요구가 커지면 단일 EC2에 검색 스택을 올리기보다 CloudWatch Logs 또는 S3 등 관리형 저장소를 우선 검토한다. ERROR, 보안/인증 실패, 감사 이벤트만 선별 보관해 비용과 개인정보 노출을 제한한다.

## 운영 점검

```bash
docker system df
docker inspect --format='{{json .HostConfig.LogConfig}}' msa-gateway
df -h
```

- 운영에서 DEBUG 로그를 장시간 유지하지 않는다.
- 개인정보, 토큰, 쿠키, `Authorization` 헤더를 로그에 남기지 않는다.
- 루트 디스크 70%에서 경고, 85%에서 즉시 조치한다.
