package com.media.bus.app.web

import com.media.bus.iam.port.dto.content.AdminFaqRequest
import com.media.bus.iam.port.dto.content.AdminImageUploadRequest
import com.media.bus.iam.port.dto.content.AdminInquiryStatusRequest
import com.media.bus.iam.port.dto.content.AdminNoticeRequest
import com.media.bus.iam.port.dto.content.AdminPortfolioRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import tools.jackson.databind.json.JsonMapper
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/** Spring이 실제로 쓰는 JSON 매퍼로 요청 DTO의 생략 가능한 필드를 읽는다 */
@BusAppContextTest
class RequestBodyDeserializationTest {

    @Autowired
    private lateinit var mapper: JsonMapper

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    @Test
    fun `관리자 콘텐츠 요청은 생략 가능한 Boolean과 List를 빼도 읽힌다`() {
        val notice = mapper.readValue("""{"title":"t","body":"b"}""", AdminNoticeRequest::class.java)
        val portfolio = mapper.readValue(
            """{"title":"t","category":"ETC","imageKey":"portfolio/a.webp","thumbnailKey":"portfolio/a_thumb.webp"}""",
            AdminPortfolioRequest::class.java,
        )
        val faq = mapper.readValue("""{"category":"MEDIA","question":"q","answer":"a"}""", AdminFaqRequest::class.java)
        val upload = mapper.readValue(
            """{"purpose":"NOTICE","contentType":"image/webp","contentLength":10}""",
            AdminImageUploadRequest::class.java,
        )
        val status = mapper.readValue("""{"status":"DONE"}""", AdminInquiryStatusRequest::class.java)

        assertThat(notice.imageKeys).isNull()
        assertThat(notice.pinned).isNull()
        assertThat(portfolio.visible).isNull()
        assertThat(faq.visible).isNull()
        assertThat(upload.thumbnail).isNull()
        assertThat(status.memo).isNull()
    }

    @Test
    fun `모든 요청 본문 DTO에 null이 아닌 기본값 필드가 없다`() {
        val bodyTypes = handlerMapping.handlerMethods.values
            .flatMap { it.methodParameters.toList() }
            .filter { it.hasParameterAnnotation(RequestBody::class.java) }
            .map { it.parameterType.kotlin }
            .toSet()

        val offenders = bodyTypes.flatMap { nonNullDefaults(it, mutableSetOf()) }

        assertThat(bodyTypes).isNotEmpty
        assertThat(offenders).describedAs("Jackson이 Kotlin 기본값을 쓰지 않으므로 nullable로 바꿔야 하는 필드").isEmpty()
    }

    private fun nonNullDefaults(type: KClass<*>, visited: MutableSet<KClass<*>>): List<String> {
        if (!visited.add(type) || type.qualifiedName?.startsWith("com.media.bus") != true) return emptyList()
        val parameters = type.primaryConstructor?.parameters ?: return emptyList()
        val own = parameters.filter { it.isOptional && !it.type.isMarkedNullable }.map { "${type.simpleName}.${it.name}" }
        val nested = parameters.flatMap { parameter ->
            val classifiers = listOf(parameter.type) + parameter.type.arguments.mapNotNull { it.type }
            classifiers.mapNotNull { it.classifier as? KClass<*> }.flatMap { nonNullDefaults(it, visited) }
        }
        return own + nested
    }
}
