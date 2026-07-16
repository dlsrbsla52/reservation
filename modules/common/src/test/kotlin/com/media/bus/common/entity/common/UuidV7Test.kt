package com.media.bus.common.entity.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * ## UuidV7 생성기 검증
 *
 * `UuidV7`은 모든 Entity의 PK 발급 경로이므로, 생성 구현을 교체하더라도
 * RFC 9562 UUIDv7 형식과 시간 정렬 특성이 유지되는지 보장한다.
 */
class UuidV7Test {

    @Test
    @DisplayName("생성된 UUID는 RFC 9562 UUIDv7 형식(version 7, variant 2)이어야 한다.")
    fun `UUIDv7 형식을 만족한다`() {
        repeat(1_000) {
            val uuid = UuidV7.generate()

            // version nibble = 7 (0111)
            assertThat(uuid.version()).isEqualTo(7)
            // variant = 2 -- RFC 9562 variant (상위 2비트 10)
            assertThat(uuid.variant()).isEqualTo(2)
        }
    }

    @Test
    @DisplayName("상위 48비트 타임스탬프는 현재 시각(Unix epoch milliseconds)과 일치해야 한다.")
    fun `타임스탬프 prefix가 현재 시각을 담는다`() {
        val before = System.currentTimeMillis()
        val uuid = UuidV7.generate()
        val after = System.currentTimeMillis()

        // 상위 48비트를 추출해 epoch milliseconds로 복원
        val timestamp = uuid.mostSignificantBits ushr 16

        assertThat(timestamp).isBetween(before, after)
    }

    @Test
    @DisplayName("동일 프로세스에서 연속 생성한 UUID는 엄격히 단조 증가해야 한다.")
    fun `연속 생성 시 단조 증가한다`() {
        // 같은 밀리초에 다수 생성되도록 충분히 많이 뽑는다.
        // 이전 자체 구현은 같은 ms 안에서 난수 suffix에 의존해 이 검증을 통과하지 못했다.
        val uuids = List(10_000) { UuidV7.generate() }

        // UUID.compareTo는 부호 있는 비교라 정렬 판정에 부적합하므로
        // 부호 없는(unsigned) 사전식 비교로 B-tree 삽입 순서를 검증한다.
        uuids.zipWithNext { previous, next ->
            val msbComparison = java.lang.Long.compareUnsigned(
                previous.mostSignificantBits,
                next.mostSignificantBits,
            )
            val ordered =
                if (msbComparison != 0) {
                    msbComparison < 0
                } else {
                    java.lang.Long.compareUnsigned(
                        previous.leastSignificantBits,
                        next.leastSignificantBits,
                    ) < 0
                }

            assertThat(ordered)
                .`as`("$previous 다음에 생성된 $next 는 더 커야 한다")
                .isTrue()
        }
    }

    @Test
    @DisplayName("대량 생성 시 UUID가 중복되지 않아야 한다.")
    fun `중복이 발생하지 않는다`() {
        val count = 10_000
        val uuids = List(count) { UuidV7.generate() }

        assertThat(uuids.toSet()).hasSize(count)
    }
}
