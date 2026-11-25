package com.media.bus.content.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.content.dto.ImageUploadCommand
import com.media.bus.content.entity.enums.ImagePurpose
import com.media.bus.content.result.ContentResult
import com.media.bus.content.storage.ContentStorage
import com.media.bus.content.storage.PresignedUpload
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime

class ContentImageServiceTest {
    private val storage = mockk<ContentStorage>()
    private val service = ContentImageService(storage)

    @Test
    fun `용도별 새 키로 업로드 URL을 발급하고 썸네일은 키에 표시한다`() {
        val keySlot = slot<String>()
        every { storage.maxUploadBytes } returns 10L * 1024 * 1024
        every { storage.presignUpload(capture(keySlot), "image/webp", 2048) } answers {
            PresignedUpload("https://upload.test/${keySlot.captured}", mapOf("Content-Type" to "image/webp"), OffsetDateTime.now())
        }
        every { storage.publicUrl(any()) } answers { "https://img.test/${firstArg<String>()}" }

        val ticket = service.issueUpload(ImageUploadCommand("PORTFOLIO", "image/webp", 2048, thumbnail = true))

        assertThat(ticket.key).matches(ContentImageService.keyPattern(ImagePurpose.PORTFOLIO).toPattern())
        assertThat(ticket.key).endsWith("_thumb.webp")
        assertThat(ticket.publicUrl).isEqualTo("https://img.test/${ticket.key}")
    }

    @Test
    fun `허용하지 않은 형식과 용량 초과와 알 수 없는 용도는 발급하지 않는다`() {
        every { storage.maxUploadBytes } returns 1024

        val type = assertThrows<BusinessException> { service.issueUpload(ImageUploadCommand("NOTICE", "image/gif", 10)) }
        val size = assertThrows<BusinessException> { service.issueUpload(ImageUploadCommand("NOTICE", "image/png", 2048)) }
        val purpose = assertThrows<BusinessException> { service.issueUpload(ImageUploadCommand("AVATAR", "image/png", 10)) }

        assertThat(type.result).isEqualTo(ContentResult.UNSUPPORTED_IMAGE_TYPE)
        assertThat(size.result).isEqualTo(ContentResult.IMAGE_TOO_LARGE)
        assertThat(purpose.result).isEqualTo(ContentResult.INVALID_CATEGORY)
    }

    @Test
    fun `다른 용도 접두사의 키는 형식 오류로 막는다`() {
        val noticeKey = "notice/0f8f5f0e-1111-4222-8333-944445555666.webp"
        val ex = assertThrows<BusinessException> { service.verifyUploaded(ImagePurpose.PORTFOLIO, listOf(noticeKey)) }
        assertThat(ex.result).isEqualTo(ContentResult.INVALID_IMAGE_KEY)
    }
}
