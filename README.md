# 보일러 플레이트
>`java25`, `Spring Boot 4`, `Spring Security 7` 을 위한 보일러플레이트

## 서버 구성
> port:8180 (Default 서버 포트와 겹치지 않게 구성)

## DB 구성
> DB는 Docker를 띄워야 구동이 가능합니다 \
> 초기 구동시 DB가 존재하지 않는다면 자동으로 구성됩니다.\
> 세부사항은 `docker-compose.yaml` 참조 부탁드립니다.

![img.png](img.png)


## Virtual-Thread 구성
> Virtual-Thread는 Java의 Project Loom이 시작되면서 생겼습니다. \
> 이는 Multi-Thread 환경에서 발되는 컨텍스트 스위칭 비용을 마치 넌블러킹 처럼 처리해줍니다. \
> 구성은 N개의 Platform Thread에 M개의 Virtual-Thread를 매칭시켜 동작합니다. \
> 이 때 고가용성 처리를 특화로 할 수 있습니다.
> 


## 주의사항
> 이 보일러 플레이트는 Java Virtual-Thread를 기본 사향으로 간주하고 있습니다.\
> Virtual Thread 환경에서는 스레드에 종속적인 데이터를 다룰 때 `ThreadLocal` 사용을 지양하고 `ScopedValue`(JDK 21+) 사용을 권장합니다. \
> `ThreadLocal`을 잘못 사용하면 Virtual Thread가 Carrier Thread에 고정(pinning)되어 **심각한 성능 저하와 메모리 누수를 유발할 수 있습니다.** \
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
> 자세한 내용은 `com.hig.boilerplate.core.aop.TransactionalBulkheadAspect` 참조 바랍니다.