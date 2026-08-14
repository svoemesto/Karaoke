# Feature Specification: Live Documentation (LiveDocs)

**Feature Branch**: `189-live-documentation`
**Created**: 2026-08-14
**Status**: Draft
**Input**: User description: "Задача - создание и внедрение в проект Live Documentation, дальнейшая поддержка его в актуальном состоянии. Давай обсудим. Так же возможно обсуждение внедрения не только SDD, но и DDD и C4.
Надо чтобы агенты при работе в первую очередь обращались к LiveDocs, а не начинали лазить по старым спецификациям (которые, в силу обстоятельств, устаревают). Грамотно составленная Live Docs позволит сократить конституцию, AGENTS.md и DEVELOPMENT.md, которые сейчас разрослись уже до того, что контент каждой новой сессии занимает больше 40K токенов.
Работать нужно сразу в новой ветке, потому что к этой задаче будем возвращаться регулярно с разных компьютеров. Надо будет вести эту задачу по проекту. Будем постепенно обсуждать все тонкости концепций, модулей, их работы, интерфейса и т.п.
Возможно имеет смысл сегодня обсудить и создать полную структуру лайвдоков, а в дальнейшем - уже заполнять её."

## Обзор

**LiveDocs** — это единый актуальный источник знаний о проекте для AI-агентов и
разработчиков. Объединяет три подхода:

| Подход | Что даёт | Артефакт в LiveDocs |
|--------|----------|---------------------|
| **SDD** (Specification-Driven Development) | Фичи как спецификации с user stories, FR, SC | `features/<NNN-slug>.md` |
| **DDD** (Domain-Driven Design) | Единый язык (ubiquitous language), bounded contexts, aggregate roots | `domain/<context>.md` |
| **C4** (Context → Container → Component → Code) | Архитектура от «кто пользуется системой» до «какой класс» | `architecture/<level>.md` + Mermaid-диаграммы |

**Главная цель**: при старте сессии AI-агент идёт в LiveDocs **до** того, как
лезть в устаревшие спецификации (`specs/NNN-*`), `AGENTS.md`, `constitution.md`,
`DEVELOPMENT.md`. Это позволит:

- Сократить токены стартовой сессии с ~40K до ≤ 30K (цель).
- Перестать дублировать знания в `AGENTS.md` (с ~230 строк → ≤ 100).
- Сохранить единый источник правды (когда обновляется код — обновляется LiveDocs).

**Не цель** (out of scope для первого раунда):
- Авто-генерация LiveDocs из кода (TODO Pass 2+).
- Публичный сайт с LiveDocs (только внутренний формат, рендерится через IDE/Markdown preview).
- Полная миграция 188 существующих спек — только выборочно (см. US4).
- Замена всех per-feature документов (`docs/features/*.md`) — они остаются как drill-down.

## Clarifications

### Session 2026-08-14

- **Q1**: Где физически должна жить директория LiveDocs в репозитории? → **A**: `livedocs/` в корне (отдельная директория, как в FR-001).
- **Q2**: Какой Definition of Done для первого merge `189-live-documentation`? → **A**: Всё сразу — каркас + ≥ 5 фич миграции + ≥ 5 bounded context'ов + все 3 уровня C4 (L1/L2/L3) + полная миграция AGENTS.md. Один большой PR, а не серия инкрементальных.
- **Q3**: Какое каноническое написание LiveDoc / LiveDocs / Live Document использовать во всех артефактах? → **A**: `LiveDocs` (система/каталог) + `LiveDoc` (один документ). CamelCase, без пробелов, как в OpenAPI/OpenAPI Specification.
- **Q4**: Как именовать файлы bounded context'ов в `livedocs/domain/`? → **A**: простые kebab-case без нумерации и префиксов — `catalog.md`, `processing.md`, `publishing.md`, `identity.md`, `editorial.md`. Алфавитная сортировка в IDE = логический порядок.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — AI-агент при старте сессии читает LiveDocs первым (Priority: P1)

AI-агент (opencode, Claude Code, Cursor, ...) начинает новую сессию по задаче в
проекте Karaoke. Первым делом он **обязан** прочитать LiveDocs-манифест и
получить указатель на нужные слои (Domain / Architecture / Feature), а не лезть
в `AGENTS.md` или старые спеки.

**Why this priority**: Без этого правила LiveDocs не используются — агенты
продолжат ходить в устаревшие источники, токены не сократятся, дублирование
не исчезнет.

**Independent Test**: Открыть свежую сессию, дать задачу «опиши модуль X».
Агент должен ответить, опираясь на `livedocs/domain/<context>.md` (не на
`docs/features/*.md` или `specs/NNN-*/spec.md`).

**Acceptance Scenarios**:

1. **Given** свежая сессия, **When** агент стартует, **Then** он читает
   `livedocs/README.md` (манифест) + `livedocs/INDEX.md` (карта слоёв).
2. **Given** задача про модуль Song, **When** агент ищет контекст, **Then** он
   читает `livedocs/domain/catalog.md` (DDD bounded context) и
   `livedocs/architecture/components.md` (C4 Component).
3. **Given** задача «объясни что делает фича 184», **When** агент ищет описание,
   **Then** он читает `livedocs/features/184-approve-status-choice.md`
   (SDD-сводка), а **НЕ** лезет в `specs/184-approve-status-choice/spec.md`.

---

### User Story 2 — Структура LiveDocs создана и индексирована (Priority: P1)

В корне проекта появляется директория `livedocs/` с фиксированной структурой:
манифест, индекс, шаблоны, и 3 слоя (`domain/`, `architecture/`, `features/`).
Структура — полная (т.е. папки созданы), но заполнение — постепенное.

**Why this priority**: Без фиксированной структуры каждый агент будет
изобретать свой формат, документы разъедутся.

**Independent Test**: Открыть `livedocs/` в IDE — все ожидаемые директории и
файлы-манифесты существуют; запустить `bash tools/check-livedocs-structure.sh`
— exit code 0.

**Acceptance Scenarios**:

1. **Given** репозиторий, **When** я открываю `livedocs/`, **Then** я вижу:
   - `README.md` (назначение + навигация)
   - `INDEX.md` (карта слоёв: domain / architecture / features)
   - `templates/` (шаблоны для новых записей)
   - `domain/` (DDD bounded contexts)
   - `architecture/` (C4 уровни L1/L2/L3)
   - `features/` (SDD сводки существующих фич)
2. **Given** пустой проект, **When** создана структура, **Then** все 6
   директорий + 3 манифест-файла существуют.
3. **Given** CI прогоняется, **When** запускается lint, **Then** существует
   `tools/check-livedocs-structure.sh` который валидирует наличие всех
   обязательных файлов.

---

### User Story 3 — LiveDocs обновляются по pull request (Priority: P2)

При изменении кода (новый модуль, изменение API, изменение bounded context)
агент **обязан** в этом же PR обновить соответствующую запись в LiveDocs
(DDD/C4/Feature). Это правило зафиксировано в `AGENTS.md` (в сокращённой
форме — одна ссылка на LiveDocs).

**Why this priority**: Без процесса обновления LiveDocs протухнут за 2-3 месяца
и превратятся в ещё один слой дублирования.

**Independent Test**: В PR, который меняет модуль X, в diff есть
соответствующее изменение в `livedocs/`. Проверка через `gh pr diff <N> |
grep livedocs/`.

**Acceptance Scenarios**:

1. **Given** PR с новым модулем, **When** он отправлен, **Then** в diff есть
   изменение в `livedocs/domain/` или `livedocs/architecture/`.
2. **Given** PR с изменением API, **When** он отправлен, **Then** в diff есть
   обновление соответствующей feature-сводки в `livedocs/features/`.
3. **Given** агент меняет код, **When** он завершает задачу, **Then** в финальном
   ответе он упоминает «LiveDocs обновлены: <путь>».

---

### User Story 4 — Миграция существующих спек и per-feature документов (Priority: P2)

188 существующих спек (`specs/001-*` ... `specs/188-*`) и per-feature документы
(`docs/features/*.md`) — **остаются как drill-down** (детальная история).
LiveDocs содержат только **актуальные сводки** (1-2 страницы на фичу/модуль).

**Why this priority**: Полная миграция 188 документов займёт 2-3 месяца —
приоритет сейчас создать каркас + 1-3 примера миграции (proof of concept),
а миграцию оставить как ongoing task.

**Independent Test**: Открыть `livedocs/features/` — есть сводка для **5 фич**
(182, 184, 185, 186, 187). Каждая сводка — ≤ 2 страницы.

**Acceptance Scenarios**:

1. **Given** существующая спека `specs/184-.../spec.md`, **When** агент
   мигрирует её, **Then** создаётся `livedocs/features/184-approve-status-choice.md`
   с 1-2 страницами актуального summary, а оригинал остаётся как archive.
1. **Given** пять фич (182, 184, 185, 186, 187), **When** миграция завершена,
   **Then** в `livedocs/features/` существует пять файлов-сводок, каждый ≤ 2
   страницы, каждый со ссылкой на исходную спеку.
2. **Given** существующий `docs/features/dual-db-sync.md`, **When** агент
   мигрирует его, **Then** создаётся `livedocs/architecture/data-sync.md` (если
   это шире одной фичи) или ссылка в соответствующем domain-файле.
3. **Given** LiveDocs-запись, **When** агент ссылается на неё, **Then** в ней
   есть ссылка на оригинальный артефакт (`specs/...` или `docs/features/...`)
   для drill-down.

---

### User Story 5 — AGENTS.md и constitution.md ссылаются на LiveDocs (Priority: P2)

Текущие `AGENTS.md` (230 строк) и `.specify/memory/constitution.md` содержат
много **деталей реализации** (ловушки ffmpeg, JSON-ключи, Dockerfile) — эти
детали переезжают в LiveDocs (`livedocs/architecture/` + `livedocs/domain/`).
В AGENTS.md остаётся только governance (правила workflow, CI-gate, иерархия).

**Why this priority**: Без сокращения AGENTS.md стартовая сессия не станет
дешевле. Главный «потребитель токенов» — это и есть AGENTS.md + constitution.

**Independent Test**: Подсчитать `wc -l AGENTS.md` после миграции. Должно
быть ≤ 100 строк (с ~230 сейчас).

**Acceptance Scenarios**:

1. **Given** LiveDocs созданы, **When** правило мигрирует, **Then** в AGENTS.md
   остаётся **одна строка-ссылка** вместо детального описания.
2. **Given** новая сессия, **When** агент стартует, **Then** он читает
   `livedocs/README.md` (≤ 1 страница) + `AGENTS.md` (≤ 100 строк) + constitution
   (≤ 150 строк) = ≤ 5K токенов на онбординг (сейчас ~40K).

---

### User Story 6 — DDD ubiquitous language (Priority: P3)

В `livedocs/domain/` создаются bounded context'ы с глоссарием. Например:
`catalog` (Песня, Альбом, Исполнитель, Жанр), `processing` (Караоке-видео, MLT,
Demucs), `publishing` (Эфир, Подписка, Premium).

**Why this priority**: Без единого языка агенты путают «song» vs «track» vs
«karaoke-video» vs «multitrack» — это видно в логах (см. Q&A по Jackson
`is`-prefix — путаница boolean-полей).

**Independent Test**: Открыть `livedocs/domain/catalog.md` — список терминов
с определениями. Поиск по «track» в кодовой базе даёт ≥ 3 разных значений
(track в audio, track в journey, track в karaoke) — все они определены в
glossary.

**Acceptance Scenarios**:

1. **Given** bounded context, **When** агент читает его, **Then** в файле есть
   раздел «Ubiquitous Language» с терминами и определениями.
2. **Given** термин в коде, **When** агент сомневается в значении, **Then** он
   идёт в `livedocs/domain/<context>.md` за определением.
3. **Given** новый термин в коде, **When** он появляется, **Then** агент
   добавляет его в glossary в том же PR.

---

### User Story 7 — C4 архитектурные диаграммы (Priority: P3)

В `livedocs/architecture/` создаются C4-диаграммы трёх уровней:
- **L1 Context**: Karaoke (svoemesto) ↔ внешние системы (MinIO, Postgres,
  Ollama, Sheetsage, Browser).
- **L2 Container**: karaoke-app (Spring Boot), karaoke-web (Spring Boot
  Thymeleaf), karaoke-public (Vue 3 SPA), webvue3 (Vue 3 admin SPA),
  Postgres, MinIO.
- **L3 Component**: внутри karaoke-app — Model layer, MLT layer, Queue layer,
  LLM layer.

**Why this priority**: C4 — стандарт, но не критичен для first slice (без него
можно стартовать, постепенно добавлять).

**Independent Test**: Открыть `livedocs/architecture/L1-context.md` — есть
Mermaid-диаграмма. В README архитектурного слоя есть ссылка на
[Mermaid Live Editor](https://mermaid.live/) для редактирования.

**Acceptance Scenarios**:

1. **Given** архитектурный слой, **When** агент читает его, **Then** в файле
   есть Mermaid-блок с диаграммой.
1. **Given** first slice завершён, **When** проверяется `livedocs/architecture/`,
   **Then** существуют файлы для **всех трёх уровней** C4: `L1-system-context.md`,
   `L2-containers.md`, `L3-components.md`.
2. **Given** новый контейнер (например, новый микросервис), **When** он
   добавляется, **Then** в том же PR обновляется L2-диаграмма.
3. **Given** изменение компонента, **When** оно происходит, **Then** L3-слой
   обновляется.

---

### Edge Cases

- **Что делать с устаревшими спеками** (когда LiveDocs появились, а спека уже
  протухла)? Оставить как archive; в LiveDocs — только актуальные сводки. Не
  пытаться мигрировать всё сразу.
- **Что если LiveDocs конфликтуют с AGENTS.md** (правило описано и там, и там)?
  Приоритет — у LiveDocs; AGENTS.md должен ссылаться, не дублировать.
- **Что если агент не знает, какой слой LiveDocs ему нужен**? `livedocs/INDEX.md`
  содержит decision tree («задача про фичу → features/, про модуль → domain/,
  про систему в целом → architecture/»).
- **Что если структура LiveDocs изменилась**? Обновить `INDEX.md` +
  `check-livedocs-structure.sh` + нотис в `AGENTS.md` (но не дублировать).
- **Что если в проекте нет ни одной записи в LiveDocs**? OK в первом раунде —
  создаются шаблоны + манифест, заполнение постепенное.
- **Что если две фичи ссылаются на один модуль**? В `livedocs/domain/<context>.md`
  — раздел «Related features» со списком.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: LiveDocs MUST жить в директории `livedocs/` в корне репозитория.
- **FR-002**: LiveDocs MUST содержать манифест `livedocs/README.md`
  (назначение + навигация, ≤ 1 страница).
- **FR-003**: LiveDocs MUST содержать `livedocs/INDEX.md` с картой слоёв
  (domain / architecture / features) и decision tree для выбора.
- **FR-004**: LiveDocs MUST включать слой **SDD** — `livedocs/features/`
  с шаблоном сводки фичи (≤ 2 страницы).
- **FR-005**: LiveDocs MUST включать слой **DDD** — `livedocs/domain/`
  с bounded context'ами и ubiquitous language glossary.
- **FR-006**: LiveDocs MUST включать слой **C4** — `livedocs/architecture/`
  с Mermaid-диаграммами L1/L2/L3.
- **FR-007**: LiveDocs MUST иметь `livedocs/templates/` с шаблонами для
  каждой записи (feature summary, bounded context, C4 level).
- **FR-008**: AI-агенты MUST читать `livedocs/README.md` первым при старте
  сессии (правило в `AGENTS.md` — короткая ссылка, не дублирование).
- **FR-009**: `AGENTS.md` MUST сократиться до ≤ 100 строк (с ~230) за счёт
  миграции деталей в LiveDocs.
- **FR-010**: `.specify/memory/constitution.md` MUST оставаться кратким
  (≤ 150 строк, текущий размер) и ссылаться на LiveDocs для деталей.
- **FR-011**: Должен существовать `tools/check-livedocs-structure.sh`,
  валидирующий наличие всех обязательных файлов и директорий.
- **FR-012**: Должен существовать `docs/strategy/livedocs.md` (или
  `docs/livedocs-conventions.md`) с правилами именования и процесса
  обновления LiveDocs.
- **FR-013**: При добавлении новой фичи в `livedocs/features/` должна
  быть ссылка на исходную спеку (`specs/<NNN>/spec.md`) для drill-down.
- **FR-014**: При изменении bounded context в `livedocs/domain/<context>.md`
  в том же PR должна быть ссылка на код (KDoc `@see` или путь к файлу).
- **FR-015**: CI MUST запускать `tools/check-livedocs-structure.sh` при
  каждом PR (добавить в `.github/workflows/lint.yml`).
- **FR-016**: Существующие спеки (`specs/001-*` ... `specs/188-*`) MUST
  оставаться как drill-down archive; **выборочная** миграция 5 фич (proof of
  concept) — в первом раунде; массовая миграция остальных — ongoing backlog
  (после first slice, отдельными PR).
- **FR-017**: Существующие `docs/features/*.md` MUST интегрироваться в
  LiveDocs (либо переехать в `livedocs/architecture/` / `livedocs/domain/`,
  либо получить ссылку в соответствующем LiveDoc).
- **FR-018**: LiveDocs MUST использовать только Markdown + YAML frontmatter
  + Mermaid (без новых зависимостей, без MkDocs/Docusaurus в первом раунде).

### Key Entities

- **LiveDoc Feature Summary** (`livedocs/features/<NNN-slug>.md`): SDD-сводка
  одной фичи. Атрибуты: номер фичи, slug, статус (Active/Deprecated/Archived),
  описание (1 абзац), user stories (краткий список), FR/SC (указатель на
  оригинальную спеку), Related code (модули/файлы), Related LiveDocs (другие
  слои).
- **LiveDoc Bounded Context** (`livedocs/domain/<context>.md`): DDD-context
  проекта. Атрибуты: имя, описание (1 абзац), Aggregate roots, Entities,
  Value Objects, Domain events, Ubiquitous language (глоссарий), Related
  features (список фич, работающих с этим context), Related code (модули).
- **LiveDoc C4 Level** (`livedocs/architecture/<L1|L2|L3>.md` или
  `<topic>.md`): архитектурная диаграмма. Атрибуты: уровень (L1/L2/L3),
  описание, Mermaid-диаграмма, Related LiveDocs (другие уровни/контексты).
- **LiveDoc Manifest** (`livedocs/README.md`): корневой документ — назначение
  LiveDocs, навигация по слоям, ссылки на INDEX и шаблоны.
- **LiveDoc Index** (`livedocs/INDEX.md`): карта всех документов слоёв +
  decision tree («как найти нужный LiveDoc»).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: AI-агент при старте сессии читает ≤ 5K токенов на онбординг
  (сейчас ~40K). Считается как суммарный размер `livedocs/README.md` +
  `livedocs/INDEX.md` + `AGENTS.md` + constitution.md (≤ 150 строк).
- **SC-002**: AGENTS.md сокращается с ~230 до ≤ 100 строк (после миграции
  деталей в LiveDocs).
- **SC-003**: 100% новых фич (после принятия этой спеки) имеют запись в
  `livedocs/features/` (FR-013).
- **SC-004**: 100% изменений bounded context получают обновление
  `livedocs/domain/<context>.md` в том же PR (FR-014).
- **SC-005**: 100% PR прогоняются через `tools/check-livedocs-structure.sh`
  в CI, exit code ≠ 0 блокирует merge (FR-015).
- **SC-006**: **≥ 5 примеров фич** мигрированы в LiveDocs (proof of concept —
  `livedocs/features/182-editor-self-assign-tasks.md`,
  `184-approve-status-choice.md`, `185-song-dto-audit-sponsr-remove.md`,
  `186-zakroma-songs-fast-load.md`, `187-site-traffic-anomaly-investigation.md`).
- **SC-007**: **≥ 5 bounded context'ов** описаны в `livedocs/domain/` (например,
  `catalog`, `processing`, `publishing`, `identity`, `editorial`).
- **SC-008**: **Все 3 уровня C4** (L1 System Context, L2 Container, L3 Component)
  реализованы в `livedocs/architecture/` с Mermaid-диаграммами. Ни один уровень
  не может быть отложен «на потом».
- **SC-009**: AI-агент НЕ обращается к устаревшим спекам (`specs/NNN-*/spec.md`)
  как к первоисточнику при поиске контекста — лог tool calls показывает
  обращение к `livedocs/*` в первую очередь.
- **SC-010**: Время поиска описания модуля для нового разработчика
  ≤ 30 секунд (открыл `livedocs/INDEX.md` → нашёл `livedocs/domain/<context>.md`).

## Assumptions

- LiveDocs будут жить в `livedocs/` в корне репозитория (отдельная директория,
  чтобы не путать с `docs/`). Если команда предпочтёт `docs/livedocs/` —
  это легко переименовать в Pass 2.
- Формат — Markdown + YAML frontmatter для метаданных (статус, slug, related).
- Диаграммы — Mermaid (поддерживается GitHub, IDE, mermaid.live).
- В первом раунде НЕТ авто-генерации (нет плагинов Gradle для KDoc→Markdown,
  нет typedoc-vue). Обновление — ручное, по pull request.
- Существующие спеки (`specs/NNN-*`) и per-feature документы
  (`docs/features/*.md`) **НЕ удаляются** — остаются как archive и drill-down.
- AI-агенты (opencode / Claude Code / Cursor) поддерживают правило
  «читать LiveDocs первым» через явную инструкцию в `AGENTS.md` и
  через настройку `system prompt` в их конфигах (opencode уже это умеет).
- Пользователь готов к амбициозному first slice — каркас + **5 примеров миграции**
  + **5 bounded context'ов** + **все 3 уровня C4** + полная миграция AGENTS.md.
  Это один большой PR, а не серия инкрементальных.
- Уровни DDD/C4 выбраны из книг Эванса (DDD) и Саймона Брауна (C4) как
  стандарт де-факто. Не кастомные.
- Язык LiveDocs — русский (соответствует `AGENTS.md`, Principle «АБСОЛЮТНОЕ
  ПРАВИЛО: язык общения»).
- **Каноническое написание (фиксировано через Q3)**: `LiveDocs` — для системы
  и каталога (CamelCase, без пробелов); `LiveDoc` — для одного файла-документа.
  Все имена директорий — lowercase (`livedocs/`), все заголовки и имена
  сущностей — CamelCase (`LiveDocs`, `LiveDoc`). Это исключает неоднозначность
  в grep-паттернах, CI-скриптах и тексте `AGENTS.md`.
- **Naming convention для файлов (фиксировано через Q4)**: `livedocs/domain/<context>.md`
  — простые kebab-case имена без нумерации и префиксов. То же правило
  применяется к `livedocs/architecture/<topic>.md` (например, `L1-system-context.md`,
  `data-sync.md`, `deployment.md`). Исключение — `livedocs/features/<NNN-slug>.md`,
  где формат диктуется NNN-нумерацией фич.