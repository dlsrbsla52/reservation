package com.media.bus.content.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.validation.InputValidator
import com.media.bus.content.dto.ImageUploadCommand
import com.media.bus.content.dto.ImageUploadTicket
import com.media.bus.content.entity.enums.ImagePurpose
import com.media.bus.content.result.ContentResult
import com.media.bus.content.storage.ContentStorage
import org.springframework.stereotype.Service
import java.util.*

/** 콘텐츠 이미지 업로드 URL 발급과 등록 전 키 검증 */
@Service
class ContentImageService(private val storage: ContentStorage) {

    /** 용도·형식·크기를 검증하고 새 객체 키로 업로드 URL을 발급한다. */
    fun issueUpload(command: ImageUploadCommand): ImageUploadTicket {
        InputValidator.validate(command)
        val purpose = ImagePurpose.fromName(command.purpose) ?: throw BusinessException(ContentResult.INVALID_CATEGORY)
        val extension = EXTENSIONS[command.contentType] ?: throw BusinessException(ContentResult.UNSUPPORTED_IMAGE_TYPE)
        if (command.contentLength > storage.maxUploadBytes) throw BusinessException(ContentResult.IMAGE_TOO_LARGE)

        val suffix = if (command.thumbnail) "_thumb" else ""
        val key = "${purpose.prefix()}/${UUID.randomUUID()}$suffix.$extension"
        val presigned = storage.presignUpload(key, command.contentType, command.contentLength)
        return ImageUploadTicket(
            key = key,
            uploadUrl = presigned.url,
            headers = presigned.headers,
            publicUrl = storage.publicUrl(key),
            expiresAt = presigned.expiresAt,
        )
    }

    /** 이 서비스가 발급한 형식의 키이고 실제로 업로드된 객체인지 확인한다. */
    fun verifyUploaded(purpose: ImagePurpose, keys: Collection<String>) {
        keys.forEach { key ->
            if (!keyPattern(purpose).matches(key)) throw BusinessException(ContentResult.INVALID_IMAGE_KEY)
            if (!storage.exists(key)) throw BusinessException(ContentResult.IMAGE_NOT_UPLOADED)
        }
    }

    companion object {
        private val EXTENSIONS = mapOf("image/webp" to "webp", "image/jpeg" to "jpg", "image/png" to "png")

        private fun ImagePurpose.prefix() = name.lowercase(Locale.ROOT)

        internal fun keyPattern(purpose: ImagePurpose) = Regex(
            "^${purpose.prefix()}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(_thumb)?\\.(webp|jpg|png)$",
        )
    }
}
