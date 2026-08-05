# Implementation Plan: Ложное срабатывание новости «песня появилась в коллекции» после синхронизации

**Branch**: `152-fix-false-collection-news` | **Date**: 2026-08-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/152-fix-false-collection-news/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

`MainController.doChangeRecords` (`POST /changerecords`, karaoke-web) детектирует переход
`newsAvailableAnnounced: false → true` сравнением с **собственным, локальным для целевой
БД** значением, прочитанным непосредственно перед применением входящего `UPDATE`
(`Song.readNewsAvailableFlag`). Это ложно интерпретирует ситуацию «целевая БД впервые
получает уже давно истинное значение флага» (например, после ранее рассинхронизированного
backfill'а, пропущенного push'а или просто позднего первого схождения записи) как «песня
только что стала доступна» — и создаёт лишнюю новость категории `premium` («в коллекции»)
для песни, которая была доступна уже давно. Дополнительно у этого триггера, в отличие от
триггера «в эфире» (`checkOnAirWindow`), вообще нет защиты от дублей через
`News.existsAnnouncement`.

Технический подход (детали и альтернативы — [research.md](./research.md)):

1. Добавить в `SongReleaseAnnouncementService.detectAndAnnounceAvailability` идемпотентную
   защиту `News.existsAnnouncement(songId, link, category="premium", …)` — тот же паттерн,
   что уже используется для «в эфире» — так, чтобы повторное обнаружение перехода (с любой
   стороны, при любом количестве прогонов синхронизации) никогда не создавало вторую
   новость по одной и той же песне.
2. Добавить содержательную защиту от **самого первого** ложного срабатывания: не создавать
   новость «в коллекции», если у песни уже существует (или должна была бы существовать)
   новость «в эфире» — на практике: `News.existsAnnouncement(songId, link, category="air")`.
   Песня «в эфире» по определению уже доступна в коллекции — обратный порядок появления
   этих двух новостей всегда означает, что «в коллекции» опоздала, а не что событие
   произошло только что.
3. Существующее сравнение «до/после» в `doChangeRecords` остаётся как дешёвый fast-path
   (не грузить полную песню, если флаг локально уже был `true`), но перестаёт быть
   единственным источником истины о том, «новое» ли это событие.

Схема БД не меняется (переиспользуются существующие `tbl_news.category/song_id/link` и
существующий JSON-флаг `player_readiness_flags.newsAvailableAnnounced`).

## Technical Context

**Language/Version**: Kotlin 2.x, Spring Boot 3.x, JDK 17 (существующий стек, без изменений)

**Primary Dependencies**: сырой JDBC (`KaraokeConnection`), kotlinx.serialization (`Json` —
парсинг `player_readiness_flags`), существующие `Song`/`News`/`SongReleaseAnnouncementService`
классы — новых зависимостей не добавляется

**Storage**: PostgreSQL — LOCAL (admin-машина) и SERVER (прод), таблицы `tbl_songs`
(колонка `player_readiness_flags`, JSON-текст) и `tbl_news` (`category`, `song_id`, `link`);
новых колонок/миграций не требуется

**Testing**: в CI нет автотестов для этой области (константа проекта — см. constitution.md,
раздел «Рабочий процесс», «Тесты: в CI нет»); проверка — ручной replay сценария инцидента
на LOCAL-сэндбоксе (см. [quickstart.md](./quickstart.md))

**Target Platform**: `karaoke-web` (сервер, где реально исполняется `POST /changerecords`)
и `karaoke-app` (владелец класса `SongReleaseAnnouncementService`, используемого также
из `karaoke-web`); `karaoke-app` на проде не разворачивается — фактическое исполнение
триггера всегда происходит на сервере (karaoke-web JVM), затрагивая серверную БД

**Project Type**: web-service (backend-only фикс, без изменений во фронтендах)

**Performance Goals**: без деградации существующего пути `doChangeRecords` — батчи могут
затрагивать много строк `tbl_songs` за раз (каталог 18k+ записей); добавляемые проверки
(`News.existsAnnouncement`) — точечные indexed SELECT по одной песне, вызываются только
для строк, уже прошедших дешёвый fast-path фильтр (см. Principle II — раздел «Ограничения»)

**Constraints**: Principle II (сырой JDBC, без JPA/Hibernate, без O(n²) сравнений),
Principle III (набор синхронизируемых полей/таблиц не меняется — фикс не трогает
`SyncRegistry`), FR-009 (обязательное обновление `docs/features/approve-pipeline.md` и
`docs/features/dual-db-sync.md` — оба документа описывают текущий, ныне неверный,
контракт `detectAndAnnounceAvailability`)

**Scale/Scope**: прод-каталог ~18k+ песен; изменение затрагивает ровно один метод
(`SongReleaseAnnouncementService.detectAndAnnounceAvailability`) и точку его вызова
(`MainController.doChangeRecords`) — оба уже существуют, новых эндпоинтов/сервисов нет

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Применимость | Статус |
|---|---|---|
| I. Self-contained автопайплайн | Не затрагивается (не медиа-пайплайн) | N/A |
| II. Сырой JDBC + дифф по хэшам | Новые проверки — точечные `SELECT` по PK/индексу, без JPA, без O(n²) | ✅ PASS |
| III. Двух-БД синхронизация через SyncRegistry | Набор синхронизируемых полей/таблиц не меняется — фикс живёт поверх существующего sync, не в нём | ✅ PASS |
| IV. Async-очередь задач | Не затрагивается (не `KaraokeProcess*`) | N/A |
| V. Двух-фронтенд | Не затрагивается (backend-only, UI не меняется) | N/A |
| VI. Code Standards (FR-006/007/009) | KDoc на изменённых публичных методах, ktlint, обязательное обновление `docs/features/approve-pipeline.md` + `docs/features/dual-db-sync.md` в этом же PR | ⚠️ Обязательство зафиксировано в tasks (не блокер) |
| VII. Cross-Machine Setup | Не затрагивается | N/A |
| VIII. Секреты и git-гигиена | Секреты не затрагиваются | ✅ PASS |

Нарушений, требующих обоснования в Complexity Tracking, нет.

**Post-Design re-check (после Phase 1)**: `data-model.md`/`contracts/`/`quickstart.md` не
вводят новых таблиц, колонок, эндпоинтов или сервисов — решение (Decision 1/2 в
research.md) целиком укладывается в уже существующие `News.existsAnnouncement` и
`tbl_news.category`. Таблица гейтов выше остаётся в силе без изменений.

## Project Structure

### Documentation (this feature)

```text
specs/152-fix-false-collection-news/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── collection-news-trigger.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── services/
│   └── SongReleaseAnnouncementService.kt   # detectAndAnnounceAvailability — основная правка
└── model/
    ├── Song.kt                              # readNewsAvailableFlag — остаётся fast-path'ом, не единственным источником истины
    └── News.kt                              # existsAnnouncement — переиспользуется как есть, без изменений сигнатуры

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
└── controllers/
    └── MainController.kt                    # doChangeRecords — точка вызова, минимальные правки (или без правок, если весь фикс укладывается в сервис)

docs/features/
├── approve-pipeline.md                      # FR-009: обновить описание L3-идемпотентности
└── dual-db-sync.md                          # FR-009: обновить раздел про detectAndAnnounceAvailability
```

**Structure Decision**: Фикс укладывается в существующую двух-модульную структуру
(`karaoke-app` + `karaoke-web`), без новых модулей/директорий. Основная логика — в
`SongReleaseAnnouncementService.kt` (karaoke-app), так как именно там уже живут оба
существующих механизма детекции (`detectAndAnnounceAvailability`, `checkOnAirWindow`) и
их общая зависимость `News.existsAnnouncement`; `MainController.doChangeRecords`
(karaoke-web) остаётся точкой вызова без изменения контракта `POST /changerecords`
(вход/выход эндпоинта не меняются — см. [contracts/collection-news-trigger.md](./contracts/collection-news-trigger.md)).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нарушений нет — таблица не заполняется.
