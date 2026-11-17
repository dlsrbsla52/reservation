# Docker Compose 단일 서버 배포 가이드

이 문서는 EC2 같은 단일 서버에서 PostgreSQL, Valkey와 모듈러 모놀리스 애플리케이션(`app`) 하나를 Docker Compose로 실행하는 기준을 정리한다.
(2026-11 MSA 4개 서비스 구성에서 전환했다. 배경은 `docs/modular-monolith-migration-plan.md`.)

## 구성 개요

운영 배포의 기본 파일은 루트의 `docker-compose.yml`이다. DB/Valkey는 외부에 직접 노출하지 않고, 외부에는 `app`의 8080만 연다.

| 컴포넌트 | 서비스명 | 외부 노출 | 용도 |
|----------|----------|-----------|------|
| PostgreSQL 18.3 | `database` | 없음 | 모듈별 schema(`auth`, `audit`, `stop`, `reservation`)를 쓰는 주 데이터베이스 |
| Valkey 8.1.6 | `valkey` | 없음 | Redis 호환 캐시/세션 저장소 |
| App | `app` | `${APP_PORT:-8080}` | iam·stop·reservation을 한 JVM에서 실행. 인증·라우트 허용 목록은 앱의 보안 필터가 맡는다 |

Actuator는 컨테이너 안 `127.0.0.1:8081`에만 열린다. compose healthcheck가 이 포트를 쓴다. 공개 8080에서 `/actuator/**`는 404다.

## Compose 파일 역할

| 파일 | 용도 |
|------|------|
| `docker-compose.yml` | 단일 서버 운영 배포용. DB/Valkey는 내부망 전용, app 8080만 공개. `mem_limit: 1g`, `-Xmx640m` |
| `docker-compose-local.yml` | 로컬 전체 스택 실행용. 운영과 같은 토폴로지지만 DB/Valkey/디버그 포트를 호스트에 노출, `local` 프로필 |

## 운영 환경 변수

비밀값은 프로젝트 루트의 `.env`에서 읽는다. `.env`는 `.gitignore`·`.dockerignore` 대상이라 커밋되지도, 빌드 컨텍스트에 실리지도 않는다. `.env.example`을 복사해 만든다.

- `JWT_SECRET`은 기본값이 없다. 없으면 `docker compose`가 기동을 거부한다(`${JWT_SECRET:?...}`).
- `SEOUL_API_KEY`는 선택이다. 비우면 정류소 일괄 등록만 실패한다.
- 운영에서는 서버의 `.env` 대신 배포 파이프라인 환경변수(AWS Secrets Manager 연동)를 권장한다. 환경변수가 `.env`보다 우선한다.

```env
JWT_SECRET=<openssl rand -base64 48 로 생성, 로컬과 다른 값>
SEOUL_API_KEY=<서울 열린데이터광장 키>
POSTGRES_PASSWORD=change-me
CORS_ALLOWED_ORIGIN=http://<server-host>:3000
```

선택 환경 변수:

```env
POSTGRES_USER=postgres
POSTGRES_DB=default
APP_PORT=8080
COOKIE_SECURE=true
CORS_ADMIN_ALLOWED_ORIGIN=https://admin.example.com
```

`JWT_SECRET`은 HS256 서명에 사용되므로 반드시 32바이트 이상으로 설정한다. HTTPS가 적용되기 전까지는 `COOKIE_SECURE=false`를 사용하고, 도메인과 TLS를 붙인 뒤 `COOKIE_SECURE=true`로 전환한다.

## 메모리와 복구

- 힙 상한은 `mem_limit`과 `-Xmx`를 함께 줘야 보장된다. 제한 없이 `MaxRAMPercentage`만 주면 호스트 메모리 기준으로 잡힌다(4GB 호스트에서 60%면 약 2.4GB).
- 시작점은 `mem_limit: 1g` / `-Xmx640m`이다. 실제 상한은 실행 중인 프로세스에서 확인한다: `docker compose exec app jcmd 1 VM.flags -all | grep MaxHeapSize`. 유휴·일반 요청·정류소 일괄 등록 중 RSS를 측정해 조정한다.
- `restart: unless-stopped`는 **프로세스가 종료됐을 때만** 재시작한다. healthcheck가 `unhealthy`여도 프로세스가 살아 있으면 재시작하지 않으므로, 응답 불능은 외부 감시(업타임 모니터 + 알림)로 대응한다.

## 프론트엔드 별도 확장 기준

현재 Compose에는 프론트엔드가 없다. 향후 Next.js 프론트엔드를 추가할 경우에는 app과 같은 EC2에서 각각 노출할 수 있다.

```text
Browser
  -> http://<server-host>:3000  Next.js
  -> http://<server-host>:8080  App API
```

이 구조에서는 브라우저 기준으로 프론트와 API의 origin이 다르므로 `CORS_ALLOWED_ORIGIN`을 프론트 주소로 설정해야 한다.

HTTPS 도메인을 붙일 때는 Caddy/Nginx 같은 리버스 프록시를 앞에 두고 `https://example.com`은 프론트로, `/api/**`는 app으로 보내는 구성이 더 단순하다. 이때 다음을 함께 정한다.

- app 8080은 호스트에 공개하지 않고(`ports` 대신 `expose`, 또는 `127.0.0.1:8080`에만 공개) 프록시만 접근하게 한다.
- 클라이언트 IP를 `X-Forwarded-For`로 받으려면 `server.forward-headers-strategy: native`와 `server.tomcat.remoteip.internal-proxies`를 **프록시 주소 하나로** 좁혀 설정한다. 기본값은 사설 대역 전체(Docker 브리지 포함)를 믿는다. 지금 앱은 이 설정이 없어 `X-Forwarded-For`를 신뢰하지 않는다.

## 운영 실행

```bash
docker compose up --build -d
docker compose up -d --build --no-deps app   # 앱만 재빌드
docker compose ps
docker compose logs -f --tail=200 app
```

## 로컬 실행

전체 스택:

```bash
docker compose -f docker-compose-local.yml up --build -d
docker compose -f docker-compose-local.yml up -d --build --no-deps app
```

앱을 IntelliJ나 터미널에서 직접 실행할 때는 인프라만 올린다.

```bash
docker compose -f docker-compose-local.yml up -d database valkey
./gradlew :modules:app:bootRun --args='--spring.profiles.active=local'
```

주요 로컬 포트:

| 서비스 | 호스트 포트 |
|--------|-------------|
| App | `8080` (Swagger UI: `/swagger-ui.html`, local 프로필만) |
| App 디버그(JDWP) | `18080` |
| PostgreSQL | `15433` |
| Valkey | `6379` |

## 로그 확인

로그는 애플리케이션 stdout → Docker json-file 로그로만 남긴다. 모든 프로파일이 사람이 읽기 쉬운 패턴 로그를 출력한다.
로그 줄의 `[user:...]`는 인증 필터가 토큰을 검증한 뒤 넣은 회원 ID다(클라이언트 헤더로는 바뀌지 않는다).

## 운영 주의사항

- 서버 보안 그룹 또는 방화벽에서는 기본적으로 `8080`만 열고, PostgreSQL/Valkey 포트는 열지 않는다.
- `postgres_data`, `valkey_data`는 Docker named volume으로 유지된다. compose 프로젝트 이름을 바꾸면 새 빈 볼륨이 생기니 바꾸지 않는다. 데이터를 지울 때만 `docker compose down -v`를 사용한다.
- 단일 서버·단일 JVM 구성은 단순하고 저렴하지만, 서버나 JVM 장애가 전체 장애로 이어진다. 운영 중요도가 올라가면 DB 백업(`docs/infra/postgres-backup-plan.md`), 볼륨 스냅샷, 이미지 태그 고정, 배포 롤백 절차를 마련한다.
