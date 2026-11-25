package com.media.bus.content.storage

import com.media.bus.common.exceptions.ServiceException
import com.media.bus.content.result.ContentResult
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneId

/** 브라우저 직접 업로드용 presigned URL 발급과 객체 확인·삭제 */
@Component
@EnableConfigurationProperties(ContentStorageProperties::class)
class ContentStorage(private val properties: ContentStorageProperties) {

    private val log = LoggerFactory.getLogger(ContentStorage::class.java)

    private val credentials by lazy {
        StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey, properties.secretKey))
    }

    private val client: S3Client by lazy {
        S3Client.builder()
            .region(Region.of(properties.region))
            .endpointOverride(URI.create(properties.endpoint))
            .credentialsProvider(credentials)
            .forcePathStyle(properties.pathStyle)
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .overrideConfiguration {
                it.apiCallTimeout(properties.apiCallTimeout).apiCallAttemptTimeout(properties.apiCallAttemptTimeout)
            }
            .build()
    }

    private val presigner: S3Presigner by lazy {
        S3Presigner.builder()
            .region(Region.of(properties.region))
            .endpointOverride(URI.create(properties.browserEndpoint))
            .credentialsProvider(credentials)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle).build())
            .build()
    }

    val maxUploadBytes: Long
        get() = properties.maxUploadBytes

    /** 정해진 형식·크기로만 올릴 수 있는 PUT URL을 발급한다. */
    fun presignUpload(key: String, contentType: String, contentLength: Long): PresignedUpload {
        requireConfigured()
        val put = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(contentLength)
            .build()
        val presigned = storageCall {
            presigner.presignPutObject(
                PutObjectPresignRequest.builder().signatureDuration(properties.uploadExpiry).putObjectRequest(put).build(),
            )
        }
        return PresignedUpload(
            url = presigned.url().toString(),
            headers = mapOf("Content-Type" to contentType),
            expiresAt = OffsetDateTime.ofInstant(presigned.expiration(), ZoneId.of("Asia/Seoul")),
        )
    }

    /** 객체가 저장소에 있는지 확인한다. */
    fun exists(key: String): Boolean {
        requireConfigured()
        return try {
            client.headObject(HeadObjectRequest.builder().bucket(properties.bucket).key(key).build())
            true
        } catch (_: NoSuchKeyException) {
            false
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) false else throw ServiceException(ContentResult.STORAGE_ERROR, e)
        } catch (e: SdkException) {
            throw ServiceException(ContentResult.STORAGE_ERROR, e)
        }
    }

    /** 공개 URL. 저장소 설정이 없으면 키를 그대로 돌려준다. */
    fun publicUrl(key: String): String =
        if (properties.publicBaseUrl.isBlank()) key else "${properties.publicBaseUrl.trimEnd('/')}/$key"

    /** 트랜잭션이 커밋된 뒤 객체를 지운다. 롤백이면 지우지 않고, 삭제 실패는 로그만 남긴다. */
    fun deleteAfterCommit(keys: Collection<String>) {
        val targets = keys.filter { it.isNotBlank() }.distinct()
        if (targets.isEmpty()) return
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() = deleteAsync(targets)
                },
            )
        } else {
            deleteNow(targets)
        }
    }

    private fun deleteAsync(keys: List<String>) {
        try {
            Thread.ofVirtual().name("content-storage-delete").start { deleteNow(keys) }
        } catch (e: RuntimeException) {
            log.warn("[ContentStorage] 이미지 삭제 시작 실패: keys={}", keys, e)
        }
    }

    internal open fun deleteNow(keys: List<String>) {
        if (!properties.configured) return
        try {
            val response = client.deleteObjects(
                DeleteObjectsRequest.builder()
                    .bucket(properties.bucket)
                    .delete(Delete.builder().objects(keys.map { ObjectIdentifier.builder().key(it).build() }).build())
                    .build(),
            )
            response.errors().forEach {
                log.warn("[ContentStorage] 이미지 삭제 거절: key={}, code={}, message={}", it.key(), it.code(), it.message())
            }
        } catch (e: RuntimeException) {
            log.warn("[ContentStorage] 이미지 삭제 실패: keys={}", keys, e)
        }
    }

    private fun requireConfigured() {
        if (!properties.configured) throw ServiceException(ContentResult.STORAGE_NOT_CONFIGURED)
    }

    private fun <T> storageCall(block: () -> T): T = try {
        block()
    } catch (e: SdkException) {
        throw ServiceException(ContentResult.STORAGE_ERROR, e)
    }
}

/** presigned PUT 결과 */
data class PresignedUpload(
    val url: String,
    val headers: Map<String, String>,
    val expiresAt: OffsetDateTime,
)
