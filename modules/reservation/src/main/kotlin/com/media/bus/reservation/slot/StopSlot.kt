package com.media.bus.reservation.slot

/**
 * ## 정류소 광고면(slot) 식별 규칙
 *
 * 선점과 계약 독점의 단위는 정류소가 아니라 **정류소 + 광고면 번호**다.
 * 지금은 정류소당 광고면이 1개라 모든 row 가 [DEFAULT_NO] 를 쓴다.
 * 광고면이 여러 개 생기면 면 마스터는 정류소 데이터 소유자인 stop 모듈에 두고,
 * 요청 DTO 에서 면 번호를 받아 이 값 대신 넘기면 된다(유니크 키·제외 제약·Redis 키는 이미 면 번호를 포함한다).
 */
object StopSlot {
    const val DEFAULT_NO: Short = 1
}
