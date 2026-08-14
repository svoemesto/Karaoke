---
status: Active
slug: 101-song-news-flag
related:
  - ../domain/publishing.md
  - ../domain/catalog.md
  - ../architecture/data-sync.md
  - ../../specs/101-song-news-flag/spec.md
---

# 101 (news flag) — Флаг «песня доступна» + очистка ленты (LiveDoc)

> Drill-down — [specs/101-song-news-flag/spec.md](../../specs/101-song-news-flag/spec.md).

## Что делает

Модификация механизма формирования авто-новостей:
- Добавлено **поле-флаг у песни** (`news` Boolean, default `false`) в `Song`.
- При `KaraokeDbTable.save()` если `idStatus >= 6` + readiness-флаги + `news == false`
  → установить `news = true` (локально).
- При sync LOCAL → SERVER если `news: false → true` → **на сервере** создаётся
  новость «песня появилась в коллекции» (`category="premium"`).
- **Очистить `tbl_news`** (она «заспамлена»), чтобы не было дубликатов по
  ранее существовавшим песням.
- **Удалить** таблицу `tbl_song_news_announced` — больше не нужна (флаг `news`
  заменяет механизм дедупа).
- **Сохранить** механизм «В эфире» по таймеру на проде.

## User Stories (краткий список)

- **US1** (P1): При появлении песни в коллекции — авто-новость на проде.
- **US2** (P2): Лента очищена от старого спама.

## Functional Requirements (указатель)

- **FR-001**: `Song.news: Boolean` (с миграцией БД, если колонки нет).
- **FR-002**: Триггер в `markNewsAvailableIfReady()` — выставляет `news = true` при переходе.
- **FR-003**: В sync-логике LOCAL → SERVER — реакция на `news: false → true`.
- **FR-004**: Удалить `tbl_song_news_announced`.
- **FR-005**: Очистить `tbl_news` (по согласованию с пользователем).

## Acceptance Criteria

- [ ] **AC1**: Песня достигла `idStatus=6` → `news = true`, новость «в коллекции» появляется.
- [ ] **AC2**: Повторное сохранение песни без изменения состояния → новость не дублируется.
- [ ] **AC3**: `tbl_song_news_announced` удалена.
- [ ] **AC4**: Лента очищена от старых дубликатов.

## Связанные LiveDocs

- Domain: [publishing.md](../publishing.md) (news lifecycle), [catalog.md](../catalog.md) (Song.news)
- Architecture: [data-sync.md](../architecture/data-sync.md) (sync logic)

## Код

- Backend: `karaoke-app/.../model/Song.kt` — `news: Boolean`
- Backend: `karaoke-app/.../service/SongReleaseAnnouncementService.kt`
- SQL: `deploy/karaoke-db/<NNN>_tbl_settings_news.sql`
- SQL: `deploy/karaoke-db/<NNN>_drop_tbl_song_news_announced.sql`
- SQL: миграция `tbl_news` (truncate с backup)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14