package com.svoemesto.karaokeweb.util

import jakarta.servlet.http.HttpServletRequest

// Правило определения адреса источника запроса (source address) — единое для всех
// потребителей: rate-limit, IP-allowlist debug-endpoint'а, rate-limit claim'ов
// share-ссылок, `client_ip_hash` в БД, аналитика `tbl_events`.
//
// ТОПОЛОГИЯ (прод, deploy/prod-single-host):
//   клиент → host-nginx (443) → 127.0.0.1:8897 → контейнер `karaoke-web`.
// В контейнере TCP-peer — это docker-шлюз (172.18.0.1), реальный адрес клиента
// в нём недоступен: единственный источник адреса — заголовки от НАШЕГО nginx.
//
// ЧТО ВЫСТАВЛЯЕТ НАШ nginx (deploy/prod-single-host/80to8897, все location'ы):
//   proxy_set_header X-Real-IP $remote_addr;            ← адрес клиента, клиентское
//                                                          значение ПЕРЕЗАПИСЫВАЕТСЯ;
//   proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
//                                                        ← "<то, что прислал клиент>, $remote_addr".
//
// ПОЧЕМУ НЕЛЬЗЯ БРАТЬ ПЕРВЫЙ ЭЛЕМЕНТ X-Forwarded-For (дефект аудита BR-003
// «xff-first-element»): клиент присылает СВОЙ `X-Forwarded-For`, nginx дописывает
// реальный адрес в КОНЕЦ — префикс остаётся под контролем клиента. Старый код
// (`ClientIpResolver`, `RateLimitInterceptor.clientIp`) брал именно первый элемент,
// поэтому анонимный клиент мог:
//   * обходить rate-limit (новый bucket на каждый подделанный XFF);
//   * обходить IP-allowlist `/api/public/debug/db` (подставить разрешённый адрес);
//   * писать недостоверный `client_ip_hash` в БД и недостоверный IP в аналитику.
//
// ПРАВИЛО (в порядке приоритета):
//   1. `X-Real-IP` — его наш nginx ПЕРЕЗАПИСЫВАЕТ значением `$remote_addr`,
//      поэтому клиент подделать его не может (в отличие от любого элемента XFF
//      в заголовке, присланном клиентом).
//   2. ПОСЛЕДНИЙ непустой элемент `X-Forwarded-For` — fallback для прокси, которые
//      не выставляют `X-Real-IP`, но дописывают реальный адрес в конец цепочки
//      (как наш nginx через `$proxy_add_x_forwarded_for`). Клиентский префикс
//      игнорируется.
//   3. `request.remoteHost` — dev-окружение без nginx перед приложением
//      (в контейнере — адрес docker-шлюза).
//
// Тот же приоритет (`X-Real-IP` → `remoteHost`) применяет журнал безопасности
// синхронизации и публичных настроек (аудит BR-011, OpenProject #357) — там
// источник берётся НАМЕРЕННО уже (без XFF), потому что запись в журнале безопасности
// не должна опираться на заголовок, который клиент контролирует хотя бы частично.

/**
 * Singleton-объект Client Ip Resolver.
 *
 * Единственное место в проекте, где решается, «какой адрес считать адресом
 * источника запроса». Потребители: `RateLimitInterceptor`, `DebugDbAccessGuard`,
 * `SongShareLinkService` (rate-limit claim'ов и `client_ip_hash`), `MainController`
 * (аналитика `tbl_events`).
 *
 * @see AGENTS.md
 * @see docs/features/client-ip-resolution.md
 * @see specs/336-code-audit/findings/P-37.md (P-37-03 — BR-003 «xff-first-element»)
 */
object ClientIpResolver {
    /** Заголовок, который наш nginx перезаписывает значением `$remote_addr`. */
    const val HEADER_REAL_IP = "X-Real-IP"

    /** Заголовок со списком адресов; реальный адрес дописывается в конец цепочки. */
    const val HEADER_FORWARDED_FOR = "X-Forwarded-For"

    /** Значение, когда адрес источника определить не удалось. */
    const val UNKNOWN = "unknown"

    /**
     * Адрес источника запроса.
     *
     * @param request HTTP-запрос.
     * @return адрес источника (IP-строка) либо [UNKNOWN].
     */
    fun resolve(request: HttpServletRequest): String =
        resolve(
            xRealIp = request.getHeader(HEADER_REAL_IP),
            xForwardedFor = request.getHeader(HEADER_FORWARDED_FOR),
            remoteHost = request.remoteHost,
        )

    /**
     * Чистая функция правила — отделена от [HttpServletRequest], чтобы правило
     * можно было проверить юнит-тестом без servlet-окружения.
     *
     * @param xRealIp значение заголовка `X-Real-IP` (или `null`).
     * @param xForwardedFor значение заголовка `X-Forwarded-For` (или `null`).
     * @param remoteHost TCP-peer запроса (или `null`).
     * @return адрес источника (IP-строка) либо [UNKNOWN].
     */
    internal fun resolve(
        xRealIp: String?,
        xForwardedFor: String?,
        remoteHost: String?,
    ): String {
        firstToken(xRealIp)?.let { return it }
        lastToken(xForwardedFor)?.let { return it }
        return remoteHost?.trim()?.takeIf { it.isNotEmpty() } ?: UNKNOWN
    }

    /**
     * Первый непустой элемент списка — для `X-Real-IP` (по конвенции это один
     * адрес; если список всё же пришёл — берём ближайший к клиенту, как в XFF).
     */
    private fun firstToken(header: String?): String? = header?.split(",")?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }

    /**
     * Последний непустой элемент списка — для `X-Forwarded-For`: реальный адрес
     * дописывается прокси в конец цепочки, клиентский префикс игнорируется.
     */
    private fun lastToken(header: String?): String? = header?.split(",")?.map { it.trim() }?.lastOrNull { it.isNotEmpty() }
}
