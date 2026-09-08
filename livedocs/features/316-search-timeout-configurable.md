---
status: Active
slug: search-timeout-configurable
related:
  - specs/316-search-timeout-configurable/spec.md
  - specs/316-search-timeout-configurable/plan.md
  - specs/316-search-timeout-configurable/tasks.md
---

# Настраиваемый таймаут между поисковыми запросами (iter #316, rev 3)

Настраиваемый интервал между поисковыми запросами текста песен + авто-подбор минимального
интервала между успешными запросами. OpenProject WP #61.

## Что делает

- **Хранение**: `KaraokeProperties.lyricsSearchTimeoutSeconds` (Int, default 10) — backend-настройка
  в `/sm-karaoke/system/Karaoke.properties` (base64), НЕ web-storage. Зарегистрирована в
  `listKaraokeProperties` (Properties UI + `set()`/`setFromString` работают). Редактируется через Properties UI/API.
- **Путь A — `searchsongtextall`** (кнопка «Найти тексты для всех песен» в `SongsTable.vue`):
  серийный цикл `ids.forEachIndexed` + `Thread.sleep(timeout * 1000L)` между `getLyricsSearch`, кроме последней итерации.
- **Путь B — `createfromfolder`** (импорт файлов из папки в `HomeView.vue`): rate-limit
  `Thread.sleep(timeout * 1000L)` ПЕРЕД каждым `lyricsSearchExecutor.submit` (пул 4 потока), кроме последнего.
- **UI (rev 3)**: поле `timeout` в существующих `<custom-confirm>` (SongsTable.vue «Подтвердите поиск текста»
  + HomeView.vue «Добавление файлов из папки`). НЕ отдельный диалог. Default читается из backend через API;
  значение сохраняется в KaraokeProperties после подтверждения. Валидация `Number.isInteger(Number(v)) && v >= 1`.
- **Авто-подбор (FR-009/FR-010, rev 3)**: в обоих путях замеряется `minIntervalMs` (минимальное время между
  двумя подряд успешными `getLyricsSearch`; упавшие запросы не учитываются). После цикла — backend-лог
  `[lyrics-search-summary]` + SSE notification `massSearchSummary` (broadcast, всем вкладкам).
- **API**: `GET/POST /api/lyrics-search-timeout` — read/write для UI.

## Где в коде

**Backend** (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`):
- `Karaoke.kt` — `lyricsSearchTimeoutSeconds` (companion object, рядом с `checkSearchAsync`).
- `KaraokeProperties.kt` — регистрация `lyricsSearchTimeoutSeconds` в `listKaraokeProperties` (B1 fix).
- `controllers/ApiController.kt` — `getSearchSongTextAll` (R-002 + FR-009/010), `doCreateFromFolder` (R-002a + FR-009/010), endpoint R-008.
- `controllers/MainController.kt` — deprecated `getSearchSongTextAll` (симметрично, параметризован).
- `model/SseNotificationType.kt` — `MASS_SEARCH_SUMMARY("massSearchSummary")`.
- `services/SseNotificationService.kt` — `massSearchSummary` broadcast (не в `addressedTypes`).

**Frontend** (`webvue3/src/`):
- `components/Songs/store.js` — actions `getLyricsSearchTimeout`/`setLyricsSearchTimeout`, `searchTextForAll`+timeout, `createFromFolderPromise`+timeout.
- `components/Songs/SongsTable.vue` — timeout поле в custom-confirm «Подтвердите поиск текста».
- `views/HomeView.vue` — timeout поле в custom-confirm «Добавление файлов из папки».
- `App.vue` — case `MASS_SEARCH_SUMMARY` в switch SSE (нотификация в веб).

## Ключевые решения

- **KaraokeProperties, не setWebvueProp** (rev 2): backend-настройка, единый источник правды, покрывает оба пути.
- **UI fix (rev 3)**: поле таймаута в существующих custom-confirm, НЕ отдельный `SearchTimeoutDialog.vue`
  (принцип «сделай так же, как рядом»).
- **`Thread.sleep`, не coroutine delay**: методы не suspend, паттерн уже есть в `MainController.kt`.
- **1-арг `getInt`**: `KaraokeProperties.getInt(key)` возвращает 0 при отсутствии → fallback `.takeIf { it >= 1 } ?: 10`.
- **`promisedXMLHttpRequest` резолвит raw string** → `JSON.parse(r)` в `getLyricsSearchTimeout`.
- **Пул 4 потока НЕ меняется** (out of scope); rate-limit на submit-цикле.
- **Thread-safety пути B**: `synchronized(this@ApiController)` для счётчиков (production-hardening — out of scope).
- **`massSearchSummary` broadcast**: НЕ в `addressedTypes` (иначе был бы addressed, а не broadcast).

## Как тестировать

Владелец прогоняет `specs/316-search-timeout-configurable/quickstart.md` (7 scenarios):
1. Первый запуск — default 10.
2. Повторный запуск — default = последнее введённое.
3. Validation — отвергает 0/-3/abc.
4. Импорт папки — таймаут применяется (путь B).
5. Две точки входа — единое значение.
6. Properties UI — изменение без перезапуска.
7. Backend пауза между запросами + лог `[lyrics-search-summary]` + SSE `massSearchSummary`.
