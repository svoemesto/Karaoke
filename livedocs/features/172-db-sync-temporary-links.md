---
status: Active
slug: 172-db-sync-temporary-links
related:
  - ../domain/identity.md
  - ../domain/publishing.md
  - ../architecture/data-sync.md
  - ../../specs/172-db-sync-temporary-links/spec.md
---

# 172 — Временные ссылки в sync LOCAL↔SERVER (LiveDoc)

> Drill-down — [specs/172-db-sync-temporary-links/spec.md](../../specs/172-db-sync-temporary-links/spec.md).

## Что делает

Добавляет `tbl_song_share_links` (временные ссылки) в одно-клик-синхронизацию
LOCAL↔SERVER между админкой и продом. **По умолчанию** направление — **с сервера
на локал** (читай: актуальные ссылки живут на проде, админ подтягивает к себе для
аналитики). Можно вручную выбрать обратное направление.

**Что добавлено в `SyncRegistry`**: новая запись для `SongShareLink` + 8 флагов
`sync_song_share_link_<push|pull>_<insert|update|delete|move>_allowed` в
`KaraokeProperties`. `recordhash`-триггер на таблице (см. `39_song_share_recordhash.sql`)
уже есть — этого **недостаточно** для участия в sync (см. Constitution § III:
наличие триггера НЕ = участие).

**Edge cases**:
- Нет соединения с одной из БД → понятное сообщение, не «частично успешно».
- Конфликтующие изменения → админ получает сведения о конфликте, никаких тихих
  перезаписей.
- Истёкшие/отозванные ссылки синхронизируются как обычные записи со своим
  текущим состоянием.
- Повторный запуск после успешной sync не создаёт дубликатов.

## User Stories (краткий список)

- **US1** (P1): Админ запускает sync, по умолчанию — сервер → локал.
- **US2** (P2): Можно выбрать обратное направление (локал → сервер) для восстановления.

## Functional Requirements (указатель)

- **FR-001**: `SyncRegistry.all` MUST включать `SongShareLink`.
- **FR-002**: 8 флагов `sync_song_share_link_<...>` в `KaraokeProperties`.
- **FR-003**: `recordhash`-триггер на `tbl_song_share_links` MUST быть (есть — `39_song_share_recordhash.sql`).
- **FR-004**: Дефолтное направление для share-links — server → local.
- **FR-005**: При отсутствии соединения — clear error message, без ложного «success».
- **FR-006**: При конфликте — UI отображает diff для выбора направления.

## Acceptance Criteria

- [ ] **AC1**: Создать/изменить share-link на сервере → sync по умолчанию → локал содержит ту же запись.
- [ ] **AC2**: Удалить share-link на сервере → sync отражает удаление на локали (по sync_<key>_push_delete_allowed).
- [ ] **AC3**: Идентичное состояние → sync без изменений, успешно.
- [ ] **AC4**: Явный выбор локал → сервер → изменения переносятся на сервер.

## Связанные LiveDocs

- Domain: [identity.md](../domain/identity.md) (ShareLink owner = SiteUser), [publishing.md](../domain/publishing.md) (гостевой доступ)
- Architecture: [data-sync.md](../architecture/data-sync.md) (SyncRegistry, recordhash, O(n))

## Код

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt` — добавить `SongShareLink` в `SyncRegistry.all`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` — 8 флагов
- `deploy/karaoke-db/38_song_share_links.sql` — таблица (уже существует)
- `deploy/karaoke-db/39_song_share_recordhash.sql` — триггер (уже существует, см. Pass 47)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14