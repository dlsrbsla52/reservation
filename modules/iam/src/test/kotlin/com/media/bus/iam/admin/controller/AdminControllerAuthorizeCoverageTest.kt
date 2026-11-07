package com.media.bus.iam.admin.controller

import com.media.bus.contract.security.annotation.Authorize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `/api/v1/admin` 하위 컨트롤러의 **인가 누락을 빌드 단계에서 막는** 테스트.
 *
 * gateway 는 이 경로에 관리자 카테고리·관리자 발급처만 통과시키지만, 그것은 거친 1차 차단이다.
 * `ADMIN_USER` 와 `ADMIN_MASTER` 의 구분 같은 세부 인가는 `@Authorize` 가 담당하므로,
 * 핸들러 메서드마다 메서드 또는 클래스 레벨 `@Authorize` 가 반드시 있어야 한다.
 * (`AuthorizeHandlerInterceptor` 는 `@Authorize` 가 없으면 인가 검사를 건너뛴다 — 기본 허용)
 */
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
        // 스캔 대상이 비면 아래 검사가 공허하게 통과하므로 최소 개수를 보장한다.
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
