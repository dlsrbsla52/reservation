package com.media.bus.reservation.support

import com.media.bus.common.exceptions.BusinessException
import org.jetbrains.exposed.v1.dao.flushCache
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

const val SQLSTATE_UNIQUE_VIOLATION = "23505"

const val SQLSTATE_EXCLUSION_VIOLATION = "23P01"

/** 현재 트랜잭션에 쌓인 DAO 변경을 지금 DB 로 내보내고, 지정한 제약 위반을 비즈니스 예외로 바꾼다. */
fun flushTranslating(sqlState: String, onViolation: () -> BusinessException) {
    try {
        TransactionManager.current().flushCache()
    } catch (e: ExposedSQLException) {
        if (e.sqlState == sqlState) throw onViolation() else throw e
    }
}
