# Контракт: структура SSoT после слияния (`knowledge/`)

**Feature**: `675-merge-docs-into-knowledge` | **Issue**: `KARAOKE-334` | **Дата**: 2026-10-29
**Источник**: D1–D5, D19–D20 (см. [research.md § 1](../research.md))

> Контракт описывает **интерфейс** новой структуры документации: какие пути существуют,
> что в них лежит и чем это проверяется. Кодом контракт не является — реализуется
> содержимым `knowledge/` и набором guard'ов ([guards.md](./guards.md)).

---

## C1. Две ветки одного SSoT

| Ветка | Каталоги | Что описывает | Профиль |
|---|---|---|---|
| Архитектурная | `knowledge/domains/`, `knowledge/system/`, `knowledge/guidelines/`, `knowledge/adr/`, `knowledge/epics/`, `knowledge/public/`, `knowledge/templates/` | устройство системы | A — компонент |
| Продуктовая | `knowledge/product/` | поведение продукта (видимое админу или пользователю) | B — per-feature (FR-009) |

**Правила**

1. `docs/` в корне репозитория **не существует** — ни отслеживаемых файлов, ни каталога (FR-001, SC-001). Проверка: [guard E7a](./guards.md#c1).
2. Продуктовый слой — **структурная категория, не домен** (D19): `knowledge/domains/README.md` не меняется, 16 доменов остаются.
3. Linking Protocol (L3 → L2 → L1) к `knowledge/product/**` **не применяется**. Родитель продуктовых файлов — `knowledge/product/README.md`.
4. Корневой индекс всего SSoT — `knowledge/README.md`. Он определяет термин «продуктовый слой» (единственное каноническое место) и публикует проекцию в `knowledge/public/glossary.md`.

---

## C2. Профиль A — компонент

| Поле | Значение |
|---|---|
| Где | `knowledge/domains/*/components/*.md`, `knowledge/system/**` |
| Шаблон | `knowledge/templates/component.md` |
| Секции | Ответственность / Интерфейсы и Контракты / Логика и Алгоритмы / Зависимости / Связанные ADR |
| Проверки | `tools/lint-knowledge.py`, `tools/check-knowledge-structure.sh`, `tools/check-knowledge-cross-links.sh` |

**Правила**

1. Изменения **не меняют** правил профиля A (D14: разделение по каталогу, а не по конфигу).
2. `tools/lint-knowledge.py` обходит только `knowledge/domains` — файлы `knowledge/product/**` в его обход не попадают. В коде рядом с `SECTION_EXEMPTIONS` добавляется **явный комментарий**: `knowledge/product/` обслуживается guard'ем FR-009 (D16).
3. `lint-knowledge.py` не требует исключений для `knowledge/product/**` — механизм исключений не расширяется.

---

## C3. Профиль B — per-feature документ (FR-009)

| Поле | Значение |
|---|---|
| Где | `knowledge/product/features/*.md` — **26 файлов** |
| Шаблон | `knowledge/templates/product-feature.md` (новый) |
| Секции | `## Что делает` / `## Зачем` / `## Как работает` / `## Инварианты` / `## Известные ловушки` / `## Ссылки` |
| Проверка | `tools/check-feature-doc.sh` |
| Правило пути | `slug` == имя файла без `.md` |
| Правило статуса | `status` ∈ {`active`, `deprecated`, `experimental`} |

**Правила**

1. Шаблон `knowledge/templates/product-feature.md` содержит 6 секций, правило «slug == имя файла», допустимые значения `status` и краткое назначение профиля.
2. `knowledge/templates/README.md` описывает `product-feature.md` как шаблон **второго профиля** и ссылается на него (FR-005).
3. `knowledge/product/README.md` — индекс ветки: перечень 26 документов, правило границы «что пишется в продуктовую ветку, а что — в компонент домена» (FR-004).
4. Формат per-feature **не конвертируется** в профиль A (форматы несовместимы: «Зачем» и «Известные ловушки» аналогов в профиле A не имеют).
5. Правило FR-009 сохраняется: «при правке кода MUST обновить per-feature документ в том же PR»; **меняется путь** (D17) — см. C6.

**Состав 26 документов** — 25 продуктовых (`docs/features/`, группа а.1 инвентаря) + `tonality-bpm-songs-table` (мост-файл с продуктовым решением, D6). Полный поимённый список — [migration-map.md § 1](./migration-map.md).

---

## C4. Индекс и навигация

| Файл | Роль | Обязательное содержимое |
|---|---|---|
| `knowledge/README.md` | канон SSoT | таблица «Структура» со строкой `product/`; подсекция «Две ветки SSoT» с таблицей двух профилей; обновлённый Karaoke-override п. 5; раздел «Бывшие пути»; расширенный шаг Initialize протокола агента (FR-006) |
| `knowledge/product/README.md` | индекс продуктовой ветки | перечень 26 документов + правило границы с компонентами домена (FR-004) |
| `knowledge/guidelines/README.md` | индекс guidelines | строка `knowledge-first.md` (enforcement MUST #0) в «Структуре»; `kdoc-jsdoc.md` переезжает из «Планируемого наполнения» в «Структуру»; термин «runbook» вместо «playbook» (D19) |
| `knowledge/guidelines/runbooks/README.md` | индекс runbook'ов | 5 runbook'ов; термин «runbook» |
| `knowledge/system/infra/governance-docs.md` | каталог governance-документов | 10 несуществующих `docs/*.md` убраны; строка per-feature → `knowledge/product/features/<slug>.md`; добавлены `knowledge/guidelines/knowledge-first.md`, `knowledge/guidelines/kdoc-jsdoc.md`, `knowledge/guidelines/runbooks/`, `knowledge/product/README.md`, `knowledge/system/infra/audit-336-registry.md`; раздел «Archive» без изменений (FR-010) |
| `knowledge/adr/README.md` | реестр ADR | строка про `0011-merge-docs-into-knowledge` + уточнение, что `0007` заменён **в части** |
| `knowledge/public/glossary.md` | публичная проекция Универсального языка | секция «Продуктовый слой» с терминами: продуктовый слой / per-feature документ / runbook / реестр аудита #336 (D19) |

---

## C5. Бывшие пути (справочник соответствия)

| Поле | Значение |
|---|---|
| Место | раздел «Бывшие пути» в `knowledge/README.md` |
| Формат | таблица «было → стало» |
| Покрытие | **все 73** старых пути слоя `docs/`, включая удалённые и свёрнутые (FR-024) |

**Правила**

1. Отдельный файл-редирект **не заводится** — иначе получится второй источник правды о путях (D12).
2. Раздел — единственное место, где в `knowledge/` допустимо упоминание `docs/` как пути (кроме исторического контекста ADR-0011 и пометок «архив»).
3. Покрытие проверяется сверкой: 73 файла `docs/` до миграции ↔ 73 строки (или 73 строки с группировкой, покрывающей все) таблицы.

---

## C6. Правила, указывающие на профили

| Файл | Что меняется | Версия |
|---|---|---|
| `AGENTS.md` | Tier-2 R-32: `docs/features/<slug>.md` → `knowledge/product/features/<slug>.md`; § «Plane Workflow» source: `docs/tracker-setup.md` → `knowledge/guidelines/runbooks/tracker-setup.md`; § «Диагностика через docker logs» source: `docs/ops/log-correlation.md` → `knowledge/guidelines/runbooks/log-correlation.md`; Tier-1 «Процесс wayfinder» source: `docs/tracker-setup.md` → новая ссылка; запись в Changelog | 3.7.1 → **3.7.2** (PATCH — обновление ссылок) |
| `CLAUDE.md` | таблица «Где искать что»: per-feature → `knowledge/product/features/<slug>.md`; стратегия → `archive/docs/strategy/growth.md` + `growth-audit.md`; логи прода → `knowledge/guidelines/runbooks/log-correlation.md`; трекер → `knowledge/guidelines/runbooks/tracker-setup.md`; changelog PR → `archive/docs/architecture-notes.md`; строка «Настройка Claude Code» → `docs/claude-code-setup.md` (файла нет) — текст без ссылки; запись в Changelog | 1.8.1 → **1.8.2** (PATCH) |
| `.specify/memory/constitution.md` | § VI FR-006: `@see docs/features/<slug>.md` → `knowledge/product/features/<slug>.md`; § VI FR-009: путь; § VII и § Governance 1: ссылки на несуществующие `docs/onboarding.md`, `docs/claude-code-setup.md`, `docs/architecture-notes-archive.md` → текст без ссылки / `archive/docs/architecture-notes.md`; § VIII: `docs/migration-prod-server.md` (нет) → текст без ссылки; § IX: `docs/governance/knowledge-first.md` → `knowledge/guidelines/knowledge-first.md`; **Sync Impact Report** в HTML-комментарии в начале файла | 2.4.0 → **2.4.1** (PATCH — смысл правил не меняется) |
| `.github/PULL_REQUEST_TEMPLATE.md` | 6 упоминаний `docs/` → новые пути; чеклист FR-009 указывает `knowledge/product/features/<slug>.md` | — |
| `DEVELOPMENT.md`, `README.md`, `PR_DESCRIPTION.md`, `tools/README.md` | ссылки на перемещаемые файлы → новые пути | — |

**Правила**

1. Правка `AGENTS.md` — governance-PR с явным semver bump (§ «Как обновлять этот файл»); версии 3.7.1 / 1.8.1 / 2.4.0 — PATCH, так как меняются **пути**, а не смысл правил.
2. Правка Конституции дополнительно требует Sync Impact Report (constitution § Governance 2 и 5): какие принципы затронуты, какие шаблоны обновлены, что осталось TODO.
3. Смысл FR-006 и FR-009 **не меняется** — меняется только путь (D17, ответ 329 § 6).

---

## C7. ADR-0011

| Поле | Значение |
|---|---|
| Файл | `knowledge/adr/0011-merge-docs-into-knowledge.md` |
| Frontmatter | `id: 0011-merge-docs-into-knowledge`, `title`, `status: Accepted`, `supersedes: 0007-adopt-knowledge-as-ssot`, `superseded_by: null`, `date: 2026-10-29`, `modules: [documentation]`, `tags: [ssot, livedocs, documentation, wayfinder]` |
| Блок после H1 | `> **Supersedes**: ADR-0007 — в части п. 2, п. 4 и следствия 5. Остальные решения ADR-0007 остаются в силе.` |
| Секции | Контекст и проблема / Решение / Обоснование / Последствия |

**Правила**

1. ADR-0011 фиксирует: единый слой документации + запрет `docs/`; границу двух веток SSoT и второй профиль формата; «продуктовый слой» как новый вид документа; реестр аудита #336; формат ADR с YAML-frontmatter и блоком `> **Supersedes**:`.
2. ADR-0011 **не** включает: детали порядка миграции (D20), глубину карточек реестра (D8), механику линтинга (D14).
3. В § «Контекст» указываются источники решения: **KARAOKE-320** (задача-источник) и **KARAOKE-323** (карта с 12 решениями владельца).
4. В § «Обоснование» — три отклонённые альтернативы: оставить `docs/` отдельным слоем; полная отмена ADR-0007; конвертация 65 per-feature в шаблон компонента.
5. В § «Последствия» обязательно фиксируются: (а) каноничная Karaoke-копия скилла — проектная `.agents/skills/…`, `~/.dsh/skills/` — read-only зеркало; (б) **инвариант рантайм-копии**: `.dsh/skills/` (ранг 100) перекрывает `.agents/skills/` (ранг 200) ⇒ правка git-версии не действует без синхронизации; (в) сравнительные таблицы generic ↔ Karaoke не трогаются; (г) правка `.agents/skills/audit-living-docs/SKILL.md:156`.
6. В ADR-0007 правится **только** `superseded_by: 0011-merge-docs-into-knowledge`; тело не трогается (append-only).
7. В ADR-0011 числа BR-кодов — **510 / 267** (совпадает с FR-011), а не «274 из 530» (см. [research.md § 3.6](../research.md)).

---

## C8. Скиллы

| Файл | Что меняется |
|---|---|
| `.agents/skills/managing-living-docs/SKILL.md` | таблица Karaoke-overrides — строка про второй профиль `knowledge/product/`; § Impact Surface Analysis — уровень «Продуктовая проекция → `knowledge/product/features/<slug>.md`»; § Final Validation — добавить `bash tools/check-feature-doc.sh knowledge/product/features/*.md`; § Quick Reference — пункт про `knowledge/product/` и новый гейт |
| `.agents/skills/audit-living-docs/SKILL.md:156` | `docs/architecture-notes.md` → `archive/docs/architecture-notes.md` |
| `.dsh/skills/managing-living-docs/`, `.dsh/skills/audit-living-docs/` | **заменить копии симлинками** на `.agents/skills/…` (ранг 100 перекрывает 200) — операционная задача вне git |
| `~/.dsh/skills/**`, `/home/nsa/agents-team-srv/**` | **не трогать** (read-only; «правка зеркала без правки исходника запрещена») |
| Сравнительные таблицы «generic `docs/` ↔ Karaoke `knowledge/`` | **не трогать** (4 упоминания в `managing-living-docs`, 5 в `audit-living-docs`) |
