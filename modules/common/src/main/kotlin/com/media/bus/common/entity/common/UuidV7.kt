package com.media.bus.common.entity.common

import java.security.SecureRandom
import java.time.Instant
import java.util.*

/**
 * ## UUID v7 생성기 (시간 정렬 가능, Virtual Thread 안전)
 *
 * RFC 9562 표준 기반 UUID v7. Time-ordered 특성을 가지며 B-Tree 인덱스 환경에 최적화되어 있다.
 */
object UuidV7 {
    private val random = SecureRandom()

    /** UUID v7 생성 -- 타임스탬프 기반 monotonically increasing, B-tree 친화적 */
    @JvmStatic
    fun generate(): UUID {
        val timestamp = Instant.now().toEpochMilli()
        val msb = (timestamp shl 16) or (7L shl 12) or (random.nextLong() and 0xFFFL)
        val lsb = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
        return UUID(msb, lsb)
    }
}
