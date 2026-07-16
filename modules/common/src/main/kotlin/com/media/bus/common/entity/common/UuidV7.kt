package com.media.bus.common.entity.common

import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * ## UUID v7 생성기 (시간 정렬 가능, Virtual Thread 안전)
 *
 * RFC 9562 표준 기반 UUID v7. Time-ordered 특성을 가지며 B-Tree 인덱스 환경에 최적화되어 있다.
 *
 * 생성 로직은 Kotlin 표준 라이브러리의 [Uuid.generateV7]에 위임한다.
 * 표준 구현은 같은 밀리초 안에서 counter를 증가시켜 **동일 프로세스 내 엄격한 단조 증가**를 보장하므로,
 * 난수 suffix에 의존하던 이전 자체 구현보다 인덱스 지역성이 좋다.
 *
 * 단, 단조성은 JVM 프로세스 단위다. 여러 Pod가 동시에 발급하거나 clock skew·재시작·과거 데이터 backfill이
 * 있으면 B-tree 중간 삽입이 발생할 수 있다. 전역 순서가 필요한 로직은 PK 정렬이 아니라
 * 별도 타임스탬프 컬럼을 기준으로 삼아야 한다.
 */
object UuidV7 {

    /**
     * UUID v7 생성 -- 타임스탬프 기반 시간 정렬, B-tree 친화적.
     *
     * 반환 타입을 [java.util.UUID]로 유지하여 Exposed의 `javaUUID()` / `UUIDTable` / `UUIDEntity`
     * 기반 스키마와 호출부를 그대로 사용한다.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun generate(): UUID = Uuid.generateV7().toJavaUuid()
}
