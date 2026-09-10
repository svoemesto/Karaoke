# Implementation Plan: Настраиваемое количество строк на странице таблиц в админке

**Branch**: `359-rows-per-page` | **Date**: 2026-09-10 | **Spec**: [spec.md](./spec.md)
**Issue**: OpenProject #74

## Summary

Добавить UI-поле «Строк на странице» в верхнем блоке пагинации 13 таблиц webvue3
(Songs, Authors, Albums, Pictures, SiteUsers, Subscriptions, ShareLinks,
Dictionaries, Properties, SitePlaylists, ListeningHistory, Processes, News).
**Stats таблицы (TopUsersTable, TopListenedSongsTable) исключены** — это
chart-cards с собственным `pageSize` через `$emit('page-size')`, другой паттерн.
Значение хранится в `KaraokeProperties` (глобально per-table) и подгружается при
старте SPA. Изменение применяется только после успешного ответа backend
(`/api/properties/setproperty`), без оптимистичного обновления.

**Архитектурный подход**:
- Backend: расширить `listKaraokeProperties` 14 записями `ui.*.rows_per_page` типа `INT`
  в `karaoke-app/.../KaraokeProperties.kt`. Добавить серверную валидацию `1..1000` в
  эндпоинт `/api/properties/setproperty` (FR-006).
- Frontend: новый Vuex-модуль `webvue3/src/store/modules/tableSettings.js` (читает
  `/api/propertiesdigests` при старте, кеширует, пишет через `/api/properties/setproperty`).
  Изменить 14 таблиц — заменить hardcoded `perPage` на геттер из store, добавить UI-поле.

## Technical Context

**Language/Version**:
- Backend: Kotlin 1.x (JDK 17), Spring Boot 3.x.
- Frontend: Vue 3 + Vite + Bootstrap-vue-next.

**Primary Dependencies**:
- Backend: Spring Web (`@PostMapping`), KaraokeProperties (companion object + `KaraokePropertySerializable`).
- Frontend: Vuex 4, `bootstrap-vue-next` (`<b-form-input type="number">`, `<b-pagination>`), `promisedXMLHttpRequest` (existing util).

**Storage**:
- Backend: файл `/sm-karaoke/system/Karaoke.properties` (base64-encoded JSON-список).
- Frontend: Vuex store `tableSettings` (in-memory cache, заполняется при старте SPA).

**Testing**: Manual E2E (см. `quickstart.md`). В CI нет автоматических тестов для webvue3 — пользователь проверяет вручную.

**Target Platform**: Linux (admin-машина + прод-сервер), Docker-контейнеры (`karaoke-app`, `webvue3`).

**Project Type**: Web-приложение (admin SPA + backend API).

**Performance Goals**:
- NFR-001: `POST /api/properties/setproperty` ≤ 200 мс p95.
- NFR-002: Изменение в UI применяется после успешного ответа (без оптимистичного).

**Constraints**:
- НЕ менять существующие hardcoded `perPage` дефолты (FR-010).
- НЕ ломать существующее поведение пагинации (FR-011, ADR-0004).
- НЕ добавлять новых REST-эндпоинтов (FR-007).

**Scale/Scope**: 14 таблиц × 1 UI-поле × 1 store = ~15 файлов.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Compliance | Заметки |
|---|---------|------------|---------|
| I. Self-contained автопайплайн | ✅ N/A | Фича только в admin UI, не в hot-path рендера |
| II. Сырой JDBC + дифф по хэшам | ✅ N/A | Не работаем с БД; используем файл `Karaoke.properties` |
| III. Двух-БД синхронизация через SyncRegistry | ✅ N/A | `Karaoke.properties` НЕ синхронизируется (он per-machine) |
| IV. Async-очередь задач с парсингом stdout | ✅ N/A | Никаких async-операций |
| V. Двух-фронтенд | ✅ Compliant | Меняем только `webvue3` (admin), не `karaoke-public`. Используем существующие Vuex + `b-pagination` |
| VI. Code Standards | ✅ Compliant | KDoc/JSDoc для новых функций (FR-006 constitution). Линтеры ktlint + eslint + prettier в CI. Per-feature doc: `docs/features/<slug>.md` (FR-009) |
| VII. Cross-Machine Setup | ✅ Compliant | `.git-blame-ignore-revs` не трогаем; `.gitattributes` — line endings уже настроены |
| VIII. Секреты и git-гигиена | ✅ N/A | Никаких секретов |
| IX. Knowledge-first | ✅ Compliant | Pre-flight выполнен в Stage 1; ADR `local-0004` (webvue3 pagination) и `local-0001` (KaraokeProperties defaults) учтены |

**Re-evaluation post-Phase-1**: все gates остаются ✅. Никаких новых нарушений.

## Project Structure

### Documentation (this feature)

```text
specs/358-rows-per-page/
├── plan.md              # This file (/speckit.plan output)
├── spec.md              # Feature spec (/speckit.specify output, refined by /speckit.clarify)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── api-properties.md  # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks — created in next stage)
```

### Source Code (repository root)

```text
karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/
    ├── KaraokeProperties.kt                # MODIFY: add 14 INT parameters to listKaraokeProperties
    └── controllers/ApiController.kt        # MODIFY: add server-side validation 1..1000 for ui.*.rows_per_page keys in setproperty

webvue3/
├── src/store/
│   ├── index.js                            # MODIFY: register new module tableSettings
│   └── modules/
│       └── tableSettings.js                # NEW: Vuex module with state/getters/actions
└── src/components/
    ├── Songs/SongsTable.vue                       # MODIFY: bind perPage to store, add UI input
    ├── Authors/AuthorsTable.vue                   # MODIFY
    ├── Albums/AlbumsTable.vue                     # MODIFY
    ├── Pictures/PicturesTable.vue                 # MODIFY
    ├── SiteUsers/SiteUsersTable.vue               # MODIFY
    ├── Subscriptions/SubscriptionsTable.vue       # MODIFY
    ├── ShareLinks/ShareLinksTable.vue             # MODIFY
    ├── Dictionaries/DictionariesTable.vue         # MODIFY
    ├── Properties/PropertiesTable.vue             # MODIFY
    ├── SitePlaylists/SitePlaylistsTable.vue       # MODIFY
    ├── ListeningHistory/ListeningHistoryTable.vue # MODIFY
    ├── Processes/ProcessesTable.vue               # MODIFY
    ├── News/NewsTable.vue                         # MODIFY
    └── Stats/{TopUsersTable,TopListenedSongsTable}.vue  # MODIFY (treated as one "stats" key)

docs/
└── features/
    └── rows-per-page.md                    # NEW (per FR-009 constitution): per-feature document
```

**Structure Decision**: Option 2 — Web application (frontend + backend). Существующая
структура Karaoke уже следует этому паттерну (`karaoke-app` + `webvue3`).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет нарушений) | — | — |

**Замечания (не нарушения, но trade-offs)**:

1. **Глобальное хранение вместо per-user** — пользователь явно выбрал в Clarifications Q1
   хранение в `KaraokeProperties`, который сам по себе глобальный. Альтернатива (per-user
   через новую таблицу `tbl_user_table_settings`) требует миграции + новых эндпоинтов —
   out of scope для этой фичи.

2. **Без оптимистичного обновления** — пользователь явно выбрал B в Clarifications Q3
   (отверг рекомендацию A). Альтернатива (optimistic + rollback) сложнее в реализации
   и была отвергнута.

3. **14 разных дефолтов** (25, 30, 50, 500) — это существующее поведение, не меняем
   (FR-010). Если бы был один дефолт — можно было бы один параметр; сейчас — 14.

## References

- [spec.md](./spec.md) — спецификация фичи.
- [research.md](./research.md) — исследования по техническим решениям.
- [data-model.md](./data-model.md) — модель данных (KaraokeProperties + Vuex store).
- [contracts/api-properties.md](./contracts/api-properties.md) — контракт API.
- [quickstart.md](./quickstart.md) — manual E2E сценарий.
- [knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md](../../knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md) — паттерн webvue3 пагинации.
- [knowledge/adr/local-0001-karaoke-properties-defaults.md](../../knowledge/adr/local-0001-karaoke-properties-defaults.md) — конвенция дефолтов `KaraokeProperties`.

## Готовность к следующему этапу

✅ Все Constitution Check gates пройдены.
✅ Все 3 NEEDS CLARIFICATION резолвнуты (см. spec.md § Clarifications).
✅ Phase 0 research завершён.
✅ Phase 1 design artifacts (data-model, contracts, quickstart) созданы.
✅ Complexity Tracking — нет нарушений, есть только документированные trade-offs.

**Готов к Stage 4** (`/speckit-tasks`).
