# Implementation Plan: Замена поискового движка для поиска текстов песен

**Branch**: `014-lyrics-search-replacement` | **Date**: 2026-07-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-lyrics-search-replacement/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Сегодняшний поиск URL с текстами песен (`SearchTool.searchUrls` в
`karaoke-app/.../llm/Tools.kt`, вызывается из `LyricsFinderService` и потока
`getSearXNGSearch` в `UtilsAI.kt`) возвращает мало или пустые результаты для
заметной доли песен через SearXNG. Решение (см. `research.md`): развернуть рядом
отдельный self-hosted мета-поисковик **4get** (поддерживает Yandex как источник —
важно для русскоязычных запросов «автор текст песни …») и переключить на него
только этот путь, оставив существующий `searxng`-контейнер и свойство
`searxng.base-url` без изменений — они по-прежнему обслуживают поиск обложек
альбомов (`AlbumCoverService.searchSearxngImages`, вне рамок задачи, FR-007).
Внутренний Kotlin-контракт (`searchUrls(query: String): List<String>`) и
вызывающие места не меняются (FR-004).

## Technical Context

**Language/Version**: Kotlin (проектный стандарт, см. `constitution.md` →
Технологический стек: Kotlin 1.x/2.x, JDK 17, Spring Boot) — без изменений,
интеграция целиком внутри существующего модуля `karaoke-app`.

**Primary Dependencies**: Spring Boot (`@Component`, `@Value` конфигурация — как
у сегодняшнего `SearchTool`), `langchain4j` (`@Tool`-аннотация, т.к. функция
используется как LLM-инструмент), Jackson `ObjectMapper` (разбор JSON-ответа),
`java.net.http.HttpClient` (те же примитивы, что и в текущей реализации — новых
библиотек не добавляется).

**Storage**: N/A — постоянных сущностей БД задача не вводит и не меняет (см.
`data-model.md`); существующие `SearchAsync`/`SearchResult` не меняют структуру.

**Testing**: Ручная/интеграционная проверка (см. `quickstart.md`) — соответствует
принятой в проекте практике (`constitution.md` → «Рабочий процесс» → «Тесты»: в
CI автотестов нет, интеграционные тесты в основном `@Disabled`, проверка —
пользователем вручную/в production-like окружении). Формальных unit-тестов для
парсинга JSON нового бэкенда не требуется добавлять новой инфраструктурой тестов
сверх существующей практики модуля.

**Target Platform**: Linux, Docker/docker-compose на admin-машине (тот же хост,
где сегодня работает контейнер `searxng`); новый поисковый бэкенд — ещё один
контейнер в той же docker-сети (`karaokenet`).

**Project Type**: Backend-интеграция внутри существующего Spring Boot модуля
`karaoke-app` (single project, не отдельное приложение) + правка docker-compose
конфигурации для нового self-hosted сервиса.

**Performance Goals**: Не хуже сегодняшнего поведения по задержке (таймаут
запроса — явно ограничен, по аналогии с текущими `Duration.ofSeconds(30)`);
главная метрика — не скорость, а доля запросов с непустым результатом (SC-001,
SC-002 в `spec.md`), т.к. проблема сегодня — в полноте результатов, а не в
задержке.

**Constraints**: Только self-hosted замена (FR-002, Principle I конституции —
без внешнего SaaS в рантайме); интерфейс вызова не меняется (FR-004); адрес
нового бэкенда — конфигурируемый, отдельным свойством от `searxng.base-url`,
чтобы не задеть поиск обложек альбомов (FR-005, FR-007, см. `research.md`
Вопрос 2); при недоступности бэкенда — не падать, логировать и возвращать
пустой список (FR-006).

**Scale/Scope**: Низкая нагрузка — поиск запускается по одной песне за раз в
рамках существующего пайплайна импорта/обработки песен (не отдельный
высоконагруженный поисковый сервис); изменение затрагивает один интеграционный
класс + конфигурацию, не архитектуру приложения.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Обоснование |
|---|---|---|
| I. Self-contained автопайплайн (NON-NEGOTIABLE) | **PASS** | Замена — другой self-hosted мета-поисковик (4get), не внешний SaaS; явное решение пользователя, зафиксированное в `spec.md` FR-002. Одобрение пользователя на использование внешнего API не требуется, т.к. внешний API не используется. |
| II. Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE) | **N/A** | Новых/изменённых таблиц и сравнений LOCAL↔SERVER нет (см. `data-model.md`). |
| III. Двух-БД синхронизация через SyncRegistry | **N/A** | Новых синхронизируемых сущностей нет. |
| IV. Async-очередь задач с парсингом stdout | **N/A** | Веб-поиск текстов песен сегодня выполняется синхронным HTTP-вызовом (`HttpClient`) внутри обработки запроса, а не как `ProcessBuilder`-подпроцесс с очередью/приоритетом — принцип регулирует именно медиа-обработку (ffmpeg/melt/Demucs/Sheetsage). Поведение не меняется. |
| V. Двух-фронтенд: админка и публичный сайт | **N/A** | Изменение целиком в бэкенде `karaoke-app`; UI (webvue3/karaoke-public) не затрагивается. |
| VI. Code Standards (NON-NEGOTIABLE) | **ACTION REQUIRED, не блокирует** | Новый/изменённый публичный Kotlin-код обязан получить KDoc с `@see` на `docs/features/llm-lyrics-search.md` (FR-006 конституции); т.к. правится код ключевой подсистемы (llm-lyrics-search), FR-009 требует обновить `docs/features/llm-lyrics-search.md` в том же PR — учтено в Project Structure ниже. ktlint должен пройти без новых нарушений baseline. |
| VII. Cross-Machine Setup | **N/A** | Изменение не касается персональных AI-конфигов/onboarding-документов. |

Нарушений, требующих секции «Complexity Tracking», нет.

*Re-check после Phase 1 (см. `research.md`, `data-model.md`, `contracts/`,
`quickstart.md`): решения Phase 0/1 не вводят исключений из принципов — замена
остаётся self-hosted (I), не трогает БД/sync (II/III), не трогает async-очередь
медиа-обработки (IV) и фронтенды (V), требует лишь обновления per-feature
документа и KDoc (VI) — статусы выше не меняются. Constitution Check
**повторно пройден без замечаний**.

## Project Structure

### Documentation (this feature)

```text
specs/014-lyrics-search-replacement/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/
│   └── lyrics-search-backend.md   # Phase 1 output (/speckit.plan command)
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

Single project (существующий бэкенд-модуль `karaoke-app`), без изменений в
`webvue3`/`karaoke-public` (Principle V — не трогаем):

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── llm/
│   └── Tools.kt                # SearchTool.searchUrls — интеграция меняется на
│                                # новый бэкенд; сигнатура и @Tool-описание сохраняются (FR-004)
├── UtilsAI.kt                   # getSearXNGSearch (строка 88) — вызывающий код, БЕЗ изменений
├── AlbumCoverFinder.kt           # AlbumCoverService.searchSearxngImages — БЕЗ изменений (FR-007)
└── (application.yml)             # karaoke-app/src/main/resources/application.yml —
                                   # добавить `lyrics-search.base-url` рядом с существующим
                                   # `searxng.base-url` (строки 50-51), не заменяя его

deploy/
├── docker-compose.yml                              # + новый сервис (4get), `searxng` не трогаем
├── docker-compose-app.yml                          # + новый сервис (4get), `searxng` не трогаем
└── new_comp/sm-karaoke-system/deploy/
    └── docker-compose-app-new-comp.yml             # + новый сервис (4get), `searxng` не трогаем

docs/features/llm-lyrics-search.md   # ОБЯЗАТЕЛЬНО обновить в том же PR (FR-009,
                                       # Principle VI конституции) — описать новый
                                       # бэкенд взамен SearXNG в разделе «Как работает»
                                       # и «Известные ловушки»
```

**Structure Decision**: Изменение целиком внутри модуля `karaoke-app`
(single-project backend integration) — новый self-hosted поисковый бэкенд
подключается через отдельное Spring-свойство и отдельный docker-compose сервис,
без изменений в структуре модулей, БД-схеме или фронтендах. Тесты — по
существующей практике проекта (ручная/интеграционная проверка, см.
`quickstart.md`), отдельная директория `tests/` не создаётся.

## Complexity Tracking

*Нарушений Constitution Check нет (см. таблицу выше) — секция не заполняется.*
