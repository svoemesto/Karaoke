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
| `.specify/memory/constitution.md` | Непреложные принципы | **Активен** (обновлён Pass 340+) |
| `knowledge/public/onboarding.md` | Setup новой машины | **Активен** (перенесён из слоя `docs/`) |
| `archive/docs/architecture-notes.md` | Changelog (dated-история) | **Активен** (перенесён в архив) |
| `knowledge/system/utilities.md` | Ключевые инварианты (ловушки) | **Активен** |
| `knowledge/system/infra/deploy-overview.md` | Деплой на прод | **Активен** |
| `knowledge/domains/persistence/domain.md` | DB schema | **Активен** |
| `archive/docs/strategy/growth.md` | visitor→registration→premium | Активен (архив) |
| `archive/docs/strategy/growth-audit.md` | Полный аудит (37+ гипотез) | Активен (архив) |
| `knowledge/product/features/<slug>.md` | Per-feature документы (22) | **Активен** (FR-009, профиль B) |
| `knowledge/product/README.md` | Индекс продуктовой ветки | **Активен** |
| `knowledge/guidelines/knowledge-first.md` | Правило MUST #0 (enforcement из `AGENTS.md`) | **Активен** |
| `knowledge/guidelines/kdoc-jsdoc.md` | Правила KDoc/JSDoc и шаблоны | **Активен** |
| `build/api-docs/` | Вывод генератора Dokka + typedoc | Автогенерируется, вне git |
| `knowledge/guidelines/runbooks/` | Операционные runbook'и (5) | **Активен** (в т. ч. `log-correlation.md`, FR-019) |
| `knowledge/system/infra/audit-336-registry.md` | Реестр BR-кодов аудита #336 (551 код) | **Активен** |

## Archive (`archive/docs/features/`)

**30 per-feature документов**. Это **legacy** — заменены на
`knowledge/domains/*/components/*.md` (Pass 340+) и на продуктовую ветку
`knowledge/product/features/` (спека 675, ADR-0011).

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
- **Per-feature** — `knowledge/product/features/<slug>.md` при реализации
  новой фичи (FR-009).
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
- **Перехват команд AI-агента** ([agent-command-guard.md](agent-command-guard.md)) —
  host-плагин DSH: часть правил Hard Gates проверяется до исполнения, нарушения
  получают след в журнале и публичный record (задача Plane с меткой
  `type:governance-violation`); ADR `local-0016`.

## Известные TODO

- [ ] **Каждый из 30 archive-docs** — перенос в Knowledge (где ещё
      не покрыто). Многие уже покрыты в Pass 340-365.
- [x] ~~**docs/invariants.md** — синхронизация с `system/utilities.md`.~~
      → инварианты живут в [utilities.md](../../system/utilities.md);
      отдельного файла в `docs/` больше нет (спека 675).
- [x] ~~**docs/architecture-notes-archive.md** — changelog.~~
      → [archive/docs/architecture-notes.md](../../../archive/docs/architecture-notes.md).
- [x] ~~**Per-feature docs** — нужна детализация.~~
      → продуктовая ветка: 22 документа в
      [knowledge/product/features/](../../product/features/), индекс — 
      [knowledge/product/README.md](../../product/README.md).

## Changelog

- **2026-10-29** (спека 675, ADR-0011): переписан после слияния слоя `docs/`
  в `knowledge/`. Убраны 10 строк с несуществующими файлами (`docs/onboarding.md`,
  `docs/claude-code-setup.md`, `docs/architecture-notes-archive.md`,
  `docs/invariants.md`, `docs/deployment.md`, `docs/database.md`,
  `docs/public-modules.md`, `docs/strategy/growth-audit.md`,
  `docs/features/<slug>.md`); добавлены строки продуктовой ветки
  (`knowledge/product/features/<slug>.md`, `knowledge/product/README.md`),
  guidelines (`knowledge-first.md`, `kdoc-jsdoc.md`, `runbooks/`) и реестра
  аудита. Раздел «Archive» сохранён как есть (FR-010).
- **Pass 376-378** (2026-09-09): Initial. Автор: agent (Karaoke).