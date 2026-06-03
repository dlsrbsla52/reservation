package com.media.bus.iam.audit.entity

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.postgresql.util.PGobject

/**
 * ## PostgreSQL `jsonb` 컬럼용 String 컬럼 타입
 *
 * 이미 직렬화된 JSON 문자열(`String`)을 그대로 저장/조회한다.
 * - **PostgreSQL** : `PGobject(type=jsonb)` 로 바인딩 → `text → jsonb` implicit cast 가
 *   허용되지 않아 발생하던 `column "detail" is of type jsonb but expression is of type
 *   character varying` 오류를 회피한다.
 * - **H2** (테스트) : 평문 `String` 으로 바인딩 → `clob` 컬럼과 호환.
 *
 * DDL 은 Liquibase 가 관리하므로 [sqlType] 은 Exposed 의 SchemaUtils 등
 * 보조 경로에서만 의미가 있으며, 실제 컬럼 정의에는 영향을 주지 않는다.
 */
class JsonbStringColumnType : ColumnType<String>() {

    override fun sqlType(): String = when (currentDialect) {
        is H2Dialect -> "CLOB"
        else -> "JSONB"
    }

    /**
     * PostgreSQL JDBC 는 `jsonb` 컬럼을 [PGobject] 로 반환한다.
     * H2/그 외 dialect 는 평문 [String] 으로 반환하므로 두 경우 모두 처리한다.
     */
    override fun valueFromDB(value: Any): String = when (value) {
        is PGobject -> value.value.orEmpty()
        is String -> value
        else -> value.toString()
    }

    /**
     * PostgreSQL 에서는 [PGobject] 로 감싸 jsonb 로 명시 바인딩한다.
     * H2 등 다른 dialect 는 평문 String 그대로 전달한다.
     */
    override fun notNullValueToDB(value: String): Any = when (currentDialect) {
        is H2Dialect -> value
        else -> PGobject().apply {
            type = "jsonb"
            this.value = value
        }
    }
}

/**
 * PostgreSQL `jsonb` (또는 H2 `clob`) 컬럼에 매핑되는 [String] 컬럼을 등록한다.
 *
 * Nullable 컬럼은 호출부에서 `.nullable()` 을 체이닝한다.
 */
fun Table.jsonbString(name: String): Column<String> =
    registerColumn(name, JsonbStringColumnType())
