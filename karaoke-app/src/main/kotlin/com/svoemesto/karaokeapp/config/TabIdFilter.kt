package com.svoemesto.karaokeapp.config

import com.svoemesto.karaokeapp.services.TabIdContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

// Читает заголовок X-Tab-Id (проставляется фронтендом webvue3 на каждый XHR-запрос, см.
// promisedXMLHttpRequest в utils.js) и кладёт его в TabIdContext на время обработки запроса. Это
// позволяет SseNotificationService.send() адресно доставлять MESSAGE/ERROR (ответ на действие
// пользователя) только той вкладке браузера, что инициировала запрос, а не всем подписчикам разом.
//
// TabIdContext.clear() в finally обязателен: поток обрабатывается из пула Tomcat и переиспользуется
// между запросами - без очистки значение "протечёт" в следующий, никак не связанный запрос.
@Component
class TabIdFilter : OncePerRequestFilter() {

    companion object {
        const val HEADER_NAME = "X-Tab-Id"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            TabIdContext.set(request.getHeader(HEADER_NAME))
            filterChain.doFilter(request, response)
        } finally {
            TabIdContext.clear()
        }
    }
}
