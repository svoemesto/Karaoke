---
description: "Task list for 294-fourget-scraper-order — порядок scrapers fourget и post-filter «мусорных» URL"
---

# Tasks: 294 — Порядок и состав scraper'ов fourget для поиска текстов песен

**Input**: Design documents from `/specs/294-fourget-scraper-order/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md
**Tests**: Включены (FR-009 спеки — обязательные unit-тесты для `filterUselessLyricsUrls`). Паттерн `AlbumCoverFinderParsingTest`.
**Branch**: `294-fourget-scraper-order` (уже создана хуком `before_specify`)
**Scope**: Только `karaoke-app` модуль. Без миграций БД. Без изменений UI. Без изменений в `AlbumCoverFinder.kt` (NFR-006 спеки).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какой user story относится задача (US1, US2, US3)
- Все пути абсолютные от корня репозитория `/home/nsa/Karaoke`

## Граница изменений (напоминание)

| Файл | Действие |
|---|---|
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt` | MODIFY |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` | MODIFY |
| `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/llm/ToolsTest.kt` | CREATE |
| `archive/docs/features/llm-lyrics-search.md` | MODIFY (опционально, FR-008 спеки) |

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка инфраструктуры — новое свойство в `KaraokeProperties`,
базовые константы и helper-функции в `Tools.kt`. Эти изменения блокируют
все user stories (US1+US2+US3 используют `lyricsSearchScrapersList()`
и/или `lyricsSearchUselessUrlPatterns`).

**⚠️ CRITICAL**: Phase 2 MUST be полностью завершён до начала любой
user story.

- [X] T001 Добавить 3 новых ключа в `listKaraokeProperties` (`KaraokeProperties.kt:186-…`) после строки `albumCoverSearchEngine` (строка ~202):
  - `lyricsSearchScrapers: String`, дефолт `"yep;brave"`, описание про порядок scrapers и `;`-разделитель
  - `lyricsSearchMinResults: Int`, дефолт `2`, описание про порог качества после post-filter
  - `lyricsSearchUselessUrlPatterns: String`, дефолт (длинный список паттернов из FR-004 спеки, см. data-model.md), описание про паттерны для post-filter
- [X] T002 [P] Добавить top-level `private val DEFAULT_LYRICS_SEARCH_SCRAPERS = listOf("yep", "brave")` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt` (после `LyricsSearchResult` data class, перед `companion object` SearchTool)
- [X] T003 [P] Добавить top-level `private val DEFAULT_USELESS_URL_PATTERNS = listOf(...)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt` (рядом с DEFAULT_LYRICS_SEARCH_SCRAPERS). Список паттернов — из data-model.md, раздел «Константа DEFAULT_USELESS_URL_PATTERNS»
- [X] T004 [P] Добавить helper `private fun lyricsSearchScrapersList(): List<String>` в `companion object` `SearchTool` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt`. Логика: `KaraokeProperties.getString("lyricsSearchScrapers").split(";").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { DEFAULT_LYRICS_SEARCH_SCRAPERS }`. KDoc — описание helper'а
- [X] T005 [P] Добавить helper `internal fun uselessUrlPatternsList(): List<String>` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt` (top-level, `internal` — для unit-тестов). Логика: `KaraokeProperties.getString("lyricsSearchUselessUrlPatterns").split(";").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { DEFAULT_USELESS_URL_PATTERNS }`. KDoc

**Checkpoint**: Phase 2 готов — все user stories могут стартовать параллельно.

---

## Phase 2: Foundational — `filterUselessLyricsUrls` (Blocking для US1+US2)

**Purpose**: Реализация чистой функции `filterUselessLyricsUrls` + её unit-тесты.
Без этой функции невозможны ни US1 (post-filter перед fallback), ни US2
(сама post-filter-логика).

**⚠️ CRITICAL**: Phase 3 MUST be полностью завершён до старта US1/US2.

- [X] T006 Реализовать top-level `internal fun filterUselessLyricsUrls(urls: List<String>, patterns: List<String> = uselessUrlPatternsList()): List<String>` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt`. Алгоритм по data-model.md (7 правил FR-004 спеки): невалидный URL → отброс; схема ≠ http/https → отброс; homepage без path → отброс; служебный path/extension/tracking (substring match через patterns, case-insensitive) → отброс; дедупликация через `LinkedHashSet` для сохранения порядка. O(N), без regex, без HTTP. KDoc — ссылка на FR-004 спеки и data-model.md
- [X] T007 Создать файл `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/llm/ToolsTest.kt` с пустым классом `internal class ToolsTest` (по образцу `AlbumCoverFinderParsingTest` — JUnit5, кириллические имена в backticks, НЕ `@Disabled`)
- [X] T008 [P] [US2] Добавить unit-тест `filter отбрасывает невалидный URL` в `ToolsTest.kt`: вход `["not a url", "http://", "https://example.com/song"]` → ожидаемо `["https://example.com/song"]`. Покрывает правило FR-004 п.1
- [X] T009 [P] [US2] Добавить unit-тест `filter отбрасывает неподдерживаемые схемы` в `ToolsTest.kt`: вход `["ftp://example.com", "mailto:u@e.com", "javascript:alert(1)", "https://example.com"]` → ожидаемо `["https://example.com"]`. Покрывает FR-004 п.2
- [X] T010 [P] [US2] Добавить unit-тест `filter отбрасывает homepage без path` в `ToolsTest.kt`: вход `["https://example.com", "https://example.com/", "https://example.com/page"]` → ожидаемо `["https://example.com/page"]`. Покрывает FR-004 п.3
- [X] T011 [P] [US2] Добавить unit-тест `filter отбрасывает служебные path` в `ToolsTest.kt`: вход `["https://example.com/login", "https://example.com/wp-login.php", "https://example.com/sitemap.xml", "https://example.com/feed", "https://example.com/Search", "https://example.com/real-song"]` → ожидаемо `["https://example.com/real-song"]`. Покрывает FR-004 п.4 (case-insensitive)
- [X] T012 [P] [US2] Добавить unit-тест `filter отбрасывает файлы по расширению` в `ToolsTest.kt`: вход `["https://example.com/song.pdf", "https://example.com/track.mp3", "https://example.com/cover.jpg", "https://example.com/page"]` → ожидаемо `["https://example.com/page"]`. Покрывает FR-004 п.5
- [X] T013 [P] [US2] Добавить unit-тест `filter отбрасывает tracking-маркеры` в `ToolsTest.kt`: вход `["https://example.com/song?utm_source=vk", "https://example.com/song?fbclid=abc", "https://example.com/song?id=12345", "https://example.com/song?page=2"]` → ожидаемо `["https://example.com/song?id=12345", "https://example.com/song?page=2"]`. Покрывает FR-004 п.6 (НЕ трогает легитимные query-параметры)
- [X] T014 [P] [US2] Добавить unit-тест `filter дедуплицирует сохраняя порядок` в `ToolsTest.kt`: вход `["https://a.com/x", "https://b.com/y", "https://a.com/x"]` → ожидаемо `["https://a.com/x", "https://b.com/y"]`. Покрывает FR-004 п.7
- [X] T015 [P] [US2] Добавить unit-тест `filter happy path — все URL чистые` в `ToolsTest.kt`: вход из 5 нормальных URL → ожидаемо все 5 в исходном порядке
- [X] T016 [P] [US2] Добавить unit-тест `filter edge case — пустой вход` в `ToolsTest.kt`: вход `[]` → ожидаемо `[]`
- [X] T017 [P] [US2] Добавить unit-тест `filter edge case — все URL мусор` в `ToolsTest.kt`: вход `["https://example.com/", "https://example.com/login", "https://example.com/sitemap.xml"]` → ожидаемо `[]` (провоцирует fallback в US1)
- [X] T018 Запустить `./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.llm.ToolsTest"` — все 10 тестов MUST проходить. Если падают — фиксить `filterUselessLyricsUrls` (T006) до зелёного состояния
- [X] T019 Запустить `./gradlew :karaoke-app:compileKotlin` — компиляция должна пройти без ошибок и предупреждений (warning-as-error включён по умолчанию в Karaoke)
- [X] T020 Запустить `./gradlew :karaoke-app:ktlintCheck` — никаких НОВЫХ нарушений (baseline OK)

**Checkpoint**: `filterUselessLyricsUrls` работает, покрыт тестами. User stories US1+US2 могут стартовать.

---

## Phase 3: User Story 1 — Порядок scrapers `yep` → `brave` + fallback по порогу (Priority: P1) 🎯 MVP

**Goal**: Изменить порядок scrapers в `SearchTool.searchUrls` с `["brave", "yep"]`
на `["yep", "brave"]` (читается из `KaraokeProperties`), добавить порог
`lyricsSearchMinResults` — если scraper вернул ≤N URL **после post-filter**,
пробовать следующий. Без post-filter (только проверка isNotEmpty) — это
**MVP US1**; с post-filter — полная версия US1+US2 (см. US2).

**Independent Test**: Запустить `SearchTool.searchUrls("Кино Группа крови текст")`
через integration-тест (или вручную через UI поиска текста). В логах:
- Если первый scraper (`yep`) дал 5+ URL → только одна строка `🔍 Запрос к fourget (scraper=yep)`.
- Если первый scraper дал ≤`lyricsSearchMinResults` URL → видны последовательные запросы к `yep` → `brave`.
- Если оба дали мало → `emptyList()`.

### Tests for User Story 1 (опционально — при наличии integration-окружения)

> **NOTE**: Эти тесты — интеграционные (требуют запущенный `fourget`).
> По политике Karaoke тесты в CI нет (см. Constitution), но они могут
> быть полезны для ручной проверки пользователем на admin-машине.

- [ ] T021 [P] [US1] (опционально) Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/llm/SearchToolFallbackTest.kt` с интеграционным тестом, который запускает локальный mock fourget (Testcontainers или простой HttpServer) и проверяет порядок fallback. **Если mock-инфраструктура недоступна** — пропустить T021 и полагаться на ручную проверку по quickstart.md

### Implementation for User Story 1

- [X] T022 [US1] Модифицировать `companion object` `SearchTool` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt`: удалить `private val LYRICS_SEARCH_SCRAPERS = listOf("brave", "yep")` (больше не используется — заменено на `lyricsSearchScrapersList()` helper из T004)
- [X] T023 [US1] Модифицировать `fun searchUrls(query: String): List<String>` в `SearchTool` (Tools.kt:43-49): заменить `for (scraper in LYRICS_SEARCH_SCRAPERS)` на `for (scraper in lyricsSearchScrapersList())`; добавить чтение `val minResults = KaraokeProperties.getInt("lyricsSearchMinResults").coerceAtLeast(0)` перед циклом; после `val urls = searchUrlsViaScraper(query, scraper)` добавить `val filteredUrls = filterUselessLyricsUrls(urls)`; проверка порога: `if (filteredUrls.size >= minResults) return filteredUrls` (вместо `if (urls.isNotEmpty()) return urls`). KDoc — обновить описание (новый порядок, ссылка на FR-002 спеки)
- [X] T024 [US1] Обновить KDoc класса `SearchTool` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt` (строки 16-25): заменить «используются реально рабочие на этом хостинге `brave` (основной) с фолбэком на `yep`» на «используются реально рабочие на этом хостинге `yep` (основной) с фолбэком на `brave`»; добавить ссылку на новое свойство `KaraokeProperties.lyricsSearchScrapers` (порядок настраивается); добавить упоминание post-filter через `filterUselessLyricsUrls`
- [X] T025 [US1] Запустить `./gradlew :karaoke-app:compileKotlin` — компиляция без ошибок
- [X] T026 [US1] Запустить `./gradlew :karaoke-app:ktlintCheck` — никаких НОВЫХ нарушений baseline
- [X] T027 [US1] Запустить `./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.llm.ToolsTest"` — все тесты US2 по-прежнему проходят (US2 не сломан US1)

**Checkpoint**: US1 (порядок scrapers + fallback по порогу) работает.

---

## Phase 4: User Story 2 — Post-filter «мусорных» URL в `searchUrlsViaScraper` (Priority: P1)

**Goal**: Добавить post-filter (`filterUselessLyricsUrls`) в
`searchUrlsViaScraper` **между парсингом JSON и возвратом списка** + логирование
статистики `🔧 [SearchTool] post-filter: было N, осталось M (отброшено K)`.
Также добавить post-filter в **начало** `searchUrls` (между scrapers), если
US1 его ещё не сделал (зависит от порядка реализации — описано ниже).

**Замечание**: US2 **сильно пересекается** с US1. Логика post-filter
применяется **в двух местах**:
1. Внутри `searchUrlsViaScraper` (T028 ниже) — фильтрация после каждого
   HTTP-запроса к fourget, **до** оценки порога в US1.
2. В `searchUrls` (T023 из US1 уже включает это через `filteredUrls`) —
   итоговый post-filter (на случай если US1 не применил filter внутри
   scraper-метода).

**Архитектурное решение**: post-filter применяется **внутри**
`searchUrlsViaScraper` (T028). US1 (T023) получает уже отфильтрованные
URL. Это согласуется с Clarifications Q3 спеки — «сначала post-filter,
потом оценка порога».

**Independent Test**: Запустить поиск на запрос, который ранее возвращал
мусорные URL от brave. В логах должна появиться строка `🔧 post-filter:
было N, осталось M (отброшено K)` с `K > 0`. Если `K == 0` — это легитимный
случай (scraper вернул «чистые» URL); повторить на другом запросе.

### Implementation for User Story 2

- [X] T028 [US2] Модифицировать `private fun searchUrlsViaScraper(query: String, scraper: String): List<String>` в `SearchTool` (Tools.kt:95-137): после строки `val urls = searchResponse.web.map { it.url }.filter { it.isNotBlank() }` (строка 127) вставить:
  ```kotlin
  val filteredUrls = filterUselessLyricsUrls(urls)
  logger.info("🔧 [SearchTool] post-filter: было ${urls.size}, осталось ${filteredUrls.size} (отброшено ${urls.size - filteredUrls.size})")
  return filteredUrls
  ```
  (вместо `return urls`). KDoc существующей функции — обновить: добавить ссылку на `filterUselessLyricsUrls` и FR-006 спеки (логирование статистики)
- [X] T029 [US2] Обновить строку лога `✅ [SearchTool] scraper=$scraper — найдено URL: ${urls.size}` (Tools.kt:129): заменить `urls.size` на `filteredUrls.size` для консистентности (post-filter применяется ДО этой строки в T028, поэтому нужно обновить порядок строк — сначала `✅`, потом post-filter и `return` — пересмотреть T028 при реализации). **Уточнение**: можно оставить старую строку как `✅ ... найдено URL: ${urls.size}` (raw), а ниже добавить `🔧 post-filter` — это даёт в логах обе метрики (raw и filtered), что полезно для SC-005
- [X] T030 [US2] Запустить `./gradlew :karaoke-app:compileKotlin` — компиляция без ошибок
- [X] T031 [US2] Запустить `./gradlew :karaoke-app:ktlintCheck` — никаких НОВЫХ нарушений baseline
- [X] T032 [US2] Запустить `./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.llm.ToolsTest"` — все тесты по-прежнему проходят

**Checkpoint**: US1+US2 полностью работают. Поиск текстов песен стартует с `yep`, fallback на `brave` при пороге ≤2, мусорные URL отфильтровываются.

---

## Phase 5: User Story 3 — Расширяемость списка scrapers через `KaraokeProperties` (Priority: P2)

**Goal**: Подтвердить, что изменение `lyricsSearchScrapers` через БД/UI
меняет поведение без передеплоя (как уже работает для `lyricsSearchEngine`).
Никакого нового кода — только верификация через smoke-тест.

**Independent Test**: Через UI настроек Karaoke (или прямой UPDATE в БД)
поменять `lyricsSearchScrapers` на `["brave"]`. Отправить поисковый
запрос. В логах — только `scraper=brave`. Восстановить обратно.

### Implementation for User Story 3

- [ ] T033 [US3] Верифицировать через quickstart.md шаг 4 («Проверить fallback на brave при «мало URL от yep»»): добавить в `lyricsSearchScrapers` тестовое значение через UI, проверить, что порядок изменился в логах без рестарта `karaoke-app`. Восстановить исходное значение
- [ ] T034 [US3] Верифицировать через quickstart.md шаг 3 («Проверить fallback на brave при деградации yep»): добавить `mojeek` (нерабочий scraper) первым в `lyricsSearchScrapers`, проверить, что итеративный fallback пропускает `mojeek` и переходит к `yep`. Восстановить
- [ ] T035 [US3] Верифицировать через quickstart.md AC-US3.1: установить `lyricsSearchScrapers = "brave"` → проверить, что в логах только `scraper=brave` без рестарта `karaoke-app`. Восстановить

**Checkpoint**: US3 подтверждён — hot-fix через `KaraokeProperties` работает.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Финальные улучшения, документация, верификация.

- [X] T036 [P] Обновить `archive/docs/features/llm-lyrics-search.md` (FR-008 спеки): добавить раздел «Post-filter мусорных URL» с описанием правил FR-004 + ссылку на спеку 294. Также обновить таблицу scrapers, если в списке появились изменения (не должно быть — порядок остался `yep;brave`, но убедиться)
- [X] T037 [P] Запустить `./gradlew ktlintCheck` (полный, не только `:karaoke-app`) — никаких НОВЫХ нарушений во всём проекте
- [X] T038 [P] Запустить `bash tools/check-kdoc-coverage.sh` — KDoc coverage ≥100% (требование Constitution §VI FR-006). Убедиться, что новые функции (`filterUselessLyricsUrls`, `lyricsSearchScrapersList`, `uselessUrlPatternsList`) имеют KDoc
- [X] T039 Запустить `bash tools/check-eslint-baseline.sh webvue3` и `bash tools/check-eslint-baseline.sh karaoke-public` — никаких НОВЫХ нарушений baseline (т.к. фронт не менялся, baseline не должен расти)
- [X] T040 Запустить `pre-commit run --all-files` — все проверки проходят (см. CLAUDE.md «🚦 ОБЯЗАТЕЛЬНО перед каждым `git commit`»)
- [X] T041 Выполнить сборку backend: `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel` (см. AGENTS.md «Сборка / деплой / тесты»). На `nsa-i9`/`nsa` — оба bootJar обязательны
- [X] T042 Выполнить сборку frontend (формальная проверка, не менялся): `cd webvue3 && npm run build && npm run format:check && cd ..` и `cd karaoke-public && npm run build && npm run format:check && cd ..`
- [ ] T043 (опционально) По согласованию с пользователем — пересобрать Docker-образы: `cd deploy && bash do.sh build_webvue3` (формальная проверка; образ не менялся, но шаг AGENTS.md требует). `karaoke-public` Docker-образ не пересобирается (там только изменение в `karaoke-app`)
- [ ] T044 (опционально) По согласованию с пользователем — выполнить quickstart.md сценарии AC-US1.1, AC-US1.2, AC-US1.3 на admin-машине для финальной приёмки перед PR
- [X] T045 Создать коммит на ветке `294-fourget-scraper-order` с сообщением в стиле `lyrics-search: новый порядок scrapers + post-filter (294)` и отправить PR через `gh pr create --base master`. Дождаться `gh pr checks` (CI 7/7 PASS) и смерджить через `gh pr merge --merge` (без `--delete-branch` — lifecycle правило AGENTS.md)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: T001-T005. T002-T005 параллельны (разные top-level константы/helper'ы в одном файле, но без зависимостей между собой — `[P]`). T001 — отдельный файл, можно делать параллельно с T002-T005
- **Phase 2 (Foundational)**: T006-T020. T006 — реализация функции (зависит от T003, T004, T005 из Phase 1). T007 — создание файла тестов (зависит от T006 — нужен `filterUselessLyricsUrls` для компиляции тестов). T008-T017 — тесты `[P]` между собой (все пишут в `ToolsTest.kt`, но каждый `@Test` — отдельный метод, можно делать параллельно в редакторе). T018-T020 — verification шаги, последовательно
- **Phase 3 (US1)**: T022-T027. Зависит от Phase 2 (нужны `filterUselessLyricsUrls`, `lyricsSearchScrapersList()`, новое свойство `KaraokeProperties`)
- **Phase 4 (US2)**: T028-T032. Зависит от Phase 3 (US1 модифицирует `searchUrls`, US2 модифицирует `searchUrlsViaScraper` — нужна согласованность)
- **Phase 5 (US3)**: T033-T035. Зависит от Phase 4 (нужны все изменения работающими). Это только верификация — нет нового кода
- **Phase 6 (Polish)**: T036-T045. Зависит от Phase 5

### User Story Dependencies

- **US1 (P1)**: Depends on Phase 2 (Foundational — `filterUselessLyricsUrls`). **MVP** — можно деплоить после US1+US2 (минимально работающая фича).
- **US2 (P1)**: Depends on US1 (использует `filterUselessLyricsUrls`, встроенный в `searchUrls` через `searchUrlsViaScraper`). Должна идти сразу после US1.
- **US3 (P2)**: Depends on US1+US2 (нужны все изменения, чтобы верифицировать hot-fix через `KaraokeProperties`).

### Within Each User Story

- **US1**: T022 → T023 → T024 (последовательно — все правят `Tools.kt`). T025-T027 — verification шаги.
- **US2**: T028 → T029 (последовательно, T029 уточняет T028). T030-T032 — verification.
- **US3**: T033-T035 — последовательная верификация (smoke-тесты на admin-машине).

### Parallel Opportunities

В пределах **одной фазы** можно делать параллельно:
- **Phase 1**: T002, T003, T004, T005 — `[P]` (разные top-level сущности в `Tools.kt`, нет кросс-зависимостей в коде, только в KDoc/порядке объявления).
- **Phase 2**: T008-T017 (10 unit-тестов) — `[P]` друг с другом (каждый `@Test` — независимый метод, нет cross-test dependencies). Можно создать одним коммитом.
- **Phase 6**: T037, T038, T039 — `[P]` (разные инструменты, разные области кода).

Между **фазами** параллелизм ограничен dependency chain:
- После Phase 2 → Phase 3 + Phase 4 могут стартовать последовательно (T022 US1 → T028 US2), но не параллельно (правят один файл).
- Phase 5 (US3) — только верификация, не блокируется другими stories.

---

## Parallel Example: User Story 1 + User Story 2

Хотя US1 и US2 правят один файл (`Tools.kt`), их удобно делать **в одном
коммите** в порядке T022-T024 (US1) → T028-T029 (US2), чтобы избежать
неконсистентного состояния (где US1 использует `filterUselessLyricsUrls`
в `searchUrls`, но `searchUrlsViaScraper` ещё не фильтрует).

```bash
# Phase 2: всё вместе (один коммит "tools: filterUselessLyricsUrls + 10 unit-тестов")
T006 + T007 + T008-T017 + T018-T020

# Phase 3+4: последовательно (один коммит "lyrics-search: scraper order + post-filter (294)")
T022 → T023 → T024 → T028 → T029 + T025-T027 + T030-T032

# Phase 6: Polish (один коммит или несколько мелких)
T036 → T037-T045
```

---

## Implementation Strategy

### MVP First (US1 + US2 — оба P1)

1. Complete Phase 1: Setup (T001-T005)
2. Complete Phase 2: Foundational (T006-T020) — `filterUselessLyricsUrls` + тесты
3. Complete Phase 3: US1 (T022-T027) — порядок scrapers + fallback
4. Complete Phase 4: US2 (T028-T032) — post-filter в `searchUrlsViaScraper`
5. **STOP and VALIDATE**: запустить quickstart.md AC-US1.1, AC-US1.2, AC-US1.3 на admin-машине
6. Deploy/demo — MVP готов

### Incremental Delivery

1. Setup + Foundational → foundation ready (Phase 1+2)
2. **MVP = US1+US2** (один PR, обе P1) → Deploy
3. **US3** (Phase 5) → верификация hot-fix через `KaraokeProperties` (можно
   делать в том же PR, если US3 — это просто smoke-тест)
4. **Polish** (Phase 6) → документация, CI, Docker, merge

### Parallel Team Strategy

Не применимо — фича маленькая, всё делается одним разработчиком за
1-2 коммита. Если команда >1 человека, можно параллелить:
- Developer A: Phase 1+2 (`Tools.kt` + `KaraokeProperties.kt` + `ToolsTest.kt`)
- Developer B: Phase 6 документация (`archive/docs/features/llm-lyrics-search.md`)

---

## Notes

- [P] задачи = разные файлы или разные сущности в одном файле без зависимостей
- [Story] метка (US1/US2/US3) обеспечивает трассируемость к спеку
- Каждая user story **должна** быть независимо завершаемой и тестируемой
- Тесты пишутся в Phase 2 ДО интеграции в Phase 3+ (test-first подход
  для `filterUselessLyricsUrls`)
- Коммит после каждой фазы или логической группы (T001-T005 одним
  коммитом, T006-T020 другим, T022-T032 третьим)
- Stop на любом checkpoint для валидации story независимо
- Избегать: расплывчатых задач, конфликтов в одном файле,
  cross-story зависимостей, ломающих независимость
- **NFR-006 спеки**: `AlbumCoverFinder.kt` НЕ трогаем — обложки работают
  как раньше через `scraper=brave` без fallback
- **AGENTS.md** «Машинно-специфичные исключения»: на `nsa-i9`/`nsa` можно
  пересобирать `karaoke-app:bootJar` без явного согласия, но **НЕ**
  перезапускать контейнер. Деплой на прод — только по согласию
- **Constitution §VI FR-006**: KDoc 100% для новых публичных/internal API —
  проверено в T038
- **Constitution §VIII**: никаких секретов в коде/настройках этой фичи —
  pre-commit проверка `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'`
  должна быть пустой (T040)
