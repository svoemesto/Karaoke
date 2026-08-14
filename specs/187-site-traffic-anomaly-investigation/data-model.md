# Data Model: Аудит источников аномальной нагрузки

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-08-14

## Замечание

Эта фича — **performance/infra оптимизация**. Существующая схема БД НЕ меняется. `tbl_events` остаётся как есть (только добавляется retention scheduler для её очистки). Новые таблицы НЕ вводятся.

Этот документ фиксирует:
1. **Существующие сущности, задействованные в фиче**.
2. **Новые runtime-объекты** (in-memory caches, фильтры).
3. **Новые методы на существующих сущностях** (rate-limit, sampling, dedup).
4. **Границы изменений** — что НЕ меняется.

---

## Существующие сущности (задействованные)

### Backend (Kotlin)

#### `AuthorTilePublicDto` (data class)
- **Модуль**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt`.
- **Назначение**: плитка автора для сетки «Закрома» в `karaoke-public`.
- **Изменения**:
  - Поле `authorPictureUrl` MUST возвращать прямой URL `/minio/karaoke/<encoded path>` (FR-002).
  - Старый формат `/api/public/picture?file=<encoded>` остаётся для legacy-вызовов, но НЕ формируется в этом DTO.

#### `MainController.doRegisterEvent` (Spring Controller method)
- **Модуль**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt:121-282`.
- **Назначение**: принимает событие от клиента и пишет INSERT в `tbl_events`.
- **Изменения**:
  - Для `eventType=CALL_REST` применить sampling (FR-006): `1/20` для анонимов, `1/5` для залогиненных, `1/1` для админов.
  - Применить in-memory дедуп (FR-007): ключ `(restName, canonical(parameters), anonId-or-userId)`, TTL 30 сек.
  - Логировать `SQLException` через `SLF4J log.warn` (FR-012).

#### `PublicApiController.authorsTiles` (Spring Controller method)
- **Модуль**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:141-181`.
- **Назначение**: возвращает список `AuthorTilePublicDto` для сетки авторов.
- **Изменения**: нет прямого (изменения в `AuthorTilePublicDto.fromAuthorName` достаточно).

#### `PublicApiController.picture` (Spring Controller method)
- **Модуль**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:871-888`.
- **Назначение**: 302-redirect на `/minio/karaoke/<encoded path>` для legacy-вызовов.
- **Изменения**: нет. Endpoint остаётся для обратной совместимости (FR-001), но больше не используется в `AuthorTilePublicDto`.

#### `PublicApiController.songPicture` / `songVkImage` (Spring Controller methods)
- **Модуль**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:656-768`.
- **Назначение**: генерация `BufferedImage` (800×194 / 1200×630) для share-плеера и VK.
- **Изменения**: добавить rate-limit 60 req/мин на IP (FR-010).

#### `PublicNewsController.since` (Spring Controller method)
- **Модуль**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicNewsController.kt:59-100`.
- **Назначение**: возвращает новости с момента timestamp.
- **Изменения**: добавить server-side in-memory cache, TTL 60 сек (FR-008, решение D-7).

#### `PublicChatController.unreadCount` (Spring Controller method)
- **Модуль**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicChatController.kt:102-108`.
- **Назначение**: возвращает количество непрочитанных сообщений для премиум-юзера.
- **Изменения**: добавить server-side in-memory cache, TTL 10 сек (FR-008, решение D-7).

#### `PublicShareController.heartbeat` (Spring Controller method)
- **Модуль**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt:194-211`.
- **Назначение**: продлевает lease для share-сессии.
- **Изменения**: добавить server-side in-memory cache, TTL 15 сек (FR-008, решение D-7). **Не** пишет в `tbl_events` (уже FR-009 выполнено).

### Frontend (Vue 3 / Vuex)

#### `zakroma` (Vuex module)
- **Модуль**: `karaoke-public/src/store/modules/zakroma.js`.
- **Назначение**: хранилище `authors`, `authorTiles`, `zakroma`, `specialBucket`, `isStreaming`, `streamProgress`, `streamError`.
- **Изменения** (FR-014):
  - Добавить state `lastLoadedTilesAt: 0` (epoch ms).
  - В action `loadAuthorTiles`: если `now() - lastLoadedTilesAt < 30_000L` И `authorTiles.length > 0` — no-op без HTTP.

#### `AuthorTiles.vue` (Vue component)
- **Модуль**: `karaoke-public/src/components/AuthorTiles.vue`.
- **Назначение**: рендер сетки тайлов авторов.
- **Изменения**: нет (URL тайла уже прямой `/minio/...` после изменения `AuthorTilePublicDto`).

---

## Новые runtime-объекты (in-memory, НЕ БД)

### `DedupCache<K, V>` (общий)
- **Модуль (новый)**: `karaoke-web/.../services/DedupCache.kt`.
- **Назначение**: thread-safe in-memory cache с TTL и lazy cleanup для дедупа запросов.
- **Реализация**:
  ```kotlin
  class DedupCache<K, V>(private val ttlMillis: Long) {
      private val map = ConcurrentHashMap<K, Long>() // key → lastSeen epoch ms

      fun shouldSkip(key: K): Boolean {
          val now = System.currentTimeMillis()
          val lastSeen = map[key] ?: run {
              map[key] = now
              return false
          }
          if (now - lastSeen > ttlMillis) {
              map[key] = now
              return false
          }
          return true // dedup hit
      }

      fun size(): Int = map.size
  }
  ```
- **Используется в**:
  - `MainController.doRegisterEvent` для `(restName, canonical(parameters), anonId-or-userId)` (FR-007).
- **Размер**: ~30k активных ключей × 100 байт = ~3 MB heap (приемлемо).

### `SamplingConfig` (data class)
- **Модуль**: `karaoke-web/.../services/SamplingConfig.kt`.
- **Назначение**: настройки sampling rate для разных типов пользователей.
- **Реализация**:
  ```kotlin
  data class SamplingConfig(
      val anonSamplingRate: Int = KaraokeProperties.getInt("KARAOKE_WEB_EVENTS_SAMPLING_ANON", 20),
      val loggedSamplingRate: Int = KaraokeProperties.getInt("KARAOKE_WEB_EVENTS_SAMPLING_LOGGED", 5),
      val adminSamplingRate: Int = KaraokeProperties.getInt("KARAOKE_WEB_EVENTS_SAMPLING_ADMIN", 1),
      val dedupTtlSeconds: Long = KaraokeProperties.getLong("KARAOKE_WEB_EVENTS_DEDUP_TTL_SECONDS", 30),
  )
  ```
- **Используется в**: `SamplingFilter` (см. ниже).

### `SamplingFilter` (Spring Component)
- **Модуль (новый)**: `karaoke-web/.../services/SamplingFilter.kt`.
- **Назначение**: применяет sampling и dedup к `doRegisterEvent` для `eventType=CALL_REST`.
- **Сигнатура**:
  ```kotlin
  @Component
  class SamplingFilter(
      private val samplingConfig: SamplingConfig,
      private val dedupCache: DedupCache<String, Unit>,
  ) {
      fun shouldSkip(eventType: String, restName: String, parameters: Any?, userType: UserType): Boolean {
          if (eventType != "CALL_REST") return false
          val dedupKey = "$restName:${canonicalParameters(parameters)}:${userType.userId}"
          return dedupCache.shouldSkip(dedupKey) || userType.samplingRandom > samplingConfig.getRate(userType)
      }
  }
  ```
- **Используется в**: `MainController.doRegisterEvent` ДО INSERT'а (early-exit).

### `PollingCache<K>` (Spring Component)
- **Модуль (новый)**: `karaoke-web/.../services/PollingCache.kt`.
- **Назначение**: in-memory cache для polling-эндпоинтов (news/since, chat/unreadcount, share/heartbeat).
- **Реализация**: обёртка вокруг `ConcurrentHashMap<K, CacheEntry<V>>` с TTL per-key.
- **Используется в**:
  - `PublicNewsController.since` — TTL 60 сек.
  - `PublicChatController.unreadCount` — TTL 10 сек.
  - `PublicShareController.heartbeat` — TTL 15 сек.

### `RateLimitBucket` (data class) + `RateLimitInterceptor` (Spring HandlerInterceptor)
- **Модуль (новый)**: `karaoke-web/.../services/RateLimitInterceptor.kt`.
- **Назначение**: rate-limit по IP для конкретных endpoints.
- **Реализация**:
  - `ConcurrentHashMap<String, RateLimitBucket>` где `key = clientIp`.
  - `RateLimitBucket` хранит `slidingWindow` (list timestamps) + лимит.
  - При превышении — 429 + `Retry-After` заголовок.
- **Используется в**: `songPicture`, `songVkImage` (FR-010).

### `EventsRetentionScheduler` (Spring Component)
- **Модуль (новый)**: `karaoke-web/.../services/EventsRetentionScheduler.kt`.
- **Назначение**: ежедневно удаляет строки из `tbl_events` старше N дней.
- **Реализация**:
  ```kotlin
  @Component
  class EventsRetentionScheduler {
      @Scheduled(cron = "0 0 3 * * *")
      fun cleanup() {
          val retentionDays = KaraokeProperties.getLong("KARAOKE_WEB_EVENTS_RETENTION_DAYS", 7)
          val sql = "DELETE FROM tbl_events WHERE last_update < now() - interval '$retentionDays days'"
          WORKING_DATABASE.getConnection().prepareStatement(sql).use { it.executeUpdate() }
      }
  }
  ```
- **Ловушка**: см. D-4 — таблица без `recordhash`-триггера, без sync. Удаление не влияет на синк.

### `DebugDbController` (Spring Controller)
- **Модуль (новый)**: `karaoke-web/.../controllers/DebugDbController.kt`.
- **Назначение**: debug-эндпоинт `GET /api/public/debug/db` (FR-013).
- **Реализация**:
  ```kotlin
  @RestController
  @RequestMapping("/api/public/debug")
  class DebugDbController {
      @GetMapping("/db")
      fun dbStatus(request: HttpServletRequest): ResponseEntity<Map<String, Any>> {
          // 1. Проверка IP allowlist
          val allowedIps = KaraokeProperties.getString("KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS", "")
          if (allowedIps.isBlank()) return ResponseEntity.notFound().build()
          val clientIp = ClientIpResolver.resolve(request)
          if (clientIp !in allowedIps.split(",")) return ResponseEntity.status(403).build()

          // 2. SELECT pg_stat_activity
          val pgStats = mutableMapOf<String, Any>()
          WORKING_DATABASE.getConnection().createStatement().use { st ->
              val rs = st.executeQuery("""
                  SELECT count(*) FILTER (WHERE state='active') as active,
                         count(*) FILTER (WHERE state='idle') as idle,
                         (SELECT setting::int FROM pg_settings WHERE name='max_connections') as max
                  FROM pg_stat_activity
              """.trimIndent())
              if (rs.next()) {
                  pgStats["pgActiveConnections"] = rs.getInt("active")
                  pgStats["pgIdleConnections"] = rs.getInt("idle")
                  pgStats["pgMaxConnections"] = rs.getInt("max")
              }
          }

          // 3. Tomcat threads
          val tomcat = (TomcatServletWebServerFactory::class.java as Any)
          // ... (через ManagementFactory или прямой bean lookup)

          pgStats["sampledAt"] = System.currentTimeMillis()
          return ResponseEntity.ok(pgStats)
      }
  }
  ```

---

## Новые Vuex state (фронт)

### `zakroma.lastLoadedTilesAt`
- **Модуль**: `karaoke-public/src/store/modules/zakroma.js`.
- **Назначение**: epoch ms последнего успешного `setAuthorTiles`.
- **State**: `lastLoadedTilesAt: 0`.
- **Mutation**: `SET_LAST_LOADED_TILES_AT(state, ms) { state.lastLoadedTilesAt = ms }`.
- **Используется в**: action `loadAuthorTiles` (FR-014):
  ```javascript
  async loadAuthorTiles({ commit, state }) {
      const now = Date.now()
      if (now - state.lastLoadedTilesAt < 30_000 && state.authorTiles.length > 0) {
          return // dedup hit
      }
      const tiles = await api.get('/authors-tiles')
      commit('setAuthorTiles', tiles)
      commit('SET_LAST_LOADED_TILES_AT', now)
  }
  ```

---

## nginx-конфиг (вне Kotlin)

### `deploy/80to8897` — `/minio/` location
- **Изменения** (FR-003/004/005):
  ```nginx
  location /minio/ {
      proxy_pass http://minio:9000/;
      proxy_set_header Host $host;
      # NEW: cache headers
      expires 24h;                     # Cache-Control: max-age=86400
      add_header ETag $upstream_http_etag; # пробрасываем из MinIO
      add_header Cache-Control "public, max-age=86400";
      # NEW: 404 cache override (FR-005)
      error_page 404 = @minio_404;
  }
  location @minio_404 {
      add_header Cache-Control "public, max-age=300";
      return 404;
  }
  ```
- **Ловушка**: nginx reload требует `nginx -t` (Constitution governance #6). Не делать `nginx -s reload` без `nginx -t`.

---

## Границы изменений (что НЕ меняется)

### НЕ меняется схема БД
- Никаких миграций.
- Никаких изменений в `deploy/karaoke-db/`.
- Никаких новых индексов.

### НЕ меняется контракт REST API
- `AuthorTilePublicDto.authorPictureUrl` — формат `String` остаётся, меняется только содержимое (прямой URL vs через `/api/public/picture`).
- `doRegisterEvent` — сигнатура остаётся, меняется только поведение под капотом (sampling/dedup).
- `songPicture` / `songVkImage` — сигнатура остаётся, добавляется только 429 ответ.
- `news/since` / `chat/unreadcount` / `share/heartbeat` — сигнатура остаётся, кэшируется только ответ.

### НЕ меняется `tbl_events` структура
- Те же колонки.
- Только retention scheduler удаляет старые строки (FR-011).

### НЕ меняется `useAuth.fetchMe` polling
- Уже module-level dedup (`useAuth.js:55-67`).
- 5 минут × N вкладок = N запросов / 5 минут — приемлемо без backend cache.

### НЕ меняется `KaraokeConnection` (thread-local)
- Остаётся single-threaded connection cache (Constitution Principle II).
- Дедуп/sampling/caches — это in-memory структуры в Spring beans, не в БД.

### НЕ меняется nginx `/minio/` location path
- Тот же `/minio/karaoke/...` URL.
- Добавляются только cache headers.

### НЕ затрагивается `webvue3` (админка)
- Изменения только в `karaoke-web` (публичная часть).
- `webvue3` не использует `AuthorTilePublicDto` или polling-эндпоинты.

---

## State transitions

### `MainController.doRegisterEvent` (для `eventType=CALL_REST`)

| Before | After | Notes |
|--------|-------|-------|
| INSERT в `tbl_events` всегда | INSERT пропускается по sampling (FR-006) | 95% анонимов, 80% залогиненных, 0% админов |
| INSERT в `tbl_events` всегда | INSERT пропускается по dedup (FR-007) | Same `(restName, params, userId)` в течение 30 сек → skip |
| `SQLException` → молча | `SQLException` → `log.warn` (FR-012) | Помогает в post-hoc анализе инцидентов |

### `PublicNewsController.since`

| Before | After | Notes |
|--------|-------|-------|
| SELECT каждый раз | Первый запрос — SELECT, последующие (60 сек) — из in-memory cache | Не влияет на пользовательский UX (новости редко меняются) |

### `PublicChatController.unreadCount`

| Before | After | Notes |
|--------|-------|-------|
| SELECT каждый раз для премиум | Первый запрос — SELECT, последующие (10 сек) — из cache | UX: бейдж «новых сообщений» может лагать на 10 сек — приемлемо |

### `PublicShareController.heartbeat`

| Before | After | Notes |
|--------|-------|-------|
| UPDATE каждый 25 сек | Первый запрос — UPDATE, последующие (15 сек) — no-op | Heartbeat lease продлевается раз в 25 сек, кэш 15 сек → каждый 2-й no-op |

### `zakroma.loadAuthorTiles`

| Before | After | Notes |
|--------|-------|-------|
| HTTP всегда | HTTP пропускается если `< 30 сек` с последнего успешного | Внутри одной SPA-сессии |

---

## Validation rules (из спеки)

Из спеки [spec.md](./spec.md#requirements):

- **FR-001**: legacy `/api/public/picture` остаётся 302 → /minio/. → Не меняется.
- **FR-002**: `AuthorTilePublicDto.authorPictureUrl` возвращает прямой URL. → Изменяется.
- **FR-003/004/005**: nginx cache headers. → Изменяется в `deploy/80to8897`.
- **FR-006**: sampling для CALL_REST. → Изменяется в `SamplingFilter`.
- **FR-007**: dedup в `doRegisterEvent`. → Изменяется в `DedupCache` + `SamplingFilter`.
- **FR-008**: polling server-side cache. → Изменяется в `PollingCache`.
- **FR-009**: heartbeat не пишет в tbl_events. → Уже выполнено.
- **FR-010**: rate-limit 60 req/мин. → Изменяется в `RateLimitInterceptor`.
- **FR-011**: events retention. → Изменяется в `EventsRetentionScheduler`.
- **FR-012**: logging на SQLException. → Изменяется в `doRegisterEvent`.
- **FR-013**: debug endpoint. → Изменяется в `DebugDbController` (новый).
- **FR-014**: dedup `loadAuthorTiles` на фронте. → Изменяется в `zakroma.js`.
- **FR-015/016/017/018/019/020**: документация и контракты — в plan.md / research.md.

---

## См. также

- [spec.md](./spec.md) — функциональные требования.
- [plan.md](./plan.md) — имплементационный план.
- [research.md](./research.md) — детальный аудит.
- [contracts/](./contracts/) — API-контракты.
- [quickstart.md](./quickstart.md) — ручные сценарии.
- [`docs/features/stats.md`](../../docs/features/stats.md) — прецедент для in-memory cache (StatsCacheScheduler).
- [`docs/features/dual-db-sync.md`](../../docs/features/dual-db-sync.md) — почему KaraokeConnection + thread-local.
