# Implementation Plan: Поле `song_name_censored` в `tbl_songs`

**Branch**: `277-song-name-censored` | **Date**: 2026-08-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/277-song-name-censored/spec.md`

## Summary

`tbl_songs` получает предвычисленную колонку `song_name_censored VARCHAR NOT
NULL DEFAULT ''` с тем же содержимым, что и результат
`String.songName.censored(database)` на момент записи. Колонка позволяет
читать цензурированное название песни без обращения к `tbl_dictionaries`
на горячем пути (читается через `Song.loadListFromDb`/`loadFromDbById`
вместе с остальными полями). Миграция добавляет колонку + бэкфилл
`song_name_censored = song_name` для существующих строк; реальное
цензурирование по словарю выполняется фоновой операцией
`rescanAllCensoredNames` (новая endpoint `/api/utils/rescanallcensorednames`,
запускается кнопкой CustomFunction в админке). В `SongEdit` — новое поле
для ручного редактирования (политика «доверие редактору», без re-censor на
горячем пути).

Технический подход (по результатам `research.md`):
- 1 SQL-миграция `42_song_name_censored.sql` с ALTER TABLE +
  пересборкой `update_tbl_songs_recordhash()` + бэкфиллом колонки и
  бэкфиллом recordhash;
- 1 новое значение enum `SongField.SONG_NAME_CENSORED` + пара геттер/сеттер
  в `Song.kt`;
- 4 точки правки `Song.kt` (loadListFromDb, getSqlToInsert×2, saveToDb — последняя
  с авто-заполнением пустого значения по `songName.censored(database)`);
- ~30 точек правки в `Song.kt`/сервисах шаблонов (замена
  `song.songName.censored(database)` → `song.songNameCensored` для устранения
  запросов в `tbl_dictionaries` на горячем пути);
- 1 новая фоновый function `Utils.rescanAllCensoredNames()` по образцу
  `Utils.customFunction()` + 2 endpoint'а (POST в ApiController, GET в
  MainController);
- 1 новая кнопка в `HomeView.vue` с подтверждением;
- 1 новое поле в `SongEdit.vue` (input + undo/copy/paste + tooltip).

## Technical Context

**Language/Version**: Kotlin 2.x (JVM 17, Spring Boot 3.x) — backend; JavaScript/Vue 3 (Node 22) — frontend. Без изменений относительно существующего стека.

**Primary Dependencies**: Существующие — Spring Boot, сырой JDBC (`KaraokeConnection`), Vue 3 + Vite, Bootstrap 5 / Bootstrap-vue-next (webvue3). Новых npm/pip/gradle зависимостей НЕ добавляется — фоновая функция работает на стандартном `kotlin.concurrent.thread` (как существующая `Utils.customFunction`), UI — на существующих компонентах `CustomConfirm`.

**Storage**: PostgreSQL через сырой JDBC. Новая миграция `deploy/karaoke-db/42_song_name_censored.sql`, применяется вручную на LOCAL и PROD (Принцип II/III).

**Testing**: В проекте нет CI-тестов для этого слоя (Конституция, «Рабочий процесс»: «в CI нет … не полагаться»). Проверка — ручная, через `quickstart.md` (админка + публичный сайт в браузере) + прямые SQL-запросы для подтверждения backfill/recordhash.

**Target Platform**: `karaoke-app` (admin-машина, ядро/БД-слой, новая фоновая функция + endpoint), `karaoke-web` (прод, без изменений по сравнению с текущим), `webvue3` (admin SPA, новая кнопка в HomeView + поле в SongEdit). Docker + docker-compose, без изменений в образах/рантайме.

**Project Type**: Web application (двух-фронтенд: admin + public, backend Kotlin/Spring, raw JDBC — Принцип V). Изменения локализованы в admin-стеке (`karaoke-app` + `webvue3`), `karaoke-public` не трогается.

**Performance Goals**: Не производительность-критичная фича на уровне запросов — наоборот, устраняет 1 запрос к `tbl_dictionaries` на каждое чтение песни (SC-003). Фоновая операция реckana ожидаемо занимает единицы минут на 18k+ строк (SC-002) — `Song.loadFromDbById` пакетно, N+1 не допускается.

**Constraints**:
- Никакого JPA/Hibernate (Принцип II).
- `tbl_songs` синхронизируется через `SyncRegistry` (Принцип III) — добавление колонки ОБЯЗАТЕЛЬНО сопровождается пересборкой `update_tbl_songs_recordhash()` на LOCAL и PROD.
- Никаких новых sync-флагов в `KaraokeProperties.kt` — поведение push/pull остаётся прежним, меняется только состав md5.
- Admin (`webvue3`) и public (`karaoke-public`) остаются раздельными приложениями — общий код между ними не создаётся (Принцип V).
- Секреты НЕ трогаются (Принцип VIII), миграция работает с обычной DDL/DML Postgres.

**Scale/Scope**: 1 новая колонка в `tbl_songs`; 1 новое `SongField`; ~4 точки правки в `Song.kt` (loadList/loadFromDbById, getSqlToInsert×2, saveToDb); ~30 точек замены `song.songName.censored(database)` → `song.songNameCensored` (Song.kt + 3 TemplateService + UtilsPictures + Publication.kt); 1 новая функция в `Utils.kt` + 2 endpoint'а; 1 кнопка в `HomeView.vue`; 1 поле в `SongEdit.vue`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Оценка | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн (NON-NEGOTIABLE) | ✅ N/A | Фича не трогает ffmpeg/MLT/Demucs/Sheetsage/внешние API. Фоновая функция — только JDBC-запросы. |
| II. Сырой JDBC + diff по хэшам (NON-NEGOTIABLE) | ✅ PASS (с действием) | Новая колонка — через `ALTER TABLE`; запись/чтение — через `Song.kt` reflection-free путь (`SongField` map + ручные правки в `getSqlToInsert`/`loadListFromDb`/`saveToDb`); JPA не вводится. **Обязательное действие**: пересобрать `update_tbl_songs_recordhash()` в миграции, включив `song_name_censored` в md5-конкатенацию (по образцу `31_entity_description_fields.sql`) — иначе LOCAL↔SERVER diff не увидит новую колонку. |
| III. Двух-БД синхронизация через SyncRegistry | ✅ PASS (без нового кода) | `tbl_songs` уже зарегистрирован в `SyncRegistry.all` (`SettingsSyncTarget` key=`"settings"`, `SyncTarget.kt`) — новых sync-таргетов/флагов `KaraokeProperties` заводить не нужно, только регенерация recordhash (см. выше). Поведение push/pull/update/delete остаётся прежним. |
| IV. Async-очередь / ProcessBuilder | ✅ PASS (с действием) | Фоновая функция `rescanAllCensoredNames()` ОБЯЗАТЕЛЬНО должна работать через `thread { … }` (по образцу `Utils.customFunction()`), НЕ блокировать HTTP-поток; `redirectErrorStream(true)` не применимо (нет подпроцесса); флаг «уже запущено» для защиты от гонок (FR-006); SSE-тост по завершении. |
| V. Двух-фронтенд: админка и публичный сайт | ✅ PASS | Все UI-правки — строго в `webvue3` (`HomeView.vue` + `SongEdit.vue`). `karaoke-public` не затрагивается. Общих компонентов между ними не создаётся. |
| VI. Code Standards (NON-NEGOTIABLE) | ✅ PASS (с действием) | Новые публичные символы — `Song.songNameCensored` getter/setter (виртуально существует, см. `Song.kt:608` — заменить реализацию), `Utils.rescanAllCensoredNames()`, 2 endpoint-метода, `SongField.SONG_NAME_CENSORED` — все получают KDoc с `@see` на этот spec. **per-feature-doc FR-009 конституции**: вероятно требуется правка `docs/features/dual-db-sync.md` (зафиксировать, что `tbl_songs` получила новую колонку `song_name_censored` и пересобран recordhash); задача будет явно выделена в `/speckit.tasks`. |
| VII. Cross-Machine Setup | ✅ N/A | Фича не трогает AI-конфиги/`.gitattributes`/`.git-blame-ignore-revs`. |
| VIII. Секреты и git-гигиена | ✅ N/A | Миграция — чистый DDL/DML Postgres, никаких секретов; UI — публичный текст, никаких токенов; pre-commit хук (`git ls-files | grep -iE '\.env$\|do\.env$\|\.key$\|\.pem$'`) остаётся пустым после изменений. |

Нарушений, требующих обоснования в Complexity Tracking, нет.

## Project Structure

### Documentation (this feature)

```text
specs/277-song-name-censored/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── api.md           # Phase 1 output — изменения в API-контрактах
└── tasks.md             # Phase 2 output (/speckit.tasks — ещё не создан)
```

### Source Code (repository root)

```text
# Web application — двух-фронтенд (Принцип V), backend Kotlin, raw JDBC

deploy/karaoke-db/
└── 42_song_name_censored.sql          # ALTER TABLE tbl_songs + recordhash + backfill

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/
│   ├── SongField.kt                   # + SONG_NAME_CENSORED
│   ├── Song.kt                        # ~4 точки правки (loadListFromDb, loadFromDbById,
│   │                                    getSqlToInsert×2, saveToDb) +
│   │                                    ~15 замен songName.censored(database) → songNameCensored
│   │                                    (getTextBoostyHead, getTextBoostyFilesHead,
│   │                                    getDescriptionDemoHeader, getDescriptionVkDemoHeader,
│   │                                    getDescriptionDemoWithLinkSM, getVKGroupDescription, и др.)
│   └── Publication.kt                 # ~14 замен в top-песнях publish10..publish23
│                                       # (publishNNN.songName.censored(database) → songNameCensored)
├── services/
│   ├── VkTemplateService.kt           # -1 замена в buildReplacements + 2 правки в KDoc
│   ├── TelegramTemplateService.kt     # -1 замена в buildReplacements + 2 правки в KDoc
│   └── NewsTemplateService.kt         # -1 замена в buildReplacements + 2 правки в KDoc
├── controllers/
│   ├── ApiController.kt               # + POST /api/utils/rescanallcensorednames
│   └── MainController.kt              # + GET /utils/rescanallcensorednames
├── Utils.kt                           # + fun rescanAllCensoredNames(storageService, lyricsFinderService, storageApiClient)
└── UtilsPictures.kt                   # -7 замен song.songName.censored(song.database) → song.songNameCensored

webvue3/src/
├── components/Songs/
│   └── edit/SongEdit.vue              # + поле «Композиция (цензурированная)» с undo/copy/paste
│                                       #   и tooltip title="..."
├── views/
│   └── HomeView.vue                   # + новая кнопка «Пересканировать цензурированные названия»
│                                       #   с подтверждением через CustomConfirm
└── components/Songs/store.js          # + новый action rescanAllCensoredNamesPromise()
                                       #   (POST /api/utils/rescanallcensorednames)
```

**Structure Decision**: Существующая структура репозитория (`karaoke-app` /
`karaoke-web` / `webvue3` / `karaoke-public` / `deploy/karaoke-db`)
сохраняется без изменений — новых модулей/пакетов не создаётся, фича
добавляет колонку, поле enum, фоновую функцию и UI-элементы в уже
существующие файлы вдоль уже установленных паттернов (см.
`data-model.md`/`research.md`). Никакой отдельной `test/` структуры не
заводится — проверка мануальная (`quickstart.md`), как и для остальных
фич проекта.

## Complexity Tracking

*Нет нарушений Constitution Check, требующих обоснования — таблица не заполняется.*