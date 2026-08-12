# Implementation Plan: Временные ссылки в синхронизации БД

**Branch**: `172-db-sync-temporary-links` | **Date**: 2026-08-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/172-db-sync-temporary-links/spec.md`

## Summary

Добавить `tbl_song_share_links` как синхронизируемую сущность механизма «Синхронизация в 1 клик». Сущность будет описана моделью `KaraokeDbTable`, зарегистрирована в `SyncRegistry`, получит восемь разрешений операций, а её направление one-click будет `SERVER_TO_LOCAL`. UI уже строит таблицу сущностей динамически, поэтому после регистрации она сможет отображать новую строку и существующие флаги без отдельного специального экрана. Таблица сессий временных ссылок не включается в синхронизацию: это runtime/аудит-данные, а не сама ссылка.

## Technical Context

**Language/Version**: Kotlin 1.x, JDK 17; Vue 3/JavaScript для существующего динамического sync UI

**Primary Dependencies**: Spring Boot, Gradle multi-module, PostgreSQL raw JDBC, существующий `SyncTarget`/`GenericKaraokeDbTableSyncTarget`, Vuex

**Storage**: PostgreSQL, таблица `tbl_song_share_links`, существующие `recordhash` и триггер из миграций 38/39

**Testing**: `./gradlew ktlintCheck`, `./gradlew :karaoke-app:compileKotlin`, существующие интеграционные проверки; ручная end-to-end проверка через Sync UI и локальную/серверную БД

**Target Platform**: Linux admin-машина с LOCAL PostgreSQL и доступом к SERVER PostgreSQL

**Project Type**: Kotlin multi-module backend + Vue 3 admin web application

**Performance Goals**: Сравнение и загрузка записей — O(n) по идентификатору и пакетными запросами; обычная синхронизация временных ссылок не должна требовать запроса на каждую запись

**Constraints**: Использовать только raw JDBC и существующий hash/diff engine; не передавать секреты в UI/логи; направление one-click по умолчанию только SERVER → LOCAL; нельзя включать share sessions в доменную синхронизацию

**Scale/Scope**: Метаданные временных ссылок и их insert/update/delete/move операции; один новый SyncTarget, модель, свойства разрешений и покрытие документацией/валидацией

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle II — PASS**: используется существующий raw JDBC, `recordhash`, reflection diff, O(n) map-сравнение и пакетная загрузка.
- **Principle III — PASS**: сущность будет добавлена в `SyncRegistry.all` и получит 8 `sync_<key>_<push|pull>_<operation>_allowed` флагов; one-click direction — `SERVER_TO_LOCAL`.
- **Principle V — PASS**: UI использует существующую admin SyncTable и не смешивает public/admin ответственность.
- **Principle VI — PASS WITH FOLLOW-UP**: новые публичные Kotlin/JS символы требуют KDoc/JSDoc с per-feature `@see`; при изменении подсистемы обновить `docs/features/dual-db-sync.md`.
- **Principle VIII — PASS**: секретная часть ссылки не выводится в UI/логи и не добавляются секреты в код/миграции.
- **Security gate — PASS**: по умолчанию разрешения остаются выключенными, пока администратор явно не включит нужные операции; опасные push/delete/move не становятся доступными автоматически.

## Project Structure

### Documentation (this feature)

```text
specs/172-db-sync-temporary-links/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── sync-temporary-links.md
└── tasks.md              # создаётся отдельной командой
```

### Source Code

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/
└── SongShareLink.kt              # sync-модель tbl_song_share_links
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/
└── SyncTarget.kt                 # target и регистрация в SyncRegistry
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
└── KaraokeProperties.kt          # 8 разрешений операций
karaoke-app/src/test/             # проверки mapping/hash/diff при наличии подходящего набора
webvue3/src/components/Sync/     # только если контракт API потребует UI-коррекцию
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/
└── ApiController.kt              # существующие sync endpoints, без нового endpoint
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt
└── Utils.kt                      # существующий generic sync engine
```

**Structure Decision**: Используется существующая архитектура универсальной синхронизации. Специальный контроллер и отдельный frontend store не нужны: `/api/sync/entities`, `/api/sync/run` и `/api/sync/oneclick` работают по registry.

## Phase 0: Research

Результаты зафиксированы в [research.md](research.md): модель таблицы, границы синхронизации и правила дефолтных разрешений.

## Phase 1: Design

Артефакты: [data-model.md](data-model.md), [contracts/sync-temporary-links.md](contracts/sync-temporary-links.md), [quickstart.md](quickstart.md).

## Post-Design Constitution Check

- **Principle II — PASS**: дизайн опирается на generic hash/diff и пакетную работу.
- **Principle III — PASS**: registry, 8 flags и default SERVER → LOCAL явно предусмотрены.
- **Principle V — PASS**: динамический admin UI сохраняется.
- **Principle VI — PASS WITH FOLLOW-UP**: implementation tasks обязаны добавить KDoc/`@see` и обновить per-feature документацию.
- **Principle VIII — PASS**: token hash остаётся внутренним полем и не выводится в результаты.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| Нет | — | Архитектура использует существующий generic sync engine без нового слоя |
