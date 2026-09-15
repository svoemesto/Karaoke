# Knowledge-first policy (Pass 340, operational layer 2)

> **Дата введения**: 2026-09-09.
> **Прецедент**: spec #339 (см. `git log --grep '339'`).
> **Канонический документ**: `AGENTS.md` MUST #0, Constitution Principle IX.
> **Этот файл**: **только operational layer 2** (enforcement). Документация
> правила — в AGENTS.md / Constitution. Содержание секций L1 и L3
> **дублировало** их, и было удалено в Pass 379 wayfinder #101 (#112).

Этот файл — **операционная памятка** для agent'а. После прочтения
AGENTS.md MUST #0 + Constitution Principle IX агент использует этот файл
**только** для запуска pre-flight enforcement.

## Layer 2 — enforcement

### `tools/spec-knowledge-preflight.sh`

```bash
# Перед tools/specify-bootstrap.sh:
bash tools/spec-knowledge-preflight.sh <<'KNOWLEDGE'
knowledge/domains/<X>/domain.md — зачем прочитан
knowledge/domains/<X>/components/<Y>.md — зачем прочитан
knowledge/adr/local-NNNN-<slug>.md — принятое решение
KNOWLEDGE

# Получаем exit 0 + сообщение «OK: Knowledge-first pre-flight пройден».
# Затем — вызов bootstrap с флагом:
SPECIFY_KNOWLEDGE_PREFLIGHT=1 bash tools/specify-bootstrap.sh my-slug

# Без флага — backward compat (для governance-PR, которые могут
# обходить pre-flight).
```

Скрипт `tools/spec-knowledge-preflight.sh`:

- Читает stdin построчно.
- Проверяет, что ≥ 1 строка начинается с `knowledge/`.
- Проверяет, что **все** указанные файлы существуют (`test -e`).
- Exit 0 — OK, exit 2 — usage (нет stdin), exit 3 — ошибка (мало ссылок
  или несуществующие файлы).

### Интеграция в `tools/specify-bootstrap.sh`

- Если `SPECIFY_KNOWLEDGE_PREFLIGHT=1` и нет файла-маяка
  `.specify/.preflight-${slug}.ok` — bootstrap падает с exit 3.
- По умолчанию (без env) — backward compat: pre-flight опционален.

### Когда можно обойти pre-flight

Только для governance-PR (правки `AGENTS.md`, `constitution.md`, шаблонов
`.specify/templates/*`, `tools/spec-knowledge-preflight.sh`,
`tools/specify-bootstrap.sh`). Эти PR не создают новую спеку, они
улучшают сам процесс.

Для обычных feature-PR (новый код/спека) — `SPECIFY_KNOWLEDGE_PREFLIGHT=1`
**обязателен** (см. PR-шаблон, раздел «Governance Impact»).

## Связанные документы (canonical)

- `AGENTS.md` MUST #0 — **основное правило** (layer 1 documentation).
- `.specify/memory/constitution.md` Principle IX — **нормативная база** (layer 1).
- `CLAUDE.md` MUST-CHECKLIST — **операционная выжимка** для Claude Code.
- `tools/spec-knowledge-preflight.sh` — **enforcement скрипт** (layer 2, этот файл).
- `.specify/templates/spec-template.md` секция «Knowledge References» — **артефакт** (layer 3).

## Changelog

- **Pass 340** (2026-09-09): Initial policy. Прецедент: spec #339.
  Автор: agent (Karaoke). Согласовано: владелец (см. PR #340).
- **Pass 379 wayfinder #112** (2026-09-15): Сокращение до layer 2 only.
  Дублирующие секции L1 и L3 удалены (они теперь только в AGENTS.md +
  Constitution + spec-template). Этот файл стал **чисто operational
  memory** для запуска pre-flight. Compliance: knowledge-first MUST #0
  соблюдается (см. AGENTS.md).
