---
status: Active
slug: 287-stop-lyrics-after-first
related:
  - ../features/015-search-engine-selection.md
  - ../features/020-fix-search-lyrics-autofill.md
  - ../features/278-fix-key-loss-on-lyrics-search.md
  - ../features/281-find-lyrics-overwrites-key-bpm.md
  - ../domain/processing.md
  - ../../specs/287-stop-lyrics-after-first/spec.md
---

# 287 — Прекращение извлечения текста после первого успеха + ручная попытка по ссылке (LiveDoc)

> Drill-down — [specs/287-stop-lyrics-after-first/spec.md](../../specs/287-stop-lyrics-after-first/spec.md).

## Что делает

Модифицирует алгоритм автоматического поиска текста песни: после того как для одной из
найденных ссылок успешно извлечён непустой текст, дальнейшие HTTP-запросы и парсинг для
остальных ссылок прекращаются. При этом в БД сохраняются **все** ссылки — для необработанных
создаётся запись `SearchResult` с заполненным `url` и пустым `text`/`html`. В модалке
«Поиск текста песни в интернете» добавлена новая кнопка «Получить текст по ссылке» под
«Открыть на сайте», которая позволяет вручную запустить попытку извлечения текста для
конкретной «серой» ссылки (с пустым `text`).

## User Stories (краткий список)

- **US1/US2 (P1)**: алгоритм останавливается после первой успешной ссылки; в модалке
  визуально — 1 «белая» ссылка + N-1 «серых».
- **US3 (P1)**: кнопка «Получить текст по ссылке» запускает попытку только для одной ссылки.
- **US4 (P2)**: совместимость с поиском для всех песен и фоновым воркером YANDEX_ASYNC.

## Functional Requirements (указатель)

| # | Что | Где |
|---|-----|-----|
| FR-001..004 | Алгоритм остановки в 2 общих точках | `SearchResult.getSearchResultsForSearchAsync`, `UtilsAI.getLyricsSearchViaSearchTool` |
| FR-010..011 | Подстановка в `Song.sourceText` через `applyFoundLyricsIfMissing` | без изменений (Pass 020 + 281) |
| FR-020..024 | Новый ручной endpoint + идемпотентность | `ApiController.extractLyricsBySearchResultId`, `UtilsAI.extractLyricsBySearchResultId` |
| FR-030..034 | UI модалки: кнопка «Получить текст по ссылке» | `webvue3/.../edit/SearchText.vue` |
| FR-040..043 | Без изменений: «Открыть на сайте», «Искать заново», «Удалить результаты», `applyFoundLyricsIfMissing` | без изменений |

## Acceptance Criteria

- [ ] **AC1** (US1): для песни без `source_text` после поиска в `tbl_search_results` — ровно
      1 запись с непустым `text`/`html`, остальные — с пустыми (тест по `quickstart.md` Сценарий 1).
- [ ] **AC2** (US2): в модалке визуально 1 «белая» ссылка + N-1 «серых» (тест `quickstart.md`).
- [ ] **AC3** (US3): кнопка «Получить текст по ссылке» обрабатывает ровно 1 ссылку
      (тест `quickstart.md` Сценарий 2).
- [ ] **AC4** (US4): регрессии Pass 020/278/281 не сломаны (тест `quickstart.md` Сценарии 3-4).

## Архитектура

**Двухпроходный подход** (для обеих общих точек цикла):

```
Шаг 1: для каждой ссылки из links создаём SearchResult с пустым text/html (INSERT)
Шаг 2: обходим записи; для каждой пытаемся HTTP+парсинг; первый успех → break
```

Преимущество: после `break` оставшиеся записи уже в БД — пользователь видит их в модалке
как «серые» и может кликнуть на любую для ручной попытки.

**Покрытие 4 движков** одной правкой:

| Движок | Общая точка |
|--------|-------------|
| `YANDEX_SYNC` | `UtilsAI.getYandexSearch` → `SearchResult.getSearchResultsForSearchAsync` (T003) |
| `YANDEX_ASYNC` | `KaraokeProcessWorker.kt:898` → `SearchResult.getSearchResultsForSearchAsync` (T003) |
| `SEARXNG` | `UtilsAI.getLyricsSearchViaSearchTool` (T004) |
| `FOURGET` | `UtilsAI.getLyricsSearchViaSearchTool` (T004) |

## Что НЕ меняется (регрессии)

- `applyFoundLyricsIfMissing` (Pass 281): получает список из 1 непустого текста вместо N
  — логика `firstOrNull { it.isNotBlank() }` корректно отрабатывает. Reload-from-db-before-save
  сохранён.
- `doCreateFromFolder` (Pass 278): без изменений.
- `Song.saveToDb()`: без изменений.
- `Song.setSourceMarkers` / `setSourceText`: без изменений.
- `tbl_search_results` / `tbl_search_async`: SQL-миграций нет, схема без изменений.
- `SearchTextResultsTable.vue`: визуальное состояние уже работает через `text === ''`.
- `SubsEdit.vue`: только открывает модалку.

## Связанные LiveDocs

- Domain: [processing.md](../domain/processing.md) — контекст обработки песни
- LiveDocs фич: [015-search-engine-selection](../features/015-search-engine-selection.md)
  (выбор движка), [020-fix-search-lyrics-autofill](../features/020-fix-search-lyrics-autofill.md)
  (подстановка), [278-fix-key-loss-on-lyrics-search](../features/278-fix-key-loss-on-lyrics-search.md),
  [281-find-lyrics-overwrites-key-bpm](../features/281-find-lyrics-overwrites-key-bpm.md)

## Код

- Backend:
  - `karaoke-app/.../model/SearchResult.kt:getSearchResultsForSearchAsync` — двухпроходный цикл
  - `karaoke-app/.../UtilsAI.kt:getLyricsSearchViaSearchTool` — двухпроходный цикл
  - `karaoke-app/.../UtilsAI.kt:extractLyricsBySearchResultId` — новая функция для ручной попытки
  - `karaoke-app/.../controllers/ApiController.kt:extractLyricsBySearchResultId` — новый endpoint
  - `karaoke-app/.../model/SearchResultDTO.kt` — поле `lastError: String? = null`
- Frontend:
  - `webvue3/.../edit/SearchText.vue` — новая кнопка «Получить текст по ссылке»,
    computed `canExtractLyrics`, метод `extractLyricsFromSelectedResult`
  - `webvue3/.../store.js` — новый action `extractLyricsBySearchResultId`

## История

- Создан: 2026-08-31 (Pass 287)
- Последнее обновление: 2026-08-31