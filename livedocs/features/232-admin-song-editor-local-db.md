---
status: Active
slug: 232-admin-song-editor-local-db
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../architecture/dual-db-access.md
  - ../../specs/232-admin-song-editor-local-db/spec.md
---

# 232 — Облегчённый редактор песен в админке пишет в локальную БД admin-машины (LiveDoc)

> Drill-down — [specs/232-admin-song-editor-local-db/spec.md](../../specs/232-admin-song-editor-local-db/spec.md).

## Что делает

Точечная фича: эндпоинты `POST /api/songeditor/edit/byId` и
`POST /api/songeditor/edit/save` в режиме `mode='song'` теперь
**всегда** читают и пишут Song в локальную БД admin-машины
(`Connection.local()`), независимо от того, какое значение `target`
прислал клиент.

### Почему это было нужно

В webvue3 store `assignmentsTarget` по умолчанию = `'remote'`
(см. KaraokeProperty `editorAssignmentDefaultTarget`,
[`webvue3/src/components/SongEditor/store.js:145`](../../webvue3/src/components/SongEditor/store.js)).
Это нужно для **заданий** (редактор на сайте может править черновик,
живущий на SERVER). Но эта же дефолтность тащилась и в `mode='song'` —
правки на песню уезжали на SERVER-БД. После sync LOCAL → SERVER
локальная версия перезаписывалась серверной, и пользователю казалось,
что «правки пропали».

### Что изменилось

| Метод | До | После |
|-------|-----|-------|
| `editById` (mode='song') | `Song.loadFromDbById(songId, WORKING_DATABASE, …)` | `Song.loadFromDbById(songId, Connection.local(), …)` |
| `editSave` (mode='song') | `withDb(target) { db → Song.loadFromDbById(id, db, …) }` | напрямую `Song.loadFromDbById(id, Connection.local(), …)` |

Параметр `target` в HTTP-запросе к обоим эндпоинтам **остаётся**
(обратная совместимость с фронтом), но для `mode='song'` **игнорируется**
для выбора БД.

### Что НЕ изменилось

- `mode='assignment'` — без изменений. Задания/черновики остаются
  target-aware (`db = withDb(target)`).
- Другие target-aware эндпоинты `SongEditorController` (`digest`,
  `statusbysongids`, `assign`, `revoke`, `delete`, `approve`,
  `reject`) — без изменений.
- Контракт HTTP-ответа: структура JSON, имена полей, HTTP-200 для всех
  ответов контроллера. Существующий фронт (`SongKaraokeEditorModal.vue`)
  работает без изменений.
- Sync LOCAL ↔ SERVER — отдельная, явная операция пользователя
  («Синхронизация в 1 клик»), а не побочный эффект редактирования.

## User Stories (краткий список)

- **US1** (P1): Правки в облегчённом редакторе песни (`mode='song'`)
  сохраняются в локальную БД admin-машины, а не уходят на серверную.
- **US2** (P2): Источник данных для чтения и записи в `mode='song'`
  совпадает (обе операции — в LOCAL-БД); расхождение «что вижу — что
  сохраняю» устранено.

## Functional Requirements (указатель)

- **FR-001**: `editById` в `mode='song'` читает Song из `Connection.local()`.
- **FR-002**: `editSave` в `mode='song'` пишет Song в `Connection.local()`.
- **FR-003**: `mode='assignment'` остаётся target-aware (без изменений).
- **FR-004**: Другие эндпоинты `SongEditorController` — без изменений.
- **FR-005**: Если Song отсутствует в LOCAL-БД, `editById` возвращает
  `{"found": false, "error": "song_not_found_in_local_db", ...}` —
  отличимый код для понятного UI-сообщения «выполните sync LOCAL ← SERVER».
- **FR-006**: Обратная совместимость HTTP-контракта.
- **FR-007**: Параметр `target` для `mode='song'` игнорируется
  серверной стороной при выборе БД.
- **FR-008**: Никаких миграций данных.

Полный список — в [spec.md](../../specs/232-admin-song-editor-local-db/spec.md).

## Acceptance Criteria

- [ ] **AC1**: На admin-машине при `assignmentsTarget='remote'` редактор
  песни (`mode='song'`) показывает данные из LOCAL-БД.
- [ ] **AC2**: После сохранения правка появилась в `tbl_songs` LOCAL-БД
  (через прямой SELECT), а SERVER-БД не менялась до явного sync.
- [ ] **AC3**: Повторное открытие редактора той же песни — правки на месте.
- [ ] **AC4**: `mode='assignment'` не сломался: задания на LOCAL
  редактируются в LOCAL-БД, задания на SERVER — в SERVER-БД.
- [ ] **AC5**: Логи `karaoke-app` показывают `name="LOCAL"` для
  `KaraokeConnection` при чтении/записи Song в `mode='song'`.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song/lyrics),
  [editorial.md](../domain/editorial.md) (задания редакторов остаются target-aware)
- Architecture: [dual-db-access.md](../architecture/dual-db-access.md)
  (`Connection.local()/remote()`, ThreadLocal, фабрики)
- Specs: [specs/232-admin-song-editor-local-db/spec.md](../../specs/232-admin-song-editor-local-db/spec.md),
  [contracts/api-contracts.md](../../specs/232-admin-song-editor-local-db/contracts/api-contracts.md),
  [quickstart.md](../../specs/232-admin-song-editor-local-db/quickstart.md)
- Предыдущие фичи редакторов:
  [163-fix-song-editor-regressions.md](163-fix-song-editor-regressions.md),
  [182-editor-self-assign-tasks.md](182-editor-self-assign-tasks.md)

## Код

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt`
  - `editById` (строки ~732–836) — `Song.loadFromDbById(songId, Connection.local(), …)`
  - `editSave` (строки ~843–908) — для `mode='song'` убрана обёртка
    `withDb(target)`, прямое использование `Connection.local()`.
- Frontend: `webvue3/src/components/SongEditor/SongKaraokeEditorModal.vue`
  — **без изменений** (сервер игнорирует `target` для `mode='song'`,
  фронт продолжает слать как раньше).

## История

- Создан: 2026-08-15
- Последнее обновление: 2026-08-15
