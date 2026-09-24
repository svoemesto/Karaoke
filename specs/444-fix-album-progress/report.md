# Report: Корректный прогресс загрузки песен при открытии альбома автора

**Issue**: #179
**Branch**: `444-fix-album-progress`
**Spec**: [`spec.md`](./spec.md) | **Tasks**: [`tasks.md`](./tasks.md)
**Date**: 2026-09-24

## Проблема

Прод, `karaoke-web` + `karaoke-public`. Автор «Машина Времени» — 2485 песен, много
альбомов. При заходе в плашку любого альбома прогресс показывал «Загружаем 0 из 2485
песен автора Машина Времени…» вместо числа песен альбома (например, «0 из 10»).
Список «Все песни автора с группировкой по альбомам» показывал корректно «0 из 2485».

## Диагностика (Knowledge-first, issue #179)

**Root cause** — две точки:
1. `karaoke-public/src/views/ZakromaView.vue:690` — `tryStartZakromaStream()` всегда
   передавал `expectedCount: tile.songCount` (число песен **всего автора**), даже при
   активном `?albumId=`.
2. `karaoke-web/.../PublicApiController.kt` (`zakromaStream`) — бэкенд **доверял**
   присланному `expectedCount` (`if (expectedCount != null && expectedCount > 0)`), не
   пересчитывая по `albumId`; fallback `Song.loadAuthorSongCounts()` тоже считал по автору.

На псевдо-плашке «Все песни автора…» (без `albumId`) — корректно, поэтому баг виден
только внутри альбома.

## Изменения

| Файл | Δ | Описание |
|---|---|---|
| `karaoke-web/.../services/ZakromaStreamProgress.kt` | NEW | Чистая `resolveExpectedCount` + `AlbumCounters`: album-scoped знаменатель. KDoc. |
| `karaoke-web/.../controllers/PublicApiController.kt` | +27 −18 | При `albumId` грузит `Album.getAlbumById` → `AlbumCounters` → helper; не доверяет фронтовому count. |
| `karaoke-public/src/views/ZakromaView.vue` | +5 −2 | При `selectedAlbumId != null` шлёт `expectedCount: undefined`. |
| `karaoke-web/src/test/.../services/ZakromaStreamProgressTest.kt` | NEW | 6 unit-тестов (гость/редактор/не найден/no-albumId trust/no-albumId fallback/no fallback при albumId). |

**Приоритет выбора знаменателя** (`resolveExpectedCount`): `albumId != null` → счётчик
альбома (`ready_song_count` гостю / `total_song_count` редактору; альбом не найден → `0`);
иначе — `providedExpectedCount > 0`; иначе — author-fallback. Без `albumId` поведение
спеки 181 не меняется.

## User stories закрыты

- **US1 (P1)** — прогресс альбома показывает число песен альбома.
- **US2 (P2)** — «Все песни автора» без `albumId` сохраняет авторский знаменатель.
- **US3 (P3)** — deep-link и редактор (total vs ready) корректны.

## Проверки (локально)

| Проверка | Команда | Результат |
|---|---|---|
| Compile | `:karaoke-web:compileKotlin` / `:karaoke-app:compileKotlin` | OK |
| Unit-тесты | `:karaoke-web:test --tests ZakromaStreamProgressTest` | 6/6 PASS |
| ktlint | `:karaoke-web:ktlintCheck` / `:karaoke-app:ktlintCheck` | PASS |
| ESLint | `cd karaoke-public && npm run lint:check` | PASS |
| Prettier | `npx prettier --check src/**` | PASS |
| Vite build | `cd karaoke-public && npm run build` | OK |
| bootJar | `:karaoke-web:bootJar` | OK |
| SSoT structure | `tools/check-knowledge-structure.sh` | 9/9 |
| SSoT cross-links | `tools/check-knowledge-cross-links.sh` | 631/631 |
| Knowledge lint | `tools/lint-knowledge.py --baseline ...` | PASS |
| Spec↔Issue link | `tools/check-spec-issue-link.py` | OK (36/36) |
| KDoc / JSDoc | `--strict` | 96.5% / 98.1% |

## Knowledge / docs

- `knowledge/system/frontend/composable-zakroma-stream.md` — Domain Invariant #4 (album-scoped expectedCount) + changelog.
- `knowledge/adr/local-0007-zakroma-album-id-in-stream-dto.md` — Follow-up-секция + история.
- `docs/features/zakroma-albums-by-author.md` — раздел Bugfix #179.
- `archive/docs/features/zakroma-stream-progress.md` — инвариант #1 дополнен исключением.
- `.ssot-map.yml` — правил для `PublicApiController.kt`/`ZakromaView.vue` нет → no-op.

## Ручная валидация (владелец, при ревью)

`quickstart.md`, 5 сценариев: главный кейс, гость vs редактор, регресс «все песни»,
deep-link, несуществующий альбом. API-проверка:
`curl -sN '.../api/public/zakroma/stream?author=...&albumId=<id>' | head -1`.

## Ограничения

- Сборка/рестарт контейнеров `karaoke-public`/`karaoke-web` — только владелец
  (AGENTS.md § Machine-Specific Exceptions, `nsa-i9`).
- SKIP-рассинхронизация счётчика-денормализации не входит в scope (корректируется
  существующим drift-detection по `done.actualCount`).
