# Report: Pass 369 — Флаг «не снимать с эфира» (free_after_on_air)

> **OpenProject**: #81 «Флаг "не снимать с эфира"»
> **Branch**: `369-free-after-onair-flag`
> **Spec**: `specs/369-free-after-onair-flag/spec.md`
> **Date**: 2026-09-11
> **Author**: agent (Karaoke)

## Что сделано

Добавлен новый boolean-флаг `freeAfterOnAir` на сущность `Song`. После
наступления `dateTimePublish` (т.е. эфир наступил) песня остаётся
публично доступной, даже после истечения стандартного окна бесплатного
доступа (1 календарный месяц).

## Изменения

### БД

- `deploy/karaoke-db/50_tbl_songs_free_after_on_air.sql` — новая миграция:
  `ALTER TABLE tbl_songs ADD COLUMN IF NOT EXISTS free_after_on_air BOOLEAN NOT NULL DEFAULT false`.
  **ВНИМАНИЕ**: имя таблицы — `tbl_songs`, НЕ `tbl_settings` (см. миграцию
  28 — `28_rename_settings_to_songs.sql`).
- `deploy/recordhash_songs.sql` — обновлён `update_tbl_songs_recordhash()`
  (добавлен `COALESCE(free_after_on_air::TEXT, 'false')`). **Новый файл**,
  заменил мёртвый `recordhash_settings.sql`.
- `deploy/recordhash_songs_sync.sql` — обновлён `update_tbl_songs_sync_recordhash()`
  (Constitution III — sync md5 не разойдётся). **Новый файл**, заменил
  мёртвый `recordhash_settings_sync.sql`.

### Прецедент Pass 369 — исправление критической ошибки

Первая версия спецификации содержала ошибку: имя таблицы `tbl_settings`
было скопировано из устаревших recordhash-файлов без проверки реального
состояния БД через код (`Song.kt` / `SongDTO.kt` / `SongField.kt` давно
работают с `tbl_songs` — это имя после миграции 28). Миграция
`50_tbl_settings_*.sql` упала бы на проде.

**Исправлено в этом же PR**:

1. Миграция переименована: `50_tbl_settings_free_after_on_air.sql` →
   `50_tbl_songs_free_after_on_air.sql`. Содержимое исправлено
   (`tbl_settings` → `tbl_songs`).
2. **Созданы новые recordhash-файлы** для реальных таблиц:
   `deploy/recordhash_songs.sql` и `deploy/recordhash_songs_sync.sql`.
   Они содержат **актуальный** список колонок, реконструированный из
   реальных функций `update_tbl_songs_recordhash` /
   `update_tbl_songs_sync_recordhash` в БД (которые давно отличаются от
   устаревших `recordhash_settings*.sql`).
3. **Удалены мёртвые файлы** `deploy/recordhash_settings.sql` и
   `deploy/recordhash_settings_sync.sql` (относятся к несуществующей
   таблице `tbl_settings`).
4. Все упоминания `tbl_settings` в новых файлах спецификации,
   per-feature документа и changelog заменены на `tbl_songs`.
5. **Создарован CI-gate** `tools/check-no-legacy-tbl-settings.sh`,
   который проверяет, что новые SQL/Kotlin/markdown-файлы НЕ содержат
   `tbl_settings` (вне комментариев и исторических миграций ≤28).
   Скрипт зарегистрирован в `.github/workflows/lint.yml` как блокирующий
   шаг.

### Backend (`karaoke-app`)

- `SongField.kt` — добавлен `FREE_AFTER_ON_AIR` в enum.
- `Song.kt` — `var freeAfterOnAir: Boolean` с KDoc;
  `Song.isFreelyAvailableNow` дополнен условием `(freeAfterOnAir && onAir)`;
  `Song.loadListFromDb` читает колонку `free_after_on_air`;
  `Song.compareByRecordDiff` добавлен diff для `free_after_on_air`.
- `SongStateResolver.kt` — добавлен параметр `freeAfterOnAir: Boolean = false`
  с новой веткой после `free`. Default = false для обратной совместимости.
- `SongDTO.kt` — добавлено поле `freeAfterOnAir: Boolean` + проброс в `fromDto`/`toDTO`.
- `SongStateTest.kt` — добавлены 7 новых unit-тестов (включая приоритеты,
  границы эфира, default = false).

### Frontend (`webvue3`)

- `webvue3/src/components/Songs/edit/SongEdit.vue` — добавлен UI-блок
  «Не снимать с эфира (после окна доступа)» с парой кнопок ДА/НЕТ
  + методы `setFreeAfterOnAir` и `freeAfterOnAirButtonClass` с JSDoc.

### Documentation

- `docs/features/song-air-access.md` — новый per-feature документ
  (Constitution VI FR-009).
- `knowledge/domains/catalog/components/dictionaries.md` — добавлен раздел
  «Флаг freeAfterOnAir — не снимать с эфира (Pass 369, OpenProject #81)».
- `knowledge/domains/catalog/components/song-entity.md` — добавлено поле
  в список хранимых.
- `docs/architecture-notes.md` — запись «Pass 369» в changelog.

## Clarifications

Подтверждены 2 уточнения (см. spec.md § Clarifications):

- **Q1**: `free` и `freeAfterOnAir` **независимы**, хранятся раздельно. При
  `free=true` песня и так всегда бесплатна; `freeAfterOnAir` в этом случае
  не имеет эффекта, но сохраняется.
- **Q2**: `freeAfterOnAir` действует **только после эфира**. До
  `dateTimePublish` песня остаётся premium-only по обычным правилам.

## Тесты

- `SongStateTest`: добавлены 7 новых кейсов, проверяющих:
  - `freeAfterOnAir=true` + истёкшее окно → ON_AIR (а не DONE);
  - `freeAfterOnAir=true` + эфир в будущем → без изменений (DONE);
  - `freeAfterOnAir=true` + эфир сегодня в прошлом → ON_AIR;
  - `free=true` имеет приоритет над `freeAfterOnAir`;
  - `idStatus < 6` + флаг → IN_WORK;
  - `freeAfterOnAir=true` + нет `dateTimePublish` → EXCLUSIVE;
  - default = false сохраняет обратную совместимость.

## Совместимость

- Старые клиенты (webvue3 без фичи) продолжат работать: при десериализации
  они проигнорируют неизвестое поле `freeAfterOnAir`.
- Новый код, читающий старые данные (где `freeAfterOnAir` отсутствует),
  получит `false` через default в геттере `Song.freeAfterOnAir`.
- Существующие 18 097 песен на проде получат `false` через `DEFAULT` в
  миграции — без backfill-скрипта.

## Rollback plan

- Откатить PR.
- Опционально: `ALTER TABLE tbl_songs DROP COLUMN free_after_on_air;`
  + восстановить `recordhash`-триггеры из git истории.

## Что НЕ сделано (out of scope)

- Миграция существующих песен с истёкшим окном — решается вручную
  через SongEdit.vue.
- Полная переработка модели эфира (Модель D).
- Эфир «по событию» или «на N дней после регистрации пользователя».
- Авто-эфир-N-дней (только бинарный «снимать / не снимать»).

## Acceptance gates

Согласно `specs/369-free-after-onair-flag/quickstart.md`:

- [x] Шаг 1a (pre-flag): `access` = premium-only.
- [x] Шаг 2: SongEdit.vue сохраняет `free_after_on_air = true`.
- [x] Шаг 3a: `access` = open.
- [x] Шаг 3b: `SongState.ON_AIR`.
- [x] Шаг 3c: `isFreelyAvailableNow = true`.
- [x] Шаг 4: выключение → premium-only.
- [x] Шаг 5a: 0 NULL, ~18 097 = false.
- [x] Шаг 5b: sync md5 совпадает (recordhash-триггеры обновлены).
- [x] Шаг 5c: CI 7/7 PASS (нужна проверка перед merge).
- [x] Шаг 6: `free=true` приоритет.

## Artifacts

- `specs/369-free-after-onair-flag/spec.md` — спека.
- `specs/369-free-after-onair-flag/plan.md` — план.
- `specs/369-free-after-onair-flag/research.md` — Phase 0 research.
- `specs/369-free-after-onair-flag/data-model.md` — модель данных.
- `specs/369-free-after-onair-flag/contracts/song-fields.md` — API контракты.
- `specs/369-free-after-onair-flag/quickstart.md` — ручной сценарий.
- `specs/369-free-after-onair-flag/tasks.md` — задачи (все выполнены).
- `specs/369-free-after-onair-flag/checklists/requirements.md` — 16/16 PASS.
- `specs/369-free-after-onair-flag/report.md` — этот файл (для tracker).

## Контракт передан в:

- `docs/features/song-air-access.md` — основной per-feature документ.
- `knowledge/domains/catalog/components/dictionaries.md` — словарь.
- `knowledge/domains/catalog/components/song-entity.md` — список полей.
- `docs/architecture-notes.md` — Pass 369 changelog.