# 예약 시스템 개발 프로젝트
>`java25`, `Spring Boot 3.4.13`을 위한 MSA(Microservices Architecture) 기반 보일러플레이트

## 시스템 아키텍처 (MSA Multi-Module 구조)
본 프로젝트는 **MSA(Microservices Architecture)** 구조를 지향하며, 단일 프로젝트(Monorepo) 내에서 멀티 모듈 기반으로 구성됩니다. 각 서브 모듈은 도메인 특성에 맞게 독립적인 웹 애플리케이션으로 동작합니다.

- **common**: 전체 프로젝트에서 공유하는 공통 도메인/DTO, 유틸리티, 글로벌 예외 처리, JPA 엔티티, QueryDSL Q-Class 빈, AWS SDK, DB 설정 및 Security 기본 설정이 응집된 핵심 라이브러리 모듈입니다.
- **gateway**: 클라이언트의 모든 요청을 단일 진입점으로 받아 각 마이크로서비스로 라우팅하는 Spring Cloud Gateway 모듈입니다. (Expected Port: 8080)
- **auth**: JWT 토큰 발급 프로세스와 사용자의 인증(Authentication)/인가(Authorization) 및 회원의 라이프사이클(가입, 조회, 수정, 제재, 탈퇴 등) 및 회원 관련 비즈니스 도메인을 관리하는 모듈 전담하여 처리하는 인증 인가 모듈입니다. (Expected Port: 8181)
- **reservation**: 시스템의 Core 비즈니스인 실제 예약 생성, 변경, 취소 등의 트랜잭션 로직을 책임지는 메인 워커 모듈입니다. (Expected Port: 8183)

## DB 및 통합 테스트(MSA) 구동 환경
> 본 프로젝트는 Docker Compose를 이용해 모든 MSA 모듈을 로컬 컨테이너 환경에서 통합 테스트할 수 있도록 최적화되어 있습니다.
> 
> 다음과 같은 명령어로 DB(PostgreSQL) 및 API Gateway를 포함한 3개의 마이크로서비스(`gateway`, `auth`, `reservation`)를 띄울 수 있습니다.
>
> ```bash
> docker-compose up --build -d
> ```
> 특정 서비스만 재빌드시 사용
> ```bash
> docker-compose up -d --build --no-deps {{service-name}}
> docker-compose up -d --build --no-deps gateway-service
> ```
> 병렬 빌드 실행 (약 30% 속도 향상)
> ```bash
> DOCKER_BUILDKIT=1 docker compose build --parallel
> DOCKER_BUILDKIT=1 docker compose -f docker-compose-local.yml build --parallel
> DOCKER_BUILDKIT=1 docker compose -f docker-compose-local.yml up --build -d
> ```
> 로컬 빌드 실행
> ```bash
> docker-compose -f docker-compose-local.yml up -d --build --no-deps {{service-name}}
> docker-compose -f docker-compose-local.yml up -d --build --no-deps gateway-service
>```
> 위 명령어를 실행하면 Host OS의 아키텍처(AMD64/ARM64)에 맞춰 Java 25 환경이 동적으로 구성되는 빌더 이미지를 통해 각 모듈의 `.jar`가 패키징됩니다. 
> API Gateway(`8080`)를 단일 진입점으로 하여 뒤단의 개별 서비스(`8181`, `8183`) 포트가 묶여 백그라운드에서 구동됩니다. DB는 `15433` 포트로 바인딩됩니다.
>
> 💡 **데이터 영속성(Persistence)**
> 개발환경에선 `docker-compose-local.yml` 내에 `postgres_data`라는 Docker Name Volume이 매핑되어 있습니다.
> 이로 인해 `docker-compose down`이나 컨테이너(rm)를 강제로 삭제하더라도, **매핑된 볼륨(`-v`)을 함께 삭제하지 않는 이상 DB 내의 데이터는 영구적으로 유지**되므로 안전하게 테스트를 껐다 켤 수 있습니다.


## MSA 내부 통신 (RestClient)
> 본 프로젝트는 마이크로서비스 간의 동기 통신을 위해 Spring Boot 3.2+ 표준인 **`RestClient`**를 사용합니다.
> 모든 내부 통신은 `common` 모듈에 정의된 `internalRestClient` 빈(Bean)을 통해 이루어지며, 다음과 같은 보안 및 편의 기능을 내장하고 있습니다.
>
> 1. **인증 컨텍스트 자동 전파 (Security Context Propagation)**
>    - 외부에서 유입된 유저의 JWT 토큰을 Interceptor 수준에서 가로채어, 내부 서비스 호출 시 `Authorization` 헤더에 자동으로 주입합니다.
> 2. **S2S (System-to-System) Fallback**
>    - 스케줄러(@Scheduled)나 비동기(@Async) 등 유저 컨텍스트가 없는 환경에서 호출될 경우, 미리 정의된 시스템 전용 토큰을 자동으로 주입하여 인가 장애를 방지합니다.
> 3. **엔드포인트 화이트리스트 (Security Whitelisting)**
>    - 내부 인증 정보가 외부 API(예: Google, Kakao 등)로 유출되는 것을 차단하기 위해, 신뢰할 수 있는 도메인으로의 요청에만 토큰을 주입합니다.
>    - **허용 도메인**: `*.local`, `*.internal`, `*service*`, `localhost`, `10.* (Internal IP)`
>
> 💡 **사용 방법**
> ```java
> @Service
> @RequiredArgsConstructor
> public class MyService {
>     private final RestClient internalRestClient;
>
>     public void callOtherModule() {
>         internalRestClient.get()
>             .uri("http://member-service/api/v1/profile")
>             .retrieve()
>             .body(MemberDto.class);
>     }
> }
> ```
>
> 자세한 구현 내용은 `com.common.configuration.RestClientConfig`를 참조하시기 바랍니다.

## 디버깅 (Remote JVM Debug)
> 본 프로젝트는 Docker Compose로 구동되는 각 마이크로서비스에 대해 원격 디버깅(Remote JVM Debug) 환경을 기본 제공합니다.
> `docker-compose.yml` 리소스에 JDWP(Java Debug Wire Protocol) 설정 및 포워딩이 구성되어 있습니다.

### 디버깅 모드 접속 방법
1. **IntelliJ Remote Debugger 설정**
   - 우측 상단 `Run/Debug Configurations` -> **Edit Configurations...**
   - **+** 기호 -> **Remote JVM Debug** 선택
   - **Name**: `Remote: Auth Service` 등 원하는 이름 설정
   - **Host**: `localhost` / **Port**: 해당 서비스의 디버그 포트 입력
     - `gateway`: `18080`
     - `auth`: `18181`
     - `reservation`: `18183`
   - **Use module classpath**: 디버깅 타겟 모듈 지정 (예: `reservation.modules.auth.main`)
2. **실행 및 Attach**
   - `docker-compose up -d` 로 컨테이너 기동
   - 비즈니스 로직에 Breakpoint(중단점) 지정
   - 생성한 `Remote JVM Debug` 환경을 실행(디버그 아이콘 클릭)하여 컨테이너에 Attach
   - 정상 연결 시 `Connected to the target VM` 메시지 확인 가능

💡 **애플리케이션 초기화 시점 디버깅**
컨테이너 기동 과정(예: 빈 생성, 컨텍스트 초기화)을 디버깅해야 한다면, `docker-compose.yml`의 `JAVA_TOOL_OPTIONS` 환경 변수 중 `suspend=n`을 `suspend=y`로 변경 후 컨테이너를 재시작하십시오. 디버거가 Attach 될 때까지 애플리케이션 Bootstrap이 일시 정지(Hold)됩니다.

## 주의사항
> 이 프로젝트는 Java Virtual-Thread를 기본 사양으로 간주하고 있습니다.\
> Virtual Thread 환경에서는 스레드에 종속적인 데이터를 다룰 때 `ThreadLocal` 사용을 지양하고 `ScopedValue`(JDK 25+) 사용을 강력히 권장합니다. \
> `ThreadLocal`을 잘못 사용하면 Virtual Thread가 Carrier Thread에 고정(pinning)되어 **심각한 성능 저하와 메모리 누수를 유발할 수 있습니다.** \
> 해당 현상은 java 25에 들어 Jvm 레벨에서 해소 되었기 때문에 java 25 이상을 사용하는 환경에서 발생하지 않습니다.\
> \
> 서버의 고가용성을 위해 Virtual-Thread를 DB 커넥션과 1:1로 매핑 시키면 \
> 순식간에 connection pool이 고갈되는 현상이 발생할 수 있습니다.\
> 이를 해결하기 위해 Bulkhead 처리가 필요합니다. \
> \
> 이는 검증된 라이브러리인 Resilience4j 통해 Bulkhead를 구현 했습니다. \
> AOP를 통해 **`Transactional`** 을 얻는 모든 로직에 대해 세마포어를 적용합니다. \
> \
> 이는 **`Transactional`** 사용하며 트랜잭션을 얻는 그 순간부터 Server에 존재하는 \
> `DB Connection Pool`을 사용하기 때문입니다. \
> \
> 자세한 내용은 `com.common.boilerplate.core.aop.TransactionalBulkheadAspect` 참조 바랍니다.