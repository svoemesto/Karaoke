# Component: karaoke-web/config

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: Spring config — interceptors, security, WebClient.

## Назначение

`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/config/` —
Spring configuration: WebMvc interceptors, security, WebClient
beans, properties.

## Файлы (5)

| # | Файл | Назначение |
|---|---|---|
| 1 | `WebMvcConfig.kt` | Регистрирует interceptors (auth, rate limit) + path patterns. |
| 2 | `SecurityConfig.kt` | Spring Security config. |
| 3 | `SiteAuthInterceptor.kt` | Проверка JWT-токена для `/api/public/account/*` (см. ниже). |
| 4 | `WebClientConfig.kt` | WebClient beans (yookassa-proxy, etc.). |
| 5 | `WebShareProperties.kt` | Share-ссылки настройки. |

## Детальные контракты

### `WebMvcConfig`

**Регистрирует**:
- `SiteAuthInterceptor` для `/api/public/account/**` + `/api/public/auth/me`.
- Два экземпляра `RateLimitInterceptor` для `/api/public/song-picture/` и `/api/public/song-vk-image/`.

```kotlin
@Configuration
class WebMvcConfig(
    private val siteAuthInterceptor: SiteAuthInterceptor,
    private val rateLimitInterceptor: RateLimitInterceptor,
    private val properties: KaraokeProperties,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(siteAuthInterceptor)
            .addPathPatterns("/api/public/account/**", "/api/public/auth/me")
        // ...
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/api/public/song-picture/**")
        // ...
    }
}
```

### `SiteAuthInterceptor`

**Файл**: `karaoke-web/.../config/SiteAuthInterceptor.kt`.

**Логика**:

1. Извлекает `Authorization: Bearer <token>` из header.
2. `siteUserTokenService.resolveToken(token, WORKING_DATABASE)` →
   `SiteUser?`.
3. Если null → 401 + `{"error":"unauthorized"}`.
4. Иначе → `request.setAttribute("siteUser", user)`.

**Применяется**: к `/api/public/account/*` (защита личного
кабинета).

**NB**: имя атрибута `"siteUser"` — должен совпадать с тем, что
читают контроллеры (`request.getAttribute("siteUser") as SiteUser`).

### `WebClientConfig`

**Beans**:

- `yookassaWebClient` — для `PaymentService` (см. [monetization
  domain](../../monetization/domain.md)).
- `minioWebClient` — возможно (для `StorageApiClientWeb`).
- Другие.

**Конфигурация**: baseUrl из env (`KaraokeProperties`),
connection timeout, retry policy, etc.

### `SecurityConfig`

Spring Security config. На karaoke-web — минимальный (нет auth
на публичных endpoint'ах). Auth делает `SiteAuthInterceptor`
вручную.

### `WebShareProperties`

Конфигурация для share-ссылок. Настройки через env.

## Связь с другими компонентами

- **Integration** ([integration domain](../../integration/domain.md)) —
  WebClient beans.
- **SSE** ([sse domain](../../sse/domain.md)) — нет прямой связи
  (SSE на karaoke-app).

## Известные TODO

- [ ] **Каждый interceptor** — детальный flow (Pass 343+).
- [ ] **CORS** — где настроен, какие origin.
- [ ] **WebClient timeouts** — точные значения.
- [ ] **SecurityConfig** — какие фильтры активны.

## Changelog

- **Pass 365** (2026-09-09): Initial. Автор: agent (Karaoke).