package com.media.bus.common.validation

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.slf4j.LoggerFactory

/**
 * ## 진입점 입력 검증
 *
 * DTO에 선언된 Bean Validation 제약(`@NotBlank`, `@Size`, `@Positive` 등)을 서비스·Facade 진입점에서
 * 직접 실행하고, 실패하면 `BusinessException(CommonResult.VALIDATION_FAIL)`을 던진다.
 *
 * 설계 의도:
 * - 모듈 간 호출이 HTTP에서 같은 프로세스 안의 직접 호출로 바뀌면 제공자 컨트롤러의 `@Valid`가 실행되지 않는다.
 *   규칙을 손으로 다시 쓰지 않고, DTO 애노테이션 한 곳을 원본으로 둔 채 진입점에서도 같은 규칙을 실행한다.
 * - `@Validated` 프록시 방식은 쓰지 않는다. 실패 시 `ConstraintViolationException`이 나는데
 *   `ExceptionAdvisor`에 처리기가 없어 500/`00100`이 되고, 단위 테스트로도 확인할 수 없다.
 * - 외부 응답은 기존 S2S 경로와 같게 맞춘다. 지금 제공자 `@Valid` 실패는 400/`00260`과 기본 메시지로
 *   전달되고 필드 오류 목록은 S2S 변환에서 빠지므로, 여기서도 기본 메시지로 던지고 위반 내역은 로그로만 남긴다.
 */
object InputValidator {

    private val log = LoggerFactory.getLogger(InputValidator::class.java)

    // Validator 는 스레드 안전하고 생성 비용이 커서 한 번만 만든다.
    private val validator: Validator = Validation.buildDefaultValidatorFactory().use { it.validator }

    /**
     * `target`의 제약을 검증한다.
     *
     * @throws BusinessException 위반이 하나라도 있으면 400 `VALIDATION_FAIL`
     */
    fun validate(target: Any) {
        val violations = validator.validate(target)
        if (violations.isEmpty()) return
        val details = violations
            .sortedBy { it.propertyPath.toString() }
            .joinToString { "${it.propertyPath}: ${it.message}" }
        log.warn("[InputValidator] 입력 검증 실패: type={}, violations=[{}]", target.javaClass.simpleName, details)
        throw BusinessException(CommonResult.VALIDATION_FAIL)
    }
}
