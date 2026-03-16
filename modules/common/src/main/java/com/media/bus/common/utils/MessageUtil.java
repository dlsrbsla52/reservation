package com.media.bus.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

/**
 * Message Bundle 관련 Utility
 */
@Slf4j
public class MessageUtil {
	
	private static final ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
	static {
		messageSource.setBasenames("messages/message", "messages/common-message");
		messageSource.setDefaultEncoding("UTF-8");
	}
	
	private MessageUtil() {
		throw new IllegalStateException("Utility class");
	}
	
	public static String getMessage(String messageId) {
		return getMessage(messageId, null);
	}
	
	public static String getMessage(String messageId, Object[] objects) {
		return getMessage(messageId, objects, Locale.getDefault());
	}
	
	public static String getMessage(String messageId, Object[] objects, Locale locale) {
		try {
			return messageSource.getMessage(messageId, objects, locale);
		} catch(NoSuchMessageException e) {
			log.error("No message found under code '{}' for locale '{}'.", messageId, locale);
			return "";
		}
	}

}
