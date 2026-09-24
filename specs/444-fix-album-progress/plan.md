# Implementation Plan: Корректный прогресс загрузки песен при открытии альбома автора

**Branch**: `444-fix-album-progress` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)
**Issue**: OpenProject #179

## Summary

При фильтре по альбому (`?albumId=N`) знаменатель прогрессометра «Загружаем X из N…» брался от всего автора (2485), а не от альбома (10). Причина: фронт всегда передавал `expectedCount = tile.songCount` (автор), а бэкенд доверял этому значению даже при `albumId`. Фикс: при `albumId` знаменатель считает **сервер** из денормализованного счётчика альбома (`total_song_count`/`ready_song_count`), фронт не передаёт авторский `expectedCount` при активном фильтре.

## Technical Context

**Language/Version**: Kotlin (Spring Boot, JDK 17) + JavaScript (Vue 3 / Vite)
**Primary Dependencies**: karaoke-app (модель `Album`), karaoke-web (Spring controller), karaoke-public (Vuex store + Vue view)
**Storage**: PostgreSQL — `tbl_albums.total_song_count` / `ready_song_count` (триггер миграции `49_albums_song_counts.sql`)
**Testing**: JUnit 5 (`:karaoke-web:test`) — чистый unit без Spring/БД
**Target Platform**: Linux server (prod `sm-karaoke.ru`), браузер
**Project Type**: web (backend + public SPA)
**Constraints**: без SQL-миграций; без изменения wire-контракта NDJSON (только значение `expectedCount`)

## Constitution Check

- **Principle II (raw JDBC)**: OK — счётчик читается через `Album.getAlbumById` (`KaraokeDbTable`), не JPA.
- **Principle VI (Code Standards)**: KDoc + JSDoc на новых публичных API, per-feature doc обновляется.
- **Principle IX (Knowledge-first)**: pre-flight выполнен, ADR `local-0007` учтён.
- **Linking Protocol**: `knowledge/` обновляется синхронно.
- **Без нарушений** — Complexity Tracking пуст.

## Project Structure

```text
specs/444-fix-album-progress/
├── spec.md
├── plan.md          # this file
├── data-model.md
├── quickstart.md
├── contracts/
│   └── zakroma-stream-progress.md
├── checklists/requirements.md
├── tasks.md
└── report.md

karaoke-web/
├── src/main/.../services/ZakromaStreamProgress.kt   # NEW — чистая логика знаменателя
├── src/main/.../controllers/PublicApiController.kt  # изменён: album-scoped expectedCount
└── src/test/.../services/ZakromaStreamProgressTest.kt  # NEW — 6 unit-тестов

karaoke-public/
└── src/views/ZakromaView.vue                         # изменён: не шлёт авторский count при albumId
```

## Implementation

1. `ZakromaStreamProgress.resolveExpectedCount(...)` — приоритет album → provided → author-fallback.
2. `PublicApiController.zakromaStream` — при `albumId` грузит `Album`, строит `AlbumCounters`, вызывает helper.
3. `ZakromaView.tryStartZakromaStream` — `expectedCount` = `undefined`, если `selectedAlbumId != null`.

## Verification

См. [tasks.md](./tasks.md) Phase 4 и SC в [spec.md](./spec.md).
