package com.media.bus.common.utils;

import com.media.bus.common.exceptions.BaseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;


/**
 * 즐겨찾는 유틸 클래스
 */
@SuppressWarnings("unused")
public class FavoriteUtil {

    private FavoriteUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * source가 null이거나 공백이면 replace를 리턴하고 아니면 source를 리턴함
     *
     *  @return source or replace
     */
    public static String ifEmpty(String source, String replace) {
        if(source == null || source.isEmpty()) {
            return replace == null ? "" : replace;
        }
        return source;
    }

    /**
     * source가 숫자 형식이 아닐 경우 0을 리턴함
     *
     * @return source or 0
     */
    public static int ifNotNumericToZero(String source) {
        return ifNotNumericToInt(source, 0);
    }

    /**
     * source가 숫자 형식이 아닐 경우 replace를 리턴함
     *
     * @return source or replace
     */
    public static int ifNotNumericToInt(String source, int replace) {
        if(!isNumeric(source)) {
            return replace;
        }
        return Integer.parseInt(source);
    }


    /**
     * 숫자형식 여부를 리턴함
     *
     * @return true or false
     */
    public static boolean isNumeric(String str){
        if(str == null || str.isEmpty()) {
            return false;
        }
        String regEx = "^[+-]?[0-9]+$";
        return str.matches(regEx);

    }

    /**
     * target 문장에서 prefix가 존재할 경우 prefix를 제거하고 존재하지 않을 경우 전체 문장을 리턴함
     *
     * @return prefix가 제거된 문장 또는 전체 문장
     */
    public static String removeStartWith(String target, String prefix) {
        if (target != null && target.startsWith(prefix)) {
            return target.replaceAll(prefix, "");
        }
        return target;
    }

    /**
     * 파라미터로 넘어온 JSON 스타일의 메시지를 요청받은 타입의 객체로 변환하여 리턴함
     *
     * @return T타입의 객체
     */
    public static <T> T convertTo(String message, Class<T> clazz) {
        try {
            return new ObjectMapper().readValue(message, clazz);
        } catch (IOException e) {
            throw new BaseException(e);
        }
    }

    /**
     * 파라미터로 받은 객체를 Map으로 변환하여 리턴함
     *
     * @return Map 객체
     */
    public static <T> Map<String, Object> convertToMap(T source) {
        Map<String, Object> returnMap = new HashMap<>();
        try {
            Field[] fields = source.getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                String key = field.getName();
                if (int.class == field.getType() || Integer.class == field.getType()) {
                    returnMap.put(key, field.get(source));
                } else if (long.class == field.getType() || Long.class == field.getType()) {
                    returnMap.put(key, field.get(source));
                } else if (double.class == field.getType() || Double.class == field.getType()) {
                    returnMap.put(key, field.get(source));
                } else if (boolean.class == field.getType() || Boolean.class == field.getType()) {
                    returnMap.put(key, field.get(source));
                } else if (String.class == field.getType()) {
                    returnMap.put(key, field.get(source));
                } else {
                    returnMap.put(key, field.get(source));
                }
            }
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new BaseException(e);
        }
        return returnMap;
    }

    /**
     * 구분 문자 이후의 문자열을 반환한다. 전체 문자열 또는 구분 문자열이 null일 경우 공백을 리턴함
     *
     * @return 마지막 문자열
     */
    public static String getExtractLastString(String fullString, String delimiter) {
        if (fullString == null || delimiter == null) {
            return "";
        }
        return fullString.substring(fullString.lastIndexOf(delimiter) + 1);
    }

}
