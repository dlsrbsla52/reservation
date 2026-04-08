package com.media.bus.common.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.media.bus.common.result.type.CommonResult;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/// 통합 API 성공 응답 래퍼.
///
/// 어떤 인프라 클래스도 상속하지 않는 순수 독립 클래스.
/// 컨트롤러에서 직접 반환하면 Jackson이 그대로 직렬화한다.
///
/// ## 팩토리 메서드
/// - `success(T data)` — 데이터가 있는 성공 응답
/// - `success()` — 데이터 없는 성공 응답
/// - `successWithMessage(String)` — 커스텀 메시지가 필요한 성공 응답
/// - `page(List<E>)` — 목록 성공 응답
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

	private final String code;
	private final String message;
	private final T data;

	/// 데이터가 있는 성공 응답
	public static <T> ApiResponse<T> success(T data) {
		return ApiResponse.<T>builder()
				.code(CommonResult.SUCCESS.getCode())
				.message(CommonResult.SUCCESS.getMessage())
				.data(data)
				.build();
	}

	/// 데이터 없는 성공 응답
	public static ApiResponse<Void> success() {
		return ApiResponse.<Void>builder()
				.code(CommonResult.SUCCESS.getCode())
				.message(CommonResult.SUCCESS.getMessage())
				.build();
	}

	/// 커스텀 메시지가 포함된 데이터 없는 성공 응답
	public static ApiResponse<Void> successWithMessage(String message) {
		return ApiResponse.<Void>builder()
				.code(CommonResult.SUCCESS.getCode())
				.message(message)
				.build();
	}

	/// 목록 성공 응답 — `List<E>`를 `ListData<E>`로 감싸 반환
	public static <E> ApiResponse<ListData<E>> page(List<E> list) {
		return ApiResponse.<ListData<E>>builder()
				.code(CommonResult.SUCCESS.getCode())
				.message(CommonResult.SUCCESS.getMessage())
				.data(ListData.<E>builder().list(list).build())
				.build();
	}
}
