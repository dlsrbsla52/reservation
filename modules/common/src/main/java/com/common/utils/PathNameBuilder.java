package com.common.utils;

import java.util.Arrays;

/**
 * Path name을 조합하기 위한 유틸 클래스
 */
@SuppressWarnings("unused")
public class PathNameBuilder {
	
	private PathNameBuilder() {
		throw new IllegalStateException("Utility class");
	}
	  
	public static String get(String...strings) {
		return get(false, strings);
	}
	
	public static String get(boolean isDirectory, String...strings) {
		StringBuilder stringBuilder = new StringBuilder();
		Arrays.stream(strings).forEach(string-> stringBuilder.append("/").append(string));
		if (isDirectory) {
			return stringBuilder.append("/").substring(1);
		} else {
			return stringBuilder.substring(1);
		}
	}
}
