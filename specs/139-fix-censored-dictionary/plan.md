# Implementation Plan: Исправление цензурирования {songNameCensored} на продакшене

**Branch**: `139-fix-censored-dictionary` | **Date**: 2026-08-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/139-fix-censored-dictionary/spec.md`

## Summary

Слово из словаря «Censored» присутствует в БД (и LOCAL, и SERVER), но реально опубликованная
«новость на сайте» (`tbl_news.title`/`body`, категории `air`/`premium`) выходит с
нецензурированным словом вместо `{songNameCensored}`. Корневая причина найдена трассировкой кода
(Phase 0, см. `research.md`): `NewsTemplateService.render()` внутри строит `songNameCensored` через
`String.censored()`, а та функция **не принимает** `database`-параметр — она всегда обращается к
глобальному `com.svoemesto.karaokeapp.WORKING_DATABASE`, даже когда вызывающий код (в частности,
`SongReleaseAnnouncementScheduler` в `karaoke-web`, единственный триггер обеих категорий
auto-новости) уже явно передал правильное соединение (`com.svoemesto.karaokeweb.WORKING_DATABASE`)
на уровень выше. Технический подход: прокинуть `database: KaraokeConnection` через всю цепочку
рендера censored-текста (`censored()` → `CensoredWordsDictionary` → `TextFileDictionary.dict` уже
поддерживает параметр, не хватает его на верхних уровнях) вместо использования модуля-глобала;
добавить логирование при сбое чтения словаря; добавить admin-инструмент проверки словаря и подсказку
формата значений.

## Technical Context

**Language/Version**: Kotlin (JDK 17), Spring Boot — существующий multi-module backend
(`karaoke-app`, `karaoke-web`); Vue 3 (webvue3 admin SPA) для UI-части (FR-003/FR-004).

**Primary Dependencies**: без новых зависимостей. Используются существующие
`TextFileDictionary`/`Dictionary`/`CensoredWordsDictionary` (`karaoke-app/.../textfiledictionary`),
`NewsTemplateService`/`VkTemplateService`/`TelegramTemplateService`
(`karaoke-app/.../services`), `SongReleaseAnnouncementService` + `SongReleaseAnnouncementScheduler`
(вызывающая сторона в `karaoke-web`), `KaraokeConnection`/`Connection.local()`/`Connection.remote()`
(сырой JDBC, Principle II).

**Storage**: PostgreSQL через сырой JDBC (`tbl_dictionaries`, `dict_name='Censored'`) — схема БД не
меняется, миграций не требуется.

**Testing**: в проекте нет надёжного CI-тестового прогона (Constitution, «Рабочий процесс»); проверка
— вручную администратором/агентом на dev-pc: точечный ручной вызов изменённых функций (или
существующего `/api/news/templates/preview`) + прямая проверка `tbl_dictionaries`/логов после тика
планировщика. Подробности — `quickstart.md`.

**Target Platform**: Linux/Docker. Затрагиваются оба контекста выполнения: `karaoke-app`
(admin-машина — VK/Telegram-паблиш) и `karaoke-web` (прод-сервер — новости сайта, единственное
реальное место проявления бага).

**Project Type**: существующий web-service монорепозиторий (`karaoke-app` + `karaoke-web` backend,
`webvue3` admin-фронтенд) — не новый проект, точечное изменение сигнатур + admin UI-дополнение.

**Performance Goals**: не применимо — один дополнительный SELECT на 150 строк `tbl_dictionaries` на
рендер, тот же порядок величины, что и сейчас.

**Constraints**: НЕ менять формат значений словаря (`[x]`-разметка, Principle-совместимо с уже
существующими 149+ записями); фикс НЕ должен требовать разворачивания `karaoke-app` на
прод-сервере (Constitution, Технологический стек: «karaoke-app на проде не разворачивается
вовсе») — решение работает исключительно на уровне сигнатур/параметров общих Kotlin-классов,
общих для обоих модулей через `implementation(project(":karaoke-app"))` в `karaoke-web`; сохранить
поведение по умолчанию для существующих вызывающих мест в `karaoke-app` (VK/Telegram), где текущий
глобал уже корректен.

**Scale/Scope**: словарь «Censored» — ~150 записей; изменение сигнатур 4-6 функций
(`String.censored`, `TextFileDictionary`/`CensoredWordsDictionary`, `NewsTemplateService.render`/
`buildReplacements`, по аналогии — `VkTemplateService`/`TelegramTemplateService` для консистентности
FR-001) + логирование в `TextFileDictionary.dict` + 1 новый лёгкий admin-эндпоинт (FR-003) + UI-хинт
в `DictionariesTable.vue` (FR-004).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Применимость | Оценка |
|---|---|---|
| I. Self-contained автопайплайн | Не применим (не про медиа-обработку) | PASS |
| II. Сырой JDBC + дифф по хэшам | Применим — все DB-обращения только через `KaraokeConnection`, без JPA/Hibernate; новых diff/sync-сравнений нет | PASS |
| III. Двух-БД синхронизация через SyncRegistry | Не применим — `tbl_dictionaries` уже зарегистрирована в `DictionariesSyncTarget`, схема не меняется, новых sync-сущностей нет | PASS |
| IV. Async-очередь задач | Не применим — фикс не добавляет долгих `ProcessBuilder`-операций | PASS |
| V. Два фронтенда — раздельные ответственности | Применим — UI-изменения (FR-003/FR-004) только в `webvue3` (admin), `karaoke-public` не затрагивается | PASS |
| VI. Code Standards (KDoc/JSDoc, линтеры) | Применим ко всем новым/изменённым публичным символам | PASS (соблюсти при реализации) |
| VII. Cross-Machine Setup | Не применим (не про AI-конфиги/git-гигиену) | PASS |
| VIII. Секреты и git-гигиена | Не применим — фикс не трогает секреты/`.env` | PASS |

Нарушений нет — секция Complexity Tracking не заполняется.

## Project Structure

### Documentation (this feature)

```text
specs/139-fix-censored-dictionary/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks — not created here)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── Extentions.kt                          # String.censored() — добавить параметр database
├── textfiledictionary/
│   ├── TextFileDictionary.kt              # dict-геттер уже параметризуем через Dictionary.loadValues;
│   │                                       #   добавить логирование в catch(Throwable)
│   └── CensoredWordsDictionary.kt         # конструктор с опциональным database
└── services/
    ├── NewsTemplateService.kt             # render()/buildReplacements() — прокинуть database
    ├── VkTemplateService.kt               # renderWithFlags() — прокинуть database (консистентность FR-001)
    ├── TelegramTemplateService.kt         # аналогично
    └── SongReleaseAnnouncementService.kt  # вызывающая сторона — передать уже имеющийся database в render()

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/
└── SongReleaseAnnouncementScheduler.kt    # без изменений сигнатуры вызова — уже передаёт свой WORKING_DATABASE

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/
└── DictionariesController.kt              # существующий контроллер (/api/dictionaries/*) —
                                            #   добавить POST /api/dictionaries/test (FR-003)

webvue3/src/components/Dictionaries/
└── DictionariesTable.vue                  # UI-хинт формата `[x]` для словаря «Censored» (FR-004)
                                            #  + вызов нового эндпоинта проверки (FR-003, User Story 2)
```

**Structure Decision**: точечное изменение существующего backend-кода в `karaoke-app`
(переиспользуется как библиотека модулем `karaoke-web` — тот же механизм, что уже приводит к багу
через глобал), без новых модулей/таблиц. Один новый лёгкий REST-эндпоинт в admin-контроллере
(`karaoke-app`, доступен только через `webvue3`, Principle V) + точечное расширение существующего
Vue-компонента словарей. Тестового каталога в проекте нет (Constitution: тестов в CI нет) — проверка
вручную по `quickstart.md`.

## Complexity Tracking

*Нарушений Constitution Check нет — секция не заполняется.*

## Post-Design Constitution Check (после Phase 1)

Данные Phase 1 (`data-model.md`, `contracts/dictionary-test-endpoint.md`, `quickstart.md`)
подтверждают исходную оценку: новый эндпоинт использует уже существующий контроллер и `withDb`-хелпер
(Principle II), не заводит новых синхронизируемых сущностей (Principle III), не требует
`ProcessBuilder`/долгих операций (Principle IV), ограничен `webvue3` (Principle V). Повторных
нарушений не выявлено — gate остаётся PASS без изменений.
