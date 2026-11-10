package com.media.bus.reservation.support

import com.media.bus.common.exceptions.BusinessException
import org.jetbrains.exposed.v1.dao.flushCache
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/** PostgreSQL SQLSTATE: 유니크 제약 위반. */
const val SQLSTATE_UNIQUE_VIOLATION = "23505"

/** PostgreSQL SQLSTATE: 제외(EXCLUDE) 제약 위반. */
const val SQLSTATE_EXCLUSION_VIOLATION = "23P01"

/**
 * 현재 트랜잭션에 쌓인 DAO 변경을 지금 DB 로 내보내고, 지정한 제약 위반을 비즈니스 예외로 바꾼다.
 *
 * Exposed DAO 는 INSERT/UPDATE 를 커밋 직전까지 미루기 때문에, 그대로 두면 제약 위반이
 * `@Transactional` 메서드 밖(커밋 시점)에서 터져 500 으로 응답된다. 메서드 안에서 미리 내보내야
 * "먼저 온 요청이 이겼다"는 사실을 409 로 알려줄 수 있다.
 *
 * 제약 위반이 나면 PostgreSQL 트랜잭션은 이미 중단 상태이므로 이어서 쿼리할 수 없다.
 * 여기서 던지는 예외가 트랜잭션을 롤백시킨다.
 *
 * @param sqlState     변환할 SQLSTATE
 * @param onViolation  위반 시 던질 예외
 */
fun flushTranslating(sqlState: String, onViolation: () -> BusinessException) {
    try {
        TransactionManager.current().flushCache()
    } catch (e: ExposedSQLException) {
        if (e.sqlState == sqlState) throw onViolation() else throw e
    }
}
