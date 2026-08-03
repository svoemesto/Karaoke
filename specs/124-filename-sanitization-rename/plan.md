# Implementation Plan: Санитайзинг имён файлов при импорте и переименование при редактировании

**Branch**: `124-filename-sanitization-rename` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/124-filename-sanitization-rename/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Импорт песен сегодня не санитайзирует `Song.fileName` перед перекодированием в FLAC — файлы с символами вроде `!` физически ломают последующий шаг разделения на стемы (Demucs), потому что имя копии в temp-папке (уже прогнанное через `rightFileName()`) расходится с реальным именем файла на диске (никогда не переименованного). Решение: (1) применять существующую (расширенную) функцию санитайзинга `rightFileName()` к имени файла **до** конвертации в FLAC в `Song.createFromPath()`, сохраняя оригинальные символы только в `SongField.NAME`; при коллизии имён внутри одной папки — автоматический числовой суффикс; (2) при сохранении «Имя файла» в SongEdit (`ApiController.songs2Update`) — применять то же санитайзирование, блокировать сохранение при коллизии/пустом имени/активной фоновой обработке песни, и каскадно переименовывать все производные артефакты на диске и в обоих хранилищах (best-effort вперёд, без отката, с явной пометкой ошибки через существующий механизм `HealthReport`/`HealthReportStatus`, который уже показывается в `HealthReportTable.vue` внутри SongEdit).

## Technical Context

**Language/Version**: Kotlin 2.x (JDK 17, Spring Boot 3.x) для `karaoke-app`; минимальные правки Vue 3 в `webvue3` (admin SPA) для отображения сообщений блокировки/ошибки переименования.

**Primary Dependencies**: Никаких новых зависимостей. Переиспользуются существующие: `String.rightFileName()`/`rightFileNameSymbols()` (`Extentions.kt`), `KaraokeConnection` (сырой JDBC), `KaraokeStorageService`/`StorageApiClient` (MinIO, локальное+удалённое хранилище — нет метода rename/move, только upload/download/delete), `HealthReport`/`HealthReportStatus` (существующий фреймворк «ожидаемое vs фактическое состояние файла», уже поддерживает ERROR/FATAL_ERROR + `solutionActions` для самовосстановления), `KaraokeProcess`/`KaraokeProcessStatuses` (для проверки идущей фоновой обработки песни, FR-013).

**Storage**: PostgreSQL через сырой JDBC (`tbl_songs`, `tbl_processes`) — новых колонок/миграций не требуется (см. Research); MinIO (локальный + удалённый бакет) — объекты копируются под новым ключом и удаляются под старым (SDK не даёт server-side rename); локальная файловая система admin-машины (аудиофайл, стемы, `.kdenlive`, субтитры, sidecar-метаданные).

**Testing**: В CI юнит/интеграционных тестов для этого модуля нет (см. Конституцию, «Рабочий процесс»/«Тесты»). Валидация — вручную пользователем по `quickstart.md` в production-like окружении (реальный импорт + реальный Demucs-прогон + реальная проверка MinIO).

**Target Platform**: admin-машина (Linux, Docker) — весь бэкенд-функционал живёт только в `karaoke-app`, который на проде не разворачивается (Конституция, «Технологический стек»).

**Project Type**: web-service (существующий Gradle multi-module репозиторий) — фича не создаёт новых модулей/проектов, только точечные правки в `karaoke-app` (backend) и `webvue3` (admin frontend, только для UX сообщений).

**Performance Goals**: Не производительность-критичная фича — переименование инициируется вручную администратором по одной песне за раз (см. Assumptions спецификации); операции — локальные файловые + несколько MinIO-вызовов, счёт на секунды.

**Constraints**: Переиспользовать и расширить существующую `rightFileName()`, не создавать параллельный набор правил санитайзинга; не вводить JPA/Hibernate; коллизии проверяются SQL-запросом с `WHERE root_folder = ?` (не full-table сравнение — Principle II); каскадное переименование выполняется синхронно в потоке запроса (как существующий `sett.saveToFile()`), не через async-очередь `KaraokeProcess*`, так как это чистые файловые/HTTP(MinIO)-операции без внешних процессов (`ffmpeg`/`melt`/`Demucs`).

**Scale/Scope**: Один прод-каталог (тысячи песен), переименование — единичная ручная операция; импорт — пакетный процесс папки, но сценарий коллизии рассматривается в рамках одной папки/альбома (обычно единицы-десятки файлов).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Проверка | Статус |
|---|---|---|
| I. Self-contained автопайплайн | Никаких внешних SaaS/API не добавляется — только Kotlin/JDK file I/O + существующий MinIO-клиент. | ✅ PASS |
| II. Сырой JDBC + диф по хэшам | Проверка коллизии имени файла — `WHERE root_folder = ?` (бюджетный, не O(n²), не full-table). Новых таблиц/JPA нет. | ✅ PASS |
| III. Двух-БД синхронизация через SyncRegistry | `tbl_songs.file_name`/`root_folder` — уже существующие колонки, тип/семантика не меняются. Явная пометка ошибки переименования переиспользует существующий `HealthReport` (вычисляется on-the-fly, не персистится) — новых колонок/миграций/recordhash-пересоздания не требуется (см. `research.md` §7). | ✅ PASS |
| IV. Async-очередь с threadId-лейнами | Каскадное переименование — синхронная операция в потоке HTTP-запроса (не `KaraokeProcess`), т.к. не включает `ffmpeg`/`melt`/`Demucs`/`ProcessBuilder`. Не нарушает лейновую модель, т.к. не создаёт новых процессов этого рода (может лишь **проверять** отсутствие активных `KaraokeProcess` для песни — FR-013). | ✅ PASS |
| V. Двух-фронтенд разделение | Правки только в `webvue3` (admin), `karaoke-public` не затрагивается — SongEdit является admin-only функциональностью. | ✅ PASS |
| VI. Code Standards (KDoc/JSDoc, per-feature doc) | Новые/изменённые публичные функции (`rightFileName()` расширение, новая cascade-rename-логика) MUST получить KDoc с `@see docs/features/<slug>.md`; per-feature документ создаётся/обновляется в этом плане (FR-009). | ✅ PASS (обязательство на Phase 2/implement) |
| VII. Cross-Machine Setup | Не затрагивается. | ✅ N/A |
| VIII. Секреты и git-гигиена | Не затрагивается. | ✅ N/A |

Нарушений, требующих секции «Complexity Tracking», не выявлено.

## Project Structure

### Documentation (this feature)

```text
specs/124-filename-sanitization-rename/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md         # Phase 1 output (/speckit.plan command)
├── quickstart.md         # Phase 1 output (/speckit.plan command)
├── contracts/            # Phase 1 output (/speckit.plan command)
│   └── song-rename-contract.md
└── tasks.md              # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

Существующий Gradle multi-module репозиторий — новых модулей/проектов не
создаётся. Затрагиваемые файлы (все уже существуют):

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── Extentions.kt                       # rightFileNameSymbols()/rightFileName() — расширить набор символов
├── model/Song.kt                       # createFromPath() — применить санитайзинг до FLAC-конвертации;
│                                        # новая cascade-rename логика (вызывается из ApiController)
├── HealthReport.kt                      # переиспользуется как есть для явной пометки ошибки (без правок логики,
│                                        # если существующий "ожидаемое имя vs фактическое" уже покрывает случай)
├── KaraokeProcess.kt                    # loadList()-запрос для проверки активной обработки (FR-013), без изменений API
├── controllers/ApiController.kt          # songs2Update() — санитайзинг + коллизия + блокировка + запуск cascade rename
└── services/{KaraokeStorageService,StorageApiClient}.kt  # используются как есть (uploadFile/deleteFile/fileExists)

webvue3/src/components/Songs/edit/
└── SongEdit.vue                         # отображение сообщений блокировки/ошибки при сохранении «Имя файла»

docs/features/
├── async-process-queue.md               # точечное обновление (правка createFromPath)
└── premium-stems.md                     # точечное обновление (каскадное переименование стемов)
```

**Structure Decision**: Правки полностью укладываются в существующий модуль
`karaoke-app` (backend) плюс один компонент `webvue3` (admin UI). Новых
Gradle-модулей, таблиц БД или сервисов не создаётся — фича не меняет
архитектурные границы проекта (Principle V: `karaoke-public` не затрагивается).

## Complexity Tracking

> Не заполняется — Constitution Check выше не выявил нарушений, требующих
> обоснования.
