# Implementation Plan: 294 — Порядок и состав scraper'ов fourget для поиска текстов песен

**Branch**: `294-fourget-scraper-order` | **Date**: 2026-09-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/294-fourget-scraper-order/spec.md`

## Summary

Фича меняет порядок scraper'ов в `SearchTool.LYRICS_SEARCH_SCRAPERS` с
`["brave", "yep"]` на `["yep", "brave"]` (приоритет более стабильному
`yep`), добавляет порог «качества» (`lyricsSearchMinResults`, дефолт 2)
и вводит post-filter `filterUselessLyricsUrls`, отбрасывающий
заведомо бесполезные URL (homepage, sitemap, login-страницы, файлы,
tracking-маркеры, дубликаты). Списки scrapers и паттернов filter'а
выносятся в `KaraokeProperties` для hot-fix без передеплоя. Поиск
обложек альбомов (`AlbumCoverFinder.kt`) **не затрагивается**.

Реализация — чистая Kotlin-функция `filterUselessLyricsUrls` в
`Tools.kt` (O(N) на размер списка URL, без regex, без HTTP), unit-тесты
по образцу `AlbumCoverFinderParsingTest`, новые ключи в
`listKaraokeProperties`. Без миграций БД, без изменений UI/фронтенда,
без изменений в docker-compose.

## Technical Context

**Language/Version**: Kotlin 1.x, JDK 17, Spring Boot 3.x (см.
Constitution §Технологический стек).

**Primary Dependencies**:
- `org.springframework.stereotype.Component` (`SearchTool` — существующий
  Spring bean);
- `org.slf4j.Logger` (текущее логирование);
- `java.net.URI`, `java.net.URLEncoder`, `java.net.http.HttpClient`,
  `java.net.http.HttpRequest`, `java.net.http.HttpResponse` (текущий
  HTTP-стек fourget);
- `com.fasterxml.jackson.databind.ObjectMapper` (парсинг JSON);
- `com.svoemesto.karaokeapp.KaraokeProperties` (чтение настроек);
- JUnit5 `org.junit.jupiter.api.Test` + `Assertions.*` (тесты).

**Storage**: N/A (изменений в БД нет, `Karaoke.properties` — JSON-lines,
base64, на файловой системе).

**Testing**: JUnit5 (активные unit-тесты, паттерн
`AlbumCoverFinderParsingTest`). По политике Karaoke — тесты НЕ в CI
(см. Constitution §Рабочий процесс), проверка пользователем. Тесты
для `filterUselessLyricsUrls` НЕ `@Disabled` — это исправление ошибки в
FR-009 спеки (только `PlaywrightTests.kt` `@Disabled` в Karaoke).

**Target Platform**: Linux server (admin-машина, прод-сервер через
`karaoke-web`).

**Project Type**: Web-service (Spring Boot backend, существующий модуль
`karaoke-app`).

**Performance Goals**: post-filter ≤1 мс на список из 50 URL (NFR-005);
latency «хорошего» поиска не меняется (один scraper с 5+ URL → один
HTTP-запрос).

**Constraints**:
- Никаких новых внешних зависимостей;
- Внешний контракт `SearchTool.searchUrls` сохраняется
  (downstream-потребители не ломаются);
- Без миграций БД;
- Соответствие Constitution §VI FR-006 (100% KDoc coverage для
  публичных API + helpers);
- Соответствие Constitution §VI FR-009 (обновить
  `docs/features/llm-lyrics-search.md` в том же PR, если файл
  существует — сейчас его нет в `docs/features/`, есть только в
  `archive/docs/features/`, см. `data-model.md` § Out of Scope).

**Scale/Scope**: один файл `Tools.kt` (≤50 строк правок), один файл
`KaraokeProperties.kt` (3 новых ключа), один файл тестов
`ToolsTest.kt` (новый, ~150 строк). Никаких изменений в других модулях.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Соответствие | Комментарий |
|---|---|---|
| **I. Self-contained автопайплайн** | ✅ | Используется существующий self-hosted fourget; никаких внешних SaaS. |
| **II. Сырой JDBC + дифф по хэшам** | ✅ N/A | Никаких изменений в БД/JDBC. |
| **III. Двух-БД синхронизация через SyncRegistry** | ✅ N/A | Никаких изменений в БД/sync. |
| **IV. Async-очередь задач с парсингом stdout** | ✅ N/A | Не затрагивается. |
| **V. Двух-фронтенд** | ✅ N/A | Никаких изменений в `webvue3`/`karaoke-public`. |
| **VI. Code Standards** | ✅ | FR-006 (KDoc 100%) — соблюдается в `Tools.kt` (KDoc для `filterUselessLyricsUrls`, обновлённый KDoc для `SearchTool`); FR-007 (ktlintCheck) — соблюдается через `tools/check-eslint-baseline.sh` (не регрессирует baseline); FR-009 (per-feature документ) — `archive/docs/features/llm-lyrics-search.md` существует, обновляется в том же PR. |
| **VII. Cross-Machine Setup** | ✅ N/A | Локальная фича, нет cross-machine особенностей. |
| **VIII. Секреты и git-гигиена** | ✅ | Никаких секретов; никаких новых файлов в `.gitignore`; pre-commit проверка `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` останется пустой. |
| **Ограничения доступа агента** | ✅ | Агент **МОЖНО** пересобирать `karaoke-app` на `nsa-i9`/`nsa` (см. AGENTS.md «Машинно-специфичные исключения»). **НЕ** перезапускать контейнер — только по согласию пользователя. **НЕ** деплоить на прод. |

**Re-check после Phase 1 design**: Constitution Check остаётся без
изменений. Все ключи новых свойств в `KaraokeProperties` — НЕ секреты,
поэтому VIII.1-VIII.5 не применяются.

**Gate status**: ✅ **PASS** — никаких нарушений Constitution, все
изменения укладываются в разрешённые операции агента.

## Project Structure

### Documentation (this feature)

```text
specs/294-fourget-scraper-order/
├── plan.md              # Этот файл (/speckit.plan command output)
├── research.md          # Phase 0 output — decisions по Q1-Q6
├── data-model.md        # Phase 1 output — новые свойства + filter
├── quickstart.md        # Phase 1 output — validation guide
├── contracts/           # Phase 1 output — контракты searchUrls/filter/log
│   └── README.md
├── checklists/
│   └── requirements.md  # Quality checklist (created in /speckit.specify)
└── tasks.md             # Phase 2 output (created in /speckit.tasks — NOT this command)
```

### Source Code (repository root)

Изменения — только в трёх файлах:

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── llm/
│   └── Tools.kt                                          # MODIFY:
│                                                          #   - LYRICS_SEARCH_SCRAPERS → lyricsSearchScrapersList() (KaraokeProperties)
│                                                          #   - searchUrls: добавить порог minResults + фильтрацию
│                                                          #   - searchUrlsViaScraper: добавить post-filter + лог статистики
│                                                          #   - +filterUselessLyricsUrls() (top-level internal)
│                                                          #   - +DEFAULT_USELESS_URL_PATTERNS (top-level private)
│                                                          #   - +DEFAULT_LYRICS_SEARCH_SCRAPERS (top-level private)
│                                                          #   - Обновить KDoc класса SearchTool
└── KaraokeProperties.kt                                  # MODIFY:
                                                            #   - +3 ключа в listKaraokeProperties:
                                                            #       lyricsSearchScrapers, lyricsSearchMinResults,
                                                            #       lyricsSearchUselessUrlPatterns

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/llm/
└── ToolsTest.kt                                          # CREATE:
                                                            #   - Офлайн unit-тесты filterUselessLyricsUrls
                                                            #   - Паттерн: AlbumCoverFinderParsingTest
                                                            #   - JUnit5, кириллические имена в backticks, НЕ @Disabled
```

### Документация (Out of Scope для `docs/features/`, но в `archive/docs/features/`)

```text
archive/docs/features/
└── llm-lyrics-search.md                                  # MODIFY (опционально, по FR-008 спеки):
                                                            #   - Добавить раздел «Post-filter мусорных URL» (FR-004)
                                                            #   - Обновить таблицу scrapers (если менялся список)
```

**Structure Decision**: фича **изменяет только 2 production-файла
+ создаёт 1 тестовый файл + (опционально) обновляет 1 документационный
файл**. Никаких новых пакетов, модулей, директорий. Соответствует
существующей структуре `karaoke-app`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| (нет нарушений) | — | — |

**Все ключевые архитектурные решения задокументированы в [research.md](research.md)**
(6 решённых вопросов Q1-Q6, отклонённые альтернативы, риски).

## Implementation Steps (Phase 2 preview — НЕ выполняется здесь)

Сгенерированы в `/speckit.tasks`. Предварительная структура:

1. **`KaraokeProperties.kt`**: добавить 3 ключа в `listKaraokeProperties`
   (после строки ~202, рядом с `lyricsSearchEngine`).
2. **`Tools.kt`**: добавить `DEFAULT_USELESS_URL_PATTERNS`,
   `DEFAULT_LYRICS_SEARCH_SCRAPERS` (top-level private).
3. **`Tools.kt`**: добавить `filterUselessLyricsUrls` (top-level
   internal).
4. **`Tools.kt`**: добавить helper `lyricsSearchScrapersList()`
   (private, читает из `KaraokeProperties`).
5. **`Tools.kt`**: модифицировать `searchUrls` — заменить
   `LYRICS_SEARCH_SCRAPERS` на `lyricsSearchScrapersList()`, добавить
   post-filter + проверку порога.
6. **`Tools.kt`**: модифицировать `searchUrlsViaScraper` — добавить
   post-filter между парсингом JSON и возвратом + лог статистики.
7. **`Tools.kt`**: обновить KDoc класса `SearchTool` (новый порядок +
   ссылка на `lyricsSearchUselessUrlPatterns`).
8. **`ToolsTest.kt`** (новый): создать с тестами для
   `filterUselessLyricsUrls` (~10 кейсов по FR-004).
9. **`archive/docs/features/llm-lyrics-search.md`** (опционально):
   добавить раздел «Post-filter мусорных URL».
10. **Verification**: `./gradlew :karaoke-app:compileKotlin`,
    `./gradlew ktlintCheck`, `./gradlew :karaoke-app:test --tests
    "com.svoemesto.karaokeapp.llm.ToolsTest"`.

## Open Questions (для `/speckit.tasks` / реализации)

Нет открытых вопросов. Все 6 NEEDS CLARIFICATION из research.md
закрыты.

## References

- [spec.md](spec.md) — спецификация (FR-001..FR-009, NFR-001..NFR-006, SC-001..SC-006).
- [research.md](research.md) — 6 решённых вопросов + риски.
- [data-model.md](data-model.md) — модель данных (новые свойства + filter).
- [quickstart.md](quickstart.md) — validation guide.
- [contracts/README.md](contracts/README.md) — контракты.
- [checklists/requirements.md](checklists/requirements.md) — quality checklist.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt` — точка изменения.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:107-114` — паттерн для новых свойств.
- `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/AlbumCoverFinderParsingTest.kt` — образец unit-теста.
- `specs/014-lyrics-search-replacement/spec.md` — выбор 4get.
- `specs/014-lyrics-search-replacement/research.md` — таблица рабочих scrapers.
- `archive/docs/features/llm-lyrics-search.md` — общая документация LLM-поиска.
- `docs/ops/log-correlation.md` — карта логов для SC-001/SC-002/SC-005.
- Constitution v2.1.0 — стек, FR-006/FR-009.
