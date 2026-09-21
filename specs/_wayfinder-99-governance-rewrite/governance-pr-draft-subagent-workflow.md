# Governance-PR Draft — Subagent Workflow Rule (Pass 379 follow-up)

> **Это draft**, не merged. Нужно открыть отдельный governance-PR
> с правкой `AGENTS.md` и `.specify/memory/constitution.md`.
> Пока draft лежит здесь для следующей сессии.

---

## Прецедент

**Pass 379, 2026-09-14** — Implementation Step A в карте wayfinder #101
(3 параллельных guard-скрипта: #105 JPA, #106 docker tags, #107 MP4).

### Что произошло

Запустил 3 субагентов **параллельно в одном workspace**. Они **должны были работать в разных branch'ах** (`386-`, `387-`, `388-`), но:

1. **Branch hopping**: субагент #107 переключился на ветку `386-...` через
   `git checkout`, нашёл там commit `66946300` (от #105), и его собственный
   commit `d1e94cd4` встал поверх. Получился **чужой коммит в PR #484**.
2. **Stash путаница**: `git stash` / `git stash pop` оставлял файлы в
   неожиданных местах. Каждый субагент периодически «находил» чужие
   untracked файлы.
3. **Race condition в `.pre-commit-config.yaml` и `.github/workflows/lint.yml`**:
   оба файла редактировались всеми тремя субагентами одновременно.
   В итоге в PR #484 оказался **и JPA-hook, и MP4-hook** — хотя MP4-PR
   должен был содержать только MP4-hook.

### Что пришлось делать руками

- Rebase `388-...` на master с `--force-with-lease`.
- Удалить чужой JPA-hook из `.pre-commit-config.yaml`.
- Удалить чужие Pass 372-375 шаги из `.github/workflows/lint.yml`.
- Force-push и amend commit'а.

Это ~30 минут ручной работы, которая была бы не нужна при правильном workflow.

---

## Предлагаемое правило

**Вписать в `AGENTS.md` § «Иерархия документации и AI-агенты» + `constitution.md`
после Principle IX:**

> ### Hard Gate: Subagent Workspace Isolation
>
> **Rule**: При запуске нескольких субагентов для **параллельных** PR-веток
> каждый субагент MUST работать в **отдельном `git worktree`** (или
> отдельной рабочей копии).
>
> **Запрещено** (NON-NEGOTIABLE):
> - ❌ Несколько субагентов в одном `cwd` одновременно.
> - ❌ `git stash` поверх чужой ветки.
> - ❌ `git checkout <branch-other-than-mine>` в работающем субагенте.
>
> **Mandatory Action**:
> 1. Перед запуском каждого субагента — `git worktree add ../<branch-name> master`
>    или склонировать репо в отдельную папку.
> 2. Каждый субагент работает **только** в своём worktree.
> 3. В конце — force-push и закрытие worktree.
>
> **Failure**: Два субагента в одном workspace → race condition → чужие
> коммиты в чужих PR'ах, конфликты в shared-файлах (`.pre-commit-config.yaml`).
> Шансы на corruption: высокие.
>
> **Enforcement** (proposed, Pass 379+):
> - `tools/check-subagent-isolation.sh` — guard, который проверяет, что
>   в каждой открытой PR-ветке нет commits других worktree.
> - Hard-gate в pre-commit + CI.

---

## Конкретные правки

### `AGENTS.md` (semver bump 2.7.0 → 2.8.0)

Добавить секцию после «Git — CI-gate для master»:

```markdown
### Subagent workspace isolation

- **Rule**: При запуске нескольких субагентов для параллельных PR-веток
  каждый MUST работать в отдельном `git worktree`. Запрещено несколько
  субагентов в одном `cwd` (race condition → чужие коммиты в чужих PR'ах).
- **Protocol**:
  1. `git worktree add ../${N}-${slug} master`
  2. Субагент работает ТОЛЬКО в `../${N}-${slug}/`
  3. `git push` + `gh pr create`
  4. `git worktree remove ../${N}-${slug}` после merge
- **Failure**: race condition → corrupted commits, блокирующие CI.
- **Прецедент**: Pass 379 — 3 параллельных субагента в одном workspace
  привели к rebase + amend всех 3 PR'ов.
```

### `.specify/memory/constitution.md` (semver bump 2.2.0 → 2.3.0)

Добавить подпринцип в Principle IX (Knowledge-first):

```markdown
#### IX.3 — Subagent Workspace Isolation (Pass 379)

Каждый субагент MUST работать в отдельном `git worktree`. Запрещено несколько
субагентов в одном `cwd`. Прецедент: Pass 379 — race condition привела к
corruption в 3 PR'ах (force-push + amend вручную).
```

### `tools/check-subagent-isolation.sh` (новый guard)

~50 строк bash — проверяет, что:
1. Каждая открытая PR-ветка имеет уникальные commits.
2. Никакая PR-ветка не содержит commit другого worktree.
3. В выводе `git worktree list` — ровно N worktree'ов, где N = количество
   активных субагентов.

Подключить к `.pre-commit-config.yaml` (как advisory) и `.github/workflows/lint.yml`
(как CI gate).

---

## Compliance

- [ ] Knowledge-first MUST #0 прочитан.
- [ ] Этот draft добавлен в следующий governance-PR (semver bump AGENTS.md
      2.7.0 → 2.8.0 + constitution 2.2.0 → 2.3.0).
- [ ] Одобрение владельца перед merge.
- [ ] `tools/check-subagent-isolation.sh` создан + подключён.

---

## Когда применять

**Сейчас (после Step A)**: правило уже спасает следующую implementation-сессию
(Step B с 5 субагентами параллельно). Если draft мержится до Step B —
мы избегаем повторения Pass 379 race condition.

**После**: любая будущая сессия, где нужны параллельные субагенты для
разных веток.
