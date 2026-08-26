# Specification Quality Checklist: Закрома автора — отрисовка списка песен без N×3 фоновых запросов

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-25
**Feature**: [spec.md](./spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Примечание: спека упоминает конкретные компоненты/файлы (`PlayerIcon.vue`, `usePlaylistMembership.js`,
    `tbl_songs`, `PublicPlayerController.stemsReady`) — это **имена существующих единиц**, на которые
    накладываются требования, а не выбор технологического стека. Никаких «использовать React», «Redis»,
    «WebSocket» — фреймворк не навязан.
- [x] Focused on user value and business needs
  - Все сценарии сформулированы от лица пользователя (редактор/аноним/премиум), метрики — пользовательские
    (нет спиннеров, страница не висит, иконки правильного цвета).
- [x] Written for non-technical stakeholders
  - Описания сценариев в терминах поведения («у всех 2500 строк иконки плеера сразу показывают
    финальное состояние», «спиннеров нет»), а не кода.
- [x] All mandatory sections completed
  - User Scenarios & Testing (US1, US2, US3 + Edge Cases), Requirements (FR-001..FR-027), Key Entities,
    Success Criteria (SC-001..SC-007), Assumptions — все заполнены.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - Все «неясные» моменты (новые endpoint'ы, поведение анонима) вынесены в Assumptions с
    разумными дефолтами (FR-006 + FR-013 — добавить endpoint, если нет; «гостевая» иконка —
    на усмотрение дизайна, функциональное требование одно: 0 запросов).
- [x] Requirements are testable and unambiguous
  - FR-001..FR-027 сформулированы MUST-формой, каждое проверяемо (визуально, DevTools, логом).
- [x] Success criteria are measurable
  - SC-001 (доступность главной во время загрузки), SC-002 (≤ 3 HTTP-запроса — DevTools),
    SC-003 (визуальная проверка спиннеров), SC-004 (≤ 5 сек до полного списка), SC-005 (counter
    снижается на ≥ 95% — Prometheus), SC-006 (3 типа юзеров × 3 типа песен), SC-007 (60 FPS).
- [x] Success criteria are technology-agnostic (no implementation details)
  - SC-002 ссылается на «DevTools Network» — это инструмент разработчика, не фреймворк. SC-005
    ссылается на Prometheus — это observability-стек, но метрика («счётчик снижается на 95%»)
    может быть считана любым способом (логи, метрики, ручной аудит).
- [x] All acceptance scenarios are defined
  - US1 — 5 сценариев, US2 — 3, US3 — 4. Покрыты основной поток + ключевые ветки.
- [x] Edge cases are identified
  - 8 edge cases: стрим оборвался, membership-fetch упал, медленный membership, смена автора
    в момент загрузки, BroadcastChannel не поддерживается, низкая скорость сети, вход/выход
    во время просмотра, 0 песен.
- [x] Scope is clearly bounded
  - Фича покрывает только страницы списка песен (`ZakromaView`, `SearchView`, `AuthorPlaylistView`).
    Страницы одиночной песни (`SongView`, `PlayerView`) явно вне scope (FR-004, FR-005).
- [x] Dependencies and assumptions identified
  - Assumptions: существующие поля `flag_free`, `is_in_air`, `player_ready_*` (Pass 100), endpoint
    `/api/public/account/favorites/ids` (добавить, если нет), текущие call-site'ы (удалить в этом
    PR), Constitution Check (Принципы I, II, V, VIII), LiveDocs обновление.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - Все FR-001..FR-027 проверяются через US-сценарии или SC-метрики.
- [x] User scenarios cover primary flows
  - US1 (редактор — P1, основной репорт), US2 (премиум — P2, монетизация), US3 (аноним — P2,
    массовый сегмент).
- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC-001..SC-007 — все проверяемы без знания внутренностей реализации.
- [x] No implementation details leak into specification
  - Упоминания файлов/компонентов — это **контрактные единицы**, на которые накладываются
    требования (например, «PlayerIcon MUST принимать новые props» — это требование к
    контракту компонента, а не к его реализации). Никаких «использовать computed ref»,
    «через Vuex», «store.dispatch» — это уровень реализации.

## Notes

- Спека готова к `/speckit.clarify` (если потребуются уточнения) или `/speckit.plan`.
- Главный риск — конкретные имена endpoint'ов (FR-006, FR-012, FR-013): если на бэке уже
  есть похожие, планирование выберет наименьшее изменение. Это вынесено в Assumptions.
- Backward-compat для `PlayerIcon` (FR-017 — `'loading'` трактуется как `'notready'`) — страховка
  от частичного перевода call-site'ов в этом PR.