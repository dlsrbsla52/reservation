package com.media.bus.common.entity.common

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * ## UUID PK 기반 테이블 공통 추상 클래스
 *
 * 모든 테이블 object는 이 클래스를 상속한다.
 * `UUIDTable`이 `id` 컬럼(UUID PK)을 자동으로 제공한다.
 */
abstract class BaseTable(name: String) : UUIDTable(name)

/**
 * ## 감사 컬럼(createdAt, updatedAt) 포함 테이블
 *
 * `DateBaseTable`을 상속하면 `created_at`, `updated_at` 컬럼이 자동으로 포함된다.
 */
abstract class DateBaseTable(name: String) : BaseTable(name) {
    val createdAt = timestampWithTimeZone("created_at")
        .also { it.defaultValueFun = { OffsetDateTime.now(ZoneId.of("Asia/Seoul")) } }
    val updatedAt = timestampWithTimeZone("updated_at")
        .also { it.defaultValueFun = { OffsetDateTime.now(ZoneId.of("Asia/Seoul")) } }
}
