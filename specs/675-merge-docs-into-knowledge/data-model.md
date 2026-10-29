# Data Model — Слияние слоя `docs/` в `knowledge/` (Phase 1)

**Feature**: `675-merge-docs-into-knowledge` | **Дата**: 2026-10-29 | **Issue**: `KARAOKE-334`

> Спека — контракт; модель здесь — **структурная**, а не реляционная: миграция меняет форму
> проектной документации, а не данные приложения. «Сущности» — документы и проверки,
> у каждых — поля, места и инварианты. Базы данных не затрагиваются.

---

## 1. Карта сущностей

```text
knowledge/                                   ── корень SSoT (единственный слой документации)
├── README.md                                ── канон: структура, 2 профиля, override #5,
│                                               «Бывшие пути», протокол агента
├── domains/  system/  guidelines/  adr/     ── АРХИТЕКТУРНАЯ ветка (профиль A)
│   ├── product/                             ── ПРОДУКТОВАЯ ветка (профиль B)
│   │   ├── README.md                        ── индекс ветки + правило границы
│   │   └── features/<slug>.md               ── 22 per-feature документа (FR-009)
│   ├── guidelines/runbooks/<slug>.md        ── 5 runbook'ов
│   ├── guidelines/kdoc-jsdoc.md             ── правила KDoc/JSDoc
│   ├── guidelines/knowledge-first.md        ── enforcement MUST #0
│   ├── system/infra/audit-336-registry.md   ── реестр BR-кодов аудита #336
│   └── templates/product-feature.md         ── шаблон профиля B
└── (adr/0011-merge-docs-into-knowledge.md)  ── решение о слиянии
```

Все сущности — **файлы**. Таблицы ниже описывают поля, место и правила валидации.

---

## 2. Сущности

### E1. Продуктовый слой (`knowledge/product/`)

| Поле | Значение |
|---|---|
| Тип | ветвь SSoT (структурная категория, **не домен**) |
| Место | `knowledge/product/` |
| Родитель | `knowledge/README.md` (канон термина) |
| Индекс | `knowledge/product/README.md` |
| Содержимое | `features/<slug>.md` (22 документа) |
| Проверка | `tools/check-feature-doc.sh`, `tools/check-knowledge-structure.sh` (наличие каталога + README) |
| Linking Protocol | **не применяется** (L3→L2→L1 — только для `knowledge/domains/**`) |

**Инварианты**

| # | Инвариант |
|---|---|
| I-1 | `knowledge/domains/README.md` содержит ровно 16 доменов; продуктовый слой в реестр доменов **не вносится** |
| I-2 | `knowledge/product/README.md` существует и содержит правило границы «что пишется в продуктовую ветку, а что — в компонент домена» |
| I-3 | `knowledge/product/` присутствует в списке каталогов `tools/check-knowledge-structure.sh` |
| I-4 | Каждый файл `knowledge/product/features/*.md` — валидный документ профиля B (E2) |

### E2. Per-feature документ (профиль B)

| Поле | Значение | Проверка |
|---|---|---|
| Путь | `knowledge/product/features/<slug>.md` | `check-feature-doc.sh` |
| `slug` | имя файла без `.md`; `slug` == имя файла | `check-feature-doc.sh` |
| `status` | ∈ {`active`, `deprecated`, `experimental`} | `check-feature-doc.sh` |
| Секции (6, FR-009) | `## Что делает`, `## Зачем`, `## Как работает`, `## Инварианты`, `## Известные ловушки`, `## Ссылки` | `check-feature-doc.sh` |
| Источник | `docs/features/<slug>.md` (перенос через `git mv`) | `git log --follow` |

**Инварианты**

| # | Инвариант |
|---|---|
| I-5 | Все 22 документа имеют полный набор шести секций FR-009 |
| I-6 | `slug` документа == имя файла |
| I-7 | `status` ∈ {active, deprecated, experimental} |
| I-8 | Содержимое перенесённого документа не меняется при переносе (кроме пересчёта относительных ссылок) |
| I-9 | История файла сохранена: `git log --follow knowledge/product/features/<slug>.md` показывает исходные коммиты `docs/features/<slug>.md` |

### E3. Runbook

| Поле | Значение | Проверка |
|---|---|---|
| Место | `knowledge/guidelines/runbooks/<slug>.md` | `check-knowledge-structure.sh` (каталог) + `lint-knowledge.py` |
| Заголовок | «How to: …» | формат `knowledge/guidelines/runbooks/README.md` |
| Секции | Контекст / Шаги / Проверка / Rollback | то же |
| Ссылки | ADR + домен + спека (`specs/NNN-slug/`) | `check-knowledge-cross-links.sh` |
| Источник | `docs/ops/*.md` (4) + `docs/tracker-setup.md` (1) | `git log --follow` |

**Инварианты**

| # | Инвариант |
|---|---|
| I-10 | В `knowledge/guidelines/runbooks/` ровно 5 документов (4 операционных + «How to: вести Plane-трекер») |
| I-11 | Термин в каталоге — «runbook»; «playbook» из `runbooks/README.md` и `knowledge/guidelines/README.md` вычищен (D19) |
| I-12 | `knowledge/system/infra/tracker.md` остаётся источником деталей по трекеру; runbook — процедура |

### E4. Реестр аудита #336

| Поле | Значение |
|---|---|
| Место | `knowledge/system/infra/audit-336-registry.md` |
| Разделов | 6: назначение и границы / сводка / таблица кодов / карточки пакетов / коды без зеркала / changelog |
| Запись кода | `BR-NNN` · одна строка «что сломано → что стало» · компонент-зеркало (ссылка) · пакет (ссылка на карточку) |
| Карточка пакета | имя пакета · волна (A–E) · состав (диапазон BR-кодов) · что исправлено · куда легло зеркало |
| «Коды без зеркала» | явный список кодов, чьё содержание не перенесено в компоненты |
| Объём | **510 уникальных BR-кодов** (530+ упоминаний по методике карты) |

**Инварианты**

| # | Инвариант |
|---|---|
| I-13 | Реестр содержит все 510 уникальных `BR-NNN` из слоя `docs/features/` |
| I-14 | Для каждого кода с зеркалом — ссылка на компонент `knowledge/` |
| I-15 | Для каждого кода без зеркала — явная пометка; строка самодостаточна (называет, что чинить) |
| I-16 | Список «кодов без зеркала» вычислен **после** переноса (≤ 267) |
| I-17 | Каждый `BR-NNN`, упомянутый в `knowledge/` (кроме самого реестра), присутствует в реестре |

### E5. Профиль формата

| | Профиль A — компонент | Профиль B — per-feature |
|---|---|---|
| Где | `knowledge/domains/*/components/*.md`, `knowledge/system/**` | `knowledge/product/features/*.md` |
| Шаблон | `knowledge/templates/component.md` | `knowledge/templates/product-feature.md` |
| Секции | Ответственность / Интерфейсы и Контракты / Логика и Алгоритмы / Зависимости / Связанные ADR | Что делает / Зачем / Как работает / Инварианты / Известные ловушки / Ссылки |
| Проверка | `tools/lint-knowledge.py`, `tools/check-knowledge-structure.sh`, `tools/check-knowledge-cross-links.sh` | `tools/check-feature-doc.sh` |
| Что описывает | устройство системы | поведение продукта |

**Инварианты**

| # | Инвариант |
|---|---|
| I-18 | Разделение — по каталогу, а не по конфигу; пересечения в правилах исключены конструкцией |
| I-19 | `tools/lint-knowledge.py` содержит явный комментарий: `knowledge/product/` обслуживается guard'ем FR-009 (обход линтера — только `knowledge/domains`) |
| I-20 | `knowledge/templates/README.md` описывает `product-feature.md` как шаблон второго профиля |
| I-21 | `tools/check-knowledge-structure.sh` проверяет наличие `knowledge/templates/product-feature.md` |

### E6. Бывшие пути (справочник соответствия)

| Поле | Значение |
|---|---|
| Место | раздел «Бывшие пути» в `knowledge/README.md` (не отдельный файл — D12) |
| Формат | таблица «было → стало», одна строка на старый путь |
| Покрытие | **все** 73 старых пути слоя `docs/`, включая удалённые и свёрнутые (FR-024) |

**Инварианты**

| # | Инвариант |
|---|---|
| I-22 | Для каждого из 73 файлов `docs/` в справочнике есть строка |
| I-23 | Единственный источник правды о соответствии путей — этот раздел (не дублируется в других файлах) |

### E7. Guard (проверка)

| Поле | Значение |
|---|---|
| Место | `tools/check-*.sh` / `tools/check-*.py` + вызовы в CI и pre-commit |
| Вход | корень репозитория (или фикстура) |
| Выход | код 0 — чисто; код ≠ 0 — нарушение с сообщением |
| Фикстуры | `tools/fixtures/<guard>/{vulnerable,clean}/` |

Состав guard'ов — E7a…E7c (см. § 3 и `contracts/guards.md`).

**Инварианты**

| # | Инвариант |
|---|---|
| I-24 | Guard «каталог `docs/` отсутствует» есть в **обоих** workflow (`.forgejo/`, `.github/`) и в pre-commit |
| I-25 | Guard имеет фикстуру «каталог `docs/` появился» (падает) и «чистое дерево» (проходит) |
| I-26 | Существует проверка синхронности двух копий workflow; расхождение — падение |
| I-27 | Guard «BR-код ↔ компонент» зелёный на итоговом дереве |

### E8. ADR-0011

| Поле | Значение |
|---|---|
| Файл | `knowledge/adr/0011-merge-docs-into-knowledge.md` |
| Frontmatter | `id: 0011-merge-docs-into-knowledge`, `title`, `status: Accepted`, `supersedes: 0007-adopt-knowledge-as-ssot`, `superseded_by: null`, `date: 2026-10-29`, `modules: [documentation]`, `tags: [ssot, livedocs, documentation, wayfinder]` |
| После H1 | блок `> **Supersedes**: ADR-0007 — в части п. 2, п. 4 и следствия 5. Остальные решения ADR-0007 остаются в силе.` |
| Секции | Контекст и проблема / Решение / Обоснование (3 отклонённые альтернативы) / Последствия |
| ADR-0007 | правится **только** `superseded_by: 0011-merge-docs-into-knowledge`; тело не трогается (append-only) |
| Реестр | `knowledge/adr/README.md` — строка про 0011 + уточнение про частичную замену 0007 |

**Инварианты**

| # | Инвариант |
|---|---|
| I-28 | ADR-0007 отличается от исходного **ровно** одной строкой frontmatter |
| I-29 | «Последствия» ADR-0011 фиксирует инвариант рантайм-копии скилла (`.dsh/skills/` ранг 100 перекрывает `.agents/` ранг 200) и правку `audit-living-docs/SKILL.md:156` |
| I-30 | ADR-0011 — глобальное решение ⇒ числовая серия (`0011`), не `local-` |

---

## 3. Связи между сущностями

```text
knowledge/README.md ──определяет──► E1 (продуктовый слой), E5 (2 профиля), E6 (бывшие пути)
        │
        └──ссылается──► E2 (per-feature) ──входит в──► E1
                        E3 (runbook)      ──в guidelines/──► E5
                        E4 (реестр)      ──в system/infra/──► E5
                        E7 (guard'ы)     ──проверяют──► E1, E4, E5
                        E8 (ADR-0011)    ──обосновывает──► E1, E5, E7

ADR-0007 ──superseded_by(частично)──► ADR-0011
```

| Отношение | Кардинальность | Следствие |
|---|---|---|
| E1 → E2 | 1 : 22 | индекс `knowledge/product/README.md` перечисляет 22 документа |
| E4 → BR-код | 1 : 510 | каждый код — ровно одна строка реестра |
| E6 → старый путь | 1 : 73 | полное покрытие слоя `docs/` |
| E7a → корень | 1 : 1 | проверка «нет `docs/`» — по дереву |
| E7c → E4 | 1 : 1 | сверка кодов `knowledge/` ↔ реестр |
| E8 → ADR-0007 | частичная замена | 3 пункта отменены, остальное в силе |

---

## 4. Переходы состояний

### 4.1. Guard «каталог `docs/` в корне отсутствует» (E7a)

```text
              ┌──────────────────────────────────────────┐
              │  Чисто: нет каталога docs/ в корне      │
              │  ⇒ код 0, сообщение «OK»                │
              └──────────────────────────────────────────┘
                        ▲
        перенос 73 файлов │  (git mv / удаление)
                        │
   ┌────────────────────────────────────────────────────┐
   │  Нарушение: docs/ существует                       │
   │  (tracked-файл ИЛИ каталог на диске)                │
   │  ⇒ код ≠ 0, сообщение: «docs/ запрещён — см. ADR-0011» │
   └────────────────────────────────────────────────────┘
```

### 4.2. Проверка синхронности workflow (E7b)

```text
   Набор гейтовых команд      Набор гейтовых команд
   .forgejo/workflows/lint.yml  .github/workflows/lint.yml
            │                            │
            └──────────► Сравнение ◄──────┘
                             │
              ┌──────────────┴──────────────┐
              │ равны ⇒ код 0                │
              │ различаются ⇒ код ≠ 0,       │
              │ перечень расхождений         │
              └─────────────────────────────┘
```

### 4.3. Guard «BR-код ↔ компонент» (E7c)

```text
   BR-коды в knowledge/**        BR-коды в реестре
           │                             │
           └────────► сверка ◄───────────┘
                         │
        ┌────────────────┼────────────────┐
        │ код в knowledge/, но не в       │
        │ реестре ⇒ нарушение             │
        ├─────────────────────────────────┤
        │ код в реестре, но не в          │
        │ knowledge/ и не помечен        │
        │ «нет зеркала» ⇒ нарушение       │
        ├─────────────────────────────────┤
        │ иначе ⇒ код 0                  │
        └─────────────────────────────────┘
```

### 4.4. Жизненный цикл per-feature документа (E2)

```text
   docs/features/<slug>.md  ──git mv──►  knowledge/product/features/<slug>.md
            │                                      │
            │ (если продуктовое решение)           │
            ├──────────────────────────────────────┤
            │ (если техническое → свёртка)         ├─► status: active
            ▼                                      │      │
   компонент knowledge/domains/.../…md ◄──────────┘      ├─► deprecated
   (уникальная часть дописана)                          └─► experimental
```

---

## 5. Отображения «было → стало» (сводка E6)

| Категория | Было | Стало | Тип операции |
|---|---|---|---|
| Продуктовые per-feature (25) | `docs/features/<slug>.md` | `knowledge/product/features/<slug>.md` | перенос (`git mv`) |
| Мост-файл (1) | `docs/features/tonality-bpm-songs-table.md` | `knowledge/product/features/tonality-bpm-songs-table.md` | перенос (`git mv`) |
| Мост-файлы (7) | `docs/features/<slug>.md` | компонент `knowledge/domains|system/…` | сворачивание (уникальная часть) + BR-коды в реестр |
| Мост-файлы (2) | `docs/features/{sync-audio-descendants,target-scoped-admin-requests}.md` | — | удаление (полное зеркало) |
| Пакеты аудита (30) | `docs/features/pkg-*.md` и файлы с пометкой аудита | `knowledge/system/infra/audit-336-registry.md` | свёртка в реестр (BR-коды + карточки пакетов) |
| Операционные рецепты (4) | `docs/ops/<slug>.md` | `knowledge/guidelines/runbooks/<slug>.md` | перенос (`git mv`) |
| Трекер | `docs/tracker-setup.md` | `knowledge/guidelines/runbooks/tracker-setup.md` | перенос (`git mv`) |
| API-документация | `docs/api/README.md` | `knowledge/guidelines/kdoc-jsdoc.md` | перенос (`git mv`) |
| Governance | `docs/governance/knowledge-first.md` | `knowledge/guidelines/knowledge-first.md` | перенос (`git mv`) |
| Журнал Pass'ов | `docs/architecture-notes.md` | `archive/docs/architecture-notes.md` | перенос (`git mv`) |
| Устаревший каталог | `knowledge/system/infra/per-feature-docs.md` | — | удаление |
| Генератор API | `docs/api/dokka/`, `docs/api/typedoc-*/` | `build/api-docs/` (gitignored) | перенастройка |

Итого по `docs/features/`: 21 (продуктовые) + 4 (инфраструктурные: 1 удаление + 2 свёртки + 1 в реестр) + 1 (перенос `tonality`) + 7 (свёртка мостов) + 2 (удаление мостов) + 30 (пакеты аудита) = **65**; плюс `docs/ops/` 4 + корень 2 + `api/` 1 + `governance/` 1 = **73** ✓ (SC-009).

---

## 6. Что не меняется

| Объект | Статус |
|---|---|
| Схема БД, `SyncRegistry`, `recordhash` | не затрагивается (миграция документации) |
| Рантайм-код (`karaoke-app`, `karaoke-web`, `webvue3`, `karaoke-public`) | меняются **только** строки `@see` в KDoc/JSDoc (74 в 51 файле) — логика не трогается |
| `archive/docs/**` (43 существующих файла) | вне скоупа; добавляется только `architecture-notes.md` |
| `livedocs/**` | каталога нет в репозитории; упоминания не чинятся |
| `~/.dsh/skills/**`, `/home/nsa/agents-team-srv/**` | read-only |
| Сравнительные таблицы «generic `docs/` ↔ Karaoke `knowledge/`» в скиллах | не трогаются |
