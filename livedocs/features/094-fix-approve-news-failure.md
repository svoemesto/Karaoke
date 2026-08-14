---
status: Active
slug: 094-fix-approve-news-failure
related:
  - ../domain/editorial.md
  - ../domain/publishing.md
  - ../features/092-fix-auto-news-triggers.md
  - ../features/089-auto-news-song-release.md
  - ../features/124-news-flags-backfill.md
  - ../architecture/L3-components.md
  - ../architecture/monetization.md
  - ../../specs/094-fix-approve-news-failure/spec.md
---

# 094 — Апрув задания: ошибка «Ошибка запроса», новость не появляется (LiveDoc)

> Drill-down — [specs/094-fix-approve-news-failure/spec.md](../../specs/094-fix-approve-news-failure/spec.md).

## Что делает

При нажатии в `webvue3` «Задании редактора» → «Одобрить и премиить»:
- Песня (id=23199) успешно изменена в БД (`Updated: 1`), но в UI выводится
  «Ошибка запроса», новость на сайте не появляется.
- При повторном клике модалка закрывается без явного результата.

**Корневая причина**: после `092-fix-auto-news-triggers` появился **новый
sync-путь**, и при approve локального `Song.saveToDb()` → sync на сервер →
новостной триггер → exception (например, NPE/доступ к отсутствующему
шаблону), но exception проглатывается и клиенту возвращается «Ошибка запроса»,
а новость теряется.

**Фикс**:
- `SongEditorController.approve()` — try/catch с **детальным** логированием
  каждого шага (save/sync/news).
- Сохранять `news` событие в outbox / retry queue, если sync/news падают.
- На клиенте — показывать **что именно** не получилось (не «Ошибка запроса»,
  а «Сохранение ОК, новость не создана: <reason>»).

## User Stories (краткий список)

- **US1** (P1): Approve завершается явно: «Сохранение ОК», «Новость: создана/не создана (причина)».
- **US2** (P1): Если новость не создана — её можно «досоздать» через админ-кнопку без повторного approve.

## Functional Requirements (указатель)

- **FR-001**: Endpoint `approve` возвращает детальный статус + body с понятными ошибками.
- **FR-002**: Retry-able outbox для новости (если sync/news упали).
- **FR-003**: UI `ReviewModal.vue` — детальные статусы (не generic error).
- **FR-004**: Тест-кейсы покрывают каждый failure mode.

## Acceptance Criteria

- [ ] **AC1**: Approve → UI получает `{status: "ok"|"partial", saved: true, news: "created"|"failed", reason?}`.
- [ ] **AC2**: Если sync упал → retry через `updateRemoteSongFromLocalDatabase` повторно.
- [ ] **AC3**: Если news упала → outbox-механизм доставляет асинхронно.

## Связанные LiveDocs

- Domain: [editorial.md](../domain/editorial.md), [publishing.md](../domain/publishing.md)
- Feature: [092-fix-auto-news-triggers.md](../features/092-fix-auto-news-triggers.md)
- Architecture: [L3-components.md](../architecture/L3-components.md) (sync flow)

## Код

- Backend: `karaoke-web/.../controllers/SongEditorController.kt` — `approve()` — детальный try/catch
- Backend: `karaoke-app/.../service/NewsOutboxService.kt` (новый) — outbox для новостей
- Frontend: `webvue3/src/components/Songs/ReviewModal.vue` — display detail status

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14