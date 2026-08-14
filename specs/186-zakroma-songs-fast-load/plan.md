# Implementation Plan: Ускорение загрузки песен в Закромах

**Branch**: `186-zakroma-songs-fast-load` | **Date**: 2026-08-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/186-zakroma-songs-fast-load/spec.md`

## Summary

При клике на тайл автора с 2500 песен пользователь ждёт ~17 секунд (5 секунд пустой паузы + 12 секунд прогрессометра). Предыдущая фича (spec 181) уже оптимизировала прогрессометр до real-time (`FR-FE-008` спеки 181 запрещает синтетический прогресс, источник — backend chunks), но узкие места остались на backend'е и в стрим-парсере frontend'а:

1. **Backend `Zakroma.buildFromSongs()` (karaoke-app)**: N+1 SQL-запросов — на каждый альбом 3 lookup'а (`Pictures.getPictureByName × 2 + Album.getAlbumById × 1`), на автора 2 lookup'а для портрета + `Author.getAuthorByName`. Для автора с 30 альбомами = **92 отдельных SQL-запроса на одну загрузку**.
2. **Backend `PublicApiController.zakromaStream()` (karaoke-web)**: `writer.flush() + out.flush()` после каждого NDJSON-сообщения (один лишний flush), плюс 2500 вызовов `ObjectMapper.writeValueAsString` для 2500 песен.
3. **Frontend `useZakromaStreamProgress.js`**: `setTimeout(resolve, 0)` × N между сообщениями. На 2500 песен это 2500 event-loop ticks; в фоновой вкладке браузер тротлит `setTimeout` до 1000мс минимум — отсюда и баг «прогрессометр не сдвигается, пока пользователь на другой вкладке» (на самом деле не «прогрессометр не сдвигается», а «фронт не успевает обработать накопленные чанки»).
5-секундная «пауза перед началом» объясняется тем, что backend загружает данные в `Zakroma.getZakroma()` синхронно, не шлёт `meta` с `expectedCount` до завершения этой загрузки. После spec 181 `meta` теперь отправляется **до** загрузки, но если фронт не прислал `expectedCount`, backend всё равно ждёт `Song.loadAuthorSongCounts()` (100-500мс).

**Подход (3 направления, ни одно не ломает контракт стрима):**

A. **Backend batch-lookup (основной выигрыш)**: добавить `Pictures.getPicturesByNames(names: List<String>)` и `Album.getAlbumsByIds(ids: List<Long>)` — батч `WHERE name IN (...)` / `WHERE id IN (...)`. Переписать `Zakroma.buildFromSongs()` так, чтобы картинки и альбомы грузились ОДНИМ запросом на категорию (2 запроса на альбомы: pictures + albums). Цель: для автора с 30 альбомами — **4 SQL-запроса вместо 92**.

B. **Backend batched NDJSON flush (вспомогательный)**: собирать сообщения в `StringBuilder` пачками по 50 песен (или 1 КБ), делать один `writer.write` + один `out.flush` на пачку. Цель: сократить число `writeValueAsString` × 2500 → × 50 + сократить число flush × 5000 → × 50.

C. **Frontend micro-yield replacement (фич баг с вкладкой)**: заменить `setTimeout(resolve, 0)` на `Promise.resolve().then()` (microtask — не тротлится в фоне) **ИЛИ** увеличить пачку до 50 песен → 50 yields вместо 2500. Также: при возврате на вкладку через `visibilitychange` listener — принудительно «протолкнуть» накопленные данные через `nextTick()` (на случай, если Vue watcher ещё не отрендерил).

## Technical Context

**Language/Version**: Kotlin 1.9.x (JDK 17) — backend; JavaScript ES2022 / Vue 3.4 — frontend.
**Primary Dependencies**: Spring Boot 3.2 (web), kotlinx-coroutines (нет — синхронный код), Jackson (NDJSON сериализация), Vue 3.4 + Vuex 4 (state), Bootstrap 5 (UI).
**Storage**: PostgreSQL 15 (через `KaraokeConnection` + сырой JDBC, **НЕ вводить ORM** — Constitution II). MinIO для картинок (не затрагивается).
**Testing**: Jest (karaoke-public), ktlint baseline (karaoke-app/karaoke-web). Новые тесты: unit-тесты для `Pictures.getPicturesByNames()` / `Album.getAlbumsByIds()` (batch consistency); integration — ручной сценарий из quickstart.md.
**Target Platform**: Linux server (admin-machine) + публичный Linux (prod). Браузеры: Chrome/Edge/Firefox/Safari актуальных версий (Chromium-based и Firefox тротлят setTimeout в фоне, Safari — менее агрессивно).
**Project Type**: web (двух-фронтенд — admin `webvue3`, public `karaoke-public`). **Изменения только в `karaoke-public`** (публичный SPA) и `karaoke-app` + `karaoke-web` (backend). `webvue3` НЕ затрагивается.
**Performance Goals**:
- Время от клика до первой отрисовки первой партии песен (≥ 50 шт.) **≤ 2 секунд** (SC-001).
- Полное время до отрисовки всех 2500 песен **≤ 7 секунд** (SC-002) — текущее ≈17 с, улучшение **минимум ×2**.
- При переключении вкладки на 30 сек и возврате — прогрессометр показывает ≥ 95% или скрыт (SC-004).
- Без регрессии для авторов с ≤ 200 песен (SC-006).

**Constraints**:
- НЕ ломать существующий контракт NDJSON-стрима (5 типов сообщений: `meta`/`album`/`song`/`done`/`error`). Фронт ожидает именно такой формат.
- НЕ ломать совместимость с уже-загруженной вкладкой (state.zakroma должен работать для уже-открытого пользователя).
- НЕ вводить кеш-слой, если не обосновано метриками (Constitution II: «не плодить абстракции без нужды»).
- НЕ менять nginx-конфиг (FR-BE-006: `proxy_buffering off` уже есть в `deploy/80to8897` для стрим-эндпоинта).
- Baseline линтеров НЕ должен расти (Constitution VI: FR-007).
- KDoc/JSDoc обязательно для всех публичных API (Constitution VI: FR-006).
- Per-feature документ `docs/features/zakroma-stream-progress.md` обновить в том же PR (FR-009).

**Scale/Scope**:
- Затронуто 4 файла backend + 1 файл frontend:
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Pictures.kt` — новый метод `getPicturesByNames`.
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt` — новый метод `getAlbumsByIds`.
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt` — переписать `buildFromSongs` на batch.
  - `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` — batched flush.
  - `karaoke-public/src/composables/useZakromaStreamProgress.js` — заменить micro-yield, добавить visibilitychange listener.
- Сопутствующие документы:
  - `docs/features/zakroma-stream-progress.md` — обновить секцию «Узкие места и оптимизации».
  - `docs/architecture-notes.md` — запись Pass 52 (новая фича).

## Constitution Check

*Gate: должен пройти до Phase 0 research. Перепроверить после Phase 1 design.*

| Principle | Применимо | Compliance | Заметки |
|-----------|-----------|------------|---------|
| **I. Self-contained pipeline** | ❌ нет | n/a | фича не трогает media-пайплайн |
| **II. Сырой JDBC + дифф по хэшам** | ✅ да | ✅ COMPLIANT | Новые batch-методы `getPicturesByNames` / `getAlbumsByIds` строятся на существующем `KaraokeConnection` через `WHERE name IN (?, ?, ...)` / `WHERE id IN (?, ?, ...)`, не вводят ORM. Дифф не затрагивается (read-only path) |
| **III. Двух-БД sync** | ❌ нет | n/a | endpoint read-only, не синхронизируется |
| **IV. Async-очередь** | ❌ нет | n/a | фича не про очереди |
| **V. Двух-фронтенд** | ✅ да | ✅ COMPLIANT | Изменения только в `karaoke-public` (публичный SPA) и backend. `webvue3` (админка) не затрагивается |
| **VI. Code Standards** | ✅ да | ✅ COMPLIANT | Все новые публичные методы — с KDoc/JSDoc + ссылка `@see docs/features/zakroma-stream-progress.md`. Baseline не должен увеличить. Per-feature документ обновляется в том же PR |
| **VII. Cross-Machine** | ❌ нет | n/a | фича не трогает cross-machine setup |
| **VIII. Секреты** | ❌ нет | n/a | фича не трогает секреты |

**Все gates PASS.** Никаких нарушений для justification.

## Project Structure

### Documentation (this feature)

```text
specs/186-zakroma-songs-fast-load/
├── plan.md              # Этот файл (/speckit.plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── stream-chunking.md
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks command — НЕ создаётся этим command)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/
├── Pictures.kt          # + getPicturesByNames(names: List<String>)
├── Album.kt             # + getAlbumsByIds(ids: List<Long>)
└── Zakroma.kt           # buildFromSongs() — переписать на batch

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/
└── PublicApiController.kt   # zakromaStream() — batched flush (50 песен / пачка)

karaoke-public/src/composables/
└── useZakromaStreamProgress.js   # micro-yield + visibilitychange

docs/features/
└── zakroma-stream-progress.md   # обновить секцию «Узкие места»

docs/
└── architecture-notes.md          # Pass 52 — запись о фиче
```

**Structure Decision**: `Option 2: Web application (frontend + backend)` — это уже существующая структура проекта (см. `AGENTS.md` § Модули). Изменения локализованы: 1 SPA + 2 backend-модуля + 1 документ.

## Complexity Tracking

> **Заполняется ТОЛЬКО если Constitution Check имеет нарушения, которые нужно обосновать.**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

**Никаких нарушений.** Таблица пуста.

---

## Phase 0: Outline & Research

Подробный анализ узких мест и принятых решений — в [`research.md`](./research.md).

**Резюме research (5 узких мест и решения):**

| # | Узкое место | Файл | Текущий выигрыш | Решение | Ожидаемый эффект |
|---|-------------|------|----------------|---------|------------------|
| R1 | N+1 SQL в `buildFromSongs` | `Zakroma.kt:96-215` | 92 запроса для 30 альбомов | Batch `Pictures.getPicturesByNames` + `Album.getAlbumsByIds` | 4 запроса (вместо 92) |
| R2 | `flush()` после каждой NDJSON-строки | `PublicApiController.kt:340-365` | 5000 flush на 2500 песен | `StringBuilder` пачками по 50 песен + 1 flush | ~50 flush (вместо 5000) |
| R3 | `setTimeout(0)` × N тротлится в фоне | `useZakromaStreamProgress.js:173` | 2500 yield × 1000мс = 41 мин в фоне | Microtask `Promise.resolve().then()` + `visibilitychange` re-render | Обработка не зависит от таба |
| R4 | `ObjectMapper.writeValueAsString` × N | `PublicApiController.kt:340-365` | 2500 сериализаций | Сериализация пачкой в `[msg1, msg2, ...]\n[msg2, ...]\n...` (NDJSON-as-JSON-array-of-lines) | 50 сериализаций (или 1 на пачку) |
| R5 | 5-сек пауза перед началом загрузки | Объяснение: backend ждёт `Song.loadAuthorSongCounts()` | 100-500мс на DB count | Уже оптимизировано в спеке 181 (передача `expectedCount` с фронта); fallback только для deep-link | Уже решено частично, дополнительно не требуется |

**Решение R3 — самая контринтуитивная часть**: `setTimeout` с `delay=0` в Chrome/Firefox при скрытой вкладке имеет минимальный интервал 1000мс (это та самая «фоновая тротлинг» политика браузеров для экономии батареи). Если стрим идёт в фоне, фронт получает все чанки через `reader.read()` (это native I/O, не тротлится), НО обработка каждой строки через `await setTimeout(0)` будет ждать 1000мс между строками. На 2500 строк это **41 минута** обработки. Когда пользователь вернётся, `albums.value` всё ещё частично заполнен → прогрессометр показывает старое значение (на самом деле не показывает — он показывает «X из 2500», но X отстаёт от реально полученных байтов). В активной вкладке задержка 0мс, всё работает.

**Решение для R3**: заменить на `await Promise.resolve()` (microtask — НЕ тротлится в фоне, но даёт меньше времени на рендер). Альтернативно — рендерить **после получения всего стрима**, без промежуточных обновлений (но тогда прогрессометр показывает только «0» и потом «100» — это уже спека 181 запрещает).

---

## Phase 1: Design & Contracts

Подробности — в [`data-model.md`](./data-model.md), [`contracts/`](./contracts/), [`quickstart.md`](./quickstart.md).

**Резюме Phase 1:**

- **data-model.md**: Минимальный. Новые сущности НЕ вводятся. Меняется реализация `buildFromSongs`, но `Zakroma` / `ZakromaAlbum` / `ZakromaAlbumSong` остаются с теми же полями.
- **contracts/stream-chunking.md**: Описывает модификацию NDJSON-контракта (если есть). **Контракт НЕ меняется** — те же 5 типов сообщений. Изменяется **ритмика**: вместо «по 1 сообщению» backend отдаёт «по 1 album + до 50 song» пачками с явным `out.flush()` после пачки. Для совместимости клиент не должен полагаться на «по 1 за раз».
- **quickstart.md**: 6 ручных сценариев валидации (включая «переключение вкладки»).

**Re-evaluation Constitution Check (post-design):** все gates остаются PASS. Решения не нарушают Constitution II (сырой JDBC через `WHERE IN`), V (только `karaoke-public` + backend), VI (KDoc/JSDoc обязательно, per-feature документ обновляется).

## Notes

- Спека **НЕ** требует переписывать прогрессометр (он уже real-time после спеки 181) — требуется ускорить backend + исправить баг тротлинга в фоне.
- Кандидат на уточнение в Phase 2 (tasks): точный размер пачки (50 песен vs 1 КБ vs динамический) — это уже технический тюнинг, не блокирует план.
- Подход «3 направления (A/B/C)» — **минимальный и аддитивный**: каждое направление работает самостоятельно. Можно катить A отдельно (большой выигрыш), B отдельно (средний), C отдельно (фич бага). Не требуется рефакторить одновременно.