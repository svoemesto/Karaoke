# Контракт: карта переноса слоя `docs/` и правила переписывания ссылок

**Feature**: `675-merge-docs-into-knowledge` | **Issue**: `KARAOKE-334` | **Дата**: 2026-10-29
**Источник**: D6, D10–D13 ([research.md § 1](../research.md)), инвентарь `research/inventory-docs.md`

> Это **полный** поимённый список судьбы всех 73 файлов (SC-009: 73 из 73 учтённых).
> Ни один файл не может быть неучтённым; для каждого — ровно одно решение.

---

## 1. Поимённая карта — `docs/features/` (65 файлов)

### 1.1. Продуктовые — 25 файлов → `knowledge/product/features/<slug>.md` (перенос, `git mv`)

| # | Файл | # | Файл |
|---|---|---|---|
| 1 | `admin-home-adaptive-columns.md` | 12 | `pagination-filter-admin-tables.md` |
| 2 | `author-filter-datalist.md` | 13 | `playlist-membership.md` |
| 3 | `client-ip-resolution.md` | 14 | `process-bulk-actions.md` |
| 4 | `close-gesture-token-paywall.md` | 15 | `public-api-exposure.md` |
| 5 | `complete-song-rename.md` | 16 | `public-settings-perimeter.md` |
| 6 | `drop-empty-song-voices.md` | 17 | `publications-filters.md` |
| 7 | `escape-confirm-user-data.md` | 18 | `rows-per-page.md` |
| 8 | `idempotent-path-sanitize.md` | 19 | `search-text-extract-btn.md` |
| 9 | `internal-secret-sync.md` | 20 | `security-headers.md` |
| 10 | `kortege-execution.md` | 21 | `song-air-access.md` |
| 11 | | | `song-artifact-cleanup.md` |

**21 файл** — по решению владельца 2026-10-29 (FR-003): четыре инфраструктурных документа
(`deploy-hardening.md`, `gradle-9-upgrade.md`, `karaoke-db-module-removal.md`,
`private-docker-registry.md`) в продуктовую ветку **не входят** — см. § 1.4.

### 1.2. Пакеты аудита #336 — 30 файлов → `knowledge/system/infra/audit-336-registry.md` (свёртка)

| # | Файл | # | Файл |
|---|---|---|---|
| 1 | `admin-web-client-robustness.md` | 16 | `pkg-s3-27.md` |
| 2 | `alignment-ml.md` | 17 | `pkg-s3-28.md` |
| 3 | `app-resilience-pkg-s2-11.md` | 18 | `pkg-s3-29.md` |
| 4 | `fix-connection-leak.md` | 19 | `pkg-s3-30.md` |
| 5 | `pkg-err-stats-zero.md` | 20 | `pkg-s3-31.md` |
| 6 | `pkg-s2-10-data-integrity.md` | 21 | `pkg-s3-32.md` |
| 7 | `pkg-s2-12-audit-fixes.md` | 22 | `pkg-s3-33.md` |
| 8 | `pkg-s2-14-audit-fixes.md` | 23 | `pkg-s3-34.md` |
| 9 | `pkg-s3-22-audit-fixes.md` | 24 | `pkg-s3-35.md` |
| 10 | `pkg-s3-23.md` | 25 | `process-exit-code-check.md` |
| 11 | `pkg-s3-24.md` | 26 | `sql-injection-hardening.md` |
| 12 | `pkg-s3-25.md` | 27 | `subscription-payment-idempotency.md` |
| 13 | `pkg-s3-26.md` | 28 | `sync-resilience.md` |
| 14 | | 29 | `vk-id-admin-perimeter.md` |
| 15 | | 30 | `web-data-integrity.md`, `web-render-safety.md`, `webui-state-integrity.md` |

**Что уходит в реестр**: BR-коды (все 510 уникальных по слою `docs/features/`), карточка пакета (имя, волна, состав, что исправлено, куда легло зеркало), обратный указатель «код → компонент». Полные описания дефектов **не дублируются** — они остаются в git-истории перенесённых файлов.

### 1.3. Мост-файлы — 10 файлов (D6)

| Файл | Решение | Куда |
|---|---|---|
| `tonality-bpm-songs-table.md` | **перенос** | `knowledge/product/features/tonality-bpm-songs-table.md` (продуктовое решение: колонки Ton/BPM в главной таблице песен admin-SPA) |
| `sync-audio-descendants.md` | **удаление** | полное зеркало `knowledge/domains/catalog/components/audio-descendant-sync.md` |
| `target-scoped-admin-requests.md` | **удаление** | полное зеркало `knowledge/system/frontend/target-request-guard.md` |
| `storage-metadata-cache.md` | **свёртка** | `knowledge/domains/storage/components/storage-api-client.md` + `knowledge/domains/caching/components/web-caches.md` (TTL-кеш метаданных MinIO, `GET /api/health/cacheStats`, категория лога `infra.cache.storage`) |
| `stemjob-private-bucket.md` | **свёртка** | факты уже в `domains/storage/domain.md`, `storage-flow.md`, `karaoke-web/components/internal-stem-job-controller.md`; **BR-258 → реестр** |
| `tracker-local-integration.md` | **свёртка** | `adr/0008-tracker-openproject-migration.md` + `system/infra/tracker.md` + `adr/local-0012-plane-tracker-migration.md`; **BR-601 → реестр** |
| `zakroma-tiles-sort-order.md` | **свёртка** | `system/infra/sql-migrations.md`, `domains/persistence/domain.md`, `domains/catalog/components/dictionaries.md`; **BR-541 → реестр** |
| `zakroma-albums-by-author.md` | **свёртка** | `domains/catalog/components/album-entity.md` + `adr/local-0007-zakroma-album-id-in-stream-dto.md` |
| `telegram-auto-publish.md` | **свёртка** | `domains/publishing/components/publishing-services.md` + `domains/processing/components/schedulers.md` (состояния `scheduled/rendering/publishing/published/send_failed`) |
| `song-edit-and-censored.md` | **свёртка** | `system/frontend/webvue3-audit-invariants.md` + `domains/catalog/components/remaining-models.md` + `domains/publishing/components/publishing-services.md` |

**Правило**: уникальные BR-коды мост-файлов идут в реестр аудита, **а не** в продуктовую ветку (FR-018: BR-258, BR-541, BR-601).

### 1.4. Четыре инфраструктурных документа — свёртка (D4, принято владельцем 2026-10-29)

В продуктовую ветку **не входят** (FR-003: 21 + `tonality` = 22):

| Файл | Решение | Целевое место |
|---|---|---|
| `private-docker-registry.md` | **удалить** — содержимое уже зеркалится | `knowledge/system/infra/registry.md` + `harbor-registry.md` + `deploy-overview.md` (существуют) |
| `gradle-9-upgrade.md` | **свернуть** — уникально только «история отката с 9.1.0» | `knowledge/guidelines/architecture-conventions.md` § R-46 + `knowledge/system/infra/ci-tools.md` |
| `karaoke-db-module-removal.md` | **свернуть** — уникально обоснование (мёртвый класс «Hello world!», свой build и wrapper на 7.5.1) | `knowledge/guidelines/architecture-conventions.md` + `knowledge/system/infra/ci-tools.md` |
| `deploy-hardening.md` | **в реестр аудита** — 31 BR-код (PKG-S2-08); фактическая часть (бэкапы БД, секреты, TLS, экспозиция портов, ротация логов) — в компонент | `knowledge/system/infra/audit-336-registry.md` + `knowledge/system/infra/deploy-overview.md` |

---

## 2. Поимённая карта — остальные 8 файлов

| Файл | Решение | Целевое место |
|---|---|---|
| `docs/ops/crypto-key-rotation.md` | перенос | `knowledge/guidelines/runbooks/crypto-key-rotation.md` |
| `docs/ops/forgejo-access.md` | перенос | `knowledge/guidelines/runbooks/forgejo-access.md` |
| `docs/ops/log-correlation.md` | перенос | `knowledge/guidelines/runbooks/log-correlation.md` |
| `docs/ops/prod-config-delivery.md` | перенос | `knowledge/guidelines/runbooks/prod-config-delivery.md` |
| `docs/tracker-setup.md` | перенос | `knowledge/guidelines/runbooks/tracker-setup.md` («How to: вести Plane-трекер») |
| `docs/api/README.md` | перенос | `knowledge/guidelines/kdoc-jsdoc.md` |
| `docs/governance/knowledge-first.md` | перенос | `knowledge/guidelines/knowledge-first.md` |
| `docs/architecture-notes.md` | перенос (архив) | `archive/docs/architecture-notes.md` |

**Итого: 65 + 8 = 73** ✓ (SC-009).

### 2.1. Побочные операции вне `docs/`

| Файл | Операция |
|---|---|
| `knowledge/system/infra/per-feature-docs.md` | **удаление** (D3) + правка 3 ссылок: `knowledge/domains/README.md:115`, `knowledge/system/infra/pre-commit-config.md:183`, `knowledge/system/infra/ci-tools.md:414` |
| `knowledge/system/infra/audit-336-registry.md` | **создать** (D5) |
| `knowledge/product/README.md` | **создать** (D1) |
| `knowledge/templates/product-feature.md` | **создать** (D2) |
| `knowledge/adr/0011-merge-docs-into-knowledge.md` | **создать** (D18) |

---

## 3. Правила переписывания ссылок (D10–D13)

### 3.1. Область

Переписывается **каждая ссылка на перемещаемый файл**, где бы она ни находилась.

| Группа | Объём | Что делаем |
|---|---|---|
| `knowledge/` | 104 упоминания в 38 файлах | md-ссылки → новые пути; текстовые упоминания → новые пути |
| Корневые правила | ~50 в 7 файлах (`AGENTS.md`, `CLAUDE.md`, `.specify/memory/constitution.md`, `PR_DESCRIPTION.md`, `.github/PULL_REQUEST_TEMPLATE.md`, `DEVELOPMENT.md`, `README.md`) | новые пути; `CONTRIBUTING.md` — не трогаем (0 реальных срабатываний) |
| `tools/` и CI | 30 строк в 9 файлах | новые пути; **обе** копии workflow |
| `specs/` | 104 md-ссылки в 52 файлах | **только** md-ссылки; текстовые (~1600) — по политике проекта не переписываются |
| Код | `@see` в KDoc/JSDoc: 74 в 51 файле (karaoke-app 36, webvue3 17, karaoke-web 13, karaoke-public 8) | новые пути (это docstring'и, которые показывает IDE) |
| Внутренние ссылки перенесённых файлов | все 65 файлов `docs/features/` → `../../knowledge/…`; 17 внутренних `docs/→docs/` | пересчёт по новой глубине |

### 3.2. Что НЕ трогаем

| Путь | Почему |
|---|---|
| `archive/docs/**` (43 существующих файла) | вне скоупа (решение карты) |
| `livedocs/**` | каталога нет в репозитории; упоминания уже битые — не чиним |
| `.agents/skills/**` — сравнительные таблицы generic ↔ Karaoke | прямое решение карты |
| `~/.dsh/skills/**`, `/home/nsa/agents-team-srv/**` | read-only (SYNC.md:5) |
| Текстовые упоминания `docs/` в `specs/` | политика проекта: в `specs/` гейт проверяет только ссылки |

### 3.3. Пересчёт относительных путей

Пересчёт выполняется **по правилу «пересчитать относительный путь от нового места»**, а не заменой подстроки:

| Ситуация | Было | Стало |
|---|---|---|
| Цель внутри `knowledge/` | `docs/features/x.md` → `../../knowledge/domains/y.md` | `knowledge/product/features/x.md` → `../../domains/y.md` (кратчайшая форма, без повторения `knowledge/`) |
| Цель вне `knowledge/` | `../../knowledge/…` | `../../../tools/…` |
| Соседний файл в том же каталоге | `docs/features/a.md` → `b.md` | `knowledge/product/features/a.md` → `b.md` |

### 3.4. Попутная починка уже битых ссылок (D13)

| Тип битой ссылки | Решение |
|---|---|
| Файл существует в `archive/docs/` (`docs/strategy/growth.md`, `docs/features/dual-db-sync.md`, `monitoring.md`, `premium-stems.md`, `mlt-generator.md`, `async-process-queue.md`, `approve-pipeline.md`, `ci-lint-enforcement.md` и др.) | ведём на `archive/docs/…` **с пометкой «архив»**; не удаляем — это живая ссылка на живой документ |
| Файла нет нигде (`external-api-clients.md:106,120`, `adr/0001…0004`, 10 строк `governance-docs.md`, `docs/onboarding.md`, `docs/claude-code-setup.md`, `docs/migration-prod-server.md`, `docs/architecture-notes-archive.md`) | **текст без ссылки** (имя файла в бэктиках) либо пометка «документ не существует»; осознанные отсылки → `config/knowledge/doc-references-whitelist.txt` с reason `honest-gap` |
| `CLAUDE.md` / `AGENTS.md` → отсутствующие `docs/strategy/growth.md` и `growth-audit.md` | на `archive/docs/strategy/growth.md` и `archive/docs/strategy/growth-audit.md` (оба существуют) |
| `docs/api/README.md:119` → отсутствующий `docs/features/README.md` | индекс — `knowledge/product/README.md` |

### 3.5. Порядок переписывания (D10, ответ 329 § «Следствие для порядок работ»)

```text
1. knowledge/          → после каждого шага: check-knowledge-cross-links.sh + lint-knowledge.py
2. specs/              (только md-ссылки)
3. @see в коде
4. корневые правила
5. инструменты и CI
```

**Обязательное условие порядка**: целевой путь per-feature и `tools/check-feature-doc.sh` перенастраиваются **до** переписывания текстов скиллов и правил — иначе гейт FR-009 временно указывает на путь, которого ещё нет (или, наоборот, уже пуст), и проверка становится вакуумной (D20).

### 3.6. Инструменты с зашитыми путями — полный перечень

| Файл:строка | Что зашито | Новое значение |
|---|---|---|
| `tools/check-doc-references.py:44` | `ROOTS = ("knowledge", "docs", "archive/docs", ".github")` | `("knowledge", "archive/docs", ".github")` — каталога `docs/` больше нет; `knowledge/product/` покрыт корнем `knowledge` |
| `tools/check-doc-references.py:54` | `GENERATED_PREFIXES = ("docs/api/dokka/", "docs/api/typedoc-")` | `("build/api-docs/",)` |
| `tools/check-ssot-impact.py:33` | `EXCLUDE_PATTERNS` содержит `r"^docs/"` | исключение слоя `docs/` **снимается** (каталога нет ⇒ исключение мёртвое); поведение гейта меняется — проверяется осознанно |
| `tools/check-ssot-impact.py:170` | текст «See docs/architecture-notes.md or AGENTS.md» | `archive/docs/architecture-notes.md` |
| `tools/verify-doc-links.sh:48` | `TARGETS=("docs/features/" "CONTRIBUTING.md" "docs/")` | `("knowledge/product/features/" "CONTRIBUTING.md")` |
| `tools/check-no-legacy-tbl-settings.sh:174` | `for f in docs/features/*.md` | `for f in knowledge/product/features/*.md` |
| `tools/verify-kotlin-refs.sh:18` | `TARGETS=(docs/features/*.md)` | `TARGETS=(knowledge/product/features/*.md)` |
| `tools/generate-docs.sh:5-8,38,56,62,68` | вывод в `docs/api/dokka/`, `docs/api/typedoc-*` | `build/api-docs/` |
| `.gitignore:97-99` | `docs/api/dokka/`, `docs/api/typedoc-*/` | `build/api-docs/` |
| `tools/check-feature-doc.sh:10,44,116` | примеры `docs/features/*.md` в usage/справке | `knowledge/product/features/*.md` |
| `tools/check-knowledge-structure.sh:27-31` | список каталогов `knowledge/…` | + `knowledge/product` |
| `tools/check-knowledge-structure.sh:42-45` | шаблоны `domain/component/adr/epic` | + `product-feature` |
| `tools/lint-knowledge.py:16` | «See also: docs/operations/knowledge-lint-baseline.md (TODO)» | файл не существует ⇒ текст без ссылки; рядом — комментарий про `knowledge/product/` (D16) |
| `config/knowledge/doc-references-whitelist.txt:55` | scope `docs/api/README.md` | `knowledge/guidelines/kdoc-jsdoc.md` |
| `config/knowledge/doc-references-whitelist.txt:61,62` | scope `docs/features/pkg-s3-26.md` | `knowledge/system/infra/audit-336-registry.md` |
| `config/knowledge/doc-references-whitelist.txt:64` | scope `docs/features/pkg-s3-34.md` | `knowledge/system/infra/audit-336-registry.md` |
| `.forgejo/workflows/lint.yml:592-593,603` | lychee-таргеты + вызов `check-feature-doc.sh` | `knowledge/product/features/` + `knowledge/product/features/*.md archive/docs/features/*.md` |
| `.github/workflows/lint.yml:551-552,562` | то же | то же |
| `.pre-commit-config.yaml:325,328` | `entry:` и `files:` | `knowledge/product/features/*.md archive/docs/features/*.md`; `files: '^(knowledge/product/features\|archive/docs/features)/.*\.md$'` |
| `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS335SourceContractsTest.kt:288,294,299` | assert'ы на строку `docs/features/*.md` | `knowledge/product/features/*.md` |
| `tools/README.md:22` | документация вызова | новый путь |
| `PR_DESCRIPTION.md:131` | документация вызова | новый путь |

---

## 4. Инварианты карты переноса

| # | Инвариант |
|---|---|
| M-1 | Каждый из 73 файлов `docs/` имеет ровно одно решение (перенос / свёртка / удаление / архив) |
| M-2 | Ни один файл не теряет содержимое: сворачиваемое переносится в компонент-приёмник, удаляемое — полное зеркало, кодовое — в реестр |
| M-3 | Перенос выполняется через `git mv` ⇒ `git log --follow` показывает исходную историю (FR-019, SC-011) |
| M-4 | Все 510 уникальных BR-кодов присутствуют в реестре (SC-002) |
| M-5 | `knowledge/product/features/` содержит 22 документа, каждый проходит `check-feature-doc.sh` (SC-004) |
| M-6 | Ни одна ссылка на перемещаемый файл не остаётся устаревшей; уже битые — починены (FR-014, SC-003) |
| M-7 | В корневых правилах и инструментах нет упоминаний `docs/`, кроме раздела «Бывшие пути» и таблицы соответствия (FR-021, SC-007) |
