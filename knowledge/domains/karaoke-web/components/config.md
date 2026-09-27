# Component: karaoke-web/config

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: Spring config — interceptors, security, WebClient.


## Ответственность | Responsibility


Spring config — interceptors, security, WebClient.

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
| 4 | `WebClientConfig.kt` | WebClient beans (yookassa-proxy, captcha, storage). |
| 5 | `WebShareProperties.kt` | Share-ссылки настройки. |

## Интерфейсы и Контракты | Interfaces and Contracts

### `WebMvcConfig`

**Регистрирует** (фактические patterns из кода):
- `SiteAuthInterceptor` для `/api/public/account/**`,
  `/api/public/auth/me`, `/api/public/auth/logout`, `/api/siteusers/**`;
  исключение — `/api/public/account/subscription/tariffs`
  (витрина тарифов видна анонимам).
- Два экземпляра `RateLimitInterceptor` для
  `/api/public/song-picture/**` и `/api/public/song-vk-image/**`;
  `endpointName`/`limitPerMinute` берутся из
  `KaraokeProperties.rateLimitSongPicturePerMinute` и
  `rateLimitSongVkImagePerMinute`.

```kotlin
@Configuration
class WebMvcConfig(
    private val siteAuthInterceptor: SiteAuthInterceptor,
    private val rateLimitInterceptor: RateLimitInterceptor,
    private val properties: KaraokeProperties,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(siteAuthInterceptor)
            .addPathPatterns(
                "/api/public/account/**", "/api/public/auth/me",
                "/api/public/auth/logout", "/api/siteusers/**",
            )
            .excludePathPatterns("/api/public/account/subscription/tariffs")

        rateLimitInterceptor.apply {
            endpointName = "song-picture"
            limitPerMinute = properties.rateLimitSongPicturePerMinute
        }
        // ... и второй экземпляр для /api/public/song-vk-image/**
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
кабинета) и `/api/siteusers/**` (share-admin).

**NB**: имя атрибута — константа `SiteAuthInterceptor.SITE_USER_ATTR = "siteUser"`,
должна совпадать с тем, что читают контроллеры
(`request.getAttribute("siteUser") as SiteUser`).

### `WebClientConfig`

**Beans** (фактический список):

- `smKaraokeWebClient` — `@Primary`, `baseUrl = "https://sm-karaoke.ru/api/storage"`,
  default header `User-Agent`. `@Primary` обязателен: другие места
  (например `StorageApiClientWeb`) инжектят `WebClient` без
  `@Qualifier`.
- `yandexCaptchaWebClient` (`@Qualifier`) — `baseUrl` из
  `${captcha.proxy-url}`.
- `yookassaWebClient` (`@Qualifier`) — `baseUrl` из
  `${yookassa.proxy-url}` + Basic-заголовок
  `Base64(shopId:secretKey)` из `${yookassa.shop-id}` /
  `${yookassa.secret-key}`.

Таймауты и retry-policy в коде **не настроены** (в билдере только
`baseUrl` и default-заголовки) — это открытый TODO, а не факт.

### `SecurityConfig`

`@EnableWebSecurity`; `authorizeHttpRequests` — `/api/**` и любые
другие запросы `permitAll()`, `csrf` отключён. Собственный бин
`passwordEncoder()` нужен потому, что `karaoke-web` не сканирует
пакет `com.svoemesto.karaokeapp.config` (одноимённый бин из
karaoke-app сюда не попадает). Авторизацию делает
`SiteAuthInterceptor` вручную.

### `WebShareProperties`

`@Component @ConfigurationProperties(prefix = "karaoke.share")` —
настройки локальны для karaoke-web (не наследуются из karaoke-app).
Дефолты полей — в таблице раздела «Логика и Алгоритмы».

## Логика и Алгоритмы | Logic and Algorithms

### Правило резолва настроек

Приоритет источников — стандартный для Spring Boot (от старшего к
младшему):

1. аргументы командной строки (`--key=value`);
2. переменные окружения (OS env контейнера/процесса);
3. `application.yml` из classpath;
4. дефолт в коде — `@Value("${key:default}")` или значение поля
   `@ConfigurationProperties`-класса.

Факты, проверяемые по репозиторию:

- `karaoke-web/src/main/resources/application.yml` — **единственный**
  конфиг-файл модуля; он упакован в bootJar
  (`BOOT-INF/classes/application.yml` в `karaoke-web/build/libs/karaoke-web-1.jar`).
- Секций `karaoke-web:` и `karaoke.share:` в `application.yml` нет
  (проверено grep) — их значения приходят из env/CLI либо берутся из
  дефолтов кода.
- Env-имена Spring выводит relaxed binding'ом: точка/дефис → `_`,
  верхний регистр. Примеры: `karaoke-web.events.sampling-anon` ←
  `KARAOKE_WEB_EVENTS_SAMPLING_ANON`;
  `karaoke.share.sweep-interval-seconds` ←
  `KARAOKE_SHARE_SWEEP_INTERVAL_SECONDS`.
- `deploy/docker-compose-web.yml` и
  `deploy/prod-single-host/docker-compose-web.yml` задают только
  `WORK_IN_CONTAINER=1`, `WORK_ON_SERVER`, `DB_*`, `STORAGE_*`,
  `STEMJOBS_INTERNAL_SECRET` (на проде ещё `CAPTCHA_PROXY_URL`,
  `YOOKASSA_*`, `VK_*`, `STORAGE_PROXY_URL`). Переменных
  `KARAOKE_WEB_*` / `KARAOKE_SHARE_*` в deploy нет — то есть на
  2026-09-27 действуют дефолты кода, а переопределение возможно
  через env контейнера.

### Дефолты: `KaraokeProperties` (`@Value` + `coerceAtLeast(1)`)

| Property | Env | Дефолт |
|---|---|---|
| `karaoke-web.events.sampling-anon` | `KARAOKE_WEB_EVENTS_SAMPLING_ANON` | 20 |
| `karaoke-web.events.sampling-logged` | `KARAOKE_WEB_EVENTS_SAMPLING_LOGGED` | 5 |
| `karaoke-web.events.sampling-admin` | `KARAOKE_WEB_EVENTS_SAMPLING_ADMIN` | 1 |
| `karaoke-web.events.dedup-ttl-seconds` | `KARAOKE_WEB_EVENTS_DEDUP_TTL_SECONDS` | 30 |
| `karaoke-web.events.retention-days` | `KARAOKE_WEB_EVENTS_RETENTION_DAYS` | 7 |
| `karaoke-web.debug-db.enabled` | `KARAOKE_WEB_DEBUG_DB_ENABLED` | `false` |
| `karaoke-web.debug-db.allowed-ips` | `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` | `""` |
| `karaoke-web.rate-limit.song-picture-per-minute` | `KARAOKE_WEB_RATE_LIMIT_SONG_PICTURE_PER_MINUTE` | 60 |
| `karaoke-web.rate-limit.song-vk-image-per-minute` | `KARAOKE_WEB_RATE_LIMIT_SONG_VK_IMAGE_PER_MINUTE` | 60 |

Числовые значения при чтении проходят `coerceAtLeast(1)`
(нижняя граница — 1): нулевые/отрицательные значения из env
не отключают sampling и не обнуляют TTL.

### Дефолты: `WebShareProperties` (`karaoke.share.*`)

| Property | Дефолт |
|---|---|
| `max-active-per-user` | 5 |
| `max-generations-per-day` | 30 |
| `max-reissues-per-song-per-hour` | 3 |
| `claim-rate-limit-per-ip-per-min` | 10 |
| `max-concurrent-sessions` | 2 |
| `lease-ttl-seconds` | 90 |
| `grace-pause-seconds` | 120 |
| `sweep-interval-seconds` | 60 |
| `heartbeat-interval-seconds` | 25 |

`heartbeat-interval-seconds` (25) обязан быть меньше
`lease-ttl-seconds` (90) — один пропущенный heartbeat не должен
отзывать lease. `sweepIntervalSeconds` читается ещё и напрямую в
`@Scheduled` (`SongShareLinkService`/`ShareLinkSweeper`,
`${karaoke.share.sweep-interval-seconds:60}`), то есть значение
дублируется в двух местах.

### Значения `application.yml`: есть дефолт / обязательны

- С дефолтом через `${ENV:default}`: `WORK_IN_CONTAINER`→`0`,
  `PUBLIC_SITE_URL`→`https://sm-karaoke.ru`,
  `CAPTCHA_PROXY_URL`→`https://smartcaptcha.yandexcloud.net`,
  `YOOKASSA_API_URL`→`https://api.yookassa.ru`,
  `YOOKASSA_SHOP_ID`/`YOOKASSA_SECRET_KEY`→пусто,
  `STEMJOBS_TEMP_DIR`→`/tmp/stemjobs`,
  `STEMJOBS_INTERNAL_SECRET`→пусто,
  `DB_REMOTE_HOST`→`188.127.240.124`, `DB_REMOTE_PORT`→`8832`,
  `STORAGE_PROXY_URL`→`http://minio-proxy`,
  `VK_APP_ID`→`54704234`, `VK_ID_CLIENT_ID`→`0`, а также
  `VK_*`/`VK_ID_*` redirect-uri.
- Без дефолта (отсутствие env/CLI → ошибка старта):
  `WORK_ON_SERVER`, `DB_LOCAL_POSTGRES_USER`, `DB_LOCAL_POSTGRES_PASSWORD`,
  `DB_SERVER_POSTGRES_USER`, `DB_SERVER_POSTGRES_PASSWORD`,
  `STORAGE_KEY`, `STORAGE_SECRET`, `STORAGE_FOLDER`,
  `STORAGE_PORT_HOST`, `STORAGE_PORT_INSIDE_CONTAINER`,
  `STORAGE_CONTAINER_NAME`, `STORAGE_CONSOLE_PORT_HOST`,
  `STORAGE_CONSOLE_PORT_INSIDE_CONTAINER`.

### Кто читает настройки

- `KaraokeProperties` — interceptors (`WebMvcConfig`),
  `SamplingFilter`, `DedupCache`, `EventsRetentionScheduler`,
  `DebugDbAccessGuard`, `RateLimitInterceptor`
  (см. [services-overview.md](services-overview.md)).
- `WebShareProperties` — `SongShareLinkService`, `ShareLinkSweeper`
  (см. [song-share-link-service.md](song-share-link-service.md)).
- Прямые `@Value`: `KaraokeWebService` (`work-in-container`,
  `work-on-server`, `db-*`), `WebClientConfig` (`captcha.proxy-url`,
  `yookassa.*`), контроллеры (`app.public-site-url` в
  `PublicShareController`).

## Зависимости | Dependencies

- **Integration** ([integration domain](../../integration/domain.md)) —
  WebClient beans.
- **SSE** ([sse domain](../../sse/domain.md)) — нет прямой связи
  (SSE на karaoke-app).

## Известные TODO

- [ ] **Каждый interceptor** — детальный flow (Pass 343+).
- [ ] **CORS** — где настроен, какие origin.
- [ ] **WebClient timeouts** — точные значения (в коде не заданы).
- [ ] **SecurityConfig** — какие фильтры активны.
- [ ] **Дубль `sweep-interval-seconds`** — `@Scheduled` читает
  property напрямую, минуя `WebShareProperties`.

## Changelog

- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 365** (2026-09-09): Initial. Автор: agent (Karaoke).
