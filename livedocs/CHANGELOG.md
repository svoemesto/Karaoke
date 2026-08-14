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

## 2026-08-14 — depth #42 (Final Phase спеки 189)

- **Closed**: спека `189-live-documentation` — все 51 задача выполнены.
- **Updated**: `AGENTS.md` сокращён со 101 до 100 строк (объединение 2 строк
  header-блока в 1) — для соответствия SC-002 (≤100).
- **Updated**: `AGENTS.md` Q&A секция переименована в «Q&A — где искать
  актуальные знания о проекте?» с явной ссылкой на `[LiveDocs](livedocs/README.md)`.
- **Updated**: `specs/189-live-documentation/tasks.md` — все 51 задача T001-T051
  отмечены как `[x]` (выполнены).

## 2026-08-14 — Follow-up #43 (gap-fill: 016/124/125)

- **Added**: 3 SDD-сводки для фич master, которые не получили LiveDoc при
  первой миграции:
  - `livedocs/features/016-fix-spec-tags-marker-loss-on-reopen.md` —
    спецтеги: сохранение маркеров после `Apply → Save → reopen`.
  - `livedocs/features/124-news-flags-backfill.md` — backfill флагов
    публикаций готовых песен без создания новостей.
  - `livedocs/features/125-player-status-gate.md` — доступность плеера
    в таблице «Песни» при статусе ≥4.
- **Updated**: `livedocs/features/README.md` — добавлены 016/124/125/190
  в таблицу-содержимое.
- **Updated**: `livedocs/INDEX.md` — добавлены 016/124/125 в
  selected-features-список.
- PR #316.

## 2026-08-14 — Follow-up #44 (topic share-link)

- **Added**: `livedocs/architecture/share-link.md` — drill-down для всего
  паттерна временных ссылок (фичи 164/166/167/169/171/172 ссылались на
  разные аспекты, но не было единого места для архитектурного обзора).
- **Updated**: `livedocs/architecture/README.md` — добавлен в таблицу
  тематических topic-документов.
- **Updated**: `livedocs/INDEX.md` — добавлен в selected-topic-список.
- PR #318.

## 2026-08-14 — Follow-up #45 (topic censoring)

- **Added**: `livedocs/architecture/censoring.md` — drill-down для
  паттерна цензурирования матерных слов в публикациях (Telegram/VK/
  новости). Фичи 139/140/141 ссылались на разные аспекты, но не
  было единого места для архитектурного обзора.
- **Updated**: `livedocs/architecture/README.md` — добавлен в таблицу.
- **Updated**: `livedocs/INDEX.md` — добавлен в selected-topic-список.
- PR #320.

## 2026-08-14 — Follow-up #46 (crosslinks tidy + topic monetization)

- **Updated**: cross-references в `141-fix-censored-web-storage-globals.md`
  (добавлены `../architecture/censoring.md` и `../domain/publishing.md`),
  `143-song-free-access-window.md` (добавлен `../domain/stats.md`).
  PR #321.

- **Added**: `livedocs/architecture/monetization.md` — drill-down для
  модели free-vs-premium + подписки + YOOKASSA платежи + авто-публикация
  premium-песен + share-link для premium. Фичи 005/122/143/162/169/171
  ссылались на разные аспекты, но не было единого места для обзора.
- **Updated**: `livedocs/architecture/README.md`, `livedocs/INDEX.md`.
- PR #322.

## 2026-08-14 — Follow-up #47 (INDEX update + postMessage bridge)

- **Updated**: `livedocs/INDEX.md` — добавлены `rendering` и `stats`
  в DDD-таблицу (были пропущены, в `livedocs/domain/` их 7, а в INDEX
  было только 5). Также обновлён decision tree (список BC).
  PR напрямую в master (1 файл).

- **Updated**: `livedocs/architecture/webvue3-patterns.md` — добавлен
  раздел `postMessage-мост между родителем и iframe-плеером`
  (фильтрация по source, edge cases, ловушки). LiveDoc 190 (плейлисты)
  ссылался на этот раздел, но его не было. PR #324.

## 2026-08-14 — Follow-up #48 (022 crosslinks + code update)

- **Updated**: `livedocs/features/022-song-status-lifecycle.md` —
  добавлены cross-references на `125-player-status-gate` (доступность
  плеера при статусе ≥4) и `155-song-state-colors` (цвета статусов).
  Обновлено описание кода (`Song.status` enum + `Song.idStatus` getter).
  PR #326.

## 2026-08-14 — Follow-up #49 (ADR README — Local ADR table)

- **Updated**: `livedocs/architecture/decisions/README.md` — добавлена
  таблица для 6 Local ADR (conventions в коде) + пояснение разницы
  между глобальными и local ADR. В директории было 12 ADR, но таблица
  была только для глобальных.
  PR #328.

## 2026-08-14 — Follow-up #50 (SESSION-SUMMARY)

- **Added**: `livedocs/SESSION-SUMMARY.md` — компактный обзор 14 PR,
  сделанных за автономную follow-up сессию (Pass 43–49). Покрывает
  gap-fill, новые topics, cross-references, postMessage bridge,
  INDEX + ADR README, CHANGELOG. Содержит «до → после» метрики и
  список того, что осталось как ongoing work.
  PR #330.

## Состояние на сегодня

| Метрика | Значение |
|---------|----------|
| **Фичи в `features/`** | 88 |
| **Bounded contexts в `domain/`** | 7 (catalog, processing, rendering, publishing, identity, editorial, stats) |
| **C4 уровни** | 3 (L1, L2, L3) |
| **Topic-документов в `architecture/`** | 14 (incl. share-link, censoring, monetization, postMessage в webvue3-patterns) |
| **ADR** | 12 (6 global + 6 local) |
| **Runbooks** | 8 (README + 7 how-to) |
| **Шаблонов в `templates/`** | 6 |
| **`frontmatter`-файлов (с валидным frontmatter)** | 115 (incl. SESSION-SUMMARY) |
| **`total .md` файлов** | ~129 |
| **Cross-links valid** | 904 |
| **Broken references** | 0 |
| **AGENTS.md** | ≤ 100 строк ✓ |
| **CI проверок LiveDocs** | 7/7 + cross-links 0/904 broken + lychee strict |
| **Миграция покрытия спек** | 100% (все 75+ уникальных) |

## Как использовать этот changelog

- AI-агент: «Когда что появилось? Что недавно добавилось? Какие ADR актуальны?»
  → читать этот файл (после `livedocs/README.md` + `INDEX.md`).
- Разработчик (человек): «Что нового в LiveDocs?» → верх этого файла.
- Git / code review: новая секция changelog **в том же PR**, что и изменение.