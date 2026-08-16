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

## 2026-08-16 — 234-db-sync-connection-leak (singleton Connection-фабрики)

- **Added** `livedocs/features/234-db-sync-connection-leak.md` — LiveDoc для новой фичи:
  Singleton `Connection.Companion.local()/remote()/virtual()` через `by lazy(SYNCHRONIZED)`
  + SLF4J `log.warn` при сбоях подключения/закрытия.
- **Related**: [087-fix-shared-db-connection.md](features/087-fix-shared-db-connection.md)
  (предшественник — ThreadLocal-per-поток),
  [091-fix-connection-leak.md](features/091-fix-connection-leak.md)
  (`closeThreadConnection()` для одноразовых потоков),
  [174-fix-stats-connection-leak.md](features/174-fix-stats-connection-leak.md)
  (аналогичная проблема для дашборда, решённая через lazy load + TTL-кеш).

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

## 2026-08-14–15 — Pass 51 (docs/ → livedocs/ миграция — FR-017 спеки 189)

Спека 189 (FR-017) требовала интеграцию `docs/features/*.md` в LiveDocs.
Полная миграция выполнена в 5 PR (#334–#338):

- **Phase 2.1/2.2 (PR #334, #335)**: перенесены корневые файлы из docs/ в LiveDocs:
  - `livedocs/architecture-notes.md` (2171 строк) — большой changelog
  - `livedocs/architecture-notes-archive.md` (1718 строк) — старый changelog
  - `livedocs/architecture/database.md` (tbl_public_settings + recordhash-триггеры)
  - `livedocs/architecture/invariants.md` (ловушки karaoke-web, MTU, Jackson is*, и т.п.)
  - `livedocs/architecture/deployment.md` (серверы)
  - `livedocs/architecture/public-modules.md` (карта karaoke-public)
  - `livedocs/strategy/models-comparison.md` (сравнение LLM-моделей)
  - `livedocs/CONVENTIONS.md` (конвенции LiveDocs)
  - `livedocs/onboarding.md` (настройка новой машины)
  - `livedocs/claude-code-setup.md` (настройка Claude Code)
  - `livedocs/runbooks/how-to-migrate-prod-server.md` (миграция прода)
  - `livedocs/runbooks/how-to-demo-publish-links.md` (DEMO-публикация)
  - `livedocs/runbooks/how-to-stemjobs.md` (стемы premium)
- **Phase 3.1/3.2 (PR #336)**: cross-references на archive/docs/features/ для 25 LiveDoc (17+8).
- **Phase 4 (PR #337)**: перенесены strategy/ + onboarding-handoff/:
  - `livedocs/strategy/growth.md`, `growth-audit.md`, `about-page-draft.md`
  - `livedocs/onboarding-handoff/011-m23-*.md`, `012-vk-id-*.md`
- **Phase 5 (PR #338)**: удаление docs/ (47 .md файлов). Оставлены:
  - `docs/api/` (build artifacts — НЕ трогаем)
  - `docs/CLAUDE.md.template` (template)
- **Phase 6 (PR #339)**: INDEX.md обновлён.

Обновлены ссылки в:
- `README.md`, `AGENTS.md`, `CONTRIBUTING.md`
- `.pre-commit-config.yaml`, `.github/workflows/lint.yml`
- `karaoke-web/src/main/resources/application.yml`
- 250+ KDoc `@see` в `karaoke-app/` и `karaoke-web/`

СКРИПТЫ: `tools/check-livedocs-external-links.sh` — расширен whitelist
для internal URLs (nsa-i9, id.vk.com, svoemesto.ru, sm-karaoke.ru,
smartcaptcha.yandexcloud.net, 127.0.0.1, minio-proxy).

## 2026-08-15 — Pass 51+ (Phase 8/9: 3 новых topic + gen-скрипты)

### Phase 8 — расширение architecture/

- **PR #343**: новый topic `ci-cd-pipeline.md` (7 шагов lint.yml, baselines, deploy gates).
- **PR #344**: новый topic `db-migration-playbook.md` (5-step playbook для prod-migrations).
- **PR #345**: новый topic `conversion-funnel.md` (visitor → registration → premium,
  5-step воронка + метрики).

### Phase 9 — Pass 2 (автогенерация LiveDoc)

- **PR #351** (`tools/gen-related-suggest.sh`): предложение `related:` из 4 источников
  (backlinks, related-backlinks, same-directory, BC candidates). Обновлено 100+ `related:`.
- **PR #352** (`tools/extract-kdoc-refs.sh` + 200 file fix): извлечение `@see` из
  KDoc/JSDoc для suggestion. Slug-based substring matching.
- **PR #353** (`tools/gen-spec-skeleton.sh`): создание LiveDoc-skeleton по `spec.md`
  (Pass 2).
- **PR #354** (CI): добавлен `bash tools/check-livedocs-coverage.sh` в `lint.yml`
  (5-й шаг LiveDocs structure job). 7 проверок, 0 FAIL.
- **PR #355** (README): таблица Gen-скриптов (6 скриптов, режимы).

### Метрики после Phase 8+9

- **Топики в `architecture/`**: 16 → 19 (+3 новых: ci-cd-pipeline, db-migration-playbook, conversion-funnel)
- **CI-скриптов LiveDocs**: 7 (check-livedocs-structure/cross-links/external-links/coverage + 3 baselines)
- **Gen-скриптов LiveDocs**: 3 (gen-related-suggest, extract-kdoc-refs, gen-spec-skeleton)
- **CI шагов в LiveDocs structure job**: 5 (check-structure, check-cross-links, check-coverage, check-external-links)
- **Public-репо gen-скрипты + шаги CI** для FR-018 спеки 189 (Pass 2).

## Состояние на сегодня

| Метрика | Значение |
|---------|----------|
| **Фичи в `features/`** | 88 |
| **Bounded contexts в `domain/`** | 7 (catalog, processing, rendering, publishing, identity, editorial, stats) |
| **C4 уровни** | 3 (L1, L2, L3) |
| **Topic-документов в `architecture/`** | 16 (incl. database, invariants, deployment, public-modules + share-link, censoring, monetization, postMessage в webvue3-patterns) |
| **ADR** | 12 (6 global + 6 local) |
| **Runbooks** | 11 (README + 10 how-to, incl. how-to-migrate-prod-server, how-to-demo-publish-links, how-to-stemjobs) |
| **Шаблонов в `templates/`** | 6 |
| **Strategy-документы** | 4 (growth, growth-audit, about-page-draft, models-comparison) |
| **Onboarding-документы** | 3 (README, onboarding, claude-code-setup + 2 onboarding-handoff) |
| **Archive-документы** | 32 (30 docs/features/ + 2 onboarding-handoff) |
| **`frontmatter`-файлов (с валидным frontmatter)** | 130 |
| **`total .md` файлов** | ~140 |
| **Cross-links valid** | 1069 |
| **Broken references** | 0 |
| **AGENTS.md** | ≤ 100 строк ✓ |
| **CI проверок LiveDocs** | 7/7 + cross-links 0/1069 broken + lychee strict + check-coverage в CI |
| **Миграция покрытия спек** | 100% (все 75+ уникальных) |
| **docs/ (legacy)** | 2 файла (api/ + CLAUDE.md.template) |

## Как использовать этот changelog

- AI-агент: «Когда что появилось? Что недавно добавилось? Какие ADR актуальны?»
  → читать этот файл (после `livedocs/README.md` + `INDEX.md`).
- Разработчик (человек): «Что нового в LiveDocs?» → верх этого файла.
- Git / code review: новая секция changelog **в том же PR**, что и изменение.
## 2026-08-16 — 235-auto-sync-3h (автозапуск «Синхронизации в 1 клик»)

- **Added** `livedocs/features/235-auto-sync-3h.md` — LiveDoc для фичи 235:
  периодический автозапуск `POST /api/sync/oneclick` каждые 3 ч
  (`KaraokeProperties.autoOneClickSyncIntervalMs`, default 10_800_000 мс).
- **Added** `livedocs/architecture-notes.md §Pass 63+` — запись о фиче
  (dynamic interval через internal polling `@Volatile var`, `AtomicBoolean running`
  для lock «ручной + авто не одновременно», `try/catch(Throwable)` на двух уровнях,
  `ConcurrentLinkedDeque<AutoOneClickSyncRun>` ≤10 записей).
- **Related**: [232-admin-song-editor-local-db.md](features/232-admin-song-editor-local-db.md)
  (sync как явная операция пользователя), [research.md в спеке 235](../specs/235-auto-sync-3h/research.md)
  (5 design-decisions по Spring `@Scheduled` + KaraokeProperties).
- **API change**: `POST /api/sync/oneclick` теперь может вернуть HTTP `409 Conflict`
  с телом `{"error":"sync_in_progress","message":"..."}`, если автозапуск или
  другой ручной клик уже идёт. UI `webvue3/.../Sync/SyncTable.vue` обрабатывает 409
  в `doOneClick` через `error.status`/`error.responseBody` (`promisedXMLHttpRequest` обновлён).
- **New REST endpoint**: `GET /api/sync/auto-status` →
  `AutoOneClickSyncStatusDto` для UI-блока «Автозапуск» на странице `/sync`.
