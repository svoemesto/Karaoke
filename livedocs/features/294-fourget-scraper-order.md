---
status: Active
slug: 294-fourget-scraper-order
related:
  - ../domain/editorial.md
  - ../features/014-lyrics-search-replacement.md
  - ../features/015-search-engine-selection.md
  - ../../specs/294-fourget-scraper-order/spec.md
  - ../../archive/docs/features/llm-lyrics-search.md
---

# 294 (lyrics-search) — Новый порядок scrapers fourget + post-filter «мусорных» URL (LiveDoc)

> Drill-down — [specs/294-fourget-scraper-order/spec.md](../../specs/294-fourget-scraper-order/spec.md).

## Что делает

Решает две связанные проблемы `SearchTool.searchUrls`:

1. **Brave стал деградировать** — в логах прод регулярно появлялось
   `status='Brave did not return a result object'`. Решение — поменять порядок
   scrapers: **`yep` основной → `brave` fallback** (было наоборот).
2. **Мусор в ответах scraper'ов** — даже при `status=ok` `web[]` часто
   содержит homepage/sitemap/login-страницы, на которые LLM-парсер
   (`ScraperAgent`) тратит токены впустую. Решение — новая функция
   `filterUselessLyricsUrls` отбрасывает заведомо бесполезные URL
   **до** оценки количества результатов.

**Эффект**: больше успешных поисков текста песни (выше hit rate),
меньше шума в логах, меньше wasted LLM-запросов на парсинг мусора.

## User Stories (краткий список)

- **US1** (P1): Новый порядок scrapers — yep → brave. Fallback по порогу
  `lyricsSearchMinResults` (default 2): если scraper вернул меньше URL
  после post-filter, пробуем следующий.
- **US2** (P1): Post-filter «мусорных» URL в `searchUrlsViaScraper` —
  homepage, sitemap, login, файлы (.pdf/.mp3/...), tracking-маркеры
  (`utm_*`, `fbclid`, ...). Дедупликация.
- **US3** (P2): Hot-fix через `KaraokeProperties` без передеплоя —
  3 новых ключа в настройках.

## Functional Requirements (указатель)

- **FR-001**: `LYRICS_SEARCH_SCRAPERS` → порядок `["yep", "brave"]`.
- **FR-002**: Порог `lyricsSearchMinResults` (default 2) в `KaraokeProperties`.
- **FR-003**: Функция `filterUselessLyricsUrls` в `Tools.kt` (top-level internal).
- **FR-004**: 7 правил post-filter (невалидный URL / схема ≠ http/https /
  homepage без path / служебные path / файловые расширения /
  tracking-маркеры / дедупликация).
- **FR-006**: Новая строка лога `🔧 [SearchTool] post-filter: было N,
  осталось M (отброшено K)` на INFO для мониторинга.
- **FR-007**: 3 новых ключа в `KaraokeProperties`:
  `lyricsSearchScrapers`, `lyricsSearchMinResults`,
  `lyricsSearchUselessUrlPatterns`.

## Acceptance Criteria

- [x] **AC1**: 10/10 unit-тестов `ToolsTest` PASS.
- [x] **AC2**: `:karaoke-app:ktlintCheck` PASS (никаких НОВЫХ нарушений).
- [x] **AC3**: KDoc coverage ≥ 50% (фактически 96.3%).
- [ ] **AC4** (US3 smoke, требует admin-машину): Изменение
  `lyricsSearchScrapers` через UI/БД без рестарта отражается в логах
  в течение ≤1 минуты.
- [ ] **AC5** (после деплоя, 7 дней мониторинга): доля `(scraper=brave)`
  снижается на ≥60% (SC-001 спеки), ошибки `Brave did not return a
  result object` падают до ≤5% (SC-002), доля поисков с post-filter
  `K > 0` в диапазоне 5-30% (SC-005).

## Curl-перебор 2026-09-02 (актуальное состояние 4get 1.0.44)

Перед деплоем проведён повторный curl-перебор всех 14 известных scrapers.
**Ключевая находка**: `yep` **деградировал** (`status=ok`, `web=[]` тихо
на любой запрос), `brave` **восстановился** (20-30 URL на lyrics).

Архитектура фичи (post-filter + fallback по порогу) **корректно
обрабатывает** эту ситуацию: первый scraper тихо возвращает 0 URL →
fallback на второй → результат. Никаких изменений кода не требуется.

Альтернативы (`mojeek`, `startpage`, `qwant`, `wikipedia`) — все
по-прежнему нерабочие. Подробности — в
[specs/294-fourget-scraper-order/research.md](../../specs/294-fourget-scraper-order/research.md).

## Связанные LiveDocs

- Domain: [editorial.md](../domain/editorial.md) (тексты).
- LiveDoc: [014-lyrics-search-replacement.md](../features/014-lyrics-search-replacement.md)
  (выбор 4get как движка).
- LiveDoc: [015-search-engine-selection.md](../features/015-search-engine-selection.md)
  (выбор движка в UI).
- Spec: [specs/294-fourget-scraper-order/spec.md](../../specs/294-fourget-scraper-order/spec.md).
- Archive: [archive/docs/features/llm-lyrics-search.md](../../archive/docs/features/llm-lyrics-search.md)
  (общая документация LLM-поиска, обновлена в этом PR).
