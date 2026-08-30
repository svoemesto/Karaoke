---
status: Active
slug: 249-public-settings-cache
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/249-public-settings-cache/spec.md
  - 241-db-storage-perf-audit
  - 248-authors-tiles-cache
---

# 249 — TTL-кеш для PublicSettingsWebController.getProperty (LiveDoc)

> Drill-down — [specs/249-public-settings-cache/spec.md](../../specs/249-public-settings-cache/spec.md).
> Parent — [241-db-storage-perf-audit](241-db-storage-perf-audit.md) — Tier-2 / FR-006.
> Sister — [248-authors-tiles-cache](248-authors-tiles-cache.md) — проверенный паттерн TTL-кеша.

## Что делает

TTL-кеш (60 сек) для админского endpoint `/api/properties/getproperty`. Сейчас на каждый
HTTP-запрос — `SELECT value FROM tbl_public_settings WHERE key = ?`. С кешем — 1 cold start
+ cache hits в течение 60 сек. Инвалидация — через отдельный `dirty: AtomicBoolean`,
взводится из `setProperty` при успешном UPDATE/INSERT.

## Effect

* Warm path latency **< 10 мс** (vs ~5-15 мс baseline — экономия в SQL round-trip).
* На странице SongsTable (`webvue3`) — 0 SELECT к `tbl_public_settings` после первой загрузки.
* После `setProperty` — следующий `getProperty` возвращает свежее значение без 60-сек ожидания.

## Реализация

* `companion object` в `PublicSettingsWebController`:
  * `CACHE_TTL_MS = 60 * 1000L`
  * `NOT_FOUND_SENTINEL: Any` — маркер для «key не найден в БД» (referential equality через `===`).
  * `data class CachedProperty(value, expiresAtMs)` — `value` либо `String`, либо `NOT_FOUND_SENTINEL`.
  * `ConcurrentHashMap<String, CachedProperty> cache`
  * `AtomicBoolean dirty` — отдельный флаг (НЕ переиспользуем `StatBySong.dirty` — другой домен).
* `getCachedProperty(key, loadFn)` — helper с cache check, TTL refresh, dirty-flag инвалидация.
* `markDirty()` — вызывается из `setProperty` при успешном UPDATE/INSERT.
* `consumeDirty()` — атомарно читает и сбрасывает `dirty`.
* Kill-switch через `KaraokeProperties.getBoolean("karaoke.public.public-settings-cache.enabled", default=true)`.

## ADMIN-only (не PROD-критичная)

Endpoint используется только в webvue3-админке (страница SongsTable, kill-switch scripts).
На prod (`karaoke-web` без admin-UI) — 0 RPS, кеш неактивен. Эффект только на админ-машине:
снижает SQL-нагрузку и ускоряет отзывчивость admin-UI.

## Архитектурное решение: почему отдельный dirty-флаг

НЕ переиспользуем `StatBySong.dirty`:
- `StatBySong` — про **free-флаги песен** на главной странице публичного сайта.
- `PublicSettings` — про **kill-switches** (например, `newsAutoPublishKillSwitch`) и публичные
  настройки в админке.

Разные домены, разные админ-сценарии, разные lifecycle. Если переиспользовать один флаг —
любое сохранение песни сбрасывает кеш настроек (и наоборот), что неправильно. Single
responsibility + предсказуемая инвалидация.

## Backward-compat

Signature endpoint'а НЕ меняется: `@GetMapping("/getproperty")` с параметром `key`.
Клиенты (`webvue3`) работают без изменений. Новые параметры не добавлены.

## Семантика кеширования отсутствующего ключа

В отличие от sister-spec 248 (authors-tiles), здесь пустой результат — валидный ответ
(настройка может отсутствовать). Кладём `NOT_FOUND_SENTINEL` в cache (FR-007), чтобы не
делать повторный SELECT каждые 60 сек для несуществующих ключей. Возвращаемое значение —
`""` (как в исходной реализации). Если key появится в БД — `setProperty` взведёт `markDirty()`.