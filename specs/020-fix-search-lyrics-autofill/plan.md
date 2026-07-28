# Implementation Plan: Исправление автоподстановки найденного текста песни

**Branch**: `020-fix-search-lyrics-autofill` | **Date**: 2026-07-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/020-fix-search-lyrics-autofill/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Баг-фикс в `karaoke-app`: подстановка найденного при веб-поиске текста песни
в `source_text` молча не срабатывает, когда текущее "пустое" значение поля
хранится как `'[""]'` (а не как `''`), потому что проверка "есть ли уже
текст" в шаге автоподстановки использует `sourceText.isBlank()` вместо уже
существующего корректного `Song.haveSourceText`. Дополнительно у движка
`YANDEX_SYNC` шаг автоподстановки отсутствует полностью. Технический подход
(см. `research.md`): заменить `isBlank()` на `!haveSourceText` в двух местах
и добавить недостающий шаг разбора результата + автоподстановки для
синхронной Yandex-ветки, приведя все 4 движка поиска к единому поведению.

## Technical Context

**Language/Version**: Kotlin 2.x, JDK 17 (существующий модуль `karaoke-app`, новых языков/версий не вводится)

**Primary Dependencies**: Spring Boot 3.x (существующий `ApiController`/`KaraokeProcessWorker`), без новых зависимостей

**Storage**: PostgreSQL через сырой JDBC (`KaraokeConnection`) — схема не меняется, меняется только интерпретация существующего поля `tbl_songs.source_text`

**Testing**: Ручная проверка по `quickstart.md` (в CI нет надёжных тестов для этого пайплайна — существующие интеграционные тесты `karaoke-app/src/test` в основном `@Disabled`, конституция явно фиксирует это как норму для проекта)

**Target Platform**: Linux server (admin-машина, `karaoke-app` разворачивается только там, см. конституция §«Технологический стек»)

**Project Type**: web-service (backend-фикс; фронтенд `webvue3` не меняется — контракт `/api/songs/searchsongtextall` не меняется)

**Performance Goals**: N/A (поведенческий фикс, не меняет профиль нагрузки; синхронный скрейпинг для YANDEX_SYNC уже является существующим паттерном в SEARXNG/FOURGET-ветке того же обработчика)

**Constraints**: Не вводить внешние SaaS в горячий путь (Principle I конституции — не затрагивается, используются уже одобренные Yandex Cloud Search API/SearXNG/fourget); не менять schema БД

**Scale/Scope**: 2 файла кода с логикой (`UtilsAI.kt`, `KaraokeProcessWorker.kt`), без миграций, без новых экранов UI

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Self-contained автопайплайн)** — ✅ PASS. Используются уже
  одобренные локальные/самостоятельно развёрнутые инструменты (fourget,
  SearXNG) и уже существующий внешний Yandex Cloud Search API (не новый).
  Новых внешних SaaS-зависимостей в горячем пути не вводится.
- **Principle II (Сырой JDBC + дифф по хэшам)** — ✅ PASS. Изменений схемы БД
  нет; чтение/запись `source_text` идёт через существующие
  `Song.loadFromDbById`/`Song.saveToDb`, без новых прямых SQL-запросов.
- **Principle III (Двух-БД синхронизация)** — ✅ N/A. Колонки/таблицы не
  меняются, `recordhash`-триггеры не затрагиваются.
- **Principle IV (Async-очередь с парсингом stdout)** — ✅ PASS. Фикс не
  добавляет новый `ProcessBuilder`/subprocess. Добавляемый синхронный
  разбор результатов для YANDEX_SYNC — лёгкий HTTP-скрейпинг в существующем
  HTTP-хендлере, тот же паттерн, что уже используется в SEARXNG/FOURGET-ветке
  этого же обработчика (не подпадает под "длительные операции" из этого
  принципа — ffmpeg/melt/Demucs/стем-джобы).
- **Principle V (Двух-фронтенд)** — ✅ N/A. Фикс — только backend
  (`karaoke-app`), `webvue3`/`karaoke-public` не меняются.
- **Principle VI (Code Standards, FR-006/007/009)** — ⚠️ ДЕЙСТВУЕТ:
  - FR-006/007 (KDoc + ktlint) — новые/изменённые публичные функции ДОЛЖНЫ
    получить KDoc с `@see docs/features/llm-lyrics-search.md`; `ktlintCheck`
    обязателен перед коммитом.
  - **FR-009 — обязательное обновление per-feature документа**:
    `llm-lyrics-search` входит в 12 ключевых подсистем
    (`docs/features/README.md`), поэтому `docs/features/llm-lyrics-search.md`
    ДОЛЖЕН быть обновлён в этом же PR (зафиксировать единое определение
    "текста ещё нет" и то, что автоподстановка теперь одинакова для всех 4
    движков). Это явный task для `tasks.md`, не Complexity-нарушение.
- **Principle VII (Cross-Machine Setup)** — ✅ N/A. Не затрагивает
  `.git-blame-ignore-revs`/`.gitattributes`/onboarding-документы.

Нарушений, требующих секции «Complexity Tracking», не обнаружено.

## Project Structure

### Documentation (this feature)

```text
specs/020-fix-search-lyrics-autofill/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
└── checklists/
    └── requirements.md  # Spec quality checklist (/speckit.specify command)
```

Каталог `contracts/` не создаётся: фикс не меняет внешний контракт
(`POST /api/songs/searchsongtextall` и связанные эндпоинты сохраняют те же
параметры и формат ответа — меняется только внутреннее поведение).

### Source Code (repository root)

Существующий multi-module Gradle-проект (backend `karaoke-app`, фронтенды
`webvue3`/`karaoke-public` не затрагиваются этой фичей):

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── UtilsAI.kt                       # getLyricsSearch, getLyricsSearchViaSearchTool,
│                                     #   getYandexSearch — правки автоподстановки
├── KaraokeProcessWorker.kt          # обработка завершённого асинхронного SearchAsync — правка автоподстановки
├── model/
│   ├── Song.kt                      # haveSourceText — источник истины, без изменений логики
│   ├── SearchAsync.kt               # без изменений
│   └── SearchResult.kt              # getSearchResultsForSearchAsync — переиспользуется для YANDEX_SYNC
└── controllers/
    └── ApiController.kt             # /api/songs/searchsongtextall — без изменений контракта

docs/features/
└── llm-lyrics-search.md             # обязательное обновление per FR-009 (Constitution Check)
```

**Structure Decision**: изменения ограничены существующим модулем
`karaoke-app` (backend), без новых модулей/директорий. Тесты — ручные
(`quickstart.md`), см. Technical Context.

## Complexity Tracking

*Не заполняется — нарушений Constitution Check не обнаружено (см. секцию
выше).*
