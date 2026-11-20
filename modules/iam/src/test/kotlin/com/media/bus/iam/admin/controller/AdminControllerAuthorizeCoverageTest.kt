package com.media.bus.iam.admin.controller

import com.media.bus.contract.security.annotation.Authorize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** `/api/v1/admin` 하위 컨트롤러의 **인가 누락을 빌드 단계에서 막는** 테스트. */
class AdminControllerAuthorizeCoverageTest {

    private val adminControllers: List<Class<*>> =
        ClassPathScanningCandidateComponentProvider(false)
            .apply { addIncludeFilter(AnnotationTypeFilter(RestController::class.java)) }
            .findCandidateComponents("com.media.bus.iam")
            .map { Class.forName(it.beanClassName) }
            .filter { clazz -> classPaths(clazz).any { it.startsWith("/api/v1/admin") } }

    private fun classPaths(clazz: Class<*>): List<String> =
        AnnotatedElementUtils.findMergedAnnotation(clazz, RequestMapping::class.java)
            ?.let { (it.path + it.value).toList() }
            .orEmpty()

    @Test
    fun `admin 컨트롤러를 스캔한다`() {
        assertThat(adminControllers).hasSizeGreaterThanOrEqualTo(7)
    }

    @Test
    fun `admin 핸들러 메서드는 모두 Authorize 가 적용된다`() {
        val missing = adminControllers.flatMap { clazz ->
            val classLevel = AnnotatedElementUtils.hasAnnotation(clazz, Authorize::class.java)
            clazz.declaredMethods
                .filter { AnnotatedElementUtils.hasAnnotation(it, RequestMapping::class.java) }
                .filterNot { classLevel || AnnotatedElementUtils.hasAnnotation(it, Authorize::class.java) }
                .map { "${clazz.simpleName}.${it.name}" }
        }

        assertThat(missing).describedAs("@Authorize 가 없는 admin 핸들러").isEmpty()
    }
}
