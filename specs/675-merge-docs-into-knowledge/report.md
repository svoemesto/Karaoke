# Отчёт 675-merge-docs-into-knowledge

**Issue**: KARAOKE-334 (Plane) · **Ветка**: `675-merge-docs-into-knowledge` · **Дата**: 2026-10-29
**Спека**: `specs/675-merge-docs-into-knowledge/spec.md` (контракт уточнён владельцем 2026-10-29: базовая линия — 22 per-feature документа)

## Что сделано

Слой `docs/` (73 файла) слит в `knowledge/` — единственный слой проектной документации
(ADR-0011, спека 675). Миграция — одна атомарная волна: перенос, сворачивание и
переписывание ссылок неделимы.

### Перенос — 30 файлов (git mv, история сохранена)

| Куда | Файлов | Откуда |
|---|---|---|
| `knowledge/product/features/` | **22** | 21 продуктовый per-feature + `tonality-bpm-songs-table` |
| `knowledge/guidelines/runbooks/` | **5** | 4 из `docs/ops/` + `docs/tracker-setup.md` |
| `knowledge/guidelines/kdoc-jsdoc.md` | 1 | `docs/api/README.md` |
| `knowledge/guidelines/knowledge-first.md` | 1 | `docs/governance/knowledge-first.md` |
| `archive/docs/architecture-notes.md` | 1 | `docs/architecture-notes.md` |

`git log --follow` работает: `kortege-execution.md` — 4 коммита истории,
`log-correlation.md` — 5, `architecture-notes.md` — **80** (SC-011).

### Свёртка — 13 файлов (содержимое сохранено, ни одна потеря не молчаливая)

| Файл | Куда легло уникальное содержание |
|---|---|
| `telegram-auto-publish.md` | `publishing-services.md` — состояния попытки публикации + две фазы тика scheduler'а |
| `song-edit-and-censored.md` | `webvue3-audit-invariants.md` — инвариант 35: контракт покрытия полей SongEdit ↔ `songs2Update` |
| `zakroma-tiles-sort-order.md` | `dictionaries.md` — семантика `sort_order`; `persistence/domain.md` — BR-541 (recordhash) |
| `tracker-local-integration.md` | `tracker.md` — коды выхода BR-601/BR-605 |
| `storage-metadata-cache.md` | **не переносился**: документ описывал устаревший in-memory TTL-кеш, код реализует персистентный (TTL=∞) — актуальные факты уже в `knowledge/` |
| `private-docker-registry.md` | `registry.md` — 2 nginx-ловушки Zot (`client_max_body_size` → 413; таймаут 60 с → 502 на слое 5,6 ГБ) |
| `gradle-9-upgrade.md`, `karaoke-db-module-removal.md` | `architecture-conventions.md` § R-46 — история версий Gradle и удаление модуля-заглушки |
| `deploy-hardening.md` | реестр аудита (карточки PKG-S2-08 / PKG-S3-21) + фактическая часть в `deploy-overview.md` |
| `sync-audio-descendants.md`, `target-scoped-admin-requests.md` | полные зеркала существующих компонентов — удалены после сверки BR-кодов, инвентаря и тестов |

Ещё удалены: 30 пакетов аудита #336 (свёрнуты в реестр) и
`knowledge/system/infra/per-feature-docs.md` (устаревший каталог на 7 документов).

### Реестр аудита #336 — 551 код

`knowledge/system/infra/audit-336-registry.md` (1136+ строк): **510** кодов из слоя
`docs/features/` (§ 3, в т. ч. 385 в 18 `pkg-*.md` и 125 — только в остальных 47
файлах) + **41** код, встречавшийся только в `knowledge/` (§ 6) — эти 41 не были в
слое `docs/features/` (сверено с эталоном `3fb7c8bf`), поэтому сбор из 43 файлов
слоя их не видел; FR-015 требует их регистрации. Раздел «Коды без зеркала» (§ 5) —
**262** кода, чьё содержание остаётся носителем только в реестре (до миграции
измерялось 267). 37 карточек пакетов.

### Новые индексы и шаблоны

`knowledge/product/README.md` (индекс 22 документов + правило границы «продуктовая
ветка vs компонент домена»), `knowledge/templates/product-feature.md` (шаблон
профиля B: 6 секций FR-009, `slug` == имя файла, `status ∈ {active, deprecated,
experimental}`), `knowledge/system/infra/audit-336-registry.md`.

### Ссылки

**738** markdown-ссылок (пересчёт от нового места, кратчайшая форма внутри
`knowledge/`), **79** `@see` в KDoc/JSDoc, ~1000 текстовых упоминаний путей.
Плюс: 8 инструментов перенастроены, обе копии workflow синхронизированы,
`knowledge/README.md` получил раздел «Бывшие пути» (73/73 материала).

### Инструменты — 9

`check-doc-references.py` (`ROOTS` без `docs`, `GENERATED_PREFIXES` → `build/api-docs/`),
`check-ssot-impact.py` (снято мёртвое `^docs/`), `verify-doc-links.sh` (таргеты),
`check-no-legacy-tbl-settings.sh`, `verify-kotlin-refs.sh`, `generate-docs.sh`
(вывод в `build/api-docs/`) + `.gitignore`, `check-feature-doc.sh` (usage-текст),
`lint-knowledge.py` (комментарий про `knowledge/product/`, убрана ссылка на
несуществующий `docs/operations/knowledge-lint-baseline.md`), `check-knowledge-structure.sh`
(+`knowledge/product`).

### Guard'ы — 3, фикстур — 7 сценариев

| Guard | Фикстуры | Результат |
|---|---|---|
| `tools/check-no-docs-dir.sh` (FR-012) | `no-docs-dir/vulnerable` → 1, `clean` → 0 | ✓ |
| `tools/check-ci-workflow-sync.sh` (FR-013) | `ci-workflow-sync/divergent` → 1, `synced` → 0 | ✓ |
| `tools/check-audit-registry.py` (FR-015) | `audit-registry/orphan-code` → 1, `lost-code` → 1, `consistent` → 0 | ✓ |

Все три подключены в **обоих** workflow (набор гейтовых команд совпадает — 39 в
каждой копии) и в pre-commit (`always_run: true`).

### ADR и правила

`knowledge/adr/0011-merge-docs-into-knowledge.md` — Accepted, заменяет ADR-0007
**в части** п. 2, п. 4 и следствия 5 (тело ADR-0007 не тронуто — `git diff` = одна
строка `superseded_by`); реестр `knowledge/adr/README.md` дополнен.
Семвер-бампы (PATCH — менялись пути, не смысл): `AGENTS.md` 3.7.1 → **3.7.2**,
`CLAUDE.md` 1.8.1 → **1.8.2**, конституция 2.4.0 → **2.4.1** + Sync Impact Report.
`knowledge/README.md`: строка `product/`, подсекция «Две ветки SSoT» с таблицей
двух профилей, override п. 5 переписан, раздел «Бывшие пути».

### Скиллы

Проектные копии `.agents/skills/` приведены к новой структуре; локальные рантайм-копии
`.dsh/skills/managing-living-docs` и `.dsh/skills/audit-living-docs` заменены
**симлинками** на канон (`../../.agents/skills/…`), sha256 совпадают. Read-only
зеркала в `~/.dsh/skills/**` не тронуты (`767b61dc…a63ae3`).

## Проверки (фактический вывод)

Полный локальный прогон (quickstart § 13) — **9 из 9, код 0**:

```
bash tools/check-no-docs-dir.sh                    → OK: каталога docs/ нет
bash tools/check-ci-workflow-sync.sh               → OK: 39 гейт(ов) в каждой копии
python3 tools/check-audit-registry.py              → OK: 510 в § 3, 41 в § 6, 262 без зеркала, 551 всего
bash tools/check-knowledge-structure.sh             → OK: knowledge/ structure valid (9/9 checks passed)
bash tools/check-knowledge-cross-links.sh           → OK: All 1221 cross-links valid
python3 tools/lint-knowledge.py --baseline ...     → All documentation passes architectural style and structural checks
python3 tools/check-doc-references.py               → OK: битых ссылок, несуществующих путей и эндпоинтов нет
bash tools/check-feature-doc.sh … (22 + архив)     → Все документы валидны
bash tools/verify-doc-links.sh                     → 2238 OK, 0 Errors
```

Прочее: `git ls-files docs/` = **0**, каталога `docs/` нет на диске;
`git ls-files archive/docs/` вырос ровно на 1 (`architecture-notes.md`);
секретов в индексе нет; `pre-commit run --all-files` — **все хуки Passed, без SKIP**;
`:karaoke-app:compileKotlin` + `:karaoke-web:compileKotlin` — BUILD SUCCESSFUL;
`:karaoke-app:bootJar` + `:karaoke-web:bootJar` — BUILD SUCCESSFUL;
`ktlintCheck` — Passed; `webvue3` и `karaoke-public`: `npm run lint`, `npm run build`,
`npm run format:check` — зелёные; контрактный тест `PkgS335SourceContractsTest` —
BUILD SUCCESSFUL.

## Что осталось красным и почему

**В гейтах — ничего.** Вне скоупа и не чинилось:

- **Предсуществующие битые ссылки внутри `archive/docs/features/**`** (ссылки на код
  вида `../../deploy/…`, `../../karaoke-app/…`, резолвятся в `archive/…` и были биты
  ещё до миграции). Архив — вне скоуфа карты; его структуру проверяет отдельный джоб
  `check-feature-doc.sh` по `archive/docs/features/*.md`, а из таргетов lychee
  архивный слой убран.
- **4 текстовых упоминания `livedocs/…` внутри ADR** — ADR append-only: упоминания
  файлов в историческом тексте не переписываются, правится только навигация.
- **В `agents-team-srv`** — чужое staged-удаление `.agents/skills/superpowers-writing-skills`
  (репозиторий read-only для этого проекта; не тронут).

## CI-инцидент (2026-10-29) и ужесточение guard'а C2

Первый прогон CI на PR #21 упал **за 0 секунд** — ни один джоб не стартовал. Причина:
в обеих копиях workflow значение `- name: Guard: каталога docs/ нет` содержало
неэкранированное двоеточие, из-за чего YAML переставал разбираться. Локальные гейты
при этом были зелёными: `check-ci-workflow-sync.sh` сравнивал **наборы команд**, а обе
копии были одинаково сломаны.

| Что | Решение |
|---|---|
| Невалидный YAML | значения `name` экранированы в обеих копиях; обе разбираются (27 джобов каждая) |
| Дыра в гейте | в `tools/check-ci-workflow-sync.sh` добавлена проверка валидности YAML обеих копий — пункт 2 контракта C2, fail-closed по BR-597 («не удалось проверить» ≠ «нарушений нет») |
| Фикстура | `tools/fixtures/ci-workflow-sync/invalid-yaml/` — наборы гейтов совпадают, YAML сломан → guard падает с кодом 1 |
| Контракт | `contracts/guards.md` — в логику C2 добавлена строка 2, в кодах возврата уточнено значение кода 1 |

## Известные ограничения

- **Docker-образы не собирались.** Правки кода — только docstring'и (`@see` в
  KDoc/JSDoc + строковые assert'ы контрактного теста), 59 файлов; рантайм-код не
  менялся, значит байткод идентичен — сборка образа (упаковка) не несла смысловой
  нагрузки. При необходимости собирается по команде лида.
- **Локальные симлинки `.dsh/skills/*` не входят в коммит** — `.dsh/` в `.gitignore`;
  это операция на рабочей машине, состояние рантайма.

## Отклонения от контракта (приняты лидом)

1. **Реестр: 510 → 551 код.** FR-015 требует, чтобы код, упомянутый в `knowledge/`,
   был в реестре; 41 такой код в слое `docs/features/` отсутствовал (сверено с
   эталоном). Добавлена § 6 реестра; обновлены производные числа (сводка реестра,
   quickstart § 4.1, T073, ADR-0011). SC-002 не нарушен: все 510 кодов слоя
   `docs/features/` в реестре (100%).
2. **Проверка T067 уточнена.** Литеральный `grep "docs/"` ловил и `archive/docs/…`
   (существующий архив) — ложное срабатывание; заменён на
   `grep -rnE "(^|[^[:alnum:]_/.-])docs/"`, который ищет только ссылки на удалённый
   слой. Итог: 0 упоминаний удалённого слоя в корневых правилах.

## Не выполнено (вне фазы)

- **T088** — `tracker.sh add-comment 334 --file …report.md` → `mark-review 334`
  выполняются **после merge** (делает лид).
- Merge `origin/master` в ветку, push, PR и merge — выполняет лид после зелёного CI.
