# Docker Compose 단일 서버 배포 가이드

이 문서는 EC2 같은 단일 서버에서 PostgreSQL, Valkey, MSA 애플리케이션, Next.js 프론트엔드, Loki, Alloy, Grafana를 모두 Docker Compose로 실행하는 기준을 정리한다.

## 구성 개요

운영 배포의 기본 파일은 루트의 `docker-compose.yml`이다. 모든 서비스는 같은 Docker 네트워크 안에서 통신하고, DB/Valkey/하위 백엔드 서비스는 외부에 직접 노출하지 않는다.

| 컴포넌트 | 서비스명 | 외부 노출 | 용도 |
|----------|----------|-----------|------|
| PostgreSQL 18.3 | `database` | 없음 | 모듈별 schema를 사용하는 주 데이터베이스 |
| Valkey 8.1.6 | `valkey` | 없음 | Redis 호환 캐시/세션 저장소 |
| Gateway | `gateway-service` | `${GATEWAY_PORT:-8080}` | 외부 API 단일 진입점 |
| Frontend | `frontend` | `3000` 또는 `80/443` | Next.js 웹 애플리케이션. 프론트 repo가 준비되면 추가 |
| IAM | `iam-service` | 없음 | 인증, 토큰, 회원 관리 |
| Stop | `stop-service` | 없음 | 정류소 도메인 |
| Reservation | `reservation-service` | 없음 | 예약/계약 도메인 |
| Loki | `loki` | 없음 | 로그 저장소 |
| Alloy | `alloy` | 없음 | Docker 컨테이너 stdout 로그 수집 |
| Grafana | `grafana` | `${GRAFANA_PORT:-3300}` | 로그 조회 UI |

## Compose 파일 역할

| 파일 | 용도 |
|------|------|
| `docker-compose.yml` | 단일 서버 운영 배포용. DB/Valkey/하위 서비스는 내부망 전용, Gateway/Grafana만 공개 |
| `docker-compose-local.yml` | 로컬 전체 스택 실행용. 운영과 같은 토폴로지지만 DB/Valkey/서비스/디버그 포트를 호스트에 노출 |
| `docker-compose-dev.yml` | 로컬 하이브리드 개발 override. Gateway 라우팅을 `host.docker.internal`의 직접 실행 앱으로 전환 |

## 운영 환경 변수

현재 compose는 작은 서비스의 초기 배포를 빠르게 시작할 수 있도록 기본값을 제공한다. 서버에서는 반드시 `.env` 파일 또는 배포 파이프라인 환경 변수로 운영값을 덮어쓴다.

```env
POSTGRES_PASSWORD=change-me
JWT_SECRET=change-me-at-least-32-bytes
CORS_ALLOWED_ORIGIN=http://<server-host>:3000
GRAFANA_PASSWORD=change-me
```

선택 환경 변수:

```env
POSTGRES_USER=postgres
POSTGRES_DB=default
GATEWAY_PORT=8080
GRAFANA_PORT=3300
GRAFANA_USER=admin
GRAFANA_ROOT_URL=https://grafana.example.com
COOKIE_SECURE=true
```

`JWT_SECRET`은 HS256 서명에 사용되므로 반드시 32바이트 이상으로 설정한다. HTTPS가 적용되기 전까지는 `COOKIE_SECURE=false`를 사용하고, 도메인과 TLS를 붙인 뒤 `COOKIE_SECURE=true`로 전환한다.

## 프론트엔드 배포 기준

초기에는 Next.js 프론트엔드와 Gateway를 같은 EC2에서 각각 노출해도 충분하다.

```text
Browser
  -> http://<server-host>:3000  Next.js
  -> http://<server-host>:8080  Gateway API
```

이 구조에서는 브라우저 기준으로 프론트와 API의 origin이 다르므로 `CORS_ALLOWED_ORIGIN`을 프론트 주소로 설정해야 한다.

```env
CORS_ALLOWED_ORIGIN=http://<server-host>:3000
```

프론트 repo가 준비되면 compose에 다음 형태의 서비스를 추가한다.

```yaml
frontend:
  build:
    context: ../frontend-repository
    dockerfile: Dockerfile
  container_name: msa-frontend
  restart: unless-stopped
  ports:
    - "3000:3000"
  environment:
    NEXT_PUBLIC_API_BASE_URL: http://<server-host>:8080
```

서비스가 커지거나 HTTPS 도메인을 붙일 때는 Caddy/Nginx 같은 리버스 프록시를 앞에 두고 `https://example.com`은 프론트로, `/api/**`는 Gateway로 보내는 구성이 더 단순하다. 이때 브라우저 기준 origin이 하나가 되어 CORS 부담이 줄어든다.

## 운영 실행

```bash
docker compose up --build -d
```

특정 서비스만 재빌드:

```bash
docker compose up -d --build --no-deps gateway-service
docker compose up -d --build --no-deps iam-service
```

병렬 빌드:

```bash
DOCKER_BUILDKIT=1 docker compose build --parallel
```

상태 확인:

```bash
docker compose ps
docker compose logs -f gateway-service
```

## 로컬 전체 스택 실행

로컬에서는 기본 민감값과 디버그 포트를 제공하는 `docker-compose-local.yml`을 사용한다.

```bash
docker compose -f docker-compose-local.yml up --build -d
```

주요 로컬 포트:

| 서비스 | 호스트 포트 |
|--------|-------------|
| Gateway | `8080` |
| IAM | `8181` |
| Stop | `8182` |
| Reservation | `8183` |
| PostgreSQL | `15433` |
| Valkey | `6379` |
| Grafana | `3300` |

## 하이브리드 개발

Docker Compose로 인프라와 대부분의 서비스를 띄운 상태에서, 개발 중인 모듈만 컨테이너에서 내리고 IntelliJ 또는 터미널에서 직접 실행할 수 있다.

```bash
docker compose -f docker-compose-local.yml -f docker-compose-dev.yml up -d
docker compose -f docker-compose-local.yml -f docker-compose-dev.yml stop iam-service
```

이때 `docker-compose-dev.yml`은 Gateway의 내부 라우팅을 다음처럼 바꾼다.

| 라우팅 대상 | URL |
|-------------|-----|
| IAM | `http://host.docker.internal:8181` |
| Stop | `http://host.docker.internal:8182` |
| Reservation | `http://host.docker.internal:8183` |

직접 실행하는 모듈은 `local` profile로 실행한다.

## 관측 스택

로그 흐름은 다음과 같다.

```text
애플리케이션 stdout
  -> Docker json-file 로그
  -> Alloy
  -> Loki
  -> Grafana
```

Alloy는 Docker 소켓을 읽어 컨테이너를 자동 발견하고, 컨테이너 stdout 로그를 Loki로 전송한다. Loki 설정은 `observability/loki-config.yml`, Alloy 설정은 `observability/alloy/config.alloy`, Grafana 데이터소스 provisioning은 `observability/grafana/provisioning`에 있다.

Grafana 접속:

```text
http://<server-host>:3300
```

기본 사용자는 `${GRAFANA_USER:-admin}`이고, 비밀번호는 운영에서 반드시 `GRAFANA_PASSWORD`로 주입한다.

## 운영 주의사항

- 서버 보안 그룹 또는 방화벽에서는 기본적으로 `8080`, `3300`만 열고, PostgreSQL/Valkey/하위 서비스 포트는 열지 않는다.
- `3300` Grafana 포트는 가능하면 VPN, 사내 IP, 리버스 프록시 인증 등으로 접근을 제한한다.
- `postgres_data`, `valkey_data`, `loki_data`, `grafana_data`, `alloy_data`는 Docker named volume으로 유지된다. 데이터를 지울 때만 `docker compose down -v`를 사용한다.
- 단일 서버 구성은 단순하고 저렴하지만, 서버 장애가 전체 장애로 이어진다. 운영 중요도가 올라가면 DB 백업, 볼륨 스냅샷, 이미지 태그 고정, 배포 롤백 절차를 별도로 마련한다.
