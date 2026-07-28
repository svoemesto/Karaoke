# Implementation Plan: Закрома — альбомы с одинаковым названием, но разными годами, не должны сливаться в один

**Branch**: `018-fix-album-name-year-grouping` | **Date**: 2026-07-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/018-fix-album-name-year-grouping/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Баг: `Zakroma.buildFromSettings()` группирует песни автора в альбомы
закромов по одному лишь названию альбома (`settingsByAuthor.groupBy { it.album }`),
игнорируя год. Если у автора есть два альбома с одинаковым названием, но
разными годами, все песни обоих альбомов схлопываются в одну карточку с годом
«первой попавшейся» песни — второй альбом визуально исчезает вместе со своими
песнями. Технический подход: расширить ключ группировки до пары
(год, название альбома) в пределах автора — это соответствует реальной
идентичности альбома в БД (`tbl_albums_author_year_name_key` — уникальность по
(author_id, year, name)) и не требует, чтобы у всех песен уже был проставлен
`album_id`. Изменение локализовано в одной приватной функции
`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt`,
используемой всеми поверхностями закромов (admin-приложение, `karaoke-web`,
публичный API, включая спецзаказную плашку).

## Technical Context

**Language/Version**: Kotlin (JVM 17), Spring Boot 3.x — модуль `karaoke-app`

**Primary Dependencies**: существующий стек проекта (Spring Boot, сырой JDBC
через `KaraokeConnection`); новых зависимостей не добавляется

**Storage**: PostgreSQL через сырой JDBC (`Song`, `Album` — уже существующие
таблицы `tbl_songs`, `tbl_albums`); изменений схемы БД и миграций не требуется

**Testing**: в CI автотестов нет (см. `constitution.md` §«Рабочий процесс»,
«Тесты»); проверка — ручная, через `quickstart.md` (прямой SQL-запрос +
проверка ответа API/страницы закромов)

**Target Platform**: Linux-сервер — `karaoke-app` (admin-машина) и
`karaoke-web` (прод-сервер), оба используют одну и ту же функцию из
`karaoke-app` модели

**Project Type**: web-service (существующий Gradle multi-module монорепозиторий);
фикс — точечное изменение одной функции, новых модулей/директорий не создаётся

**Performance Goals**: без изменений — группировка выполняется в памяти на
уже загруженном `List<Song>` одного автора (обычно десятки-сотни записей),
не в БД; сложность как была `O(n)` (`groupBy`), так и остаётся

**Constraints**: не менять форму JSON-контракта (`ZakromaAlbumSongPublicDto`,
`ZakromaAlbumPublicDto`, `ZakromaPublicDto`) — только состав и корректность
элементов списка альбомов внутри уже существующей структуры; не трогать
схему БД; не расширять зону изменений за пределы группировки в закромах
(см. spec.md «Edge Cases» и «Assumptions»)

**Scale/Scope**: один файл с кодовым изменением
(`karaoke-app/.../model/Zakroma.kt`, функция `buildFromSettings()`) +
обязательное обновление per-feature документа `docs/features/special-orders.md`
(FR-009 конституции — документ явно описывает `buildFromSettings()`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Применимо? | Статус |
|---|---|---|
| I. Self-contained автопайплайн | Нет — фикс не трогает ffmpeg/MLT/Demucs/Sheetsage, внешних API не добавляет | ✅ PASS (N/A) |
| II. Сырой JDBC + дифф по хэшам | Да, косвенно — группировка `groupBy` уже в памяти (`O(n)`), это не сравнение LOCAL↔SERVER; новых `.any`/`.none` O(n²)-паттернов не вводится | ✅ PASS |
| III. Двух-БД синхронизация через SyncRegistry | Нет — новых syncable-сущностей не добавляется, `Album`/`Song` уже зарегистрированы | ✅ PASS (N/A) |
| IV. Async-очередь задач | Нет — изменение синхронное, не long-running процесс | ✅ PASS (N/A) |
| V. Двух-фронтенд: админка и публичный сайт | Да — фикс остаётся в общем backend-слое (`karaoke-app` model), не смешивает `webvue3`/`karaoke-public` код; оба фронтенда получают исправление через один и тот же общий эндпоинт/модель | ✅ PASS |
| VI. Code Standards (KDoc/ktlint/FR-009) | Да — `buildFromSettings()` уже имеет KDoc у соседних сущностей (`ZakromaAlbum`/`ZakromaAlbumSettings`), нужно сохранить/актуализировать; `docs/features/special-orders.md` явно документирует `buildFromSettings()` → FR-009 требует обновить этот документ в том же PR | ⚠️ TRACKED — обязательная задача в tasks.md: обновить `special-orders.md` |
| VII. Cross-Machine Setup | Нет — изменение не касается AI-конфигов/`.gitattributes`/onboarding | ✅ PASS (N/A) |

Нарушений, требующих обоснования в Complexity Tracking, нет.

## Project Structure

### Documentation (this feature)

```text
specs/018-fix-album-name-year-grouping/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── zakroma-api-impact.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/
    ├── model/
    │   ├── Zakroma.kt          # ИЗМЕНЯЕТСЯ: buildFromSettings() — ключ группировки альбомов
    │   ├── Album.kt            # Только чтение (getAlbumById) — без изменений
    │   └── Song.kt             # Только чтение (author/year/album/albumId) — без изменений
    └── controllers/
        └── MainController.kt   # Потребитель Zakroma.getZakroma() — без изменений

karaoke-web/
└── src/main/kotlin/com/svoemesto/karaokeweb/
    └── controllers/
        ├── MainController.kt         # Потребитель — без изменений
        └── PublicApiController.kt    # Потребитель (публичный API) — без изменений

docs/features/
└── special-orders.md   # ИЗМЕНЯЕТСЯ: FR-009 — обновить описание buildFromSettings()
```

**Structure Decision**: Существующая Gradle multi-module монорепа
(`karaoke-app` — core engine на admin-машине, `karaoke-web` — тонкий публичный
слой на проде, `webvue3`/`karaoke-public` — фронтенды). Все поверхности
закромов (`admin /zakroma`, прод `/zakroma`, публичный `/api/pub/zakroma`)
уже переиспользуют одну общую функцию `Zakroma.buildFromSettings()` в
`karaoke-app`, поэтому фикс делается один раз в этой точке — без дублирования
логики на стороне `karaoke-web` или фронтендов и без создания новых
модулей/директорий.

## Complexity Tracking

*Нарушений Constitution Check, требующих обоснования, нет — секция не заполняется.*
