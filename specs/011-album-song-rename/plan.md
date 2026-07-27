# Implementation Plan: Альбом как сущность + переименование Settings→Song

**Branch**: `011-album-song-rename` | **Date**: 2026-07-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-album-song-rename/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Добавить полноценную синхронизируемую сущность `Album` (автор, год, название, тип
студийный/концертный/сборник/бутлег, порядок сортировки внутри автора+года), связать с ней
песни через настоящий FK (`tbl_songs.album_id`) с автоматическим бэкфиллом существующих
данных; добавить песне возможность иметь произвольное число соавторов (главный автор — как
сейчас, соавторы — новая связь многие-ко-многим `tbl_song_authors`); переименовать историческую
сущность `Settings`→`Song` по всей кодовой базе **включая физическое имя таблицы**
(`tbl_settings`→`tbl_songs`), разрешив конфликт с существующим классом `Song`
(рендер-обёртка → `SongRenderContext`). Технический подход подробно зафиксирован в
`research.md` (9 решений, принятых с пользователем на этапах specify/plan) и `data-model.md`.

## Technical Context

**Language/Version**: Kotlin (JDK 17, Gradle multi-module: `karaoke-app`, `karaoke-web`); Vue 3 + Vite + Node 22 (`webvue3` admin SPA, `karaoke-public` public SPA).

**Primary Dependencies**: Spring Boot (backend), raw JDBC через `KaraokeConnection` (без JPA/Hibernate — Principle II), Vuex (оба фронтенда), Bootstrap-vue-next (`webvue3`) / Bootstrap 5 (`karaoke-public`).

**Storage**: PostgreSQL. `tbl_settings` переименовывается в `tbl_songs` (+ `tbl_settings_sync`→`tbl_songs_sync`, sequence/констрейнты/индексы/триггеры), затем получает колонку `album_id` и пересобранный `recordhash`-триггер; новые таблицы `tbl_albums`, `tbl_song_authors`. Дуальная топология LOCAL (admin-машина) ↔ SERVER (прод) — обе меняются одинаковыми ручными миграциями, строго в порядке: сначала переименование, потом остальное (см. research.md §5.1 runbook).

**Testing**: Ручная проверка (`quickstart.md`) — в проекте нет CI-тестов для этого пути (constitution.md: «Тесты: в CI нет», существующие `@Disabled`).

**Target Platform**: Docker/docker-compose, admin-машина (LOCAL Postgres + karaoke-app) и прод-сервер (SERVER Postgres + karaoke-web + karaoke-public + MinIO; karaoke-app на проде не разворачивается).

**Project Type**: Web-приложение, 4 активных модуля (backend: `karaoke-app`, `karaoke-web`; frontend: `webvue3`, `karaoke-public`) + `deploy/karaoke-db` миграции.

**Performance Goals**: Без новых количественных целей; должны сохраняться существующие инварианты — O(n) diff по `recordhash`/`associateBy{it.id}` (Principle II), пакетная загрузка `WHERE id IN (...)` для новых сущностей (наследуется от `GenericKaraokeDbTableSyncTarget`/`KaraokeDbTable`, изменений не требует).

**Constraints**: Дуальная синхронизация LOCAL/SERVER не должна сломаться (Principle III — обязательная регистрация в `SyncRegistry`, пересборка `recordhash`-триггеров при любом изменении схемы затронутых таблиц); переименование таблицы — структурное изменение схемы, требующее строгого порядка операций на LOCAL/SERVER (research.md §5.1) без потери данных/состояния синхронизации (FR-015); ktlint/ESLint/KDoc/JSDoc обязательны (Principle VI) до merge.

**Scale/Scope**: ~18k+ существующих записей `tbl_settings`/`tbl_songs` (см. constitution.md rationale Principle II) — переименование и бэкфилл должны обработать все без деградации; ожидаемое число альбомов — на порядок меньше числа песен на автора.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Как удовлетворяется |
|---|---|---|
| I. Self-contained автопайплайн | ✅ PASS | Фича не добавляет новых внешних SaaS-зависимостей в горячий путь обработки медиа. |
| II. Сырой JDBC + дифф по хэшам | ✅ PASS | `Album`/`SongCoAuthor` — обычные `KaraokeDbTable`-сущности через `@KaraokeDbTableField`, без JPA/Hibernate; diff — `recordhash`, сравнение — `associateBy { it.id }`; загрузка — `WHERE id IN (...)` (наследуется от `GenericKaraokeDbTableSyncTarget`, см. data-model.md). |
| III. Двух-БД синхронизация через SyncRegistry | ✅ PASS | Обе новые сущности регистрируются в `SyncRegistry.all` + 8 флагов `KaraokeProperties.kt` каждая (research.md §7); физическое переименование `tbl_settings`→`tbl_songs` (`28_rename_settings_to_songs.sql`) выполняется на LOCAL и SERVER строго до перехода кода на новое имя (research.md §5.1), `recordhash`-триггер пересобирается при переименовании и повторно — при добавлении `album_id` (`29_albums.sql`). |
| IV. Async-очередь задач | N/A | Фича не вводит новых длительных фоновых операций/подпроцессов. |
| V. Два фронтенда — разные приложения | ✅ PASS | UI управления альбомами/соавторами — только в `webvue3` (admin); `karaoke-public` только читает (сортировка/тип альбома в дискографии), без пересечения ответственностей. |
| VI. Code Standards | ⚠️ ACTION REQUIRED | Новые публичные классы/эндпоинты — KDoc/JSDoc с `@see` на per-feature документ. По FR-009 обязательно обновить `docs/features/dual-db-sync.md` (две новые `SyncTarget`) и `docs/features/mlt-generator.md` (переименование основного входного параметра); решение о новом 13-м per-feature документе (`album-catalog.md`) — на этапе `/speckit-tasks`. ktlint/ESLint должны остаться в пределах текущего baseline (не увеличивать). |
| VII. Cross-Machine Setup | N/A | Фича не меняет cross-machine setup файлы (`.gitattributes`, `.git-blame-ignore-revs`, onboarding-доки). |

Нарушений, требующих обоснования в Complexity Tracking, не обнаружено — таблица ниже не заполняется.

## Project Structure

### Documentation (this feature)

```text
specs/011-album-song-rename/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md         # Phase 1 output
├── contracts/
│   └── api.md            # Phase 1 output
└── tasks.md              # Phase 2 output (/speckit.tasks — не создаётся этой командой)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/
│   ├── Settings.kt → Song.kt              # переименование класса + файла
│   ├── SettingsDTO.kt → SongDTO.kt
│   ├── SettingsDTOdigest.kt → SongDTOdigest.kt
│   ├── SettingField.kt → SongField.kt
│   ├── SettingVoice.kt → SongVoice.kt
│   ├── CrossSettings.kt → CrossSong.kt
│   ├── Song.kt → SongRenderContext.kt     # существующий рендер-класс, переименован ПЕРЕД
│   │                                       # переименованием Settings→Song (иначе конфликт имён)
│   ├── Song2.kt                            # удаляется (мёртвый дубликат)
│   ├── Album.kt                            # новый
│   ├── AlbumDTO.kt                         # новый
│   ├── AlbumType.kt                        # новый (enum, по образцу SongType.kt)
│   └── SongCoAuthor.kt                     # новый
├── sync/SyncTarget.kt                      # SettingsSyncTarget→SongSyncTarget (key не меняется);
│                                            # + AlbumsSyncTarget, SongCoAuthorsSyncTarget
├── KaraokeProperties.kt                    # + 16 новых флагов (albums ×8, song_coauthors ×8)
└── controllers/ApiController.kt            # + /api/albums/*, /api/songs/coauthors/*

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── dto/SettingsPublicDto.kt → SongPublicDto.kt
└── controllers/PublicApiController.kt      # /zakroma + ZakromaAlbumPublicDto: sortOrder, albumType

webvue3/src/
├── components/Songs/                       # store.js/SongsTable.vue: SettingsDTO→SongDTO,
│                                            # updateSettings→updateSongs, URL-переименование
├── components/Albums/                      # новый (AlbumsTable.vue, store.js, filter/), по
│                                            # образцу components/Authors/
└── views/AlbumsView.vue                    # новый + router/index.js + App.vue nav-ссылка

karaoke-public/src/
└── views/ZakromaView.vue, store/modules/zakroma.js   # использование sortOrder/albumType

deploy/karaoke-db/
├── 28_rename_settings_to_songs.sql          # tbl_settings→tbl_songs, tbl_settings_sync→tbl_songs_sync
│                                             # (sequence/констрейнты/индексы/триггеры), см. runbook §5.1
├── 29_albums.sql                            # tbl_albums + tbl_songs.album_id + recordhash-пересборка
└── 30_song_coauthors.sql                    # tbl_song_authors (FK на tbl_songs.id)

deploy/new_comp/sm-karaoke-system/dumps/
└── karaoke_clear_dump.sql                   # требует регенерации (ссылается на старое имя tbl_settings)

docs/features/
├── dual-db-sync.md                         # обновление (FR-009)
└── mlt-generator.md                        # обновление (FR-009)
```

**Structure Decision**: Изменения распределены по уже существующим 4 активным модулям
(`karaoke-app`, `karaoke-web`, `webvue3`, `karaoke-public`) + `deploy/karaoke-db` — без новых
модулей/проектов. Новые сущности (`Album`, `SongCoAuthor`) следуют существующему паттерну
`model/<Entity>.kt` + `<Entity>DTO.kt` + `Generic­KaraokeDbTableSyncTarget` (как `Author`), новый
admin-раздел `webvue3/src/components/Albums/` — по паттерну `components/Authors/`.

## Complexity Tracking

> Пусто — нарушений Constitution Check, требующих обоснования, не обнаружено.
