# Local ADR-0006: Логирование и error handling в `karaoke-web`

* **Status**: Accepted
* **Date**: 2026-08-14
* **Deciders**: команда Karaoke

> **English version**: [../../../livedocs-en/decisions/local-0006-logging-and-error-handling-karaoke-web.md](../../../livedocs-en/decisions/local-0006-logging-and-error-handling-karaoke-web.md)
>
> **Note**: this is **local** ADR — описывает конвенцию для `karaoke-web`
> (отличную от `karaoke-app`, см. local-0005).

## Context

`karaoke-web` (Spring Boot, отдельный от `karaoke-app`) имеет свои особенности:
- HTTP-only — публичный endpoint + webvue3 admin.
- НЕ запускает async-задачи (queue, ML, рендер).
- НЕ имеет тяжёлых зависимостей (ML, MLT, Demucs).
- Должен логировать **каждый HTTP-request** с correlation-id.

`karaoke-app` использует MDC + structured logs (см. local-0005). `karaoke-web` —
похожий паттерн, но с HTTP-correlation через **MDC + request-id filter**.

## Decision

**Конвенция для `karaoke-web` (HTTP request logging)**:

```kotlin
// filter/MdcLoggingFilter.kt
@Component
class MdcLoggingFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = request.getHeader("X-Request-Id")
            ?: UUID.randomUUID().toString()
        response.setHeader("X-Request-Id", requestId)
        MDC.put("requestId", requestId)
        MDC.put("path", request.requestURI)
        MDC.put("method", request.method)

        val startMs = System.currentTimeMillis()
        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMs = System.currentTimeMillis() - startMs
            MDC.put("status", response.status.toString())
            MDC.put("duration", durationMs.toString())
            log.info("http:request")  // structured key
            MDC.clear()
        }
    }
}
```

**Структурированный формат**:

```
2026-08-14 21:00:00 [INFO]  http:request requestId=abc-123 path=/api/public/songs/123 method=GET status=200 duration=42
2026-08-14 21:00:01 [WARN]  http:request requestId=def-456 path=/api/admin/users/5 method=PUT status=403 duration=15
2026-08-14 21:00:02 [ERROR] http:request requestId=ghi-789 path=/api/public/share/claim method=POST status=500 duration=1200
```

### Error handling pattern

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException): ResponseEntity<ApiError> {
        log.warn("error:notFound:path={}", MDC.get("path"), e)
        return ResponseEntity.status(404).body(ApiError("not_found", e.message ?: ""))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(e: Exception): ResponseEntity<ApiError> {
        log.error("error:generic:path={}", MDC.get("path"), e)
        return ResponseEntity.status(500).body(ApiError("internal_error", "Server error"))
    }
}

data class ApiError(val code: String, val message: String)
```

### Правила

1. **Каждый request** имеет `requestId` (UUID) в MDC + `X-Request-Id` response header.
2. **Каждый request** логируется с `path`, `method`, `status`, `duration` (ms).
3. **Exception handler** логирует + возвращает JSON-ответ с error code (НЕ HTML stacktrace).
4. **MDC.clear()** в `finally` (memory leak prevention).
5. **Секреты НЕ логируются** (см. Constitution § VIII.5).

### Error response format

```json
{
  "code": "not_found",
  "message": "Song 12345 not found"
}
```

Стандартизированный формат для всех endpoints (web + public + admin).

## Consequences

### Positive
- **Traceable**: `requestId` связывает HTTP-request с логами + error responses.
- **Standardized**: все ошибки возвращают JSON с `code` + `message` (не HTML).
- **Performance monitoring**: `duration` в логах → можно вычислить p50/p95.
- **Correlation с karaoke-app**: тот же `requestId` (если передан через header).

### Negative
- **Дисциплина**: новый endpoint должен следовать конвенции.
- **Error masking**: детали ошибки не показываются клиенту (security, но усложняет debug).

### Neutral
- **Logback** (Spring Boot default) — без новых зависимостей.

## Alternatives Considered

- **MDC внутри каждого Controller**: rejected — boilerplate.
- **Aspect / AOP logging**: rejected — скрытый flow, сложно debug.
- **JSON structured logs**: rejected — overkill для текущего объёма.

## References

- [local-0005-structured-logging-karaoke-app.md](local-0005-structured-logging-karaoke-app.md) — аналогичный паттерн для `karaoke-app`.
- Constitution § VIII.5 — секреты через env (не в логах).
- [architecture/observability.md](../../observability.md) — где логи наблюдаются.
- [architecture/idempotency.md](../../idempotency.md) — cross-cutting patterns.

## Код

- `karaoke-web/src/main/kotlin/.../filter/MdcLoggingFilter.kt` — фильтр.
- `karaoke-web/src/main/kotlin/.../exception/GlobalExceptionHandler.kt` — handler.
- `karaoke-web/src/main/resources/logback-spring.xml` — конфигурация.

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14