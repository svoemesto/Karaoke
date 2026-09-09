# OpenProject #72 — GitHub branch protection для master (Pass 353 follow-up)

> **Задача для владельца** (owner action required). Это **третий уровень** из
> трёх уровней защиты от Pass 353 (см. AGENTS.md § "Git — CI-gate для
> master (NON-NEGOTIABLE) ⛔"). Первые два (pre-commit hook + CI lint step)
> уже в master через PR #454. Этот — server-side, через GitHub UI.

## Контекст

**Прецедент**: 2026-09-09, agent закоммитил отчёты напрямую в `master` после
мержа PR #452, owner поймал. Governance failure.

**Реализованные уровни защиты** (PR #454):

1. ✅ **Pre-commit hook** (client-side) — `tools/git-hooks/pre-commit-block-master.sh`.
   Блокирует `git commit` на `master`/`main` локально. Регистрируется через
   `.pre-commit-config.yaml` (id `block-master-commit`).
2. ✅ **CI lint step** (server-side safety net) — `.github/workflows/lint.yml`
   step "No direct commits to master". Детектит direct commits в `master`
   за последние 24h и fail.

3. ❌ **GitHub branch protection** (server-side, primary defense) —
   **owner action required**. Этот task.

## Зачем нужен branch protection

**Сейчас (без branch protection)**:
- `git push origin master` → 200 OK (push проходит).
- `gh pr merge --admin` → bypass'ит любые checks.
- Force-push (`git push -f origin master`) → перезаписывает историю.
- Admin (Ilya) может сделать что угодно.

**После branch protection (admin-enforced)**:
- `git push origin master` → **403 rejected** (cannot push directly to protected branch).
- Все изменения — только через PR merge.
- Force-push — заблокирован.
- Даже admin — не может bypass (если включён "Do not allow bypassing the above settings").

## Пошаговые инструкции

### Шаг 1. Открыть Branch protection settings

1. Перейти на https://github.com/svoemesto/Karaoke/settings/branches
2. Если есть кнопка "Add branch protection rule" → нажать. Если уже есть правило
   для `master` → нажать "Edit" на существующем.

### Шаг 2. Branch name pattern

**Branch name pattern**: `master`

Без wildcards — точное совпадение. Это защищает именно `master`, не другие
ветки (например, `main`, `develop`).

### Шаг 3. Включить обязательные настройки (6 items)

#### ✅ Включить (4 items)

| # | Setting | Зачем |
|---|---|---|
| 1 | **Require a pull request before merging** | Запрещает `git push origin master` напрямую. Все merge — только через PR. |
| 2 | **Required approvals: 1** (опционально, рекомендую) | Каждый PR — минимум 1 approval перед merge. Можно 0 если не хотите блокировки при отсутствии reviewer'а, но 1 — best practice. |
| 3 | **Require status checks to pass** | Без зелёного CI merge blocked. Выбрать конкретные checks ниже. |
| 4 | **Do not allow bypassing the above settings** | **КЛЮЧЕВОЙ** — даже admin (Ilya) не может bypass'ить. Без этой галчки — admin может обойти все правила. |

#### ✅ Status checks (внутри "Require status checks to pass")

Выбрать:

- **`ktlint (Kotlin/Java)`** — Kotlin lint, уже работает.
- **`Knowledge SSoT structure`** — knowledge lint, Pass 347+.
- **`Knowledge SSoT impact`** — SSoT mapping check.

Опционально (если хотите):

- `ESLint + Prettier (webvue3)` — frontend lint.
- `JSDoc coverage` — JSDoc coverage.
- `KDoc coverage` — KDoc coverage.

**Рекомендация**: для начала минимум 3 (ktlint + 2 knowledge). Остальные добавить по мере готовности.

#### ❌ Включить negative (2 items, default OFF)

| # | Setting | Default | Зачем |
|---|---|---|---|
| 5 | **Allow force pushes** | OFF | Защита от `git push -f origin master` (перезапись истории). Должно оставаться OFF. |
| 6 | **Allow deletions** | OFF | Защита от случайного удаления `master`. Должно оставаться OFF. |

### Шаг 4. НЕ включать (5 items — может сломать workflow)

| Setting | Почему НЕ |
|---|---|
| **Restrict creations** | Не нужно для нашей задачи. Защищает от создания matching refs, не от direct commits. |
| **Restrict updates** | Аналогично. |
| **Restrict deletions** | Сломает cleanup веток через GitHub UI. |
| **Require linear history** | **СЛОМАЕТ WORKFLOW** — мы используем merge commits в PR. Если включить, придётся rebase/squash для каждого PR. Слишком invasive. |
| **Require deployments to succeed** | Нет deployment environments infrastructure для Karaoke. |
| **Require signed commits** | GPG signing — дополнительная friction, не нужна при других правилах. |
| **Require code scanning / code quality / coverage** | Нужна предварительная настройка (CodeQL, codecov). Pass 353 не требует. |
| **Automatically request Copilot code review** | Beta, не нужно. |

### Шаг 5. Сохранить

Нажать **Create** (или **Save changes**). Branch protection rule вступает
в силу сразу.

## Verification (2 test-scenarios)

### Test 1: Direct push to master заблокирован

```bash
# В чистом checkout:
git clone https://github.com/svoemesto/Karaoke.git test-protection
cd test-protection
git config user.email "test@example.com"
git config user.name "Test"
echo "test" > test.txt
git add test.txt
git commit -m "test direct push"
git push origin master
```

**Ожидаемый результат**:
```
remote: error: GH006: Protected branch update failed for refs/heads/master.
remote: Required status check "ktlint (Kotlin/Java)" is expected.
```

✅ Если видите эту ошибку — protection работает.

### Test 2: PR workflow работает

```bash
N=$(./tools/reserve-branch-number.sh test-protection)
git checkout -b "${N}-test" master
echo "test2" > test2.txt
git add test2.txt
git commit -m "test via PR"
git push -u origin "${N}-test"
gh pr create --base master --title "Test PR for branch protection" --body "Test"
gh pr checks  # Ожидаемо: status checks passed (или failed)
gh pr merge --merge
```

**Ожидаемый результат**:
- Push feature-ветки → OK (200).
- PR created.
- `gh pr checks` показывает результат status checks (ktlint + knowledge SSoT).
- `gh pr merge --merge` → success (если все checks passed и есть approval).

✅ Если всё работает — protection настроен правильно.

## Rollback

Если что-то пошло не так:
1. Settings → Branches → `master` → Edit → **Delete rule** (или снимите галочки).
2. Все pushes снова работают (включая direct в master).

Это emergency-only — после исправления проблемы **сразу** восстановите
правила.

## Checklist для владельца

- [ ] Settings → Branches → master → Edit (или Add new rule)
- [ ] Branch name pattern: `master`
- [ ] ✅ Require a pull request before merging
- [ ] ✅ Required approvals: 1 (опционально, рекомендую)
- [ ] ✅ Require status checks to pass
- [ ]   Выбрать: `ktlint (Kotlin/Java)`, `Knowledge SSoT structure`, `Knowledge SSoT impact`
- [ ] ✅ Do not allow bypassing the above settings ← **КРИТИЧНО**
- [ ] ❌ Allow force pushes — оставить OFF
- [ ] ❌ Allow deletions — оставить OFF
- [ ] Save
- [ ] Test 1: `git push origin master` → 403 (verification)
- [ ] Test 2: PR workflow работает (verification)
- [ ] Отметить `tracker.sh close-issue 72`

## Связанные артефакты

- `AGENTS.md` § "Git — CI-gate для master (NON-NEGOTIABLE) ⛔" — обновлён в Pass 353 (PR #454), перечисляет 3 enforcement layers.
- `tools/git-hooks/pre-commit-block-master.sh` — Pass 353, client-side.
- `.pre-commit-config.yaml` — Pass 353, registration hook.
- `.github/workflows/lint.yml` — Pass 353, CI lint step.
- `specs/354-git-workflow-enforcement/spec.md` — governance traceability.
- `specs/354-git-workflow-enforcement/report.md` (этот файл) — пошаговые инструкции.

## Прецедент

- **2026-09-09** — agent (Pass 350/353 сессия) закоммитил отчёты напрямую
  в `master` после мержа PR #452. Owner поймал. Создан PR #453 для
  governance fix, PR #454 для client-side защиты. Этот task — server-side
  защита, которую делает владелец.
