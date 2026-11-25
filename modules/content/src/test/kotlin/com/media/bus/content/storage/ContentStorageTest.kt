package com.media.bus.content.storage

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ContentStorageTest {
    private val properties = ContentStorageProperties(
        endpoint = "http://localhost:9000",
        bucket = "bus-content",
        accessKey = "test-access-key",
        secretKey = "test-secret-key",
        publicBaseUrl = "http://localhost:9000/bus-content",
        region = "us-east-1",
        pathStyle = true,
    )
    private val deleted = CopyOnWriteArrayList<List<String>>()
    private val deletedSignal = CountDownLatch(1)
    private val storage = object : ContentStorage(properties) {
        override fun deleteNow(keys: List<String>) {
            deleted.add(keys)
            deletedSignal.countDown()
        }
    }

    @BeforeEach
    fun startSynchronization() {
        TransactionSynchronizationManager.initSynchronization()
    }

    @AfterEach
    fun clearSynchronization() {
        TransactionSynchronizationManager.clearSynchronization()
    }

    @Test
    fun `presigned URL은 Content-Type과 Content-Length를 서명 헤더에 넣어 형식과 크기를 고정한다`() {
        val upload = storage.presignUpload("portfolio/test.webp", "image/webp", 1234)

        val query = URI.create(upload.url).rawQuery.split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
        }
        val signed = query.getValue("X-Amz-SignedHeaders").split(';')
        assertThat(signed).contains("content-length", "content-type", "host")
        assertThat(upload.headers).containsEntry("Content-Type", "image/webp")
    }

    @Test
    fun `커밋 뒤에만 삭제를 실행한다`() {
        storage.deleteAfterCommit(listOf("a.webp", "a.webp", " "))

        assertThat(deleted).isEmpty()
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

        assertThat(deletedSignal.await(2, TimeUnit.SECONDS)).isTrue()
        assertThat(deleted).containsExactly(listOf("a.webp"))
    }

    @Test
    fun `롤백되면 삭제를 실행하지 않는다`() {
        storage.deleteAfterCommit(listOf("a.webp"))

        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

        assertThat(deletedSignal.await(300, TimeUnit.MILLISECONDS)).isFalse()
        assertThat(deleted).isEmpty()
    }

    @Test
    fun `삭제할 키가 없으면 동기화를 등록하지 않는다`() {
        storage.deleteAfterCommit(emptyList())

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty()
    }
}
