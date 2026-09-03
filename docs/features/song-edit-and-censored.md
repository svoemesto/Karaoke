# Feature: SongEdit ↔ censored-name fix + field-coverage checks (spec 302)

**Spec**: [`specs/302-fix-censored-name-loss/spec.md`](../../specs/302-fix-censored-name-loss/spec.md)
**Status**: Implemented (Pass 302, branch `302-fix-censored-name-loss`)
**Created**: 2026-09-03
**Last updated**: 2026-09-03

## Назначение

Фикс бага #52 из OpenProject: ручная правка поля «Censored» в `SongEdit.vue`
терялась на бэкенде, потому что в `ApiController.songs2Update` не было
соответствующего `@RequestParam songNameCensored` — Spring Web молча
отбрасывал неизвестный query-параметр.

Спека добавляет **защиту от целого класса багов** (UI шлёт X, бэкенд не
принимает) через рефактор `songs2Update` на централизованный приём всех
параметров + статический чек покрытия UI↔backend.

## Что было сделано

1. **Рефактор endpoint** (FR-011): `ApiController.songs2Update` принимает
   все параметры через `@RequestParam Map<String, String> all`, а
   централизованный `SongUpdateMapper.apply` распределяет их по
   `fields[SongField.X]` или специальным обработчикам (fileName,
   albumId, songType). Это устраняет корневую причину — параметры больше
   не могут «потеряться», потому что они автоматически попадают в Map.

2. **Защитный чек** (FR-005/006): `tools/check-songedit-field-coverage.sh`
   автоматически ловит будущие рассинхроны UI↔backend для пары
   `SongEdit.vue ↔ /api/song/update`. Интегрирован в pre-commit + CI.

3. **Общий аудит** (FR-007/008): `tools/check-endpoint-field-coverage.sh`
   проверяет все пары UI↔backend из `tools/endpoint-pairs.yml` (MVP: одна
   пара, расширяется инкрементально).

## Контракт UI↔backend для SongEdit

**Главное правило**: каждое `v-model="song.X"` в SongEdit.vue ОБЯЗАНО
иметь одно из:

- Соответствующий ключ в `SongUpdateMapper.fieldLookup`
  (`karaoke-app/.../controllers/SongUpdateMapper.kt`, mapOf("X" to SongField.Y, ...)).
- Соответствующий ключ в `SongUpdateMapper.directSetters`
  (для полей tags, rootFolder, description, shortDescription, warning).
- Соответствующий ключ в `SongUpdateMapper.specialCaseKeys`
  (для fileName, albumId, songType — обрабатываются с бизнес-логикой).
- Запись в whitelist `tools/check-songedit-field-coverage.whitelist.yml`
  с обоснованием, почему поле намеренно не покрыто.

## Как добавить новое редактируемое поле в SongEdit

1. Добавить `<input v-model="song.<X>" />` в SongEdit.vue.
2. Добавить запись в `SongUpdateMapper.fieldLookup`:
   ```kotlin
   "<X>" to SongField.<Y>,
   ```
   (если поле — стандартное `fields[...]` поле).
   Или в `SongUpdateMapper.directSetters` (если это прямой setter).
   Или в `SongUpdateMapper.specialCaseKeys` + обработка в Phase A
   (если нужна специальная логика).
3. Если поле — нестандартное (non-String тип, специальная обработка), и
   чек не может распознать его автоматически, добавить в whitelist.
4. Прогнать `bash tools/check-songedit-field-coverage.sh` → должен выдать
   `OK: N/N полей покрыты`.

## Краткое описание бага #52 (для истории)

**Симптом**: редактор правит поле «Censored» в SongEdit, видит тост
«Изменения сохранены», но при перезагрузке страницы значение не
сохранилось. В БД `tbl_songs.song_name_censored` остаётся прежним.

**Root cause**: при реализации specs/277-song-name-censored (2026-08-30)
на UI было добавлено поле `v-model="song.songNameCensored"` и оно было
интегрировано в общий Vue-diff + payload механизм. На бэкенде в
`ApiController.songs2Update` соответствующий `@RequestParam` + setter
не были добавлены. Spring Web молча отбрасывает неизвестные
query-параметры (без исключения), поэтому параметр `songNameCensored`
терялся.

**Фикс**: рефактор `songs2Update` на `@RequestParam Map<String, String> all`
+ централизованный `SongUpdateMapper` принимает ЛЮБОЕ поле автоматически.
Корневая причина устранена в принципе — добавил новое `v-model="song.X"`,
оно автоматически попадает в Map, маппер найдёт X в fieldLookup и
сохранит. Если забыл добавить в fieldLookup — чек поймает на CI.

## Изменённые файлы (для review)

| Файл | Изменение |
|---|---|
| `karaoke-app/.../controllers/ApiController.kt` | Рефактор `songs2Update` через `SongUpdateMapper` (FR-011) |
| `karaoke-app/.../controllers/SongUpdateMapper.kt` | NEW — централизованный маппер (Phase A-E) |
| `tools/check-songedit-field-coverage.sh` | NEW — чек для SongEdit ↔ /song/update |
| `tools/check-songedit-field-coverage.whitelist.yml` | NEW — whitelist (id, albumId, songType) |
| `tools/check-endpoint-field-coverage.sh` | NEW — общий чек всех пар |
| `tools/check-endpoint-field-coverage.whitelist.yml` | NEW — глобальный whitelist |
| `tools/endpoint-pairs.yml` | NEW — список пар UI↔backend |
| `tools/cleanup-test-songs.sql` | NEW — откат тестовых данных (NFR-006) |
| `.dockerignore` | NEW — Docker exclude (Constitution § VIII) |
| `.pre-commit-config.yaml` | +2 hooks (songedit-field-coverage, endpoint-field-coverage) |
| `.github/workflows/lint.yml` | +2 steps (SongEdit coverage, endpoint coverage) |
| `specs/277-song-name-censored/spec.md` | Обновлён US-2 — ссылка на spec 302 (FR-010) |

## Связанные документы

- [`specs/302-fix-censored-name-loss/spec.md`](../../specs/302-fix-censored-name-loss/spec.md) — основная спека
- [`specs/302-fix-censored-name-loss/plan.md`](../../specs/302-fix-censored-name-loss/plan.md) — implementation plan
- [`specs/302-fix-censored-name-loss/research.md`](../../specs/302-fix-censored-name-loss/research.md) — архитектурные решения
- [`specs/302-fix-censored-name-loss/quickstart.md`](../../specs/302-fix-censored-name-loss/quickstart.md) — verification guide
- [`specs/277-song-name-censored/spec.md`](../../specs/277-song-name-censored/spec.md) — исходная фича (поле + CustomFunction реckan)
- Constitution § VI FR-006 (NON-NEGOTIABLE) — этот per-feature документ обязателен
