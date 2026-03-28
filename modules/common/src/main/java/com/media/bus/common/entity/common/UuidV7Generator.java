package com.media.bus.common.entity.common;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/// RFC 9562 표준 기반 UUID v7 생성기
/// Time-ordered 특성을 가지며 B-Tree 인덱스 환경에 최적화되어 있습니다.
public class UuidV7Generator implements IdentifierGenerator {

    /* UUID v7 비트 조작을 위한 상수 정의 */

    // MSB 조작용 상수
    private static final int TIMESTAMP_SHIFT = 16;
    private static final long VERSION_7_MASK = 0x7000L;
    private static final long RAND_A_MASK = 0x0FFFL;

    // LSB 조작용 상수
    // 0x8000_0000_0000_0000L은 이진수 1000...을 의미하며 Variant 10을 설정합니다.
    private static final long VARIANT_MASK = 0x8000_0000_0000_0000L;
    private static final int RAND_B_SHIFT = 2;

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        long now = System.currentTimeMillis();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        /*
         * MSB 구성:
         * 1. now << TIMESTAMP_SHIFT : 48비트 밀리초 타임스탬프
         * 2. VERSION_7_MASK         : 4비트 버전 정보 (값: 7)
         * 3. random & RAND_A_MASK   : 12비트 난수 (rand_a)
         */
        long msb = (now << TIMESTAMP_SHIFT) | VERSION_7_MASK | (random.nextLong() & RAND_A_MASK);

        /*
         * LSB 구성:
         * 1. VARIANT_MASK           : 2비트 베리언트 정보 (값: 10)
         * 2. random >>> RAND_B_SHIFT: 62비트 난수 (rand_b)
         */
        long lsb = VARIANT_MASK | (random.nextLong() >>> RAND_B_SHIFT);

        return new UUID(msb, lsb);
    }
}