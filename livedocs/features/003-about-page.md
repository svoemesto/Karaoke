---
status: Active
slug: 003-about-page
related:
  - ../domain/publishing.md
  - ../../specs/003-about-page/spec.md
  - ../strategy/about-page-draft.md
  - ../architecture/conversion-funnel.md
---

# 003 — Страница «О проекте» (LiveDoc)

> Drill-down — [specs/003-about-page/spec.md](../../specs/003-about-page/spec.md).

## Что делает

Страница `/about` — описание проекта с упором на то, что песни на сайте —
преимущественно русский рок. Перечислены готовые исполнители (Агата Кристи,
Машина Времени, Сплин, Наутилус, и т.д.), чтобы посетитель понимал, что
здесь найдёт (или наоборот, не найдёт — поп/шансон не наша ниша).

**Структура**:
- Hero с названием проекта (sm-karaoke.ru).
- Краткое описание.
- «Что вы найдёте» (русский рок, караоке-разметка, эфирные песен).
- «Чего здесь нет» (поп, шансон, рэп, зарубежный рок).
- Уже готовые исполнители.
- Правила доступа (free vs premium — см. `005-free-vs-premium`).

## User Stories (краткий список)

- **US1** (P1): Аноним заходит на `/about` и понимает содержание сайта.

## Functional Requirements (указатель]

- **FR-001**: `AboutView.vue` со структурой.
- **FR-002**: Список готовых исполнителей (динамически из БД `WHERE haveAlbum=true`).

## Acceptance Criteria

- [ ] **AC1**: `/about` показывает структуру.
- [ ] **AC2**: Список исполнителей автогенерируется.

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (стратегия)

## Код

- Frontend: `karaoke-public/src/views/AboutView.vue`
- Frontend: получение списка исполнителей из `/api/public/authors?hasAlbum=true&limit=50`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14