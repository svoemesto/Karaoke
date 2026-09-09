# Governance & process docs

> **Домен**: system (infrastructure)
> **Компонента**: обзор governance/process документов проекта.
> Pass 376-378 Knowledge-аудита.

## Назначение

Описание **process** и **contribution** документов, которые задают
правила работы с проектом. Не путать с архитектурными Knowledge
(`knowledge/domains/*`).

## Каталог документов (корень)

| Файл | Назначение | Статус |
|---|---|---|
| `AGENTS.md` | Runtime-инструкции для AI-агента | **Активен** (см. system/README) |
| `CLAUDE.md` | Claude Code инструкции | Активен |
| `CONTRIBUTING.md` | Style guide (Kotlin/Vue/SQL/MD/Sh/Docker) | **Активен** (892 строки) |
| `DEVELOPMENT.md` | Архитектурный контекст + dated-история | **Активен** (164 строки) |
| `README.md` | Точка входа | **Активен** |
| `.specify/memory/constitution.md` | Непреложные принципы | **Активен** (366 строк, обновлён Pass 340+) |
| `docs/onboarding.md` | Setup новой машины | Активен |
| `docs/claude-code-setup.md` | Claude Code | Активен |
| `docs/architecture-notes-archive.md` | Changelog | Активен (deprecated жить) |
| `docs/invariants.md` | Ключевые инварианты (ловушки) | **Активен** (Pass 343+: см. `system/utilities.md`) |
| `docs/deployment.md` | Деплой на прод | Активен |
| `docs/database.md` | DB schema | Активен |
| `docs/public-modules.md` | Публичные модули | Активен |
| `docs/strategy/growth.md` | visitor→registration→premium | Активен |
| `docs/strategy/growth-audit.md` | Полный аудит (37+ гипотез) | Активен |
| `docs/features/<slug>.md` | Per-feature документы (11+1 = 12) | Активен (FR-009) |
| `docs/api/README.md` | API docs (Dokka + typedoc) | Автогенерируется |
| `docs/ops/log-correlation.md` | Логи прода (FR-019) | Активен (spec 288) |

## Archive (`archive/docs/features/`)

**27 per-feature документов**. Это **legacy** — заменены на
`knowledge/domains/*/components/*.md` (Pass 340+).

| Slug | Связанный Knowledge |
|---|---|
| `approve-pipeline.md` | async-process-queue.md |
| `async-process-queue.md` | async-process-queue.md (canonical теперь тут) |
| `ci-lint-enforcement.md` | ci-tools.md |
| `dictionaries.md` | (catalog/components/dictionaries.md) |
| `dual-db-sync.md` | two-db-sync.md (canonical) |
| `editor-skipped-content-access.md` | (P2) |
| `editor-tasks.md` | editorial domain |
| `guest-share-link.md` | (P2) |
| `homepage-latest-news.md` | (P1) |
| `llm-lyrics-search.md` | (P1) |
| `mlt-generator.md` | mlt-generator.md (canonical) |
| `monitoring.md` | monitor-checks.md (canonical) |
| `mp4-render.md` | (P1) |
| `news-publish-backfill.md` | (P1) |
| `news-templates.md` | (P1) |
| `player-transpose.md` | (P1) |
| `playlist-play-button-and-stems-cancel.md` | (P1) |
| `premium-stems.md` | stem-job.md |
| `seo-html-for-bots.md` | (P2) |
| `site-traffic-resilience.md` | karaoke-web domain |
| `song-free-access.md` | (P1) |
| `songs-table.md` | (P1) |
| `song-state-colors.md` | (P1) |
| `special-orders.md` | (P1) |
| `sse-notifications.md` | sse domain (canonical) |
| `stats.md` | stats domain |
| `telegram-auto-publish.md` | schedulers.md |
| `vk-id-auth.md` | (P1) |
| `vk-news-auto-publish.md` | (P1) |
| `zakroma-stream-progress.md` | (P1) |

**NB**: archive/docs/features/ НЕ удаляется (Pass 336 — explicit
решение), потому что git history — единственный source of truth.
Knowledge — **дополнение**, не замена.

## CONVENTIONS (cross-cutting)

### Kotlin style

Из CONTRIBUTING.md:
- **ktlint** — форматирование (Pass 359 pre-commit hook).
- **`redirectErrorStream(true)`** — ВСЕ `ProcessBuilder` (Constitution
  VI + ADR-0006).
- **JSON keys** — БЕЗ `is`-префикса (Jackson serialization).
- **Nullable-колонки БД** → nullable-поля в Kotlin.

### Vue/TS style

- **`<select class="form-select">`** (НЕ `form-control`).
- **KDoc/JSDoc** на публичных API.
- **Wildcard-импорты** допустимы (правило ktlint
  `no-wildcard-imports` отключено в `.editorconfig`).

### Documentation

- **На русском** (CLAUDE.md раздел «АБСОЛЮТНОЕ ПРАВИЛО»).
- **Per-feature** — `docs/features/<slug>.md` при реализации новой
  фичи (FR-009).
- **Knowledge-first** — заполнять `## Knowledge References` в
  spec.md (Pass 340+).

## Constitution (NON-NEGOTABLE principles)

Из `.specify/memory/constitution.md` (Pass 340 — добавлен
Knowledge-first принцип):

- **I.** Self-contained автопайплайн.
- **II.** Сырой JDBC + recordhash.
- **III.** Двух-БД sync через SyncRegistry.
- **IV.** Async-очередь с redirectErrorStream(true).
- **V.** Двух-фронтенд (webvue3 / karaoke-public).
- **VI.** Code Standards (KDoc/JSDoc, линтеры, per-feature doc).
- **VII.** Cross-Machine Setup.
- **VIII.** Секреты и git-гигиена (см. security issue в
  [utilities.md](../../system/utilities.md)).
- **IX.** Knowledge-first при разработке фич (Pass 340).

## Связь с другими компонентами

- **Knowledge** (`knowledge/`) — основной SSoT.
- **Archive** (`archive/docs/features/`) — legacy, не удаляется.
- **CI tools** ([ci-tools.md](ci-tools.md)) — enforce'ит правила.
- **Pre-commit** ([pre-commit-config.md](pre-commit-config.md)) —
  локальная проверка перед коммитом.

## Известные TODO

- [ ] **Каждый из 27 archive-docs** — перенос в Knowledge (где ещё
      не покрыто). Многие уже покрыты в Pass 340-365.
- [ ] **docs/invariants.md** — синхронизация с `system/utilities.md`.
- [ ] **docs/architecture-notes-archive.md** — changelog (Pass 343+).
- [ ] **Per-feature docs (docs/features/)** — 11+1 файлов, нужна
      детализация (Pass 343+).

## Changelog

- **Pass 376-378** (2026-09-09): Initial. Автор: agent (Karaoke).