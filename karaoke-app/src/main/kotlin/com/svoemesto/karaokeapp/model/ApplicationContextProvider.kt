package com.svoemesto.karaokeapp.model

import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

/**
 * Класс Application Context Provider.
 *
 * @see docs/features/async-process-queue.md
 */
@Component
class ApplicationContextProvider : ApplicationContextAware {
    companion object {
        private lateinit var context: ApplicationContext

        @Suppress("unused")
        fun getCurrentApplicationContext(): ApplicationContext = context
    }

    @Throws(BeansException::class)
    override fun setApplicationContext(context: ApplicationContext) {
        Companion.context = context
    }
}
