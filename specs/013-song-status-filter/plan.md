# Implementation Plan: Показ на проде только песен со статусом готовности >= 3

**Branch**: `013-song-status-filter` | **Date**: 2026-07-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-song-status-filter/spec.md`

## Summary

На публичных поверхностях прода (закрома автора, спецзаказная плашка «Отдельные
песни разных авторов», поиск) сейчас показываются песни любого статуса, хотя
сайт уже использует порог `id_status >= 3` как определение «песня в
коллекции» для публичных счётчиков (`StatsCacheScheduler`). Технически фикс —
добавить этот же фильтр (`id_status >= 3`) в 4 публичных call-site'а
karaoke-web (`PublicApiController.zakroma`/`songs`, legacy Thymeleaf
`MainController.zakroma`/`filter`), переиспользуя уже существующий generic
фильтр `id_status` в `Song.getWhereList`. Admin-путь (`karaoke-app`
`MainController.zakroma`, единственный не-публичный вызыватель `Zakroma`)
остаётся без изменений — фильтр не наследуется по умолчанию.

## Technical Context

**Language/Version**: Kotlin (проект на Kotlin 2.x / JDK 17), Spring Boot 3.x
(модуль `karaoke-web`)

**Primary Dependencies**: без новых зависимостей — переиспользуется
существующий generic-фильтр `id_status` в `Song.getWhereList`
(`karaoke-app/src/main/kotlin/.../model/Song.kt:7130-7148`), уже
поддерживающий операторы `>=`/`>`/`<=`/`<`/`!=`/`=`.

**Storage**: PostgreSQL через сырой JDBC (`KaraokeConnection`); используется
уже существующая колонка `tbl_songs.id_status` — миграций не требуется.

**Testing**: в CI нет автотестов для этого слоя (см. Конституцию, раздел
«Рабочий процесс» — существующие тесты `karaoke-app/src/test` в основном
`@Disabled`); проверка — вручную через `quickstart.md` в
production-like/sandbox окружении.

**Target Platform**: Linux server (Docker), backend REST (`karaoke-web` —
единственный публично разворачиваемый на проде backend-модуль) + legacy
Thymeleaf-страницы того же модуля.

**Project Type**: web-service (изменения — только backend, в существующих
Kotlin-модулях `karaoke-app` (model layer) и `karaoke-web` (controllers); без
изменений во фронтендах `webvue3`/`karaoke-public` — они лишь рендерят то,
что вернул backend).

**Performance Goals**: без деградации — фильтр по `id_status` использует ту
же индексированную колонку, что уже фильтруется в `StatsCacheScheduler`
(лёгкий `WHERE`, не полное сканирование).

**Constraints**: изменение обязано НЕ затронуть admin-видимость песен
(Principle V «двух-фронтенд»/FR-006 spec.md) и не требовать
SQL-миграций/изменений `SyncRegistry` (нет новых сущностей/колонок).

**Scale/Scope**: 2 файла в `karaoke-app` (model, добавление опционального
параметра в `Zakroma.getZakroma`/`getZakromaBySpecialOrder`) + 1 файл в
`karaoke-web` (`PublicApiController.kt`) + 1 файл в `karaoke-web`
(`MainController.kt`, legacy Thymeleaf `/zakroma` и `/filter`); плюс
обновление 2 per-feature документов (`docs/features/special-orders.md`,
`docs/features/stats.md`) по FR-009.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн | ✅ N/A | Фича не трогает медиа-пайплайн, внешних API не добавляет. |
| II. Сырой JDBC + дифф по хэшам | ✅ PASS | Никакого ORM; переиспользуется существующий generic `id_status`-фильтр в `Song.getWhereList` (сырой SQL WHERE), новых O(n²)-сравнений нет. |
| III. Двух-БД синхронизация через SyncRegistry | ✅ N/A | Нет новой сущности/колонки — `id_status` уже часть `tbl_songs` и уже синхронизируется; `recordhash`/`SyncRegistry` не меняются. |
| IV. Async-очередь задач | ✅ N/A | Изменение — синхронный SQL WHERE в HTTP-запросе, не долгая операция. |
| V. Двух-фронтенд: админка и публичный сайт — разные приложения | ✅ PASS (ключевой gate) | Фильтр добавляется ТОЛЬКО в публичные call-site'ы `karaoke-web` (проде-модуль); `karaoke-app`-контроллер (`MainController.zakroma`, используется только на admin-машине, на проде не разворачивается вовсе) остаётся без изменений — параметр фильтрации опциональный и по умолчанию выключен. |
| VI. Code Standards | ⚠️ ACTION REQUIRED | Новый/изменённый публичный код должен получить KDoc с `@see` на per-feature документ; т.к. фича трогает `Zakroma.getZakromaBySpecialOrder` (документирован в `special-orders.md`) и согласуется с определением из `stats.md` (`id_status>=3` = «коллекция») — оба документа обновляются в этом же PR (FR-009). |
| VII. Cross-Machine Setup | ✅ N/A | Не трогает AI-конфиги/git-атрибуты. |

Нарушений, требующих секции «Complexity Tracking», нет.

**Post-Phase 1 re-check**: `data-model.md` подтверждает отсутствие новых
сущностей/миграций (Principle III не затронут); `contracts/` фиксирует, что
меняется только состав ответа существующих endpoint'ов, а не их форма или
доступность для admin-путей (Principle V по-прежнему PASS); пункт VI
(Code Standards) закрывается конкретным списком документов для обновления в
`Project Structure` ниже — все статусы Constitution Check остаются
прежними, gate пройден.

## Project Structure

### Documentation (this feature)

```text
specs/013-song-status-filter/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── public-song-visibility.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/
    └── model/
        └── Zakroma.kt                 # getZakroma / getZakromaBySpecialOrder:
                                        # + опциональный параметр
                                        # onlyPublished (default false, admin
                                        # call site его не передаёт)

karaoke-web/
└── src/main/kotlin/com/svoemesto/karaokeweb/controllers/
    ├── PublicApiController.kt         # zakroma(): передаёт onlyPublished=true
                                        # в оба ветки (author / specialBucket);
                                        # songs(): добавляет id_status=">=3" в
                                        # args перед Song.loadListFromDb
    └── MainController.kt              # legacy Thymeleaf /zakroma: тот же
                                        # onlyPublished=true; /filter: тот же
                                        # id_status=">=3" в attr

docs/features/
├── special-orders.md                  # обновляется (FR-009): фильтр статуса
│                                       # теперь применяется и к спецзаказной
│                                       # плашке
└── stats.md                           # обновляется (FR-009): фиксируется,
                                        # что публичные листинги теперь
                                        # согласованы со счётчиком «коллекция»
```

**Structure Decision**: Изменения не выходят за пределы существующих
backend-модулей (`karaoke-app` — model layer, `karaoke-web` — controllers).
Новых модулей/проектов/директорий не создаётся; фронтенды (`webvue3`,
`karaoke-public`) не меняются, т.к. просто рендерят уже отфильтрованный ответ
backend'а.

## Complexity Tracking

*Нарушений Constitution Check нет — секция не заполняется.*
