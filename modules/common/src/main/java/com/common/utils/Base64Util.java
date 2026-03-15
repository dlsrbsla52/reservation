package com.common.utils;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Base64 인코딩 및 디코딩
 */
@SuppressWarnings("unused")
public class Base64Util {
	
	private Base64Util() {
		throw new IllegalStateException("Utility class");
	}
	
	public static String encode(String plainText) {
		return new String(encodeBytes(plainText.getBytes()), UTF_8);
	}
	
	public static byte[] encodeBytes(byte[] bytes) {
		return Base64.getEncoder().encode(bytes);
	}
	
	public static String decode(String encodedText) {
		return new String(decodeBytes(encodedText.getBytes()), UTF_8);
	}
	
	public static byte[] decodeBytes(byte[] bytes) {
		return Base64.getDecoder().decode(bytes);
	}
}
