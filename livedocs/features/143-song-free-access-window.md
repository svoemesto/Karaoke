---
status: Active
slug: 143-song-free-access-window
related:
  - ../domain/publishing.md
  - ../domain/catalog.md
  - ../domain/stats.md
  - ../features/144-homepage-latest-news.md
  - ../../specs/143-song-free-access-window/spec.md
---

# 143 — Временное окно бесплатного доступа к песням (LiveDoc)

> Drill-down — [specs/143-song-free-access-window/spec.md](../../specs/143-song-free-access-window/spec.md).

## Что делает

Смена модели монетизации:
- **Убран флаг `exclusive`** из админки (и из кода).
- **Песня доступна всем** ограниченное время после наступления эфира
  (по умолчанию **1 календарный месяц**), затем — только по подписке.
- **Флаг `free`** делает песню «вечно в эфире» (бесплатной всегда), даже
  без даты эфира.
- Описание правил обновлено на странице «О проекте».
- Пересчитаны счётчики (включая главную и StatBySong).
- В «Закромах» для неавторизованных/непремиум: «Будет в эфире с …» /
  «В эфире до …» для песен в окне; для вечно-бесплатных / купленных / премиум —
  ничего.

## User Stories (краткий список)

- **US1** (P1): Бесплатный доступ ограничен 1 календарным месяцем после эфира.
- **US2** (P1): После окна — «Эта песня доступна только по подписке» (обезличенно).
- **US3** (P1): Флаг `free` → вечно-бесплатная, без даты эфира.
- **US4** (P2): В Закромах правильные подписи для неавторизованных/непремиум.

## Functional Requirements (указатель)

- **FR-001**: Удалить поле `exclusive` из `Song` / DTO / UI.
- **FR-002**: Константа `FREE_ACCESS_WINDOW_DAYS = 30` (календарный месяц) → переменная в `KaraokeProperties`.
- **FR-003**: Метод `isInFreeAccessWindow(now)`: возвращает true если `free == true` ИЛИ (`publish_at` есть AND `now() < publish_at + FREE_ACCESS_WINDOW`).
- **FR-004**: Текст «Эта песня доступна только по подписке» (обезличенный, без даты/причины).
- **FR-005**: `SongView.vue` / `ZakromaView.vue` — правильные подписи.
- **FR-006**: Обновить страницу «О проекте» (правила доступа).
- **FR-007**: Пересчитать `StatBySong` (главная, виджет).

## Acceptance Criteria

- [ ] **AC1**: Песня в эфире < 30 дней → доступна бесплатно (аноним / без подписки).
- [ ] **AC2**: Песня в эфире > 30 дней → «Эта песня доступна только по подписке».
- [ ] **AC3**: Песня `free = true` → всегда бесплатная (без окна).
- [ ] **AC4**: Флаг `exclusive` отсутствует в UI, DTO, миграциях.
- [ ] **AC5**: Страница «О проекте» описывает новые правила.

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (window logic), [catalog.md](../domain/catalog.md) (Song)
- Feature: [144-homepage-latest-news.md](../features/144-homepage-latest-news.md) (главная)
- Specs: `005-free-vs-premium` (старая модель), `143` — новая модель

## Код

- Backend: `karaoke-app/.../model/Song.kt` — удалить `exclusive`, добавить `isInFreeAccessWindow()`
- Backend: `karaoke-app/.../KaraokeProperties.kt` — `FREE_ACCESS_WINDOW_DAYS`
- Backend: `karaoke-app/.../service/StatService.kt` — пересчёт счётчиков
- SQL: `deploy/karaoke-db/<NNN>_tbl_settings_drop_exclusive.sql` — миграция (drop column)
- Frontend: `karaoke-public/src/views/SongView.vue` — проверка `isInFreeAccessWindow`
- Frontend: `karaoke-public/src/views/AboutView.vue` — обновлённое описание

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14