# Implementation Plan: Админ-таблицы «Подписки», «История прослушиваний», «Временные ссылки»

**Branch**: `171-admin-subscriptions-history` | **Date**: 2026-08-11 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/171-admin-subscriptions-history/spec.md`

## Summary

Три **read-only** админ-таблицы (Подписки, История прослушиваний, Временные ссылки) с фильтрами, пагинацией и target-aware переключателем (local/remote).

- **Backend**: 3 новых контроллера в `karaoke-app` с одним эндпоинтом `digest` каждый. Переиспользуют существующие модели (`Subscription`, `ListeningHistory`, `SongShareLinkService`).
- **Frontend**: 3 новых компонента в `webvue3/src/components/{Subscriptions,ListeningHistory,ShareLinks}/` по образцу `SitePlaylistsTable` + `SiteUsersFilterModal`. Переиспользуют существующий `revokeSiteUserShareLink` для действия «Отозвать».
- **Миграции БД**: НЕТ — все три таблицы уже существуют.

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17) для backend; Vue 3 + JavaScript (composition API опционально) для frontend.

**Primary Dependencies**:
- Backend: Spring Boot 3.x, KaraokeConnection (raw JDBC wrapper, проектный).
- Frontend: bootstrap-vue-next, Vuex 4, vue-router 4, EventSourcePolyfill (SSE, не для этих таблиц, но уже в проекте).

**Storage**: PostgreSQL через сырой JDBC (`Connection.local()`/`remote()`, конституция II NON-NEGOTIABLE).

**Testing**: ручное (см. constitution — в CI тестов нет; `quickstart.md` покрывает 5 сценариев).

**Target Platform**: Linux server для `karaoke-web` (прод); Linux admin-machine для `webvue3` (dev/admin SPA).

**Project Type**: web-service (`karaoke-web` Spring Boot) + admin SPA (`webvue3` Vue 3).

**Performance Goals**:
- Подписки: < 2 сек при 10k записей.
- История: < 3 сек при 50k записей (с JOIN к `tbl_songs`).
- Share: < 2 сек при 5k записей.

**Constraints**:
- Сырой JDBC, без JPA/Hibernate (конституция II).
- Read-only кроме `revoke` для share-ссылок.
- target=local default (как везде).
- Без SSE для этих таблиц.

**Scale/Scope**:
- `tbl_subscriptions`: ~10k записей на проде.
- `tbl_listening_history`: ~50k записей.
- `tbl_song_share_links`: ~5k записей.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Статус | Комментарий |
|---|---|---|---|
| I | Self-contained автопайплайн | **N/A** | Фича не про пайплайн. Не использует внешних SaaS. |
| II | Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE) | ✅ **PASS** | Все три эндпоинта — через `KaraokeConnection.getConnection()` + сырой SQL с `prepareStatement`, как `ListeningHistory.getForUser`. JOIN-ы — там же. `associateBy { it.id }` для сортировки не нужен (у нас одна сущность на запрос). |
| III | SyncRegistry | **N/A** | `tbl_subscriptions`, `tbl_listening_history`, `tbl_song_share_links` не участвуют в LOCAL↔SERVER sync (PROD-only). `ListeningHistory` формально в `SyncTarget.kt`, но мы только читаем, не апдейтим recordhash. |
| IV | Async-очередь | **N/A** | Не запускаем подпроцессов. |
| V | Двух-фронтенд | ✅ **PASS** | Только `webvue3` (admin). `karaoke-public` НЕ затрагивается. `permitAll()` в SecurityConfig уже настроен. |
| VI | Code Standards | ✅ **PASS** | KDoc/JSDoc обязательны для новых компонентов (FR-026 спеки). ktlint и ESLint будут проверены в CI. |
| VII | Cross-Machine Setup | ✅ **PASS** | Никаких персональных AI-конфигов. Никаких lock-файлов. Никаких line-ending-проблем (только `.vue`/`.kt`, LF по умолчанию). |
| VIII | Секреты и git-гигиена | ✅ **PASS** | Никаких секретов. `target=local/remote` — пользовательский env, не хардкод. |

**GATE result**: PASS — все применимые принципы соблюдены. Никаких нарушений для Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/171-admin-subscriptions-history/
├── plan.md              # Этот файл
├── research.md          # Phase 0 — design decisions (RQ-1…RQ-11)
├── data-model.md        # Phase 1 — 3 сущности
├── quickstart.md        # Phase 1 — 5 сценариев валидации
├── contracts/
│   ├── subscriptions-digest.md
│   ├── listeninghistory-digest.md
│   └── sharelinks-digest.md
└── tasks.md             # Phase 2 — будет создан в /speckit.tasks
```

### Source Code (repository root)

**Структура: Web application (Option 2)** — backend (Kotlin/Spring Boot) + frontend (Vue 3 SPA).

#### Backend (новые файлы)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/
├── SubscriptionsController.kt          # NEW — POST /api/subscriptions/digest
├── ListeningHistoryController.kt       # NEW — POST /api/listeninghistory/digest
└── ShareLinksAdminController.kt        # NEW — POST /api/sharelinks/digest
```

#### Frontend (новые файлы)

```text
webvue3/src/components/
├── Subscriptions/
│   ├── SubscriptionsTable.vue          # NEW — основной компонент (FR-001…FR-007)
│   ├── SubscriptionsFilterModal.vue    # NEW — модалка фильтров
│   └── store.js                        # NEW — Vuex store
├── ListeningHistory/
│   ├── ListeningHistoryTable.vue       # NEW — основной компонент (FR-008…FR-014)
│   ├── ListeningHistoryFilterModal.vue # NEW — модалка фильтров
│   └── store.js                        # NEW — Vuex store
└── ShareLinks/
    ├── ShareLinksTable.vue             # NEW — основной компонент (FR-015…FR-022)
    ├── ShareLinksFilterModal.vue       # NEW — модалка фильтров
    └── store.js                        # NEW — Vuex store

webvue3/src/views/
├── SubscriptionsView.vue               # NEW — обёртка
├── ListeningHistoryView.vue            # NEW — обёртка
└── ShareLinksView.vue                  # NEW — обёртка
```

#### Frontend (изменённые файлы)

```text
webvue3/src/router/index.js             # +3 роута (/subscriptions, /listeninghistory, /sharelinks)
webvue3/src/App.vue                     # +3 пункта меню в sidebar
webvue3/src/store/index.js              # +3 модуля в Vuex (Subscriptions, ListeningHistory, ShareLinks)
```

#### Documentation (изменённые файлы)

```text
docs/features/guest-share-link.md       # +секция «Админ-таблица /sharelinks» (FR-009 constitution)
docs/architecture-notes.md              # +запись о PR (Pass 51+)
```

**Structure Decision**: Web application (Option 2) — выбрано по умолчанию для проекта (есть существующие backend + frontend раздельные модули).

## Implementation Phases

### Phase 0 — Research ✅ DONE

`research.md` — 11 RQ-вопросов решены с Decision/Rationale/Alternatives. Все NEEDS CLARIFICATION из спеки уже разрешены.

### Phase 1 — Design ✅ DONE

- `data-model.md` — 3 сущности (Subscription, ListeningHistory, SongShareLink), все колонки, JOIN-ы, lifecycle, правила для админ-таблицы.
- `contracts/*.md` — 3 эндпоинта с request/response/SQL/псевдокодом Kotlin/JavaScript.
- `quickstart.md` — 5 сценариев end-to-end + CI-проверки + Definition of Done.

### Phase 2 — Tasks (будет в `/speckit.tasks`)

Задачи будут созданы в следующей фазе:
1. Backend: 3 контроллера (TDD-стиль: SQL → endpoint → manual test через curl).
2. Frontend: 3 view-обёртки + 3 store + 3 table + 3 filter modal.
3. Роутинг + меню.
4. Per-feature doc update (`guest-share-link.md`).
5. CI/lint проверки.

## Key Design Decisions (краткий итог)

Полный список — в `research.md`. Главные:

1. **3 отдельных контроллера** в `karaoke-app` (не в `karaoke-web`) — соответствует паттерну `SiteUsersController`, `SitePlaylistsController`.
2. **`KaraokeDbTable.loadList` + сырой SQL для JOIN** — соответствует конституции II.
3. **`revokeSiteUserShareLink` переиспользуется** — не плодим дублирующий action.
4. **Пагинация 25/500/25** для подписок/истории/share — разная плотность из-за разной «жирности» строк.
5. **Без SSE** — Out of Scope, ручной F5.
6. **target=local default** — как везде в admin SPA.
7. **Без CSV/Excel экспорта** — Out of Scope.
8. **`UserSubscriptionsModal.vue` НЕ трогаем** — это per-user modal, разный UX.

## Risks & Mitigations

| Риск | Митигация |
|---|---|
| 50k записей в `tbl_listening_history` — JOIN к `tbl_songs` медленный | Индекс `tbl_listening_history(last_played_at DESC)` уже есть (по схеме `27_listening_history.sql`). Если просядет — отдельный кеш в Redis (Out of Scope для v1). |
| Админ случайно отзовёт прод-ссылку через target=Remote | FR-019: обязательное `custom-confirm` с явным текстом «Отозвать ссылку {id} для {email}?». В quickstart 3.3 — подчёркнуто «убедитесь, что target=Local для теста». |
| `revoke` race: два админа одновременно отзывают одну ссылку | Бэкенд `UPDATE ... WHERE active=true` — атомарно, второй получит `0 rows affected` (404). |
| JOIN к удалённой песне (FK не enforced) | `LEFT JOIN tbl_songs` — даёт NULL для song_name. UI показывает «песня удалена» с id. |
| 5k share-ссылок, secret длинный | UI: первые 8 символов + `title` для полного (как `orderId` в `UserSubscriptionsModal`). |

## Dependencies

- **Внешних нет** — фича self-contained, только локальная БД.
- **Внутренние**: `Subscription.kt`, `ListeningHistory.kt`, `SongShareLinkService.kt`, `Connection.local()/remote()`, существующий `revokeSiteUserShareLink` action.

## Out of Scope (повтор из спеки для удобства)

- ❌ Редактирование подписок.
- ❌ Удаление истории.
- ❌ Создание share-ссылок.
- ❌ CSV/Excel экспорт.
- ❌ SSE-обновления для таблиц.
- ❌ Корзина (`tbl_cart_items`) — отдельный пункт меню.
- ❌ Сессии шаринга (`tbl_song_share_sessions`) — отдельный drill-down.
- ❌ Drill-down к сессиям конкретной share-ссылки.

## Links

- [spec.md](./spec.md) — функциональная спецификация (FR-001…FR-027, SC-001…SC-008).
- [research.md](./research.md) — 11 RQ-вопросов.
- [data-model.md](./data-model.md) — 3 сущности.
- [contracts/](./contracts/) — 3 API-контракта.
- [quickstart.md](./quickstart.md) — 5 сценариев валидации.
- `docs/features/guest-share-link.md` — обновляется в этом PR.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений — таблица пустая. Все применимые принципы соблюдены.
