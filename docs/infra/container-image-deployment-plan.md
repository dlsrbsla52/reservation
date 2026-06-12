# 컨테이너 이미지 빌드 및 배포 계획

이 문서는 단일 EC2 Docker Compose 운영에서 서버가 직접 이미지를 빌드하지 않고, CI 또는 로컬에서 이미지를 만들어 registry에 push한 뒤 EC2에서는 pull/up만 수행하는 배포 전략을 정리한다.

## 목표

운영 서버는 빌드 서버가 아니라 실행 서버로 사용한다.

```text
개발자 push
  -> CI에서 Docker image build
  -> Container Registry에 image push
  -> EC2에서 image pull
  -> docker compose up -d
```

이 방식의 목적은 다음과 같다.

- EC2의 CPU/RAM을 Gradle, Next.js 빌드에 쓰지 않는다.
- `t4g.large` 같은 2 vCPU / 8GB 서버에서도 운영 안정성을 확보한다.
- 배포 대상 이미지를 tag로 고정해 롤백 가능성을 높인다.
- 로컬/개발 compose와 운영 compose의 책임을 분리한다.

## 권장 Registry

AWS를 운영 기준으로 잡는다면 Amazon ECR을 사용한다.

서비스별 repository를 분리한다.

```text
reservation-gateway
reservation-iam
reservation-stop
reservation-reservation
reservation-frontend
```

이미지 태그는 최소 2개를 같이 붙인다.

| 태그 | 용도 |
|------|------|
| `<git-sha>` | 실제 배포와 롤백 기준 |
| `latest` | 수동 확인 또는 개발 편의 |

운영 배포에는 `latest`보다 `<git-sha>`를 우선 사용한다.

## 현재 백엔드 빌드 방식

현재 루트 `Dockerfile`은 `MODULE_NAME` build arg로 각 모듈을 빌드한다.

예시:

```bash
docker build \
  --build-arg MODULE_NAME=gateway \
  -t <registry>/reservation-gateway:<git-sha> \
  -t <registry>/reservation-gateway:latest \
  .
```

각 모듈별 build arg:

| 서비스 | `MODULE_NAME` |
|--------|---------------|
| Gateway | `gateway` |
| IAM | `iam` |
| Stop | `stop` |
| Reservation | `reservation` |

## ARM64 기준

초기 EC2 후보인 `t4g.large`는 AWS Graviton 기반 ARM64 인스턴스다. 따라서 운영 이미지는 `linux/arm64`로 빌드해야 한다.

M1/M2 Mac에서 로컬 빌드하면 기본적으로 ARM64 이미지가 만들어진다. x86 기반 CI runner를 사용하면 `docker buildx`로 platform을 명시한다.

```bash
docker buildx build \
  --platform linux/arm64 \
  --build-arg MODULE_NAME=gateway \
  -t <registry>/reservation-gateway:<git-sha> \
  -t <registry>/reservation-gateway:latest \
  --push \
  .
```

나중에 x86 서버와 ARM 서버를 모두 지원해야 하면 `linux/amd64,linux/arm64` multi-arch 이미지로 전환한다.

## 운영 Compose 방향

로컬 compose는 `build:`를 유지한다. 운영 compose는 `build:` 대신 `image:`만 사용한다.

예시:

```yaml
gateway-service:
  image: <account-id>.dkr.ecr.ap-northeast-2.amazonaws.com/reservation-gateway:${IMAGE_TAG}
  container_name: msa-gateway
  restart: unless-stopped
  ports:
    - "${GATEWAY_PORT:-8080}:8080"
```

운영용 compose 파일은 다음 중 하나로 분리한다.

| 파일 | 역할 |
|------|------|
| `docker-compose.yml` | 현재 단일 서버 실행 기준 |
| `docker-compose-local.yml` | 로컬 build 기반 전체 스택 |
| `docker-compose-prod.yml` | 향후 ECR image 기반 운영 스택 |

초기에는 `docker-compose.yml`을 운영 기준으로 사용하고, ECR 연동 시점에 `docker-compose-prod.yml`을 추가하는 방식을 권장한다.

## EC2 배포 흐름

EC2에는 AWS CLI와 Docker가 설치되어 있어야 한다. EC2 IAM Role에는 ECR pull 권한을 부여한다.

ECR 로그인:

```bash
aws ecr get-login-password --region ap-northeast-2 \
  | docker login --username AWS --password-stdin <account-id>.dkr.ecr.ap-northeast-2.amazonaws.com
```

이미지 pull 및 배포:

```bash
IMAGE_TAG=<git-sha> docker compose -f docker-compose-prod.yml pull
IMAGE_TAG=<git-sha> docker compose -f docker-compose-prod.yml up -d
```

상태 확인:

```bash
docker compose -f docker-compose-prod.yml ps
docker compose -f docker-compose-prod.yml logs -f gateway-service
```

## 롤백

롤백은 이전에 배포했던 이미지 태그로 다시 `up -d` 한다.

```bash
IMAGE_TAG=<previous-git-sha> docker compose -f docker-compose-prod.yml pull
IMAGE_TAG=<previous-git-sha> docker compose -f docker-compose-prod.yml up -d
```

이 방식이 가능하려면 ECR에서 `<git-sha>` 태그 이미지를 일정 기간 삭제하지 않아야 한다.

## CI 작업 초안

CI는 다음 단계를 수행한다.

```text
1. 코드 checkout
2. Docker buildx 준비
3. ECR 로그인
4. backend 4개 이미지 build/push
5. frontend 이미지 build/push
6. 배포 태그 기록
```

백엔드 이미지:

```text
reservation-gateway:<git-sha>
reservation-iam:<git-sha>
reservation-stop:<git-sha>
reservation-reservation:<git-sha>
```

프론트 이미지:

```text
reservation-frontend:<git-sha>
```

프론트 repository가 별도로 분리되어 있다면 프론트 CI에서 별도 ECR repository로 push한다.

## 서버에서 빌드하지 않는 이유

`docker compose up --build -d`는 EC2에서 Gradle 빌드와 Next.js 빌드를 수행한다. 2 vCPU / 8GB 서버에서는 운영 중 빌드 작업이 CPU와 메모리를 크게 사용해 애플리케이션 응답성에 영향을 줄 수 있다.

운영 서버에서는 다음 명령을 기본으로 사용한다.

```bash
docker compose pull
docker compose up -d
```

`--build`는 로컬 개발 또는 임시 운영 복구 상황에서만 사용한다.

## 적용 순서

현재는 AWS 계정과 ECR이 준비되지 않았으므로 계획만 유지한다. 실제 적용은 다음 순서로 진행한다.

1. AWS 계정 생성
2. ECR repository 생성
3. EC2 IAM Role에 ECR pull 권한 부여
4. CI에 ECR push 권한 연결
5. `docker-compose-prod.yml` 추가
6. CI에서 ARM64 이미지 build/push 검증
7. EC2에서 `pull/up` 배포 검증
8. 롤백 절차 테스트

## 주의사항

- 운영 배포에는 `latest`보다 `<git-sha>` 태그를 사용한다.
- ARM64 서버를 쓸 경우 이미지 platform을 반드시 맞춘다.
- EC2에 AWS Access Key를 직접 저장하지 않고 IAM Role을 사용한다.
- ECR Lifecycle Policy로 너무 오래된 이미지는 삭제하되, 최근 배포/롤백 가능한 태그는 남긴다.
- DB migration은 애플리케이션 기동 시 Liquibase가 수행하므로, 롤백 시 DB schema 호환성을 별도로 확인한다.
