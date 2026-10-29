# Runbooks

> **Домен**: `knowledge/guidelines/runbooks/`
> **Назначение**: операционные runbook'и для типовых задач.

Runbook'и создаются по мере необходимости в этой директории.
Исторические playbook'и из `livedocs/runbooks/` (архивная коллекция v1)
больше недоступны — их содержимое нужно переносить сюда при
необходимости.

**Терминология**: в Karaoke операционная процедура — **runbook**
(`knowledge/guidelines/runbooks/`). Слово «playbook» используется только
в сравнительных таблицах generic ↔ Karaoke и в цитатах чужих материалов.

## Текущий каталог

| Runbook | О чём | Перенесён из |
|---|---|---|
| [tracker-setup.md](tracker-setup.md) | Настройка рабочего трекера (Plane): claim → комментарий → review → close | `docs/tracker-setup.md` |
| [log-correlation.md](log-correlation.md) | Диагностика через `docker logs`: корреляция по времени и идентификаторам | `docs/ops/log-correlation.md` |
| [crypto-key-rotation.md](crypto-key-rotation.md) | Ротация криптографических ключей | `docs/ops/crypto-key-rotation.md` |
| [forgejo-access.md](forgejo-access.md) | Доступ к Forgejo (репозиторий, CI, runner) | `docs/ops/forgejo-access.md` |
| [prod-config-delivery.md](prod-config-delivery.md) | Доставка конфигурации на прод | `docs/ops/prod-config-delivery.md` |

## Структура runbook'а

Каждый runbook должен иметь:

- **Заголовок**: «How to: ...».
- **Контекст**: когда использовать.
- **Шаги**: пронумерованный список действий.
- **Проверка**: как убедиться, что всё работает.
- **Rollback**: что делать, если что-то пошло не так.

## Линкование

Runbook'и должны ссылаться на:

- Соответствующий **ADR** в `knowledge/adr/` (почему так, а не иначе).
- Соответствующий **домен** в `knowledge/domains/<name>/` (где живёт код).
- Соответствующую **спецификацию** в `specs/NNN-<slug>/` (если есть).
