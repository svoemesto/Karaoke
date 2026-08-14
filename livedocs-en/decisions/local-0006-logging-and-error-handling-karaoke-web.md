# Local ADR-0006: Logging and error handling in `karaoke-web`

* **Status**: Accepted
* **Date**: 2026-08-14
* **Deciders**: Karaoke team

> **Russian version**: [../../../livedocs/architecture/decisions/local-0006-logging-and-error-handling-karaoke-web.md](../../../livedocs/architecture/decisions/local-0006-logging-and-error-handling-karaoke-web.md)
>
> **Note**: this is **local** ADR — describes convention for `karaoke-web`
> (different from `karaoke-app`, see local-0005).

## Context

`karaoke-web` (Spring Boot, separate from `karaoke-app`) has its specifics:
- HTTP-only — public endpoint + webvue3 admin.
- Does NOT start async tasks (queue, ML, render).
- Does NOT have heavy dependencies (ML, MLT, Demucs).
- Should log **each HTTP-request** with correlation-id.

`karaoke-app` uses MDC + structured logs (see local-0005). `karaoke-web` —
similar pattern, but with HTTP-correlation through **MDC + request-id filter**.

## Decision

**Convention for `karaoke-web` (HTTP request logging)**:

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

**Structured format**:

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

### Rules

1. **Each request** has `requestId` (UUID) in MDC + `X-Request-Id` response header.
2. **Each request** logged with `path`, `method`, `status`, `duration` (ms).
3. **Exception handler** logs + returns JSON response with error code (NOT HTML stacktrace).
4. **MDC.clear()** in `finally` (memory leak prevention).
5. **Secrets NOT logged** (see Constitution § VIII.5).

### Error response format

```json
{
  "code": "not_found",
  "message": "Song 12345 not found"
}
```

Standardized format for all endpoints (web + public + admin).

## Consequences

### Positive
- **Traceable**: `requestId` links HTTP-request with logs + error responses.
- **Standardized**: all errors return JSON with `code` + `message` (not HTML).
- **Performance monitoring**: `duration` in logs → can calculate p50/p95.
- **Correlation with karaoke-app**: same `requestId` (if passed through header).

### Negative
- **Discipline**: new endpoint must follow convention.
- **Error masking**: error details not shown to client (security, but harder to debug).

### Neutral
- **Logback** (Spring Boot default) — no new dependencies.

## Alternatives Considered

- **MDC inside each Controller**: rejected — boilerplate.
- **Aspect / AOP logging**: rejected — hidden flow, hard to debug.
- **JSON structured logs**: rejected — overkill for current volume.

## References

- [local-0005-structured-logging-karaoke-app.md](local-0005-structured-logging-karaoke-app.md) — similar pattern for `karaoke-app`.
- Constitution § VIII.5 — secrets via env (not in logs).
- [architecture/observability.md](../../observability.md) — where logs are observed.
- [architecture/idempotency.md](../../idempotency.md) — cross-cutting patterns.

## Code

- `karaoke-web/src/main/kotlin/.../filter/MdcLoggingFilter.kt` — filter.
- `karaoke-web/src/main/kotlin/.../exception/GlobalExceptionHandler.kt` — handler.
- `karaoke-web/src/main/resources/logback-spring.xml` — configuration.

## History

- Created: 2026-08-14
- Last updated: 2026-08-14