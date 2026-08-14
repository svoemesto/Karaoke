# LiveDocs — FAQ (Часто задаваемые вопросы)

> Этот файл — быстрый ответ на типичные вопросы. Подробности — в соответствующих
> разделах LiveDocs (runbooks/, features/, architecture/).

## Общие вопросы

### 1. Как найти нужный документ?

**Самый быстрый способ** — `/livedocs-find '<query>'` slash-command (для AI-агентов).
Альтернативно — `bash tools/search-livedocs.sh '<query>' [--type TYPE]`.

**Для навигации** — откройте [`livedocs/INDEX.md`](INDEX.md) (карта слоёв + decision tree).

### 2. Какие слои в LiveDocs?

- **features/** (84 файла) — сводки фич (≤ 2 страниц каждая).
- **domain/** (7 файлов) — bounded contexts + Ubiquitous Language.
- **architecture/** — C4 уровни (L1, L2, L3) + 13 topic + 6 global ADR + 5 local ADR.
- **runbooks/** (8 файлов) — операционные how-to.
- **commands/** (2 файла) — AI-agent slash-commands.
- **templates/** (6 файлов) — шаблоны для новых документов.

### 3. Что прочитать при первом старте?

1. [`README.md`](README.md) — манифест.
2. [`INDEX.md`](INDEX.md) — карта.
3. [`CHANGELOG.md`](CHANGELOG.md) — история (Pass 62+).
4. Для конкретной задачи — `features/<NNN>.md` или `domain/<context>.md`.

### 4. Что такое ADRs и как их читать?

ADR (Architecture Decision Record) — формальный документ, описывающий
**значимое архитектурное решение** с контекстом, альтернативами и обоснованием.
См. [decisions/README.md](architecture/decisions/README.md) — формат, конвенции.

В LiveDocs 6 global ADR (0001-0006) и 5 local ADR (local-0001 — local-0006).

## Работа с LiveDocs

### 5. Как добавить новую фичу в LiveDocs?

После создания спеки (через `/speckit.specify`):
1. Скопировать [`templates/feature-summary.md`](templates/feature-summary.md) →
   `features/<NNN-slug>.md`.
2. Заполнить frontmatter (`status: Active`, `slug: <NNN-slug>`, `related: [...]`).
3. Заполнить body (≤ 2 страниц).
4. Запустить `bash tools/check-livedocs-structure.sh` (7/7 PASS).
5. Commit + push + PR.

См. [runbooks/how-to-update-livedocs.md](runbooks/how-to-update-livedocs.md).

### 6. Как добавить новый bounded context?

1. Создать `domain/<context>.md` по шаблону
   [`templates/bounded-context.md`](templates/bounded-context.md).
2. Заполнить: Aggregate Roots, Entities, Value Objects, Domain Events, Ubiquitous Language.
3. Обновить [`domain/README.md`](domain/README.md) (таблица).
4. CI проверки.

См. [runbooks/how-to-add-new-domain.md](runbooks/how-to-add-new-domain.md).

### 7. Как создать новый ADR?

1. Создать `architecture/decisions/NNNN-<slug>.md` по шаблону
   (см. [decisions/README.md](architecture/decisions/README.md)).
2. Заполнить: Context → Decision → Consequences → Alternatives.
3. Обновить [`architecture/decisions/README.md`](architecture/decisions/README.md).
4. Опционально — English mirror в `livedocs-en/decisions/`.

См. [runbooks/how-to-add-new-adr.md](runbooks/how-to-add-new-adr.md).

### 8. Как обновить INDEX.md?

`bash tools/update-index.sh [--diff | --apply]` — перегенерирует INDEX.md
на основе текущего состояния LiveDocs.

По умолчанию выводит в stdout. С `--diff` показывает unified diff. С `--apply`
обновляет файл (commit отдельно).

## Валидация

### 9. Какие проверки запускаются?

| Проверка | Где | Что |
|----------|-----|-----|
| `check-livedocs-structure.sh` | CI + pre-commit | 7 структурных проверок |
| `check-livedocs-cross-links.sh` | CI + pre-commit | 818 cross-links |
| `check-livedocs-external-links.sh` | CI (strict) + weekly cron | External https:// URLs |
| `check-livedocs-coverage.sh` | CI + pre-commit | Specs ↔ LiveDocs coverage |
| `check-md-structure.sh` | CI + pre-commit | Markdown structural |
| `validate-mermaid.sh` | CI + pre-commit | Mermaid blocks |
| `test-livedocs.sh` | Local | Self-test 13 проверок |
| `cross-link-density.sh` | Local | Метрика качества |

### 10. Что делать при broken cross-link?

1. Запустить `bash tools/check-livedocs-cross-links.sh` — увидим broken.
2. Запустить `bash tools/suggest-broken-links.sh` — кандидаты.
3. Исправить ссылки (или создать missing файл).
4. `bash tools/check-livedocs-cross-links.sh` → 0 broken.

### 11. Что делать при broken external link?

1. Запустить `bash tools/check-livedocs-external-links.sh`.
2. Если URL 404 — обновить или заменить на рабочий.
3. Если URL — пример placeholder, добавить в `tools/check-livedocs-external-links.sh`
   в секцию `EXCLUDE` (если это expected false positive).
4. CI strict → exit 0.

### 12. Как понять, что LiveDocs устарели?

- `bash tools/cross-link-density.sh` — % файлов с related: ссылками.
- `bash tools/warn-coverage-gaps.sh` — список missing спека-фич.
- `git log livedocs/` — кто менял LiveDocs (semantic).
- `bash tools/gen-livedocs-stats.sh` — текущее состояние (метрики).

## Для AI-агентов

### 13. Какие скрипты может запускать AI-агент?

**Безопасно (read-only)**:
- `search-livedocs.sh`, `check-livedocs-structure.sh`, `check-livedocs-cross-links.sh`,
  `check-livedocs-coverage.sh`, `check-md-structure.sh`, `validate-mermaid.sh`,
  `cross-link-density.sh`, `gen-livedocs-stats.sh`, `test-livedocs.sh`,
  `gen-livedocs-index.sh`, `gen-toc.sh`, `update-index.sh`, `warn-coverage-gaps.sh`,
  `comment-broken.sh` (read mode).

**Только с явного согласия пользователя**:
- `comment-broken.sh --post <PR>` (создаёт комментарий в PR).
- `update-index.sh --apply` (перезаписывает INDEX.md).

### 14. Какие правила для AI-агента?

См. [`AGENTS.md`](../AGENTS.md) — главный документ для governance.
Особенно раздел «С чего начать сессию (AI-агент)» и «LiveDocs CI / pre-commit».

### 15. Где искать команды slash?

- Canonical: [`commands/livedocs-find.md`](commands/livedocs-find.md).
- Runtime-копия: `.opencode/commands/livedocs-find.md` (в `.gitignore`).

## Технические вопросы

### 16. Что если нужна публичная публикация (MkDocs/Docusaurus)?

LiveDocs намеренно plain-Markdown + GitHub рендеринг (см.
[ADR-0003](architecture/decisions/0003-livedocs-markdown-yaml-mermaid.md)).
Это **намеренный** выбор — без новых зависимостей. Если нужно
опубликовать — можно добавить MkDocs позже (Pass 17+).

### 17. Можно ли редактировать LiveDocs на английском?

Да — есть зеркало `livedocs-en/` (19 файлов). Русский canonical (см.
[`AGENTS.md` АБСОЛЮТНОЕ ПРАВИЛО](../AGENTS.md)), но English-зеркало полезно
для AI-агентов, работающих на английском.

### 18. Где версионирование LiveDocs?

Через git (semver не применяется). Смотрите
[`CHANGELOG.md`](CHANGELOG.md) для semantic истории.

### 19. Какой формат для `AGENTS.md` Q&A?

`AGENTS.md` — только governance. **НЕ** добавлять Q&A. Q&A → в
LiveDocs (см. примеры: Jackson `is`-prefix, Dockerfile ловушки, и т.д.).
См. [AGENTS.md «Как обновлять»](../AGENTS.md).

### 20. Можно ли писать LiveDocs на русском + английском в одном файле?

Не рекомендуется — лучше отдельные файлы в `livedocs/` (RU canonical) и
`livedocs-en/` (EN mirror). Исключение — таблицы, которые можно дублировать.

## Где ещё спросить?

- `AGENTS.md` — governance, workflow.
- `livedocs/README.md` — корневой манифест.
- `livedocs/INDEX.md` — карта слоёв.
- `livedocs/CHANGELOG.md` — история.
- `livedocs/runbooks/README.md` — список how-to.
- `tools/README.md` — список скриптов.
- `.specify/memory/constitution.md` — NON-NEGOTIABLE принципы.