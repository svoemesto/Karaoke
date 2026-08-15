---
status: Active
slug: 167-fix-share-claim-500
related:
  - ../domain/identity.md
  - ../domain/publishing.md
  - ../architecture/data-sync.md
  - ../../specs/167-fix-share-claim-500/spec.md
---

# 167 — Починить 500 на POST /api/public/share/claim (LiveDoc)

> Drill-down — [specs/167-fix-share-claim-500/spec.md](../../specs/167-fix-share-claim-500/spec.md).

## What it does

Премиум-владелец песни создаёт временную ссылку, пересылает другу. Друг
переходит по ссылке, лендинг `ShareView.vue` шлёт `POST /api/public/share/claim`.
Бэкенд возвращал **500** `{"errorCode":"share.notFound"}`.

**Корневая причина** (см. AGENTS.md Q&A «500 на `/api/public/share/claim` —
где DDL для share-таблиц?»): на проде **отсутствовали таблицы**
`tbl_song_share_links` и `tbl_song_share_sessions` (плюс триггеры `recordhash`/
`last_update`). DDL лежит в `deploy/karaoke-db/38_song_share_links.sql` +
`39_song_share_recordhash.sql` (восстановлены в Pass 47).

**Исключение маскируется в двух местах**:
1. `SongShareLinkService.tryClaim:597-602` — `catch (e: Exception)` →
   `throw NotFound()` (логирует в stderr/log).
2. `PublicShareController.claim:172-176` — `catch (_: Exception) → 500 share.notFound`.

**Фикс**: применить миграции на проде; убрать catch-all маскировку исключений
в сервисе и контроллере (пробросить реальный stacktrace), добавить health-check
«share-таблицы существуют» (alert в мониторинг).

## User Stories (краткий список)

- **US1** (P1): `POST /api/public/share/claim` возвращает 200 + сессию (а не 500).
- **US2** (P1): Если share-таблицы отсутствуют (на проде это и был баг) — вместо маскировки `share.notFound` показывается диагностическое сообщение + логируется как `DB_MIGRATION_REQUIRED`.
- **US3** (P2): Мониторинг: `GET /healthz` проверяет наличие share-таблиц (alerts).

## Functional Requirements (указатель)

- **FR-001**: Применить миграции `38_song_share_links.sql` + `39_song_share_recordhash.sql` на проде.
- **FR-002**: Убрать catch-all `Exception → NotFound` в `SongShareLinkService.tryClaim:597-602`.
- **FR-003**: Убрать catch-all `Exception → 500 share.notFound` в `PublicShareController.claim:172-176`.
- **FR-004**: При SQL-исключении (relation does not exist) — диагностическое сообщение + logging как `DB_MIGRATION_REQUIRED` (вместо `share.notFound`).
- **FR-005**: Health-check `/healthz`: проверка наличия `tbl_song_share_links` + `tbl_song_share_sessions`.

## Acceptance Criteria

- [ ] **AC1**: На проде после миграции `POST /api/public/share/claim` → 200 + `sessionToken`.
- [ ] **AC2**: Если таблица отсутствует — НЕ 500 `share.notFound`, а 500 `db_migration_required` + alert в логе.
- [ ] **AC3**: Health-check `/healthz` → `db.migrations_applied = true` (passive check).
- [ ] **AC4**: Тест: удалить таблицу → `POST /share/claim` → 500 `db_migration_required` (не `share.notFound`).
- [ ] **AC5**: Реальные `NotFound` (ссылка действительно истекла) → 404 `share.notFound` (как и должно быть).

## Related LiveDocs

- Domain: [identity.md](../domain/identity.md) (SiteUser), [publishing.md](../domain/publishing.md) (ShareLink = guest access)
- Architecture: [data-sync.md](../architecture/data-sync.md) (recordhash для share-таблиц)
- Docs: `AGENTS.md` Q&A (миграция share-таблиц — откуда DDL)

## Code

- Backend: `karaoke-web/.../services/SongShareLinkService.kt:597-602` — tryClaim catch-all
- Backend: `karaoke-web/.../controllers/PublicShareController.kt:172-176` — claim catch-all
- Backend: `karaoke-web/.../controllers/HealthController.kt` — добавить `db_migrations_applied`
- SQL: `deploy/karaoke-db/38_song_share_links.sql`, `39_song_share_recordhash.sql` (применить)

## History

- Created: 2026-08-14
- Last updated: 2026-08-14