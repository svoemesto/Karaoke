# LiveDocs — Changelog

> История значимых изменений LiveDocs (Pass 62+, 2026-08-14+).
> Отличие от `git log`: **семантический changelog** — что описывается
> сейчас в LiveDocs (для AI-агента, чтобы понять актуальное состояние).
>
> Конвенция: **одна секция = один PR**. Записи retroactively собраны из
> известной истории; впредь каждое изменение LiveDocs дописывается новой
> секцией.
>
> **См. также**: `git log livedocs/` — для построчной истории.

## 2026-08-14 — bootstrap + 80 features

- **Создан** каталог `livedocs/` со всеми слоями (`features/`, `domain/`,
  `architecture/`, `templates/`, `runbooks/`).
- **Манифесты**: `livedocs/README.md`, `livedocs/INDEX.md` (decision tree).
- **75+ сводок фич** мигрированы из `specs/NNN-slug/spec.md` в
  `livedocs/features/<NNN-slug>.md`. Каждая ≤ 2 страницы.
- **5 шаблонов** в `templates/` (feature-summary, bounded-context, c4-level-{L1,L2,L3}).
- **CI-валидация**: `tools/check-livedocs-structure.sh` — 7 проверок.
- **Сокращён `AGENTS.md`** с 230 до 100 строк (правило «LiveDocs первым»).

## 2026-08-14 — 4 спеки-дубликата (полная миграция)

- Added 4 сводки для спек с дубликатами по NNN: `017-fix-markers-at-position-zero`,
  `154-remove-scheduled-publications-monitoring`, `155-song-state-colors`,
  `156-publish-slots-range`.
- Все 75+ уникальных спек теперь в LiveDocs.

## 2026-08-14 — depth #1 (ADR + dual-db-access)

- **Added ADR-0001**: Сырой JDBC без JPA/Hibernate для доступа к БД.
- **Added ADR-0002**: MLT/melt как стек для караоке-видео.
- **Added ADR-0003**: LiveDocs = Markdown + YAML + Mermaid (не MkDocs/Docusaurus).
- **Added**: `livedocs/architecture/decisions/` (новый под-каталог).
- **Added topic**: `dual-db-access.md` (drill-down для JDBC).
- CI: `check-livedocs-structure.sh` — исключение для `*/decisions/*`.

## 2026-08-14 — depth #2 (3 topic)

- **Added topic**: `mlt-pipeline.md` (детальный MLT-генератор, mko, Playwright, ~150 параметров KaraokeProperties).
- **Added topic**: `concurrent-editing.md` (OptimisticConcurrency + `tbl_audits` + `VoteEnd`).
- **Added topic**: `nginx-conventions.md` (конфиг 80to8897, User-Agent routing, NDJSON proxy_buffering).

## 2026-08-14 — depth #3 (3 кросс-cut topic)

- **Added topic**: `observability.md` (SSE + heartbeat + self-healing — observability patterns).
- **Added topic**: `cache-invalidation.md` (`setWebvueProp` + Vuex + SSE — кросс-клиентский кэш).
- **Added topic**: `idempotency.md` (5 стратегий: Idempotency-Key, UNIQUE, optimistic, lease, async job).

## 2026-08-14 — depth #4 (2 bounded contexts)

- **Added BC**: `stats` (StatBySong, tbl_events, StatsCacheScheduler, visitor/bot-сегментация, funnel).
- **Added BC**: `rendering` (drill-down для processing — MP4-видео через MLT/melt/Playwright).
- Всего 7 bounded contexts: catalog, processing, rendering, publishing, stats, identity, editorial.

## 2026-08-14 — depth #6 (6 runbooks)

- **Added**: `livedocs/runbooks/` (новый слой для операционных how-to).
- **Added**: `README.md` + 6 how-to:
  - `how-to-deploy.md` — деплой web/public на прод (rsync + nginx + smoke-test).
  - `how-to-migrate-db.md` — SQL-миграция (SyncRegistry + recordhash).
  - `how-to-add-new-feature.md` — SDD workflow (/speckit.specify→.plan→.tasks→implement).
  - `how-to-debug-connection-leak.md` — pg_stat_activity + releaseForThisThread.
  - `how-to-add-new-domain.md` — новый bounded context.
  - `how-to-update-livedocs.md` — как sync кода с LiveDocs при изменениях.
- CI: `check-livedocs-structure.sh` — исключение для `*/runbooks/*`.

## 2026-08-14 — depth #7 (cross-link validator + fix 196 broken)

- **Added**: `tools/check-livedocs-cross-links.sh` — проверяет относительные
  пути `../X.md` и `related:` в frontmatter.
- **Fixed**: 196 broken references по всем LiveDocs (sed-batches).
- CI: `lint.yml` — новый шаг `Check LiveDocs cross-links`.
- **Total: 814 cross-links valid, 0 broken** на момент merge.

## 2026-08-14 — depth #8 (3 ADR)

- **Added ADR-0004**: KaraokeApp — только на admin-машине, не на проде.
- **Added ADR-0005**: Self-hosted ML (Ollama + SearXNG + Sheetsage + Demucs) вместо SaaS.
- **Added ADR-0006**: ProcessBuilder + redirectErrorStream(true) для async-задач.
- Всего 6 ADR.

## 2026-08-14 — depth #9 (discoverability tool)

- **Added**: `tools/search-livedocs.sh` — grep wrapper для AI-агентов.
- Использование: `bash tools/search-livedocs.sh 'query' [--type TYPE] [--path SUBPATH]`.

## 2026-08-14 — depth #10 (lychee external-links)

- **Added**: новый job `livedocs-external-links` в `.github/workflows/lint.yml`.
- Проверяет ВНЕШНИЕ ссылки (https://, ...) в LiveDocs через lychee в `--offline`.
- `advisory` (continue-on-error=true). Strict в Pass 17+.

## Состояние на сегодня

| Метрика | Значение |
|---------|----------|
| **Фичи в `features/`** | 84 |
| **Bounded contexts в `domain/`** | 7 |
| **C4 уровни** | 3 (L1, L2, L3) |
| **Topic-документов в `architecture/`** | 11 |
| **ADR** | 6 |
| **Runbooks** | 7 (README + 6 how-to) |
| **Шаблонов в `templates/`** | 6 |
| **`frontmatter`-файлов (с валидным frontmatter)** | 107 |
| **`total .md` файлов** | ~120 |
| **Cross-links valid** | 814 |
| **Broken references** | 0 |
| **AGENTS.md** | ≤ 100 строк ✓ |
| **CI проверок LiveDocs** | 7/7 + cross-links 0/814 broken + lychee advisory |
| **Миграция покрытия спек** | 100% (все 75+ уникальных) |

## Как использовать этот changelog

- AI-агент: «Когда что появилось? Что недавно добавилось? Какие ADR актуальны?»
  → читать этот файл (после `livedocs/README.md` + `INDEX.md`).
- Разработчик (человек): «Что нового в LiveDocs?» → верх этого файла.
- Git / code review: новая секция changelog **в том же PR**, что и изменение.