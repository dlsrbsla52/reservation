package com.media.bus.content.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 이미지 저장소(S3 호환: 운영 R2, 로컬 RustFS) 설정 */
@ConfigurationProperties("content.storage")
data class ContentStorageProperties(
    val endpoint: String = "",
    val presignEndpoint: String = "",
    val region: String = "auto",
    val bucket: String = "",
    val accessKey: String = "",
    val secretKey: String = "",
    val publicBaseUrl: String = "",
    val pathStyle: Boolean = false,
    val uploadExpiry: Duration = Duration.ofMinutes(10),
    val maxUploadBytes: Long = 10L * 1024 * 1024,
    val apiCallTimeout: Duration = Duration.ofSeconds(5),
    val apiCallAttemptTimeout: Duration = Duration.ofSeconds(2),
) {
    val browserEndpoint: String
        get() = presignEndpoint.ifBlank { endpoint }

    val configured: Boolean
        get() = listOf(endpoint, bucket, accessKey, secretKey, publicBaseUrl).none { it.isBlank() }
}
