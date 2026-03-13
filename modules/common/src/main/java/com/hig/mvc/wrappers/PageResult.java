package com.hig.mvc.wrappers;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Paging Result 객체.
 * ArrayList 상속 대신 컴포지션으로 items 리스트를 보유한다.
 * 빌드 후 items는 불변(unmodifiable)이며, 페이징 메타(totalCnt, pageRows, pageNum)도 변경 불가하다.
 */
@Getter
@ToString
@EqualsAndHashCode
@Builder
public class PageResult<E> implements Serializable {

	@Serial
    private static final long serialVersionUID = -4202447654651104371L;

	@Singular
	private final List<E> items;

	private final long totalCnt;

	private final int pageRows;

	private final int pageNum;
}
