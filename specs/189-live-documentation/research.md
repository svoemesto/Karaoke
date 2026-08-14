# Research: LiveDocs — Design Decisions

**Phase**: 0 (research)
**Date**: 2026-08-14
**Branch**: `189-live-documentation`

## Назначение

Этот документ фиксирует design decisions для фичи 189-live-documentation:
почему выбран конкретный формат/структура, какие альтернативы рассматривались,
и какие best practices применены. Документ — часть плана (`specs/189-live-documentation/`),
соответствует шаблону research.md (Spec Kit).

Все NEEDS CLARIFICATION были разрешены в `/speckit.clarify` (4 вопроса, секция
`## Clarifications` в `spec.md`). Здесь фиксируются только те решения, которые
не требуют вопроса пользователю, но влияют на implementation.

---

## D-1: Формат документов — Markdown + YAML frontmatter + Mermaid

**Decision**: Все LiveDocs — файлы `.md` с опциональным YAML frontmatter
(3 поля: `status`, `slug`, `related`) и Mermaid-блоками для диаграмм.

**Rationale**:
- **Markdown** — стандарт де-факто для технической документации в git-репозиториях.
  Читается в IDE, на GitHub, в любом Markdown preview. Не требует билд-шага.
- **YAML frontmatter** (минимальный) — даёт машиночитаемые метаданные для CI
  валидации (статус документа, связи с другими LiveDocs) без overhead'а полного
  frontmatter (как в Hugo/Jekyll). Минимальный набор: `status`, `slug`, `related`.
- **Mermaid** — диаграммы C4 как код, рендерятся GitHub автоматически
  (`\`\`\`mermaid` блок), редактируются через [mermaid.live](https://mermaid.live),
  не требуют внешних инструментов (Visio, draw.io) и не бинарные (можно diff'ить).

**Alternatives considered**:
- ❌ **MkDocs + Material theme**: требует Python-окружения + зависимости.
  Противоречит FR-018 (без новых зависимостей).
- ❌ **Docusaurus**: требует Node.js build. Overkill для внутренней документации.
- ❌ **Hugo**: бинарный билд, тяжёлый. Не git-native.
- ❌ **Antora**: для multi-repo документации, не наш случай.
- ❌ **AsciiDoc**: менее распространён, чем Markdown, порог входа выше.
- ❌ **PlantUML**: требует Java-runtime + Graphviz. Mermaid легче (рендерится на клиенте).
- ❌ **Visio / draw.io (бинарные)**: не diff'ятся, не годятся для git.

**Best practice**: ADR-001 [Architecture Decision Records] в `docs/adr/` (TODO
Pass 16+, упомянут в конституции). Наш подход — минимальный YAML frontmatter —
стандарт в OpenAPI Spec, AsyncAPI, Hugo/Jekyll, MDX. Не изобретаем велосипед.

---

## D-2: Структура — 3 слоя (SDD / DDD / C4) в общей директории

**Decision**: LiveDocs = 3 слоя в одной директории `livedocs/`:
- `livedocs/features/` — SDD-сводки фич (1-2 страницы на фичу).
- `livedocs/domain/` — DDD bounded contexts + ubiquitous language.
- `livedocs/architecture/` — C4 диаграммы L1/L2/L3 + topic-документы.

**Rationale**:
- Три подхода (SDD/DDD/C4) — стандарт в индустрии (Simon Brown — C4, Eric Evans —
  DDD, Spec Kit — SDD). Объединение даёт 3 угла зрения на одну систему.
- **SDD** (Specification-Driven Development) — отвечает на «что делает фича X»
  (user stories, FR/SC).
- **DDD** (Domain-Driven Design) — отвечает на «какие концепции живут в системе»
  (bounded contexts, ubiquitous language, aggregate roots).
- **C4** (Context → Container → Component → Code) — отвечает на «из чего система
  состоит и как взаимодействует» (архитектурные диаграммы 4 уровней).
- Объединение в одной директории упрощает навигацию: AI-агент видит все 3 слоя
  сразу, не ищет «где у нас архитектура, а где фичи».

**Alternatives considered**:
- ❌ **3 отдельные директории верхнего уровня** (`features/`, `domain/`, `architecture/`
  в корне): размазывает документацию по репозиторию, конфликтует с существующими
  `docs/features/`, `docs/strategy/`. Не объединено логически.
- ❌ **Только DDD или только C4** (без SDD): недостаточно. SDD закрывает вопрос
  «что эта фича делает», DDD/C4 — «из чего состоит». Без SDD агенту пришлось бы
  читать длинные спеки.
- ❌ **C4 только до L2** (без L3): для AI-агента, который редактирует код внутри
  karaoke-app, L3 (Component) критичен — без него непонятно, «где живёт X».

**Best practice**: «Living Documentation» (Cyrille Martraire, 2019) — книга
прямо об этом паттерне: единый каталог, 3 угла зрения, drill-down от сводок
к деталям. Применяем минимальную версию подхода (без полноценного генератора).

---

## D-3: Bounded contexts для первого раунда — 5 штук

**Decision**: Первая итерация описывает 5 bounded contexts:
1. **catalog** — Песня, Альбом, Исполнитель, Жанр (самая большая доменная область,
   ~18k записей на проде).
2. **processing** — Караоке-видео, MLT, Demucs, async-очередь задач.
4. **publishing** — Эфир, Подписка, Premium, доступ.
5. **identity** — Пользователь, Авторизация, сессии, JWT.
6. **editorial** — Редакторы, Задания (182), авто-конвейер (184).

**Rationale**:
- **catalog** — ядро бизнес-логики, главный домен. Без него агенту непонятно,
  «что такое Song/Album/Author». Самая частая точка входа в кодовую базу.
- **processing** — уникальная для проекта подсистема (karaoke-видео через MLT).
  Агенту без этого контекста не понять, что такое `RenderMp4Params`, `Demucs`,
  `Sheetsage`.
- **publishing** — монетизация + free/premium логика, активная зона роста
  (см. `docs/strategy/growth.md`). Без этого контекста агенту не понятна разница
  между эфир-песней и подписочной.
- **identity** — пользователи и авторизация. Не очевидно (в `karaoke-web`
  используется Spring Security + cookies, в `karaoke-public` — другой flow).
- **editorial** — новый контекст (после spec 182, 184). Активная зона
  разработки, агенту важно знать про редакторов и self-assign.

**Alternatives considered**:
- ❌ **Только 3 контекста** (catalog, processing, publishing): пропускаем identity
  и editorial. Но это самые активные зоны прямо сейчас (Q3 2026), агенту они
  нужны в первую очередь.
- ❌ **10 контекстов** (детализировать каждый модуль): слишком много для first slice,
  риск застрять. 5 — оптимальный баланс (см. SC-007).
- ❌ **Один «главный» контекст на весь проект**: теряется смысл DDD (bounded
  contexts = границы между доменами).

**Best practice**: Eric Evans (DDD) рекомендует начинать с **Core Domain**
(того, что даёт бизнесу конкурентное преимущество). В Karaoke core domain —
это `catalog` (песни) + `processing` (производство караоке-видео). Остальные 3 —
supporting / generic.

---

## D-4: 5 фич для proof-of-concept миграции

**Decision**: мигрировать в `livedocs/features/` фичи:
- **182-editor-self-assign-tasks** — крупная фича (10 сценариев в спеке),
  затрагивает несколько bounded contexts (catalog + identity + editorial).
- **184-approve-status-choice** — фича про условный запуск конвейера по
  выбору idStatus. Хороший пример cross-cutting concern.
- **185-song-dto-audit-sponsr-remove** — изменение API (DTO), затрагивает
  Jackson-конвенции (см. Q&A в `AGENTS.md`).
- **186-zakroma-songs-fast-load** — оптимизация производительности (загрузка
  песен для редакторов).
- **187-site-traffic-anomaly-investigation** — недавняя аналитическая фича
  (Phase 8 investigation), полезна как пример «research» спеки.

**Rationale**:
- Покрывают **разные типы фич**: feature (182), conditional pipeline (184),
  API change (185), performance (186), investigation (187). Это даёт репрезентативную
  выборку для шаблона.
- Все 5 — **недавние** (Pass 51-3 — Pass 61, 2026-08-13), их спеки ещё живые
  и понятные (не потеряли актуальность).
- Все 5 — **нетривиальные** (≥ 2 FR, ≥ 3 acceptance scenarios), демонстрируют
  как мигрировать «средний» случай, не только самые простые фичи.

**Alternatives considered**:
- ❌ **Старые фичи** (001-022): спеки могут быть протухшими, плохой пример
  для нового процесса.
- ❌ **Только самые новые** (186-188): слишком однородная выборка, не показывает
  разнообразие.
- ❌ **Все фичи Pass 50-60** (15 фич): слишком много для first slice (SC-006 =
  ≥ 5, не 15).

**Best practice**: при создании нового процесса/шаблона — брать **3-5 репрезентативных
примеров**, покрывающих разные типы случаев. Это даёт основу для шаблона без
overengineering.

---

## D-5: YAML frontmatter — минимальный (3 поля)

**Decision**: frontmatter содержит только 3 поля:

```yaml
---
status: Active | Deprecated | Archived
slug: <kebab-case-имя-файла>
related:
  - <путь-к-другому-LiveDoc>
  - <путь-к-исходной-спеке-specs-NNN>
---
```

**Rationale**:
- **status** — нужно для пометки устаревших/архивных документов (UI + CI).
- **slug** — нужно для уникальной идентификации (CI валидация, поиск).
- **related** — нужно для навигации между LiveDocs (cross-references).

**Что НЕ включаем** (и почему):
- ❌ `owner` — на старте один владелец (команда). Добавим, когда появится
  разделение ownership (Pass 2+).
- ❌ `last-reviewed` — требует процесса review (Pass 2+). Сейчас просто
  `git log -1 <file>` показывает дату.
- ❌ `version` — версионирование через git, semver для LiveDocs не нужно.
- ❌ `audience` — на старте все документы для всех (AI-агент + разработчик).

**Alternatives considered**:
- ❌ **Полный frontmatter** (как Hugo/Jekyll: title, date, author, tags, categories,
  summary, weight, ...): overkill. 80% не используются на старте, повышают
  cognitive load.
- ❌ **Без frontmatter**: теряем machine-readable метаданные. Статус и связи
  пришлось бы парсить из тела Markdown (хрупко).

**Best practice**: принцип YAGNI + минимальный набор метаданных для задач
проекта. Расширяемость — по необходимости, не впрок.

---

## D-6: Валидация через bash-скрипт (а не через отдельный tool)

**Decision**: `tools/check-livedocs-structure.sh` — POSIX bash-скрипт, проверяющий:

```bash
#!/usr/bin/env bash
# 1. Наличие обязательных директорий
for dir in livedocs/{features,domain,architecture,templates}; do
  test -d "$dir" || { echo "MISSING: $dir"; exit 1; }
done

# 2. Наличие обязательных файлов-манифестов
for f in livedocs/README.md livedocs/INDEX.md \
         livedocs/features/README.md livedocs/domain/README.md \
         livedocs/architecture/README.md livedocs/templates/README.md; do
  test -f "$f" || { echo "MISSING: $f"; exit 1; }
done

# 3. Наличие ≥ 5 файлов в features/ и domain/
features_count=$(ls -1 livedocs/features/*.md 2>/dev/null | wc -l)
test "$features_count" -ge 5 || { echo "NEED ≥ 5 features, found $features_count"; exit 1; }

# 4. Наличие всех 3 уровней C4
for f in L1-system-context.md L2-containers.md L3-components.md; do
  test -f "livedocs/architecture/$f" || { echo "MISSING: $f"; exit 1; }
done

# 5. Наличие frontmatter в каждом .md файле (status, slug)
for f in $(find livedocs -name '*.md' -not -name 'README.md'); do
  head -1 "$f" | grep -q '^---$' || { echo "NO FRONTMATTER: $f"; exit 1; }
done

# 6. Длина AGENTS.md ≤ 100 строк
agents_lines=$(wc -l < AGENTS.md)
test "$agents_lines" -le 100 || { echo "AGENTS.md = $agents_lines lines, need ≤ 100"; exit 1; }
```

**Rationale**:
- **bash + POSIX** — никаких новых зависимостей (FR-018). Скрипт запускается
  на любой Linux-машине, в любом GitHub Actions runner.
- **Простые проверки** (`test -d`, `test -f`, `wc -l`) — не нужен сложный
  парсер Markdown, скрипт читаемый и поддерживаемый.
- **Быстрый fail** — exit code ≠ 0 блокирует merge.

**Alternatives considered**:
- ❌ **markdownlint-cli** (npm): новая зависимость. Не POSIX (Node.js).
- ❌ **prettier --check** (для Markdown): prettier форматирует, но не валидирует
  семантику (наличие конкретных файлов, SC-006/007/008).
- ❌ **Python-скрипт**: новая зависимость (Python не гарантирован в окружении).
  bash POSIX — гарантирован.
- ❌ **Валидация в Gradle**: overkill, не нужен build step для документации.

**Best practice**: «Bash is glue» — для простых проверок структуры bash
предпочтительнее, чем вводить новый tool. Сложная логика (YAML-парсинг) — не
нужна в first slice, `head -1 + grep '^---$'` достаточно.

---

## D-7: Стратегия сокращения AGENTS.md (миграция, не удаление)

**Decision**: `AGENTS.md` сокращается до ≤ 100 строк за счёт **миграции деталей**
в LiveDocs, а не удаления. Структура:

**Оставить в AGENTS.md** (governance, агенту критично при старте):
1. Язык общения (русский).
2. Иерархия документов (constitution > AGENTS.md > ...).
3. CI-gate для master (feature-branch workflow).
4. Ограничения агента (запрещено/разрешено).
5. Lifecycle feature-ветки (не удалять после merge).
6. **Одна ссылка на LiveDocs** (`livedocs/README.md` + `livedocs/INDEX.md`)
   как **первый** источник при старте сессии.
7. Краткий список «куда идти» (LiveDocs → specs → features → code).

**Мигрировать в LiveDocs** (`livedocs/architecture/` + `livedocs/domain/`):
- Детальные Q&A (Jackson `is`-prefix, Dockerfile ловушки, KDoc backticks).
- Описания паттернов (таблица пагинации, dual design karaoke-public, etc.).
- Описания модулей (которые сейчас разбросаны по Q&A).

**Не трогать** в этой итерации (out of scope, Pass 2+):
- `constitution.md` — остаётся как есть (NON-NEGOTIABLE).
- `DEVELOPMENT.md` — описание архитектуры и команд; может быть интегрирован
  в `livedocs/architecture/L1-L3` в Pass 2.

**Rationale**:
- Стратегия «миграция, не удаление» сохраняет historical context (Q&A — это
  реальные баги, потеря информации недопустима).
- `AGENTS.md` остаётся **короткой картой**, LiveDocs — **детальным гайдом**.
  Это разделение снижает cognitive load: «куда идти?» — в AGENTS.md,
  «как это работает?» — в LiveDocs.
- Иерархия документов в AGENTS.md (приоритет) **расширяется** LiveDocs-слоем
  как первым приоритетом для технических вопросов.

**Alternatives considered**:
- ❌ **Удалить AGENTS.md и заменить на LiveDocs**: ломает существующие tooling
  (opencode / Claude Code / Cursor читают `AGENTS.md` по конвенции).
- ❌ **Не трогать AGENTS.md**: не достигаем SC-002 (≤ 100 строк).
- ❌ **Сократить до 50 строк**: слишком агрессивно, потеряем governance-секции.

**Best practice**: «Single source of truth, but multiple entry points» — разные
документы для разных задач, без дублирования. AGENTS.md = governance + индекс,
LiveDocs = детали. Это стандарт в больших проектах (Linux kernel `README`,
Kubernetes `docs/`, Apache `README` + `docs/`).

---

## D-8: Интеграция с CI — один шаг в `lint.yml`

**Decision**: добавить в `.github/workflows/lint.yml` один шаг
(`bash tools/check-livedocs-structure.sh`) **после** существующих 7 проверок.
Не вводить отдельный workflow — это не отдельная «фича», это часть lint.

**Rationale**:
- Минимальное изменение в существующем workflow (1 строка + 1 шаг).
- Не дублирует существующие 7 проверок ktlint/ESLint/docs-structure.
- Если проверка упадёт — PR блокируется тем же механизмом, что и остальные lint.

**Alternatives considered**:
- ❌ **Отдельный workflow** (`livedocs-lint.yml`): шум в CI badge, замедление
  (2 jobs вместо 1). Не оправдано для одной проверки.
- ❌ **pre-commit hook** (не CI): pre-commit есть в проекте (см. FR-007), но
  LiveDocs-валидация — медленнее обычных pre-commit, и должна быть **обязательной**
  в CI, не optional в pre-commit.

**Best practice**: все проверки документации — в одном lint workflow. Это
стандарт в open-source (`.github/workflows/lint.yml` обычно содержит всё:
линтеры, форматтеры, валидаторы документации).

---

## D-9: Шаблоны для новых записей

**Decision**: 5 шаблонов в `livedocs/templates/`:
1. `feature-summary.md` — для `livedocs/features/<NNN-slug>.md`.
2. `bounded-context.md` — для `livedocs/domain/<context>.md`.
3. `c4-level-L1.md` — для `livedocs/architecture/L1-system-context.md`.
4. `c4-level-L2.md` — для `livedocs/architecture/L2-containers.md`.
5. `c4-level-L3.md` — для `livedocs/architecture/L3-components.md`.

Плюс `livedocs/templates/README.md` — индекс шаблонов с примерами использования.

**Rationale**:
- Каждый шаблон — заготовка структуры (заголовки секций), не готовый текст.
- Агент при добавлении новой фичи копирует `feature-summary.md` →
  `livedocs/features/<NNN-slug>.md` и заполняет секции.
- Шаблон снижает cognitive load: «что должно быть в сводке фичи?» —
  ответ в `feature-summary.md`.

**Alternatives considered**:
- ❌ **Без шаблонов**: каждая запись изобретает свою структуру → inconsistency,
  CI валидация невозможна.
- ❌ **Один общий шаблон** для всех типов: слишком generic, не отражает
  специфику C4 L1 vs L3 vs bounded context.

**Best practice**: «Scaffolding» — дать структуру, не контент. Это паттерн
из Rails generators, Yeoman, Cookiecutter.

---

## D-10: Расположение `docs/livedocs-conventions.md`

**Decision**: `docs/livedocs-conventions.md` (не в `livedocs/`, а в `docs/`).
Это **мета-документация** о LiveDocs, а не сам LiveDoc.

**Rationale**:
- Конвенции (FR-012) описывают процесс работы с LiveDocs — это документ
  для **разработчика**, объясняющий «как добавить новую сводку фичи».
- `docs/` уже содержит мета-документы: `docs/onboarding.md`, `docs/strategy/`,
  `docs/claude-code-setup.md`. LiveDocs-conventions логически рядом с ними.
- `livedocs/` — каталог LiveDocs (артефакты), `docs/livedocs-conventions.md` —
  описание каталога (мета).

**Alternatives considered**:
- ❌ **`livedocs/CONVENTIONS.md`**: смешивает артефакты и мета-документацию.
  Плохая навигация (в `livedocs/` должны быть только LiveDocs-файлы).
- ❌ **`AGENTS.md`**: возвращает детали, которых мы пытаемся избежать.

**Best practice**: «About vs In» — документация о системе (`docs/`) vs сама
система (`livedocs/`). Аналогия: `docs/api/typedoc-*/` описывает API,
`src/` содержит API.

---

## Резюме решений

| # | Решение | Обоснование | Альтернативы |
|---|---------|-------------|--------------|
| D-1 | Markdown + YAML + Mermaid | git-native, без зависимостей | MkDocs, Docusaurus, Hugo |
| D-2 | 3 слоя в одной директории | единая навигация | 3 корневые директории |
| D-3 | 5 bounded contexts | core domain + активные зоны | 3 / 10 контекстов |
| D-4 | 5 фич для миграции | разные типы + недавние | старые / все фичи |
| D-5 | Минимальный frontmatter (3 поля) | YAGNI | полный / без frontmatter |
| D-6 | bash-скрипт валидации | без зависимостей | markdownlint, prettier |
| D-7 | Миграция, не удаление AGENTS.md | сохранить history | удалить / не трогать |
| D-8 | Один шаг в `lint.yml` | минимум изменений | отдельный workflow |
| D-9 | 5 шаблонов | scaffolding | без шаблонов / 1 общий |
| D-10 | `docs/livedocs-conventions.md` | мета-документ в `docs/` | внутри `livedocs/` |

Все NEEDS CLARIFICATION разрешены (`spec.md` секция Clarifications). Никаких
открытых вопросов для Phase 1 не остаётся.