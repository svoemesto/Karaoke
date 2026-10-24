# Component: stats-cache

> **Домен**: [publishing](../domain.md)
> **Компонент**: `StatBySong` — атомарный снимок счётчиков главной страницы
> + маркер состояния. Обновляется `StatsCacheScheduler` (час + dirty-тик).

## Ответственность | Responsibility

Главная страница (`main.html` в karaoke-web, SPA `karaoke-public`) показывает
счётчики: «Песен в коллекции», «В открытом доступе», «По подписке», «В работе».
Эти счётчики — **read-hot path**: каждый visitor генерирует запрос.

Прямой SQL-запрос на каждого visitor слишком дорог (18k+ песен, агрегаты по
`tbl_authors` и MATERIALIZED VIEW). Решение: снимок в памяти, обновляемый
`StatsCacheScheduler` раз в час и по dirty-флагу после сохранения/синхронизации
песни.

**[WARN] Cache invalidation** в hot path — типичный источник багов.
Не меняйте этот компонент без полного понимания расписания scheduler'а.

## Интерфейсы и Контракты | Interfaces and Contracts

### `StatBySong` — singleton с атомарным снимком

- **Где**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt`.
- **Снимок** (`StatBySongCounters`): `total`, `collection`, `freeNow`,
  `subscriptionOnly`, `inWork` — все поля опубликованы одной
  `AtomicReference`, поэтому читатель никогда не видит «полусостояние»
  (часть полей от нового пересчёта, часть — от прежнего).
- **Состояние** (`StatsCacheState`, BR-016):
  - `warming_up` — успешного пересчёта ещё не было, числовые поля — fallback `0`;
  - `ready` — последний пересчёт успешен, `0` — настоящий ноль;
  - `error` — последний пересчёт упал, числовые поля — от предыдущего успеха
    (если он был), иначе fallback `0`.
- **Чтение**: `getCounters(database)` — согласованный снимок + состояние;
  `getCountSongs*(database)` — отдельные числа (оставлены для совместимости);
  `getCacheState()` — только состояние.
- **Инвалидация**: `markDirty()` / `consumeDirty()` (вызывает
  `InternalStatsController.markDirty` из karaoke-app при сохранении песни с
  изменённым `free`-статусом).
- **Пересчёт**: `refreshCache(database)` — три SQL; снимок публикуется только
  если ВСЕ три вернули значение.

### `StatsCacheScheduler` — Spring `@Scheduled`

- `@PostConstruct warmUp()` — один синхронный пересчёт на старте.
- `@Scheduled(cron = "0 0 * * * *") refreshHourly()` — переход «наступил эфир»
  и истечение окна бесплатного доступа не сопровождаются сохранением записи,
  поэтому нужен часовой опрос.
- `@Scheduled(fixedRate = 60_000) refreshIfDirty()` — почти всегда no-op;
  пересчёт только если `consumeDirty()` вернул `true`.

### `GET /api/public/stats` — публичный endpoint

- **Возвращает**: 5 счётчиков (`onSponsr`, `freeNow`, `subscriptionOnly`,
  `inWork`, `total`) + `state` (`ready` / `warming_up` / `error`).
- **HTTP-статус**: всегда `200` — публичная страница не должна падать из-за
  статистики. Достоверность цифр передаётся полем `state`, а не пустым ответом.
- **`main.html`**: `data-stats-state` на таблице счётчиков; при `warming_up`
  вместо `0` рендерится `—`, при `error` — последние значения и предупреждение
  «БД временно недоступна».

## Логика и Алгоритмы | Logic and Algorithms

### Формулы счётчиков

- `total` — все песни, кроме SKIP;
- `collection` — `id_status >= 6` и непустой `source_markers` (можно
  проиграть в онлайн-плеере премиум-пользователю);
- `freeNow` — подмножество `collection`, бесплатное прямо сейчас (`free=true`
  или эфир наступил и не прошёл 1 календарный месяц, MATERIALIZED VIEW
  `mv_songs_free_now`);
- `subscriptionOnly = collection − freeNow`;
- `inWork = total − collection`.

### Пересчёт (`refreshCache`)

```
[1] SELECT SUM(total_songs_count)  FROM tbl_authors WHERE skip = false
[2] SELECT SUM(ready_songs_count)  FROM tbl_authors WHERE skip = false
[3] SELECT count(*) FROM mv_songs_free_now WHERE <free/publish-окно>
  ↓
все три вернули Int?  ──нет──▶  state = error, снимок не меняется (BR-016)
  │да
  ▼
store(total, collection, freeNow) → state = ready, INFO cache:refreshed
```

- Каждый запрос — `runCountQuery(): Int?`: `null` при отсутствии соединения,
  `SQLException` или отсутствии строки (агрегат без `GROUP BY` всегда даёт
  строку — её отсутствие считается сбоем).
- Cold-start (`@Synchronized ensureCacheInitialized`): если `hasData == false`
  и single-flight CAS выигран, пересчёт уходит в `bgExecutor` (daemon,
  single-thread). HTTP-тред получает fallback `0` за <100 мс.
- После неудачной первой попытки `hasData` остаётся `false` — следующий запрос
  снова запускает пересчёт (не залипает до часового cron).

### Чтение

```kotlin
val counters = StatBySong.getCounters(database = WORKING_DATABASE)
model.addAttribute("onSponsr", counters.collection)
model.addAttribute("statsState", counters.state.apiValue)
```

**[WARN]** Никогда не делать SQL внутри обработчика `/` или
`/api/public/stats` — только чтение снимка. Иначе деградация на 1000%.

## Зависимости | Dependencies

- → [karaoke-app `KaraokeConnection`](../../karaoke-web/domain.md) — соединение
  с БД потока; `getConnection()` возвращает `null` при сбое (не бросает).
- → `tbl_authors.total_songs_count` / `ready_songs_count` — предрассчитанные
  счётчики (specs/286-author-song-counts-cache).
- → `mv_songs_free_now` — MATERIALIZED VIEW, обновляется cron каждые 5 минут
  (specs/297-fix-tbl-songs-perf).
- → `StatsCacheScheduler` и `InternalStatsController.markDirty` — расписание и
  инвалидация.
- → [caching context](../../caching/domain.md) — другие кеши проекта.

## Ловушки и предупреждения

**[WARN] Stale data до 1 часа** — если новая песня добавлена в 14:30, а
scheduler сработал в 14:00, счётчик обновится в 15:00 (или раньше, если
сработал dirty-тик). Для публичных счётчиков это нормально.

**[WARN] Ошибка БД ≠ ноль (BR-016).** До правки `runCountQuery` возвращал `0`
при любом сбое, и `refreshCache` публиковал нули: экран «0 песен» был
неотличим от реального пустого каталога. Теперь снимок при сбое не меняется, а
состояние становится `error`; `0` при `ready` — настоящий ноль. Не возвращайте
`0` из `runCountQuery` и не публикуйте снимок при `null`.

**[WARN] Cold start** — до первого успешного пересчёта `state = warming_up`, а
getter'ы отдают fallback `0`. Шаблон обязан показывать `—`, а не `0`.

**[WARN] SPA `karaoke-public` пока не читает `state`.** `stats.js` кладёт в
store только 5 чисел; при ошибке запроса остаются начальные нули. Бэкенд уже
отдаёт `state` — подключение на фронте за владельцем (отдельная задача, вне
пакета #385).

**[WARN] Три запроса — не одна транзакция.** Снимок публикуется только при
успехе всех трёх, но BETWEEN-чтения могут разойтись, если песня изменилась
между запросами. Для публичных счётчиков это допустимо; «атомарная замена
набора» здесь означает «не публиковать частичный/нулевой набор», а не
REPEATABLE READ.

**[WARN] `total`/`collection` считаются по не-skip авторам**
(`WHERE a.skip = false`), а не по SKIP-тегу песен — расхождение ~56 песен
(specs/286). Не «исправляйте» это без отдельной спеки.

## Связанные ADR | Related ADRs

- [local-0005 — структурированное логирование](../../../adr/local-0005-structured-logging-karaoke-app.md)
  (`infra.cache.statbysong`: coldStart / refreshed / refreshFailed).
- ADR по caching (TODO, [caching domain](../../caching/domain.md)).
