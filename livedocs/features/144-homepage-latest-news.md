---
status: Active
slug: 144-homepage-latest-news
related:
  - ../domain/publishing.md
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/144-homepage-latest-news/spec.md
  - ../../archive/docs/features/homepage-latest-news.md
---

# 144 — Компактная таблица «последние 5 новостей» на главной (LiveDoc)

> Drill-down — [specs/144-homepage-latest-news/spec.md](../../specs/144-homepage-latest-news/spec.md).

## Что делает

На главной странице публичного сайта (`/` для `karaoke-public` и Thymeleaf-`/`
для `karaoke-web`) теперь виден **компактный блок «Последние новости»** —
5 самых свежих опубликованных новостей (`tbl_news`).

**Каждая строка**: дата/время (в TZ пользователя), заголовок, ссылка на песню/новость.

**Блок виден** и анонимному посетителю — без авторизации и подписки.
Если новостей < 5 — блок показывает сколько есть (1-4), без пустых заглушек.
Если 0 — блок вообще не показывается.

**Мотивация**: посетитель сегодня не видел на главной никаких новостей,
а лента на `/news` активно пополняется автоматически (19000+ записей через
`specs/089-auto-news-song-release`). «Последние 5 новостей» на главной —
минимальный сигнал «проект живой», работает на воронку
`visitor → registration` (стратегия роста).

## User Stories (краткий список)

- **US1** (P1): Посетитель видит на главной блок «Последние новости» с 5 строками.

## Functional Requirements (указатель)

- **FR-001**: Backend `GET /api/public/news/recent?limit=5` → 5 свежих опубликованных новостей.
- **FR-002**: Frontend — компактный компонент `LatestNewsBlock.vue`.
- **FR-003**: Только опубликованные (`publish_at <= now()`), order by `publish_at DESC, id DESC`.
- **FR-004**: Если новостей < 5 — блок показывает сколько есть; если 0 — не показывается.
- **FR-005**: Видим анонимам (без авторизации).

## Acceptance Criteria

- [ ] **AC1**: ≥ 5 новостей → блок на главной с 5 строками.
- [ ] **AC2**: < 5 новостей → блок с 1-4 строками, без пустых заглушек.
- [ ] **AC3**: 0 новостей → блок не показывается.
- [ ] **AC4**: Клик по строке → переход на `News.link` (для авто-новостей — `/song?id={id}`).
- [ ] **AC5**: Аноним без авторизации видит блок.

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (news lifecycle), [catalog.md](../domain/catalog.md) (Song)
- Architecture: [L3-components.md](../architecture/L3-components.md)
- Specs: `089-auto-news-song-release` (базовая логика авто-новостей), `144` — UI-расширение

## Код

- Backend: `karaoke-web/.../controllers/PublicNewsController.kt` — `GET /recent`
- Backend: `karaoke-app/.../service/NewsService.kt` — `getRecentPublished(limit)`
- Frontend: `karaoke-public/src/components/LatestNewsBlock.vue` (новый)
- Frontend: `karaoke-public/src/views/HomeView.vue` — встраивание блока
- Frontend: `karaoke-web/src/main/resources/templates/main.html` — встраивание (Thymeleaf-вариант)
- CSS: `assets/main.css` (karaoke-public) — компактный стиль таблицы

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14