# ADR-0003: LiveDocs = Markdown + YAML frontmatter + Mermaid (не MkDocs/Docusaurus)

* **Status**: Accepted
* **Date**: 2026-08-14 (Phase 62, фича 189-live-documentation)
* **Deciders**: команда Karaoke

## Context

При внедрении **LiveDocs** (фича 189) рассматривались варианты инструмента
для документации. Спека 189 § research D-1 фиксирует это решение подробно;
этот ADR — формальная запись.

**Главные требования**:
1. **Единый источник правды** для AI-агента (первый читаемый документ при
   старте сессии).
2. **CI-валидация** структуры (блокирует merge при structural failures).
3. **Визуализация архитектуры** (C4-диаграммы).
4. **Zero runtime** (нет зависимостей от внешних SaaS).
5. **Git-native** (diff'ы, blame, history).
6. **Минимальный cognitive load** для участников команды (русский язык,
   Markdown привычен).

## Decision

Мы используем **Markdown + YAML frontmatter + Mermaid** в каталоге
`livedocs/` (см. [`livedocs/README.md`](../../README.md)).

**Структура**:
```
livedocs/
├── README.md             # Манифест
├── INDEX.md              # Карта слоёв + decision tree для AI
├── features/             # SDD: сводки фич (≤ 2 стр.)
├── domain/               # DDD: bounded contexts + Ubiquitous Language
├── architecture/         # C4 + topic-документы
├── templates/             # Шаблоны для новых записей
└── decisions/             # ADR (этот файл)
```

**Формат каждого LiveDoc**:
- YAML frontmatter (3 поля): `status`, `slug`, `related` (опционально `type`, `level`).
- Body в Markdown (CommonMark).
- Mermaid-блоки для диаграмм (рендер GitHub-ом автоматически).

**CI-валидация**: `tools/check-livedocs-structure.sh` (7 проверок, exit ≠ 0
блокирует merge).

## Consequences

**Положительные**:
- **Git-native**: `git diff`, `git blame`, `git log --follow` работают без
  инструментов.
- **Zero runtime**: никаких зависимостей (нет MkDocs Python, нет
  Docusaurus Node.js).
- **CI-валидация без боли**: POSIX bash + `head/grep/wc/find/test` — без
  парсеров.
- **AI-агент читает первым**: правило в `AGENTS.md` (1 страница манифеста
  + 1 страница INDEX).
- **Версионируется через git**: ссылки между LiveDocs относительные
  (`../domain/catalog.md`), не ломаются при пересборке.
- **Язык — русский**: соответствует `AGENTS.md` «АБСОЛЮТНОЕ ПРАВИЛО».
- **Удаление/переименование без осложнений**: `git mv`, проверка ссылок
  через `lychee --offline`.
- **Markdown привычен**: ktlint/ESLint/PRETTIER уже поддерживают.
- **Сокращение токенов стартовой сессии**: с ~40K до ≤ 5K (SC-001 спеки 189).

**Отрицательные**:
- **Нет поиска по LiveDocs** (нужен `grep` или ручной обход).
- **Нет перекрёстных ссылок автогенерации** (CI-grep по `../`).
- **Нет template inheritance** (но есть `templates/` для новых записей).
- **CI-скрипт пишется вручную**: `tools/check-livedocs-structure.sh` — если
  добавится новый слой, нужно обновить скрипт.
- **Нет версионирования LiveDocs** (semver не применяется; версия — git).
- **Mermaid-диаграммы** — не полноценные C4 (нет нативной C4-нотации; см.
  [`livedocs/architecture/c4-level-template.md`](../../templates/c4-level-L1.md)).

**Нейтральные**:
- AI-агент обучен читать Markdown отлично; не требует специального обучения.
- Внешние читатели (если такие появятся) увидят plain MD — не критично
  для проекта.

## Alternatives Considered

- **MkDocs (Python) + Material theme**: отвергнут — Python-зависимость,
  build-шаг, нет нативной CI-валидации структуры.
- **Docusaurus (Node.js)**: отвергнут — Node-зависимость, тяжёлый
  (нативная C4 нет), overkill для внутренней документации.
- **Hugo (binary)**: отвергнут — бинарный билд, тяжёлый, плохо
  версионируется через git.
- **Antora (multi-repo)**: отвергнут — для multi-repo, у нас одно.
- **AsciiDoc**: отвергнут — менее распространён, чем Markdown.
- **PlantUML**: отвергнут — требует Java-runtime + Graphviz, Mermaid
  рендерится на клиенте.
- **Confluence / Notion / внешние SaaS**: отвергнут — нарушает Constitution § I
  (self-contained).

## Ссылки

- [Спека 189-live-documentation § research D-1](../../../specs/189-live-documentation/research.md) — подробное
  обоснование (10 design decisions).
- [livedocs/README.md](../../README.md) — корневой манифест LiveDocs.
- [livedocs/INDEX.md](../../INDEX.md) — карта + decision tree.
- [AGENTS.md](../../../AGENTS.md) — правило «AI-агент при старте сессии
  читает LiveDocs первым».
- [Constitution § I](.specify/memory/constitution.md) — self-contained.