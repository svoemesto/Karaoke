# Runbooks — пошаговые руководства для AI-агентов и разработчиков

> Runbook = короткое пошаговое руководство «как сделать X». Отличается от
> LiveDoc feature-фичи тем, что **описывает процесс**, а не фичу.

## Назначение

- AI-агент (opencode/Claude Code/Cursor) при новой задаче первым читает
  LiveDocs (см. `AGENTS.md` «С чего начать сессию»), потом — runbook для
  конкретной задачи.
- Разработчик (человек) находит runbook быстрее, чем копается в specs/.

## Index

| Runbook | Когда читать |
|---------|--------------|
| [how-to-deploy.md](how-to-deploy.md) | Перед `deploy_web.sh` или `deploy_public.sh` |
| [how-to-migrate-db.md](how-to-migrate-db.md) | Перед SQL-миграцией (новая колонка / таблица) |
| [how-to-add-new-feature.md](how-to-add-new-feature.md) | При создании новой фичи через `/speckit.specify → .plan.md → .tasks.md → implement` |
| [how-to-debug-connection-leak.md](how-to-debug-connection-leak.md) | При `FATAL: too many clients already` |
| [how-to-add-new-domain.md](how-to-add-new-domain.md) | При выделении нового bounded context в `livedocs/domain/` |
| [how-to-update-livedocs.md](how-to-update-livedocs.md) | При изменении кода/архитектуры (sync с LiveDocs) |

## Конвенции Runbook

- Один файл = одна задача.
- Структура:
  ```
  # How to: <задача>

  ## Prerequisites
  - Что должно быть готовым до начала.

  ## Steps
  ## 1. <шаг>
  ## 2. <шаг>
  ## ...

  ## Verification
  - Как убедиться, что всё прошло.

  ## Rollback
  - Как откатить.

  ## Related
  - Ссылки на LiveDocs фичи / architecture / constitution.
  ```
- Код/команды — bash-блоками с пояснениями.
- Без «почему» (это в LiveDocs); только «что и как».

## Frontmatter (если нужно)

Текущие runbooks — без frontmatter (минимальный, чтобы CI не требовал).
Если хочется добавить метаданные (author, last-updated, related):
```yaml
---
status: Active
slug: how-to-deploy
related:
  - ../architecture/nginx-conventions.md
---
```

## Когда добавлять новый runbook

1. Сделали задачу → записали шаги.
2. Использовали 2+ раза → вынесли в `livedocs/runbooks/`.
3. **Не дублировать** LiveDocs feature — это процесс, а не описание фичи.

## Связанные документы

- [AGENTS.md](../../AGENTS.md) — иерархия документов.
- [livedocs/README.md](../../README.md) — корневой манифест LiveDocs.
- [livedocs/INDEX.md](../INDEX.md) — карта слоёв.