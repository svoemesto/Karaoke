package com.svoemesto.karaokeapp.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Токен-барьер для admin-API (BR-114, аудит #336, пакет PKG-S3-22).
 *
 * **Что было.** В `SecurityConfig` единственное правило — `authenticated()` для
 * `/api/private/…` (контроллеров с таким путём в репозитории нет), всё остальное
 * `permitAll()`; механизма аутентификации (formLogin/httpBasic/UserDetailsService)
 * в приложении нет вовсе. То есть все admin-эндпоинты, включая меняющие состояние
 * (`/api/admin/processes/…`, `/api/cleanup/…`, `/api/dictionaries/…`), были
 * доступны анонимно любому, кто дотянется до порта приложения.
 *
 * **Почему не `authenticated()`.** Включить `authenticated()` без механизма входа
 * нельзя: приложение не выпускает ни одного аутентифицированного субъекта, и админка
 * (единственный UI) получила бы 401 на каждый запрос. Выбор механизма аутентификации
 * (form-login / mTLS / basic-auth на nginx / SSO) — **решение владельца**; в коде
 * подготовлен безопасный шов, который включается настройкой и не меняет поведение
 * по умолчанию.
 *
 * **Как работает.** Если задан `karaoke.security.admin-token` (env
 * `KARAOKE_ADMIN_TOKEN`), запросы к [PROTECTED_PREFIXES] обязаны нести заголовок
 * [TOKEN_HEADER] с этим значением, иначе — 401. Если токен не задан, фильтр
 * пропускает всё, но пишет `WARN` при первом запросе к admin-API: открытый периметр
 * виден в логе, а не только в отчёте аудита.
 *
 * **Чего фильтр НЕ делает.** Он не заменяет аутентификацию субъекта: `X-Admin-Username`
 * по-прежнему приходит от клиента и не является идентичностью. Это осознанно оставлено
 * владельцу вместе с выбором механизма.
 *
 * @see SecurityConfig
 * @see specs/394-pkg-s3-22/report.md
 */
@Component
class AdminApiTokenFilter(
    @Value("\${karaoke.security.admin-token:}")
    private val adminToken: String = "",
) : OncePerRequestFilter() {
    companion object {
        /** Заголовок с общим секретом admin-API. */
        const val TOKEN_HEADER = "X-Admin-Token"

        /** Префиксы admin-эндпоинтов (меняющих состояние или раскрывающих внутренности). */
        val PROTECTED_PREFIXES: List<String> =
            listOf(
                "/api/admin/",
                "/api/health/",
                "/api/monitor/",
                "/api/cleanup/",
                "/api/dictionaries/",
                "/api/news/",
                "/api/promorules/",
                "/api/publicsettings/",
                "/api/sharelinks/",
                "/api/stemjobs/",
            )

        // Имя НЕ `logger`: у Spring `GenericFilterBean` есть своё поле `logger`,
        // и Kotlin-свойство с тем же именем даёт ошибку KT-56386 при обращении.
        private val adminApiLog = LoggerFactory.getLogger(AdminApiTokenFilter::class.java)

        /** Первое предупреждение об открытом admin-API — один раз на процесс. */
        private val warnedAboutOpenApi = AtomicBoolean(false)

        /** Сравнение без утечки длины/времени через `MessageDigest.isEqual`. */
        internal fun tokenMatches(
            expected: String,
            actual: String?,
        ): Boolean {
            if (actual == null) return false
            return MessageDigest.isEqual(
                expected.toByteArray(StandardCharsets.UTF_8),
                actual.toByteArray(StandardCharsets.UTF_8),
            )
        }

        /** Относится ли путь к защищаемому admin-периметру. */
        internal fun isProtectedPath(path: String): Boolean = PROTECTED_PREFIXES.any { path.startsWith(it) }
    }

    private val enabled: Boolean = adminToken.isNotBlank()

    public override fun shouldNotFilter(request: HttpServletRequest): Boolean = !isProtectedPath(request.requestURI)

    // `public` — не для расширения API, а чтобы регрессионный тест вызывал шов напрямую
    // (в Kotlin protected-переопределение Java-метода по умолчанию тоже protected).
    public override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!enabled) {
            if (warnedAboutOpenApi.compareAndSet(false, true)) {
                val prefixes = PROTECTED_PREFIXES.joinToString(", ")
                adminApiLog.warn(
                    "Admin-API открыт: karaoke.security.admin-token не задан (BR-114). " +
                        "Любой, кто дотянется до порта приложения, может менять состояние через $prefixes.",
                )
            }
            filterChain.doFilter(request, response)
            return
        }
        if (tokenMatches(adminToken, request.getHeader(TOKEN_HEADER))) {
            filterChain.doFilter(request, response)
            return
        }
        adminApiLog.warn(
            "Admin-API: отклонён запрос без валидного " + TOKEN_HEADER +
                " — " + request.method + " " + request.requestURI,
        )
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Требуется заголовок $TOKEN_HEADER")
    }
}
