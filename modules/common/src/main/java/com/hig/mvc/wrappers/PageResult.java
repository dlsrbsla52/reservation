
package com.hig.mvc.wrappers;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

/**
 * Paging Result 객체
 * @author realninano
 * @since 2019.07.22.
 */
@Setter
@Getter
@EqualsAndHashCode(callSuper=false)
public class PageResult<E> extends ArrayList<E> {

	private static final long serialVersionUID = -4202447654651104371L;

	private long totalCnt;
	
	private int pageRows;
	
	private int pageNum;
	
	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("PageResult [totalCnt=")
					.append(totalCnt)
					.append(", pageRows=")
					.append(pageRows)
					.append(", pageNum=")
					.append(pageNum)
					.append(", list=")
					.append(super.toString())
					.append("]");
		
		return stringBuilder.toString();
	}
}
