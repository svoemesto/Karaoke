# How to: обновить LiveDocs при изменении кода или архитектуры

## Когда обновлять

✅ Обновите LiveDocs, если:
- Добавили новую `feature` (см. [how-to-add-new-feature.md](how-to-add-new-feature.md)).
- Изменили `bounded context` (новый AR, новое поле).
- Изменили архитектуру (новый компонент, новый ADR, новый паттерн).
- Мигрировали `tbl_*` (см. [how-to-migrate-db.md](how-to-migrate-db.md)).
- Применили значительное изменение (CI-gate, governance, language conventions).

❌ НЕ обновляйте (LiveDocs не место для chore-изменений):
- Опечатки в коде (PR-коммит сам поправит).
- Только-код изменения без архитектурного смысла.
- Внутренние параметры/KDoc.

## Steps

### 1. Определить, где изменение

Задайте себе вопросы:
- **Изменилась фича?** → `livedocs/features/<NNN>.md`.
- **Изменилось отношения между фичами?** → `related: [...]` в `livedocs/features/`.
- **Изменился bounded context?** → `livedocs/domain/<context>.md`.
- **Изменилась архитектура?** → `livedocs/architecture/L*` или `<topic>.md`.
- **Новое значимое решение?** → новый ADR (`livedocs/architecture/decisions/NNNN-...md`).
- **Новый runbook / процесс?** → `livedocs/runbooks/<how-to>.md`.

### 2. Править минимум

- **Не дублируйте**. Если правите фичу — НЕ копируйте текст в C4 или
  bounded context. Используйте `related: [...]`.
- **Обновляйте frontmatter** (`status`, `slug`, `related`).
- **Изменения в git** через обычный commit в feature-ветке `189-live-documentation`
  (или в ветке конкретной фичи — потом отдельный PR «LiveDocs sync»).

### 3. Если ветка НЕ `189-live-documentation`

Если вы в feature-ветке `<NNN>-my-feature` и нужно обновить LiveDocs:

```bash
# Вариант A: обновить LiveDocs в той же ветке (предпочтительно)
# — commit в <NNN>-my-feature
# — push + PR открывает набор изменений (фича + LiveDocs)

# Вариант B: отдельный LiveDocs PR (если LiveDocs требуется уже сейчас)
git checkout master  # или origin/master
git pull origin master
git checkout -b 189-live-documentation  # если не было
# ... обновить LiveDocs
git commit + push + PR

# Вариант C: влить изменения LiveDocs в вашу ветку
git checkout 189-live-documentation
# обновить LiveDocs + commit + push + PR
git checkout 189-my-feature
git merge --no-ff 189-live-documentation
```

Per [AGENTS.md](../../AGENTS.md) раздел «Lifecycle feature-ветки» —
ветка `189-live-documentation` живёт для follow-up. Используйте её.

### 4. PR описание

Один из стандартных форматов:

```
189-live-documentation: <что обновлено>

- [обновлённые файлы] — <что>
- [новые файлы] — <зачем>
- [CI проверки] — bash tools/check-livedocs-structure.sh
```

### 5. CI gate

`bash tools/check-livedocs-structure.sh` → 7/7 PASS обязательно до merge.

GitHub Actions: см. [CI 8/8 PASS на PR].

## Verification

- [x] Все ссылки `../...` валидны (если новые — обновлены).
- [x] Frontmatter валиден (status, slug, related).
- [x] Никаких висячих ссылок на удалённые спеки.
- [x] Глоссарий (если Bounded Context) содержит актуальные термины.
- [x] Размер файла ≤ указанного лимита (≤ 80 / ≤ 120 строк).

## Rollback

- `git revert <commit>` (для ошибочных изменений).
- `git rm livedocs/.../wrong-file.md` (для ошибочных новых файлов).

## Связь с Constitution § VI «Code Standards»

Per [FR-009](../../.specify/memory/constitution.md):
- «При правке кода одной из 9 ключевых подсистем разработчик MUST в том же
  PR обновить соответствующий per-feature документ».

Для **новой фичи** — LiveDoc-сводка (≤ 2 стр.).
Для **значимого архитектурного** изменения — ADR.
Для **boundaries** — обновить `bounded-context` + `related`.

## Related

- [AGENTS.md](../../AGENTS.md) — иерархия документов.
- [constitution.md](../../.specify/memory/constitution.md) — FR-009, §Governance.
- [how-to-add-new-feature.md](how-to-add-new-feature.md) — для новой фичи.
- [how-to-add-new-domain.md](how-to-add-new-domain.md) — для нового BC.
- [check-livedocs-structure.sh](../../../../tools/check-livedocs-structure.sh) — CI.