# Knowledge-first policy (Pass 340)

> **Дата введения**: 2026-09-09.
> **Прецедент**: spec #339 (см. `git log --grep '339'`).
> **Контракт**: NON-NEGOTIABLE — см. `AGENTS.md` MUST #0, Constitution Principle IX.

## Зачем

До Pass 340 правило «прочитай Knowledge первым» было **рекомендацией**
в `AGENTS.md` («Прочитай Knowledge первым»). Это привело к инциденту
2026-09-09: агент при работе со спекой #339 (Кеширование информации из
хранилища, OpenProject #69) проигнорировал правило, пошёл сразу в
`codegraph_explore` по `HealthReport` и `fileExists`, и **изобрёл форму
кеша** вместо использования устоявшихся паттернов из
`knowledge/domains/caching/components/caching-patterns.md`. Спека
приведена в негодность, ветка удалена, NNN 339 освобождён.

Pass 340 превращает рекомендацию в **enforced MUST** с тремя уровнями
защиты:

1. **Документация**: `AGENTS.md` MUST #0, Constitution Principle IX,
   `CLAUDE.md` MUST-CHECKLIST.
2. **Enforcement**: `tools/spec-knowledge-preflight.sh` + интеграция в
   `tools/specify-bootstrap.sh` через env `SPECIFY_KNOWLEDGE_PREFLIGHT=1`.
3. **Артефакты**: секция «Knowledge References» в `spec.md` (обязательная),
   секция «Knowledge Compliance» в `checklists/requirements.md`
   (обязательная), ссылка в шаблоне `spec-template.md`.

## Как работает (короткая версия)

Перед ЛЮБОЙ новой фичей / спекой / серьёзной правкой кода агент MUST:

1. Прочитать `knowledge/README.md` + `knowledge/domains/README.md`.
2. Сделать `grep -r '<keyword>' knowledge/` минимум 3 раза с разными
   ключевыми словами.
3. Прочитать `domain.md` + **все** `components/*.md` для каждого
   релевантного домена.
4. Прочитать все `local-*.md` ADR из `knowledge/adr/`.
5. Только после шагов 1-4 — идти в `codegraph_explore` / `grep` по
   `src/`.

Зафиксировать в `spec.md` секцию «Knowledge References (MANDATORY)»
со списком всех прочитанных файлов и одной фразой «зачем прочитан».

## Как работает (длинная версия)

### Уровень 1 — документация

| Файл | Секция | Что говорит |
|---|---|---|
| `AGENTS.md` | MUST #0 | «MUST прочитать Knowledge первым. Шаги 1-5 + failure-stop правила». |
| `AGENTS.md` | Sync с другими правилами | «Это MUST-предшественник для Constitution Principle IX и tools/spec-knowledge-preflight.sh». |
| `constitution.md` | Principle IX | «Knowledge-first при разработке фич (NON-NEGOTIABLE)». |
| `constitution.md` | п. 5 «Категорически запрещено» | Пункт 8: «Полезть в codegraph_explore до Knowledge-first». |
| `CLAUDE.md` | MUST-CHECKLIST | «Шаги 1-10 + failure-stop» (дублирует MUST #0). |
| `CLAUDE.md` | MCP-серверы | «codegraph — только после Knowledge-first (раньше было „перед grep/Read“ — изменено)». |

### Уровень 2 — enforcement

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

Интеграция в `tools/specify-bootstrap.sh`:

- Если `SPECIFY_KNOWLEDGE_PREFLIGHT=1` и нет файла-маяка
  `.specify/.preflight-${slug}.ok` — bootstrap падает с exit 3.
- По умолчанию (без env) — backward compat: pre-flight опционален.

### Уровень 3 — артефакты спеки

| Артефакт | Обязателен? | Содержимое |
|---|---|---|
| `spec.md` секция «Knowledge References» | ДА (Pass 340) | Pre-flight log + список прочитанных knowledge-файлов + явный no-op если ничего не нашлось |
| `checklists/requirements.md` секция «Knowledge Compliance» | ДА (Pass 340) | 8 чек-боксов: pre-flight выполнен, README/domains прочитаны, 3+ grep, домены прочитаны, ADR прочитаны, секция заполнена, явный no-op зафиксирован |
| `plan.md` секция «Constitution Check» | УЖЕ БЫЛО | Проверка соответствия Constitution, включая Principle IX |
| `tasks.md` | Не требует | Задачи не зависят от Knowledge-first (Knowledge-first — это процесс, а не задача) |

## Когда можно обойти Knowledge-first?

**Никогда.** Единственное исключение — явный no-op в `spec.md`:

> «Searched: `<queries>` (3+ попытки с разными ключевыми словами) →
> `<files checked>` → no relevant docs. Continuing with code-first
> exploration only after exhaustive search.»

Этот no-op сам по себе требует **доказательства** (перечислить запросы и
файлы), а не голословного утверждения «ничего не нашёл».

## Когда можно обойти pre-flight (`SPECIFY_KNOWLEDGE_PREFLIGHT`)?

Только для governance-PR (правки `AGENTS.md`, `constitution.md`, шаблонов
`.specify/templates/*`, `tools/spec-knowledge-preflight.sh`,
`tools/specify-bootstrap.sh`). Эти PR не создают новую спеку, они
улучшают сам процесс.

Для обычных feature-PR (новый код/спека) — `SPECIFY_KNOWLEDGE_PREFLIGHT=1`
**обязателен** (см. PR-шаблон, раздел «Governance Impact»).

## Известные ограничения

1. **Глобальный `~/.dsh/AGENTS.md` не правится** в Pass 340 — это
   приватный файл пользователя, его изменения требуют отдельного
   согласования. Follow-up: добавить MUST-ассерт «Knowledge-first при
   старте DSH-сессии в проекте Karaoke» в следующем governance-PR с
   явным одобрением владельца.

2. **`~/.git/hooks/pre-commit` не интегрирован** — pre-commit hooks
   сейчас проверяют ktlint/eslint/prettier, но не Knowledge-first.
   Follow-up: добавить локальный git-hook, который проверяет наличие
   секции «Knowledge References» в `spec.md` при создании новой
   директории `specs/NNN-*/`.

3. **CI-проверка в `tools/check-knowledge-structure.sh`** не
   дополнена — текущая проверка валидирует структуру Knowledge, но
   не валидирует структуру `spec.md`. Follow-up: добавить проверку,
   что каждый `specs/NNN-*/spec.md` имеет непустую секцию «Knowledge
   References» с хотя бы одной реально существующей ссылкой.

4. **`spec.md` шаблон ссылается на AGENTS.md MUST #0, но не
   enforce'ит min количество grep-запросов (только в checklist)** —
   агент может написать «Searched: foo» один раз и формально
   соответствовать. Follow-up: добавить автоматическую проверку
   количества grep-запросов (≥ 3) в checklist-template.

## Что НЕ делает Pass 340

- Не запрещает `codegraph_explore` / `grep` по коду в принципе.
  Запрещает только **до** Knowledge-first.
- Не заменяет `codegraph` / `grep`. Они остаются — но как шаг **после**
  Knowledge.
- Не предлагает автоинжекцию Knowledge в system-reminder каждой
  сессии (это раздуло бы контекст на 30k токенов).
- Не вводит «Knowledge-agent» подзадачу (overengineering).

## Связанные документы

- `AGENTS.md` MUST #0 — основное правило.
- `.specify/memory/constitution.md` Principle IX — нормативная база.
- `CLAUDE.md` MUST-CHECKLIST — операционная выжимка.
- `tools/spec-knowledge-preflight.sh` — enforcement скрипт.
- `tools/specify-bootstrap.sh` — точка интеграции.
- `.specify/templates/spec-template.md` — секция «Knowledge References».
- `.specify/templates/checklist-template.md` — секция «Knowledge Compliance».

## Changelog

- **Pass 340** (2026-09-09): Initial policy. Прецедент: spec #339.
  Автор: agent (Karaoke). Согласовано: владелец (см. PR #340).