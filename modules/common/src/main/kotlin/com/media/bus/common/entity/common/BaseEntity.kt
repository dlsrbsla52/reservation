package com.media.bus.common.entity.common

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import java.util.*

/**
 * ## UUID PK 기반 DAO 엔티티 공통 추상 클래스
 */
abstract class BaseEntity(id: EntityID<UUID>) : UUIDEntity(id)

/**
 * ## 감사 컬럼 포함 DAO 엔티티 공통 추상 클래스
 *
 * - `table` 파라미터: 이 엔티티가 매핑되는 `DateBaseTable` 구현체
 * - `var updatedAt`은 서비스 계층에서 변경 시 직접 갱신한다
 */
abstract class DateBaseEntity(id: EntityID<UUID>, table: DateBaseTable) : BaseEntity(id) {
    val createdAt by table.createdAt
    var updatedAt by table.updatedAt
}
