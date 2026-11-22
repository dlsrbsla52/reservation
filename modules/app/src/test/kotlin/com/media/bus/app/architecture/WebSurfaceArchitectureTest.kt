package com.media.bus.app.architecture

import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.contract.security.annotation.requiresAdminAudience
import io.swagger.v3.oas.annotations.media.Schema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/** 공개 포트에 열리는 웹 표면 회귀 방지 */
@DisplayName("웹 표면 아키텍처")
class WebSurfaceArchitectureTest {

    private data class Handler(val controller: Class<*>, val method: Method, val paths: List<String>)

    private val handlers: List<Handler> by lazy {
        val scanner = ClassPathScanningCandidateComponentProvider(false).apply {
            addIncludeFilter(AnnotationTypeFilter(RestController::class.java))
        }
        DOMAIN_PACKAGES.flatMap { scanner.findCandidateComponents(it) }
            .map { Class.forName(it.beanClassName) }
            .flatMap { controller ->
                val classPaths = mappingPaths(controller).ifEmpty { listOf("") }
                controller.declaredMethods
                    .filter { AnnotatedElementUtils.hasAnnotation(it, RequestMapping::class.java) }
                    .map { method ->
                        val methodPaths = mappingPaths(method).ifEmpty { listOf("") }
                        Handler(controller, method, classPaths.flatMap { c -> methodPaths.map { m -> c + m } })
                    }
            }
    }

    @Test
    fun `컨트롤러를 실제로 찾았다(스캔 범위 확인)`() {
        assertThat(handlers.map { it.controller.simpleName }.toSet())
            .contains("AuthController", "StopController", "ReservationController", "AdminStopPriceController")
    }

    @Test
    fun `internal 매핑은 0개다`() {
        val internal = handlers.filter { h -> h.paths.any { it.startsWith("/api/v1/internal") } }
        assertThat(internal.map { "${it.controller.simpleName}.${it.method.name}" }).isEmpty()
    }

    @Test
    fun `관리자 API 핸들러는 모두 @Authorize 를 가진다`() {
        val missing = handlers
            .filter { h -> h.paths.any(::isAdminPath) }
            .filterNot {
                AnnotatedElementUtils.hasAnnotation(it.controller, Authorize::class.java) ||
                    AnnotatedElementUtils.hasAnnotation(it.method, Authorize::class.java)
            }
        assertThat(missing.map { "${it.controller.simpleName}.${it.method.name}" }).isEmpty()
    }

    /** 런타임 발급처 검사와 별개로 관리자 기능을 iam admin API에 모으는 모듈 경계를 고정한다. */
    @Test
    fun `관리자만 허용하는 핸들러는 admin 경로 아래에만 있다`() {
        val misplaced = handlers
            .filter { isAdminOnly(effectiveAuthorize(it)) }
            .filterNot { h -> h.paths.all(::isAdminPath) }
        assertThat(misplaced.map { "${it.controller.simpleName}.${it.method.name} ${it.paths}" }).isEmpty()
    }

    @Test
    fun `컨트롤러는 신뢰 헤더를 직접 읽지 않는다`() {
        val offenders = handlers.flatMap { h ->
            h.method.parameters.mapNotNull { p ->
                val header = p.getAnnotation(RequestHeader::class.java) ?: return@mapNotNull null
                val name = header.name.ifEmpty { header.value }
                if (name.startsWith("X-User-", ignoreCase = true) || name.equals("X-Email-Verified", ignoreCase = true)) {
                    "${h.controller.simpleName}.${h.method.name}($name)"
                } else {
                    null
                }
            }
        }
        assertThat(offenders).isEmpty()
    }

    @Test
    fun `공개 API 의 요청·응답 DTO 는 스키마 이름이 서로 겹치지 않는다`() {
        val reachable = linkedSetOf<Class<*>>()
        handlers.forEach { h ->
            collect(h.method.genericReturnType, reachable)
            h.method.parameters.filter { it.isAnnotationPresent(RequestBody::class.java) }
                .forEach { collect(it.parameterizedType, reachable) }
        }
        val collisions = reachable
            .groupBy { schemaName(it) }
            .filterValues { it.size > 1 }
            .mapValues { (_, classes) -> classes.map { it.name } }
        assertThat(reachable).describedAs("스캔한 DTO").isNotEmpty
        assertThat(collisions).isEmpty()
    }

    /** 이 저장소 패키지(`com.media.bus`)의 타입만 따라가며 필드·제네릭 인수까지 수집한다. */
    private fun collect(type: Type, out: MutableSet<Class<*>>) {
        when (type) {
            is ParameterizedType -> {
                collect(type.rawType, out)
                type.actualTypeArguments.forEach { collect(it, out) }
            }
            is WildcardType -> type.upperBounds.forEach { collect(it, out) }
            is Class<*> -> {
                if (type.isArray) return collect(type.componentType, out)
                if (!type.name.startsWith("com.media.bus.") || type.isEnum || !out.add(type)) return
                type.declaredFields
                    .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                    .forEach { collect(it.genericType, out) }
            }
        }
    }

    private fun isAdminPath(path: String): Boolean = path == "/api/v1/admin" || path.startsWith("/api/v1/admin/")

    /** `AuthorizeHandlerInterceptor`와 같은 규칙: 메서드에 있으면 클래스 것을 무시한다. */
    private fun effectiveAuthorize(h: Handler): Authorize? =
        AnnotatedElementUtils.findMergedAnnotation(h.method, Authorize::class.java)
            ?: AnnotatedElementUtils.findMergedAnnotation(h.controller, Authorize::class.java)

    /** 런타임 인가와 동일한 기준으로 관리자 전용 규칙을 식별한다. */
    private fun isAdminOnly(authorize: Authorize?): Boolean = authorize?.requiresAdminAudience() == true

    private fun schemaName(type: Class<*>): String =
        type.getAnnotation(Schema::class.java)?.name?.takeIf { it.isNotBlank() } ?: type.simpleName

    private fun mappingPaths(element: java.lang.reflect.AnnotatedElement): List<String> {
        val mapping = AnnotatedElementUtils.findMergedAnnotation(element, RequestMapping::class.java) ?: return emptyList()
        return (mapping.path.toList() + mapping.value.toList()).distinct()
    }

    private companion object {
        val DOMAIN_PACKAGES = listOf("com.media.bus.iam", "com.media.bus.stop", "com.media.bus.reservation")
    }
}
