# Knowledge Templates

> **Домен**: `knowledge/templates/`
> **Назначение**: шаблоны для создания новых документов в `knowledge/`.

Шаблоны определяют **структуру и обязательные секции** каждого типа
документа. Используются при создании:

- Новых **доменов** → [`domain.md`](domain.md).
- Новых **компонентов** → [`component.md`](component.md).
- Новых **ADR** → [`adr.md`](adr.md).
- Новых **эпиков** → [`epic.md`](epic.md).

## Конвенция использования

1. **Создание**: при создании нового файла **обязательно** используйте
   соответствующий шаблон как стартовую точку.
2. **Редактирование**: при правке существующего файла **не удаляйте**
   обязательные секции шаблона. Добавляйте новые секции можно.
3. **Проверка**: линтер [`tools/lint-knowledge.py`](../../tools/lint-knowledge.py)
   проверяет соответствие обязательным секциям (для доменов, компонентов и ADR).

## Karaoke-overrides для шаблонов

- **ADR**: использует старый Karaoke-формат (`* Status:`, `* Date:`) или
  новый generic-формат (`--- id: ADR-XXXX`). Оба допустимы, линтер не
  валидирует ADR формат (см. ADR-0007 в `knowledge/adr/`).
- **Компоненты**: frontmatter опционален (см. исключение в
  `tools/check-knowledge-structure.sh`).
- **Домены**: frontmatter обязателен (status, slug, related).

## См. также

- [`knowledge/README.md`](../README.md) — главная SSoT-карта.
- [`knowledge/adr/0007-adopt-knowledge-as-ssot.md`](../adr/0007-adopt-knowledge-as-ssot.md) —
  ADR о принятии knowledge/ как SSoT.
- [`bootstrap-living-docs`](../../.dsh/skills/bootstrap-living-docs/SKILL.md) —
  скилл, по которому были созданы эти шаблоны.
