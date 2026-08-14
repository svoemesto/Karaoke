---
status: Active
slug: 090-news-pagination
related:
  - ../domain/publishing.md
  - ../features/093-news-pagination-top-35.md
  - ../architecture/L3-components.md
  - ../../specs/090-news-pagination/spec.md
---

# 090 — Пагинация ленты новостей (LiveDoc)

> Drill-down — [specs/090-news-pagination/spec.md](../../specs/090-news-pagination/spec.md).

## Что делает

Авто-фича `089-auto-news-song-release` создала **19000+ новостей** на проде
(по одной на каждую уже готовую песню в эфире вместо снапшота на активации).
Страница «Новости проекта» пыталась отрисовать все 19000+ карточек одним
запросом — крайне медленно или вообще не открывалась на слабых устройствах.

**Фикс** — пагинация **просмотра списка** (без изменения логики
авто-создания новостей):
- Публичный сайт: `/news` — 20/страница, ссылки «следующая порция».
- Админка: `/news` — 35/страница, стандартная пагинация (см. `093-news-pagination-top-35`).

## User Stories (краткий список)

- **US1** (P1): `/news` на публичном сайте → порция новостей (например, 20), навигация.
- **US2** (P2): Админка `/news` — пагинация (см. `093-news-pagination-top-35`).

## Functional Requirements (указатель)

- **FR-001**: Backend `GET /api/public/news?page=1&size=20` → 20 свежих.
- **FR-002**: Frontend `NewsView.vue` — отображает 20 + «Ещё».
- **FR-003**: Edge case: при последней странице — без «Ещё».

## Acceptance Criteria

- [ ] **AC1**: `/news` на стенде с 19000+ → 20 новостей, навигация.
- [ ] **AC2**: При < 20 новостях — нет «Ещё».
- [ ] **AC3**: Админка имеет пагинацию (см. `093`).

## Связанные LiveDocs

- Domain: [publishing.md](../publishing.md)
- Feature: [093-news-pagination-top-35.md](../features/093-news-pagination-top-35.md) (admin-вариант)

## Код

- Backend: `karaoke-web/.../controllers/PublicNewsController.kt` — `?page=&size=` параметры
- Frontend: `karaoke-public/src/views/NewsView.vue` — пагинация

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14