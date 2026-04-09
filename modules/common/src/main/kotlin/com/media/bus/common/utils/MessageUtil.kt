package com.media.bus.common.utils

import org.slf4j.LoggerFactory
import org.springframework.context.NoSuchMessageException
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.*

/**
 * ## Message Bundle 관련 Utility
 */
object MessageUtil {

    private val log = LoggerFactory.getLogger(MessageUtil::class.java)

    private val messageSource = ResourceBundleMessageSource().apply {
        setBasenames("messages/message", "messages/common-message")
        setDefaultEncoding("UTF-8")
    }


    fun getMessage(messageId: String): String = getMessage(messageId, null)


    fun getMessage(messageId: String, objects: Array<Any>?): String =
        getMessage(messageId, objects, Locale.getDefault())


    fun getMessage(messageId: String, objects: Array<Any>?, locale: Locale): String =
        try {
            messageSource.getMessage(messageId, objects, locale)
        } catch (e: NoSuchMessageException) {
            log.error("No message found under code '{}' for locale '{}'.", messageId, locale)
            ""
        }
}
