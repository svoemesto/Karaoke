---
status: Active
slug: 125-news-flags-backfix
related:
  - ../domain/publishing.md
  - ../features/124-filename-sanitization-rename.md
  - ../architecture/data-sync.md
  - ../../specs/125-news-flags-backfix/spec.md
---

# 125 — Backfill флагов публикации через tbl_public_settings (LiveDoc)

> Drill-down — [specs/125-news-flags-backfix/spec.md](../../specs/125-news-flags-backfix/spec.md).

## Что делает

Архитектурный fix фичи **124-filename-sanitization-rename**: kill-switch для
backfill флагов публикации хранился в `KaraokeProperties` (файл на admin-машине).
На проде `karaoke-app` не разворачивается → `getBoolean()` молча возвращал
default `false` → kill-switch **всегда выключен** → лавина auto-новостей.

**Фикс**: kill-switch перенесён в `tbl_public_settings` (Postgres) — слой,
который работает и на admin, и на проде (как Yandex SmartCaptcha).

`SongReleaseAnnouncementService.backfillPublishFlags` + UI-кнопка остаются
без изменений (они работают только на admin, и проблем там нет).

## User Stories (краткий список)

- **US1** (P1): Kill-switch реально работает на проде.

## Functional Requirements (указатель)

- **FR-001**: `tbl_public_settings.kill_switch_backfill_publish = true|false`.
- **FR-002**: Endpoint `POST /api/public/settings/kill-switch?key=backfill_publish&value=false`.
- **FR-003**: `SongReleaseAnnouncementService` читает из `tbl_public_settings` через `CaptchaConfigService`-подобный сервис.

## Acceptance Criteria

- [ ] **AC1**: На проде kill-switch работает (включение/выключение применяется).
- [ ] **AC2**: Backfill flow на admin — без изменений (UI кнопка работает).

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (news lifecycle), [data-sync.md](../architecture/data-sync.md) (`tbl_public_settings` синхронизация)
- Feature: [124-filename-sanitization-rename.md](../features/124-filename-sanitization-rename.md)

## Код

- Backend: `karaoke-app/.../service/PublicSettingsService.kt` — читать kill-switch
- SQL: `deploy/karaoke-db/<NNN>_tbl_public_settings_backfill.sql` — миграция

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14