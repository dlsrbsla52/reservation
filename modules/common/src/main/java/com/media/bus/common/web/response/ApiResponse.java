package com.media.bus.common.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.media.bus.common.result.type.CommonResult;

import java.util.List;

/// 통합 API 성공 응답 래퍼.
///
/// 어떤 인프라 클래스도 상속하지 않는 순수 독립 레코드.
/// 컨트롤러에서 직접 반환하면 Jackson이 그대로 직렬화한다.
///
/// ## 팩토리 메서드
/// - `success(T data)` — 데이터가 있는 성공 응답
/// - `success()` — 데이터 없는 성공 응답
/// - `successWithMessage(String)` — 커스텀 메시지가 필요한 성공 응답
/// - `page(List<E>)` — 목록 성공 응답
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, String message, T data) {

	/// 데이터가 있는 성공 응답
	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(CommonResult.SUCCESS.getCode(), CommonResult.SUCCESS.getMessage(), data);
	}

	/// 데이터 없는 성공 응답
	public static ApiResponse<Void> success() {
		return new ApiResponse<>(CommonResult.SUCCESS.getCode(), CommonResult.SUCCESS.getMessage(), null);
	}

	/// 커스텀 메시지가 포함된 데이터 없는 성공 응답
	public static ApiResponse<Void> successWithMessage(String message) {
		return new ApiResponse<>(CommonResult.SUCCESS.getCode(), message, null);
	}

	/// 목록 성공 응답 — `List<E>`를 `ListData<E>`로 감싸 반환
	public static <E> ApiResponse<ListData<E>> page(List<E> list) {
		return new ApiResponse<>(CommonResult.SUCCESS.getCode(), CommonResult.SUCCESS.getMessage(),
				ListData.<E>builder().list(list).build());
	}
}