# Implementation Plan: Флаг «не снимать с эфира» (free_after_on_air)

**Branch**: `369-free-after-onair-flag` | **Date**: 2026-09-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/369-free-after-onair-flag/spec.md`

## Summary

Добавить новое boolean-поле `freeAfterOnAir` в сущность `Song` (домен catalog).
Семантика: после наступления момента эфира (`dateTimePublish ≤ now()`) песня
**остаётся публично доступной** (ON_AIR / AccessMode.open) даже после истечения
стандартного окна бесплатного доступа (`freeAccessWindowMonths`). Флаги
`free=true` (всегда бесплатно) и `freeAfterOnAir=true` (не снимать после эфира)
**независимы и хранятся раздельно** (clarification 2026-09-11).

Технический подход:

1. SQL-миграция `deploy/karaoke-db/<NNN>_tbl_songs_free_after_on_air.sql` —
   добавить колонку `free_after_on_air BOOLEAN NOT NULL DEFAULT false` в
   `tbl_songs` (таблица, где хранятся «метаданные уровня песни» рядом с
   `free`, `is_exclusive`, `tags`, и т.п.; см. `Song.saveToDb`).
2. В `Song.kt` (catalog) добавить:
   - `var freeAfterOnAir: Boolean` в companion (аналогично `free: Boolean`);
   - дополнить `Song.isFreelyAvailableNow` так, чтобы при `freeAfterOnAir=true`
     и `onAir=true` песня оставалась «freely available» после истечения окна;
   - сохранить обратную совместимость: `free=true` имеет приоритет.
3. В `SongStateResolver.resolve()` (catalog, вынесенная чистая функция) — после
   проверки `free=true` добавить ветку:
   `if (freeAfterOnAir && !dateTimePublish.after(now)) return SongState.ON_AIR`.
4. В публичном доступе (`StatsService.accessModeFor` или эквивалентный путь в
   `publishing`) — добавить аналогичную проверку: при `freeAfterOnAir=true`
   и `dateTimePublish ≤ now()` возвращать `AccessMode.OPEN` независимо от
   стандартного окна.
5. В `webvue3/src/components/Songs/edit/SongEdit.vue` — добавить пару кнопок
   ДА/НЕТ «Не снимать с эфира» по тому же UX-паттерну, что и `setFree(true|false)`.
6. В `docs/knowledge/domains/catalog/components/dictionaries.md` — зафиксировать
   новое поле в централизованном словаре магических кодов.
7. Per-feature документ: создать/обновить `docs/features/song-air-access.md`
   (если отсутствует).

## Technical Context

**Language/Version**: Kotlin 2.x (JDK 17) — бэкенд (`karaoke-app`, `karaoke-web`);
Vue 3 + JavaScript — фронтенд (`webvue3`, `karaoke-public`).

**Primary Dependencies**: Spring Boot 3.x (бэкенд), Vue 3 + Vite + Vuex
(админка), Bootstrap-vue-next / Bootstrap 5, JDBC (raw, **без** JPA/Hibernate —
см. Constitution II).

**Storage**: PostgreSQL (через сырой JDBC, см. Constitution II). Миграция
`deploy/karaoke-db/<NNN>_tbl_songs_free_after_on_air.sql`.

**Testing**: unit-тесты `SongStateResolverTest` (offline, pure-функция —
см. комментарий в `SongStateResolver.kt`), property-based проверка
`accessModeFor`/`isFreelyAvailableNow`. На CI тестов нет (см. Constitution
Working process); проверка — вручную через `bash tools/check-*-coverage.sh`,
`./gradlew ktlintCheck`, `npm run lint:check` + ручной сценарий в `quickstart.md`.

**Target Platform**: прод-сервер (Linux, nginx stable) + admin-машина
(nsa-i9 / Linux), JVM 17 JRE в проде (`eclipse-temurin:22-jre-jammy`),
Node 22-alpine во фронт-контейнерах.

**Project Type**: web (admin + public) + CLI-движок karaoke-app — multi-module
Gradle.

**Performance Goals**: без новых хот-патчей. Поле `freeAfterOnAir` —
**не** в `tbl_songs` PK, добавляется к существующему набору колонок и
обрабатывается тем же `KaraokeDbTable` reflection-diff, что и `free`.
Никаких отдельных SQL-запросов на каждое обращение — поле всегда
присутствует в `Song` после загрузки.

**Constraints**: без новых публичных API-endpoint; без изменений в
миграциях БД за пределами `deploy/karaoke-db/`; без новых npm-зависимостей;
минимум touch-points (только `Song.kt`, `SongStateResolver.kt`,
`SongEdit.vue`, миграция, docs).

**Scale/Scope**: 18 097 песен в проде (см. Constitution III rationale); новый
флаг — одно boolean-поле, добавляется в `tbl_songs` через стандартную
`ALTER TABLE ... ADD COLUMN ... DEFAULT false NOT NULL`. Существующие записи
получают `false` через `DEFAULT`, без backfill-скрипта.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн | ✅ PASS | Новая фича не добавляет внешних API. |
| II. Сырой JDBC + дифф по хэшам | ✅ PASS | Новое поле сохраняется через `KaraokeDbTable.save()` (reflection-diff), никаких JPA/Hibernate. Никаких сравнений через `.any`/`.none`. |
| III. Двух-БД синхронизация через SyncRegistry | ⚠️ ТРЕБУЕТ ПРОВЕРКИ | `tbl_songs` уже синхронизируется (`free` уже там). Добавляемая колонка должна попасть в `recordhash`-триггер. Если триггер «по столбцам» — новая колонка попадёт автоматически (TODO уточнить в `deploy/karaoke-db/` какой триггер используется — это Phase 0 задача). |
| IV. Async-очередь | ✅ PASS | Фича не задействует `KaraokeProcess`. |
| V. Двух-фронтенд | ✅ PASS | Изменения только в admin (`webvue3`, `SongEdit.vue`); публичный SPA (`karaoke-public`) — без изменений. |
| VI. Code Standards | ✅ PASS | FR-006: KDoc для нового поля + JSDoc для новой пары кнопок в `SongEdit.vue`. FR-009: per-feature документ `docs/features/song-air-access.md` будет обновлён в этом PR. Линтеры `ktlintCheck` / `eslint --check` / Prettier — обязательны. |
| VII. Cross-Machine Setup | ✅ PASS | Локальные правки; никаких cross-machine конфликтов. |
| VIII. Секреты и git-гигиена | ✅ PASS | Миграция не содержит секретов; никаких новых `.env` файлов. |
| IX. Knowledge-first | ✅ PASS | Spec содержит `## Knowledge References` (mandatory, проверено в `checklists/requirements.md`); pre-flight grep выполнен с тремя попытками. |

**Complexity Tracking**: не требуется — нет нарушений Constitution.

## Project Structure

### Documentation (this feature)

```text
specs/369-free-after-onair-flag/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── contracts/           # Phase 1 output
├── quickstart.md        # Phase 1 output
├── tasks.md             # Phase 2 output (created by /speckit.tasks)
├── report.md            # REQUIRED for tracker workflow (Pass 349)
└── checklists/
    └── requirements.md  # re-validated in Stage 2
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/
├── Song.kt                          # +var freeAfterOnAir: Boolean,
│                                    #  update isFreelyAvailableNow
├── SongStateResolver.kt             # +ветка freeAfterOnAir → ON_AIR
└── Song.kt companion (список полей) # +"freeAfterOnAir" в fields

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/
└── StatsService.kt (или эквивалент) # accessModeFor: +учёт freeAfterOnAir

deploy/karaoke-db/
└── <NNN>_tbl_songs_free_after_on_air.sql  # ALTER TABLE tbl_songs
                                              # ADD COLUMN free_after_on_air
                                              # BOOLEAN NOT NULL DEFAULT false

webvue3/src/components/Songs/edit/
└── SongEdit.vue                     # +setFreeAfterOnAir(bool) / +buttons

docs/features/
└── song-air-access.md               # (обновить, создать если нет)

knowledge/domains/catalog/components/
└── dictionaries.md                  # +новая запись freeAfterOnAir

docs/architecture-notes.md           # +запись о PR (Pass N governance-notes)
```

**Structure Decision**: Существующая структура проекта, никаких новых
директорий. Все правки — точечные, в файлах, перечисленных выше.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений — секция пуста.

---

## Phase 0: Research

См. [research.md](research.md). Разрешено 2 NEEDS CLARIFICATION (см. Phase 0):

1. Какой именно `recordhash`-триггер используется на `tbl_songs` —
   нужно ли пересоздавать триггер при добавлении колонки, чтобы sync
   md5 не разошёлся (см. Constitution III).
2. Используется ли `Song.freeAfterOnAir` уже в JS-фильтрах (webvue3) для
   списка песен — если да, нужно ли добавлять в фильтр (default off, чтобы
   не менять UX существующих списков).

## Phase 1: Design & Contracts

См. [data-model.md](data-model.md), [contracts/song-fields.md](contracts/song-fields.md)
и [quickstart.md](quickstart.md).

Ключевые артефакты:

- **data-model.md** — добавление поля `free_after_on_air: BOOLEAN NOT NULL DEFAULT false`
  в `tbl_songs`; новое Kotlin-поле `Song.freeAfterOnAir: Boolean` (companion
  + instance); обновлённые `isFreelyAvailableNow` и `SongStateResolver`.
- **contracts/song-fields.md** — JSON-контракт `/api/songs/getById` /
  `/api/songs/save`: добавлено поле `freeAfterOnAir` (boolean); обратная
  совместимость — клиенты, не знающие о поле, получают `false` по default.
- **quickstart.md** — ручной сценарий проверки: применить миграцию →
  собрать `karaoke-web` → открыть `SongEdit.vue` для любой песни → включить
  флаг → сохранить → проверить, что `SongStateResolver.resolve(...)` возвращает
  `ON_AIR` для истёкшего окна + флаг = true.