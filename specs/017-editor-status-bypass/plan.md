# Implementation Plan: Редактор видит все песни в закромах и поиске на karaoke-public независимо от статуса

**Branch**: `017-editor-status-bypass` | **Date**: 2026-07-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/017-editor-status-bypass/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

На публичном сайте (`karaoke-public`) закрома автора, спецзаказная плашка
«Отдельные песни разных авторов», подпись количества песен в плашке автора и
поиск сейчас всегда фильтруют песни по `id_status >= 3` (specs/013). Нужно
единственное исключение: если текущий залогиненный пользователь публичного
сайта имеет роль «редактор» (`SiteUser.isEditor == true`), фильтр по статусу
для него не применяется вовсе — он видит все песни автора (и авторов, у
которых есть только неготовые песни), в поиске и в подписи количества песен.
Для всех остальных (анонимных и обычных зарегистрированных) поведение не
меняется.

Технически это точечное изменение трёх методов `PublicApiController`
(`authorsTiles`, `zakroma`, `songs`), которые уже параметризуют своё
поведение флагом `onlyPublished`/условием `id_status >= 3` — нужно просто
сделать это условие зависимым от `SiteUserResolver.resolve(request)?.isEditor`
вместо жёстко зашитого `true`. Bearer-токен уже передаётся фронтендом на все
эти эндпоинты (`karaoke-public/src/services/api.js#authHeader`), так что
изменений во фронтенде не требуется.

## Technical Context

**Language/Version**: Kotlin (JDK 17), Spring Boot 3.x — модуль `karaoke-web`
(тонкий REST-слой), модель данных в `karaoke-app` (переиспользуется как
библиотека).

**Primary Dependencies**: Spring Boot MVC (`@RestController`), существующий
`SiteUserResolver` (резолв `SiteUser` по `Authorization: Bearer`-заголовку),
`Zakroma`/`Song` из `karaoke-app` (модельный слой, сырой JDBC).

**Storage**: PostgreSQL через сырой JDBC (`KaraokeConnection`). Изменений схемы
не требуется — используется уже существующая колонка `tbl_site_users.is_editor`
и `tbl_songs.id_status`.

**Testing**: В CI автоматических тестов на этот путь нет (см. Конституцию,
раздел «Рабочий процесс», п. «Тесты»); проверка — вручную через
`quickstart.md` (curl с/без Bearer-токена редактора) и в браузере на
локальном стенде.

**Target Platform**: Linux-контейнер `karaoke-web` (публичный REST API),
потребитель — SPA `karaoke-public` в браузере.

**Project Type**: Web-приложение (backend REST API + существующий SPA-клиент,
клиентских изменений не требуется).

**Performance Goals**: Без изменений относительно текущего поведения — та же
структура запросов (один доп. булев параметр в `WHERE`), не более одного
дополнительного вызова `SiteUserResolver.resolve()` на запрос там, где его
ещё нет (`authorsTiles`).

**Constraints**: Поведение для не-редакторов должно остаться побайтово тем
же (SC-004 spec.md); никаких изменений в admin-панели (`webvue3`).

**Scale/Scope**: 3 метода одного REST-контроллера
(`karaoke-web/.../PublicApiController.kt`: `authorsTiles`, `zakroma`,
`songs`); опционально доп. параметр в сигнатуры `Zakroma.getZakroma` /
`getZakromaBySpecialOrder` / `Song.loadAuthorSongCounts` не нужен — они уже
принимают `onlyPublished: Boolean`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Оценка |
|---|---|
| I. Self-contained автопайплайн | N/A — фича не касается медиа-пайплайна (ffmpeg/MLT/Demucs), внешних API не добавляет. **PASS** |
| II. Сырой JDBC + диф по хэшам | Изменение не добавляет ORM и не вводит новых сравнений LOCAL↔SERVER; переиспользует существующие сырой-JDBC методы `Song`/`Zakroma` с уже готовым параметром `onlyPublished`. **PASS** |
| III. Двух-БД синхронизация через SyncRegistry | Новых колонок/сущностей не добавляется — `is_editor` уже существует и уже участвует в sync (используется в admin `webvue3` через `SiteUsersTable`/фильтр `isEditor`). **PASS** |
| IV. Async-очередь задач | N/A — синхронные REST-эндпоинты, длительных операций не появляется. **PASS** |
| V. Двух-фронтенд: админка и публичный сайт — разные приложения | Изменение затрагивает только публичный REST-слой (`karaoke-web`) и его потребителя (`karaoke-public`); `webvue3` не трогаем — сохраняет разделение ответственности. **PASS** |
| VI. Code Standards (KDoc/JSDoc, линтеры, per-feature docs) | Затронутые публичные методы уже имеют KDoc — обновить формулировки под новое поведение (FR-006). `ktlintCheck` обязателен перед коммитом. По FR-009 — метод `Zakroma.getZakroma`/`getZakromaBySpecialOrder` уже документирован в `docs/features/special-orders.md`; этот документ MUST быть обновлён в рамках PR (см. Complexity Tracking / задачи). **PASS с обязательством** |
| VII. Cross-Machine Setup | N/A — фича не касается AI-конфигов/git-атрибутов. **PASS** |

Нарушений, требующих обоснования в Complexity Tracking, нет.

## Project Structure

### Documentation (this feature)

```text
specs/017-editor-status-bypass/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md         # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-web/
└── src/main/kotlin/com/svoemesto/karaokeweb/
    ├── controllers/
    │   └── PublicApiController.kt      # authorsTiles(), zakroma(), songs() — точка изменения
    └── services/
        └── SiteUserResolver.kt          # уже существует, переиспользуется без изменений

karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/model/
    ├── Zakroma.kt                       # getZakroma()/getZakromaBySpecialOrder(onlyPublished) — без изменений сигнатуры
    ├── Song.kt                          # loadAuthorSongCounts(onlyPublished), loadListFromDb(id_status) — без изменений сигнатуры
    └── SiteUser.kt                      # isEditor — уже существует, без изменений

docs/features/
└── special-orders.md                    # обновить описание поведения закромов (FR-009)

karaoke-public/                          # изменений не требуется — apiGet() уже шлёт Bearer-токен
```

**Structure Decision**: Точечное изменение внутри уже существующего REST-слоя
`karaoke-web` (модуль публичного API, `PublicApiController.kt`), переиспользующее
готовые параметризованные методы модельного слоя `karaoke-app`
(`Zakroma`, `Song`). Новых модулей, файлов моделей или БД-миграций не требуется.
Клиентский `karaoke-public` не меняется, так как Bearer-токен уже отправляется
на все три эндпоинта существующим `apiGet()`.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

Нет нарушений — таблица не заполняется.
