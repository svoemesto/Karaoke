# Runbooks

> **Домен**: `knowledge/guidelines/runbooks/`
> **Назначение**: операционные playbook'и для типовых задач.
> **Источник**: [livedocs/runbooks/](../../../../livedocs/runbooks/) (полная коллекция, 12 шт).

Этот индекс перечисляет **ключевые** runbook'и (перенесённые или
созданные в `knowledge/`), остальные остаются в `livedocs/runbooks/`
(архивная коллекция v1).

## Перенесённые в `knowledge/`

- *(в процессе миграции; полный список появится в следующих спецификациях)*

## В `livedocs/runbooks/` (полная коллекция)

### Базовые workflow

| Runbook | Когда использовать |
| --- | --- |
| [how-to-add-new-feature.md](../../../../livedocs/runbooks/how-to-add-new-feature.md) | Создание новой спецификации фичи. |
| [how-to-add-new-topic.md](../../../../livedocs/runbooks/how-to-add-new-topic.md) | Добавление новой темы в LiveDocs. |
| [how-to-update-livedocs.md](../../../../livedocs/runbooks/how-to-update-livedocs.md) | Обновление существующего LiveDoc. |
| [how-to-add-new-adr.md](../../../../livedocs/runbooks/how-to-add-new-adr.md) | Создание нового ADR (теперь в `knowledge/adr/`). |
| [how-to-add-new-domain.md](../../../../livedocs/runbooks/how-to-add-new-domain.md) | Создание нового домена в `knowledge/domains/`. |

### Разработка

| Runbook | Когда использовать |
| --- | --- |
| [how-to-add-orm-field.md](../../../../livedocs/runbooks/how-to-add-orm-field.md) | Добавление поля в ORM/SQL. |
| [how-to-stemjobs.md](../../../../livedocs/runbooks/how-to-stemjobs.md) | Задачи стем-сепарации Demucs. |
| [how-to-debug-connection-leak.md](../../../../livedocs/runbooks/how-to-debug-connection-leak.md) | Диагностика connection leak. |

### Deploy / Operations

| Runbook | Когда использовать |
| --- | --- |
| [how-to-deploy.md](../../../../livedocs/runbooks/how-to-deploy.md) | Деплой через `deploy/do.sh`. |
| [how-to-migrate-db.md](../../../../livedocs/runbooks/how-to-migrate-db.md) | Миграция БД на прод. |
| [how-to-migrate-prod-server.md](../../../../livedocs/runbooks/how-to-migrate-prod-server.md) | Миграция прод-сервера. |
| [how-to-demo-publish-links.md](../../../../livedocs/runbooks/how-to-demo-publish-links.md) | Демо publish-links. |

## Структура runbook'а

Каждый runbook должен иметь:

- **Заголовок**: «How to: ...».
- **Контекст**: когда использовать.
- **Шаги**: пронумерованный список действий.
- **Проверка**: как убедиться, что всё работает.
- **Rollback**: что делать, если что-то пошло не так.

## Линкование

Runbook'и должны ссылаться на:

- Соответствующий **ADR** (почему так, а не иначе).
- Соответствующий **домен** (`knowledge/domains/<name>/`) (где живёт код).
- Соответствующую **спецификацию** (`specs/NNN-<slug>/`) (если есть).
