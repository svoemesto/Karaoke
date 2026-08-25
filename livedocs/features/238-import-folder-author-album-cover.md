---
status: Active
slug: 238-import-folder-author-album-cover
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../architecture/L3-components.md
  - ../../specs/238-import-folder-author-album-cover/spec.md
  - ../../specs/238-import-folder-author-album-cover/plan.md
  - ../../specs/238-import-folder-author-album-cover/research.md
  - ../../specs/238-import-folder-author-album-cover/contracts/apply-auto-album-cover.md
  - ../../specs/238-import-folder-author-album-cover/contracts/find-parent-same-author.md
---

# 238 — Импорт из папки: родители только у того же автора + автообложка альбома (LiveDoc)

> Drill-down — [specs/238-import-folder-author-album-cover/spec.md](../../specs/238-import-folder-author-album-cover/spec.md).

## Что делает

Две точечные правки в общей логике импорта из папки (`Song.createFromPath`):

1. **Поиск «родителя» ограничен только тем же автором.** В `Utils.findDuplicateOriginal` убран
   fallback на поиск среди песен других авторов — теперь функция ищет только у того же автора,
   при ненахождении возвращает `null`. Устраняет ложные привязки текста/маркеров от чужого автора.
2. **Автообложка нового альбома из графического файла в `rootFolder`.** При создании **нового**
   альбома в импорте из папки ищется ровно один графический файл (`jpg|jpeg|png|webp|bmp|tiff`,
   не скрытый) в `rootFolder` каждой песни, обрезается по короткой стороне до 1:1, масштабируется
   до 400×400 и сохраняется как `LogoAlbum.png` + превью; через существующую логику `song.pictureAlbum`
   запись попадает в `tbl_pictures` и MinIO.

Существующая `findOrCreateForSongImport` остаётся без изменений для обратной совместимости
с `AlbumBackfill`. Оба эндпоинта (`/api/utils/createfromfolder` через admin UI и
`/utils/createfromfolder` через legacy шаблон `main.html`) автоматически покрыты через общую
функцию `Song.createFromPath`.

## User Stories (краткий список)

- **US1** (P1): Поиск «родителя» только у того же автора.
- **US2** (P1): Автообложка нового альбома из графического файла в `rootFolder`.

## Functional Requirements (указатель)

- **FR-001..FR-004**: поиск «родителя» у того же автора (US1).
- **FR-005..FR-011**: автообложка нового альбома (US2).
- **FR-012**: UI/UX не меняется.

## Acceptance Criteria

- [ ] **AC1** (SC-001): Cross-author не привязывается — 100% случаев.
- [ ] **AC2** (SC-002): Same-author привязывается — текущее поведение сохранено в 100% случаев.
- [ ] **AC3** (SC-003): Новый альбом + 1 графический файл → `LogoAlbum.png` 400×400 PNG создан, превью создано, `tbl_pictures` обновлена, MinIO обновлён.
- [ ] **AC4** (SC-004): 0 или ≥2 графических файлов → автообложка не создаётся, импорт не падает.
- [ ] **AC5** (SC-005): Существующий альбом + лежащий рядом графический файл → обложка НЕ перезатирается.
- [ ] **AC6** (SC-006): `git diff webvue3/` пустой или содержит только служебные правки.
- [ ] **AC7** (SC-007): Кнопка «Найти и обработать дубликаты» работает как раньше.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Album), [editorial.md](../domain/editorial.md) (Song)
- Architecture: [L3-components.md](../architecture/L3-components.md) (Queue + Async, не задействовано)

## Код

- Backend: `karaoke-app/.../utils/Utils.kt` — `findDuplicateOriginal` (точечная правка fallback).
- Backend: `karaoke-app/.../model/Album.kt` — новые companion-методы `findOrCreateForSongImportRaw`, `applyAutoAlbumCoverFromFolder`, `findOrCreateForSongImportWithAutoCover`.
- Backend: `karaoke-app/.../model/Song.kt` — `createFromPath:8064` заменяет вызов `findOrCreateForSongImport` → `findOrCreateForSongImportWithAutoCover`.

## История

- Создан: 2026-08-25 (Pass 63+)
- Последнее обновление: 2026-08-25