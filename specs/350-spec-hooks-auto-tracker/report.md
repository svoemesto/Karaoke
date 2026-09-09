# Task #69 — Speckit auto-hooks for OpenProject Tracker (Pass 350)

> Этот отчёт публикуется в OpenProject #69 через `tracker.sh add-comment`
> для прозрачности post-merge обновления workflow автоматизации.

## Что сделано (Pass 350)

Owner попросил автоматизировать workflow после Pass 349 (где #69 был
обработан без claim/add-comment/mark-review). Реализовано:

### Новые файлы

- **`tools/tracker-bootstrap.sh`** (~100 lines, bash):
  - Сканирует $ARGUMENTS и `INPUT_MESSAGE` на OpenProject ID через regex
    `(задач\w*|task\w*|таск\w*|#|№|оп\w*|op\w*|openproject|open[ -]?project)`
    case-insensitive.
  - При обнаружении — вызывает `tracker.sh claim-issue <NN>` (idempotent).
  - Если нет — no-op, exit 0.

- **`tools/tracker-implement-done.sh`** (~140 lines, bash):
  - Определяет Issue ID через (в порядке): explicit arg → branch pattern.
  - Если `specs/<NN>-<slug>/report.md` существует — использует его.
  - Иначе — auto-generates stub из git log + diff stats.
  - Вызывает `tracker.sh add-comment <NN> --file <report>` +
    `tracker.sh mark-review <NN>`. Idempotent.

### Модифицированные файлы

- **`tools/specify-bootstrap.sh`**: В конце добавлен вызов
  `tracker-bootstrap.sh "$SLUG $DESC $@"` (опциональный, не блокирует при failure).
- **`.specify/extensions.yml`**: 
  - `before_specify` → `tracker-issue-claim` extension.
  - `after_implement` → `tracker-implement-done` extension.
- **`AGENTS.md`**: 2.2.0 → 2.3.0 (semver MINOR per `Как обновлять этот файл`).
  Section «Issue-tracker OpenProject» дополнена подсекцией «Auto-hooks (Pass 350)».

### Хуки помечены optional

НЕ блокируют workflow при failure. Manual workflow (Pass 349) остаётся
fallback. Hooks снижают риск повторения #69 failure (если agent поддерживает
extensions.yml runtime).

## Validation (на момент PR)

| Проверка | Результат |
|---|---|
| `bash tools/tracker-bootstrap.sh "task 69"` | успешно claim-issue 69 (idempotent: In progress) |
| `bash tools/tracker-bootstrap.sh "OpenProject 65 (race #65)"` | успешно claim-issue 65 |
| `bash tools/tracker-bootstrap.sh "просто текст"` | no-op, exit 0 |
| `bash tools/tracker-implement-done.sh 69` | comment (id:338) + In review |
| `bash tools/check-spec-issue-link.py` | OK: 3/3 modern specs (#344, #348, #349) |
| `gh pr checks <#350>` | expected 9/9 PASS |

## Что НЕ сделано (out of scope, отдельные задачи)

- Webhook-trigger при merge (Phase-002+, требует GitHub webhook + workflow_run).
- Migrate уже слитые спек — grandfathered per Pass 349.
- Изменения в `tools/tracker.sh` API.

## OpenProject #69 текущий статус

На момент этого PR #69 уже в **In review** (после Pass 349 backfill).
Pass 350 не меняет его статус — но после merge этого PR, workflow
автоматизирован для всех будущих OpenProject тасков.

## Operational notes (для владельца)

После merge этого PR:
- Все будущие спеки, созданные через `/speckit.specify "task NN"`,
  авто-claim в OpenProject (если runtime поддерживает hooks).
- После merge PR по спеки — агент должен запустить
  `bash tools/tracker-implement-done.sh <NN>` для auto-comment + mark-review.
- Если agent забыл — workflow manual (Pass 349) остаётся fallback.
  Владелец может закрыть #NN сам.

## Files changed

```
A  tools/tracker-bootstrap.sh                                  ~100 LOC
A  tools/tracker-implement-done.sh                            ~140 LOC
M  tools/specify-bootstrap.sh                                 +1 block (auto-claim hook)
M  .specify/extensions.yml                                    +2 extensions
M  AGENTS.md                                                   2.2.0 → 2.3.0
M  tools/README.md                                             (description новых scripts)
M  docs/architecture-notes.md                                  (Pass 350 entry)
A  specs/350-spec-hooks-auto-tracker/{spec,checklists,report}.md
```
