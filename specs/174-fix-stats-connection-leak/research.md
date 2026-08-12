# Research: Починить flood JDBC-соединений при открытии вкладки «Статистика»

**Branch**: `174-fix-stats-connection-leak` | **Date**: 2026-08-12
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## 1. Архитектурные решения

### 1.1 Где разместить `StatsCache`

**Decision**: новый файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StatsCache.kt`
в пакете `services/` (рядом с другими синглтонами — `KaraokeStorageService`,
`GeoIpService`, `SyncTarget`).

**Rationale**: пакет `services/` уже содержит in-process кеши и сервисы
(`ConcurrentHashMap`-based, см. `GeoIpService.kt:resolveMany` который
использует кеш IP→country). Согласовано с существующей конвенцией.

**Alternatives considered**:
- В `model/` — отвергнуто, это singleton-сервис, а не DTO.
- В `controllers/` — отвергнуто, кеш переиспользуется между контроллерами
  (хотя сейчас только `StatsController`, но будущие могут захотеть).

### 1.2 Spring `@Cacheable` vs ручной `ConcurrentHashMap`

**Decision**: ручной `ConcurrentHashMap<StatsCacheKey, StatsCacheEntry>` +
`@Synchronized` инвалидация (через `invalidateAll()`). Без `@Cacheable`.

**Rationale**:
- 6 ключей с фиксированным TTL — `@Cacheable` overkill.
- В проекте уже есть `GeoIpService.resolveMany` с похожей in-process cache
  логикой, явный паттерн — `ConcurrentHashMap<K, V>` + manual TTL check.
- Spring `@Cacheable` требует `@EnableCaching` в `KaraokeApplication` —
  расширение scope конфигурации для 6 ключей не оправдано.

**Alternatives considered**:
- Caffeine cache — отвергнуто, новая зависимость.
- Spring `@Cacheable` — отвергнуто, scope конфигурации растёт.

### 1.3 Формат 503-ответа — `ResponseStatusException` vs `ResponseEntity`

**Decision**: `ResponseEntity<Map<String, Any>>` с явным `HttpStatus.SERVICE_UNAVAILABLE`
и заголовком `Retry-After: 10` (через `ResponseEntity.ok().header(...)`).

**Rationale**:
- Полный контроль над телом (`{"errorCode":"stats.unavailable","retryAfterSeconds":10}`)
  и заголовком `Retry-After`.
- Паттерн уже использован в `PublicShareController.kt` (см. спеку 167 —
  `share.internal`).
- Spring `ResponseStatusException` не позволяет задать custom body
  и заголовки без `@ResponseBody` — лишний код.

**Alternatives considered**:
- Spring `ResponseStatusException` — отвергнуто, нет тела.
- `@ControllerAdvice` — отвергнуто, scope для одного endpoint'а
  избыточен.

### 1.4 SLF4J-логгер — Kotlin native vs Lombok `@Slf4j`

**Decision**: Kotlin native `private val log = LoggerFactory.getLogger(StatsCache::class.java)`
через `companion object` (стандарт для Kotlin в этом проекте).

**Rationale**:
- Kotlin проект — Lombok не используется (см. `build.gradle.kts` в
  `karaoke-app`).
- Паттерн уже применён в других классах проекта (`SponsrSyncController.kt`,
  `GeoIpService.kt`).

**Alternatives considered**:
- `@Slf4j` Lombok — отвергнуто, проект не использует Lombok.

### 1.5 Lazy load табов — `v-if` vs watch на `BTab` active

**Decision**: watch на активный таб через BTab events (`@activate` /
через `:active` prop). При смене активного таба — dispatch
`loadXxx` (через `reloadStatsBySong` / `reloadTopUsers` / и т.д.).
При unmount'е компонента (выход из меню «Статистика») — НЕ
сбрасывать state (per AGENTS.md «Персистентность страницы пагинации»).

**Rationale**:
- `v-if` размонтирует компонент — теряется scroll-позиция и фильтры.
  Лучше держать смонтированным, но показывать только активный (через
  `<div v-show="activeTab === 'kpi'">`).
- BTab из Bootstrap-vue-next поддерживает `:active-tab` и события
  `activate-tab` — стандартный паттерн.

**Alternatives considered**:
- `v-if` размонтирование — отвергнуто, потеря состояния.
- `IntersectionObserver` — отвергнуто, overkill.

### 1.6 Автоматический retry в `<DbOverloadBanner>` — `setTimeout` vs `setInterval`

**Decision**: `setTimeout(retryCallback, retryAfterSeconds * 1000)` один раз
при показе баннера. Кнопка «Retry now» disabled на тот же период через
`v-bind:disabled="!canRetry"` + countdown через
`setInterval(updateCountdown, 1000)`.

**Rationale**:
- `setTimeout` — единственный retry, как договорились в Q3 (FR-011).
- `setInterval` для countdown обновляется каждую секунду для UX.
- При F5 страницы Vue пересоздаёт компонент → countdown сбрасывается.

**Alternatives considered**:
- `setInterval` с retry каждый интервал — отвергнуто, лишний шум на БД.

### 1.7 Debug endpoint — авторизация

**Decision**: `permitAll()` (без auth), как и весь admin SPA.

**Rationale**:
- `webvue3` — admin-зона, в `SecurityConfig.kt` уже `permitAll()` для
  всех роутов (см. AGENTS.md, Principle V).
- Не раскрывает чувствительные данные (только размер кеша + счётчик
  `pg_stat_activity`).

**Alternatives considered**:
- За `X-Share-Debug-Key` (как `share/debug` в спеке 167) — отвергнуто,
  debug endpoint здесь не критичен (нет payment/secret flow), и
  admin-зона уже защищена сетевым уровнем.

## 2. Открытые вопросы из Technical Context

В Technical Context **все NEEDS CLARIFICATION закрыты** (в спеке
[spec.md](./spec.md) Q1–Q3 в Clarifications + Q в `/speckit.clarify`
по scope/observability/throttling). Никаких дополнительных research-задач
не требуется.

## 3. Best practices для in-process TTL cache в Kotlin

- **Ключ**: `data class StatsCacheKey(val endpoint: String, val params: Map<String, String>)`
  с правильным `equals/hashCode` (генерируются компилятором).
- **Значение**: `data class StatsCacheEntry(val value: Any, val expiresAt: Instant)`.
- **Чтение**: `cache[key]?.takeIf { it.expiresAt > Instant.now() }?.value`
  (atomic через `ConcurrentHashMap.get()`).
- **Запись**: `cache[key] = StatsCacheEntry(value, Instant.now().plusSeconds(60))`.
- **Thread-safety**: `ConcurrentHashMap` гарантирует atomic put/get.
  Отдельная `@Synchronized` НЕ нужна для простого TTL cache.

## 4. Best practices для lazy load BTab (Bootstrap-vue-next)

- Использовать `:active-tab="activeTab"` (data-property) +
  `@activate-tab="onActivateTab"` (event) — стандартный 2-way binding.
- При смене активного таба вызывать `reloadXxx()` только если данные
  ещё не загружены (`!xxxIsLoaded`) или старше TTL (через
  `xxxLoadedAt < now() - 60s`).
- Store в `Stats/store.js`: добавить `lastLoadedAt: timestamp` per data
  slice.

## 5. Резюме

Все архитектурные решения приняты. Никаких open questions перед
Phase 1 design.
