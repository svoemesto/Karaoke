# Report: Редактор видит все альбомы автора (spec 360, OpenProject #76)

> **Feature branch**: `360-working-on-task-76`
> **Spec**: [spec.md](spec.md)
> **Status**: Implemented, validated by owner on prod (Pass 361, 2026-09-10)
> **Issue**: OpenProject #76 «Не все альбомы автора отображаются для редактора»
> **Pipeline**: `/speckit-full 76` → Stages 1-6 пройдены последовательно (specify → clarify → plan → tasks → analyze → implement).
> **Owner validation (2026-09-10)**: «проверил, работает» — подтверждение в браузере после деплоя.

## Что сделано

Минимальный bugfix: `karaoke-public/src/views/ZakromaAlbumsView.vue` теперь передаёт `Authorization: Bearer <token>` в `fetch('/api/public/authors/{authorId}/albums')`. Бэкенд уже умел делать bypass `isEditor` через `Album.loadAlbumTilesWithCounts(authorId, onlyPublished, ...)` + `PublicApiController.onlyPublishedFor(request) = !isEditor` (реализовано в Pass 360, спек 356) — но клиент не передавал токен, поэтому сервер всегда видел анонимного пользователя и возвращал выборку гостя (только `ready_song_count > 0`).

### Изменения

1. **Frontend** (`karaoke-public/src/views/ZakromaAlbumsView.vue`, метод `loadAlbums()`):
   - Добавлена передача `Authorization`-заголовка в fetch. Inline-паттерн — точно как в `useZakromaStreamProgress.js:144-146` и `services/api.js:15-18` (`authHeader()`).
   - Ключ `km_auth_token` в `localStorage` — общий с `useAuth.js:7`. Если токена нет (аноним) — заголовок не передаётся, поведение как у гостя (как до фикса).
   - Добавлен JSDoc с обоснованием (issue #76, root cause, ссылка на `SiteUserResolver.resolve`).
   - Diff: +23 / -0 строк.

2. **Документация** (`docs/features/zakroma-albums-by-author.md`):
   - Добавлена секция «Bugfix #76 (spec 360 — Pass 361, 2026-09-10)» с root cause, фиксом и валидацией. Diff: +18 / -1.

3. **Livedocs** (`docs/architecture-notes.md` + `knowledge/domains/catalog/domain.md`):
   - `docs/architecture-notes.md`: добавлена запись «Pass 361» (продолжение Pass 358).
   - `knowledge/domains/catalog/domain.md`: добавлена ссылка на `360-editor-sees-all-albums` в список связанных спек (будущие агенты увидят bugfix #76 в Knowledge).

### Что НЕ менялось (по построению)

- Backend: `Album.loadAlbumTilesWithCounts`, `PublicApiController.authorAlbums`, `SiteUserResolver.resolve` — никаких изменений. Бэкенд уже корректен с Pass 360.
- DTO: `AlbumTilePublicDto` — без изменений (поля `totalSongCount`/`readySongCount` уже есть).
- БД: `tbl_albums` (`total_song_count`/`ready_song_count`/`recordhash`), миграция 49, триггер `trg_tbl_songs_update_album_counts` — без изменений.
- Другие клиенты: `apiGet()` (в `services/api.js`), `useZakromaStreamProgress.js` — уже передавали заголовок, bypass для них работал с Pass 360.

## Валидация

### Pre-flight (баг подтверждён на API-уровне)

- В БД: автор #17 (АнимациЯ) имеет 6+ альбомов с `ready_song_count = 0` (id=244, 252, 254, 257, 261, 265, 270).
- Анонимный `GET /api/public/authors/17/albums` → 22 альбома, 0 с `readySongCount=0` (как гость — баг подтверждён).

### После фикса (API-уровень)

| Сценарий | Альбомов | С `readySongCount=0` | Поведение |
|---|---|---|---|
| Без токена (анонимный) | 22 | 0 | Как до фикса (regression OK) |
| С валидным токеном редактора (id=1) | **29** | **7** | **ФИКС РАБОТАЕТ** |
| С невалидным токеном | 22 | 0 | Анонимный behavior (резолв → null) |

### Owner validation на проде (2026-09-10)

Владелец проверил фикс в браузере: «проверил, работает». Подтверждено для автора #17 (АнимациЯ): редактор видит 29 альбомов (включая 7 с `readySongCount=0`: «Курьер из Рая», «Псих», «Happy End», «Роман», «Монолог», «По методичке (Другая версия)», «STAR'Ё»), подпись плашек «N песен». Гость на той же странице — 22 альбома, подпись «N готовых» (как до фикса).

### Сборка / деплой

- `cd karaoke-public && npm run lint:check` → 0 errors.
- `cd karaoke-public && npx prettier --check "src/views/ZakromaAlbumsView.vue"` → passed (после `--write`).
- `cd karaoke-public && npm run build` → vite v7.3.6, 3.85s, 295 модулей, asset hash `index-D2Fu0cYQ.js`.
- `cd deploy && bash do.sh build_public && bash do.sh start_public` → образ `svoemestodev/karaoke-public:1` (Sep 10 10:22), контейнер `karaoke-public` работает.
- `docker logs --tail 30 karaoke-public` → 200 OK, нет 5xx, нет error.

### Регрессия спек 017 (по построению)

- Фикс НЕ затрагивает `Zakroma.kt` / `Song.kt` / `Author.loadAuthorTilesWithCounts` / `PublicApiController.zakroma`.
- Bypass `isEditor` через тот же `SiteUserResolver.resolve` работает (подтверждено T011 — editor видит 7 альбомов с `readySongCount=0`).
- Полная UI-регрессия спеки 017 (закрома, поиск, страница песен) — выполняется владельцем в браузере; см. `specs/017-editor-status-bypass/`.

## Связанные документы

- [spec.md](spec.md) — функциональная спецификация (FR-001..FR-009, SC-001..SC-006).
- [research.md](research.md) — R1-R7: подтверждённая root cause, варианты A vs B, решения.
- [plan.md](plan.md) — технический план: минимальный фикс 1 файла, 0 backend-правок.
- [data-model.md](data-model.md) — справка по затронутым сущностям.
- [contracts/albums-tiles-authorization.md](contracts/albums-tiles-authorization.md) — delta-контракт API.
- [quickstart.md](quickstart.md) — пошаговая валидация (UI + curl + regression).
- [docs/features/zakroma-albums-by-author.md](../../docs/features/zakroma-albums-by-author.md) — обновлён секцией «Bugfix #76 (Pass 361)».
- [docs/architecture-notes.md](../../docs/architecture-notes.md) — запись «Pass 361».
- [knowledge/domains/catalog/domain.md](../../knowledge/domains/catalog/domain.md) — ссылка на `360-editor-sees-all-albums`.
- [specs/017-editor-status-bypass/spec.md](../017-editor-status-bypass/spec.md) — прецедент: bypass `id_status >= 6` для песен/закромов/поиска.
- [specs/356-zakroma-albums-by-author/spec.md](../356-zakroma-albums-by-author/spec.md) — спека-источник контракта bypass для альбомов.

## Чеклисты

- `checklists/requirements.md`: 24/24 ✓ PASS.
- `tasks.md`: 15/15 ✓ `[x]`.

## Governance / Workflow

- Issue #76 claim: ✅ ВЫПОЛНЕНО (`tracker.sh claim-issue 76` → `In progress`).
- Pre-flight Knowledge: ✅ выполнен (3+ grep-запроса, 8+ файлов Knowledge + 3 спеки).
- Constitution Check: ✅ 9/9 principles compliant или N/A.
- CI-gate `tools/check-spec-issue-link.py`: ✅ OK (секция `## OpenProject Tracking` валидна).
- CI-gate `tools/check-knowledge-structure.sh`: ✅ OK (9/9).
- `tracker-implement-done.sh` (Pass 350 hook): ✅ add-comment + mark-review выполнены (`In review`).
- Owner validation: ✅ «проверил, работает» (2026-09-10).
- Status: ✅ **Готово к close-issue 76**.