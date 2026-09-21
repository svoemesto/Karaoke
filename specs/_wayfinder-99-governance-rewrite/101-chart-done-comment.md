<!-- comment к карте wayfinder #101 — chart done -->

## Карта создана (Pass 379)

Карта wayfinder **«Переписать governance Karaoke по образцу TOP»** создана.
Это effort на много сессий; карта движется от диагностики к переписыванию.

### Destination

Переписать всю governance-цепочку Karaoke (`AGENTS.md`, `CLAUDE.md`,
`.specify/memory/constitution.md`, governance-критичные секции
`knowledge/`) так, чтобы:

1. AGENTS.md ≤ ~200 строк, runtime-directives + hard gates + failure-stop.
2. CLAUDE.md остаётся ассиметричным рекомендательным (Karaoke-override #1 сохраняется).
3. Каждое правило имеет явный failure-stop.
4. Нет дублирования между файлами.
5. Структурные метрики достигнуты.

### Созданные child tickets

| ID | Тип | Название | Заблокирован |
|---|---|---|---|
| #102 | research | Структурное сравнение governance Karaoke vs TOP | unblocked — claimed by ai-agent (Pass 379) |
| #103 | research | Rule duplication map | blocked by #102 — claimed by ai-agent (Pass 379) |
| #104 | grilling | Scope of governance rewrite | blocked by #102, #103 — **HOLD for HITL** |

Relations (parent/child + follows/precedes):
- #101 — parent of #102, #103, #104
- #103 follows #102 (research depends on fact-finding)
- #104 follows #102 (grilling depends on facts)
- #104 follows #103 (grilling depends on rule map)

### Что сделано в этой сессии

1. Прочитан `knowledge/README.md` + `knowledge/domains/README.md` (MUST #0).
2. Сравнён `AGENTS.md` Karaoke (600 строк) vs TOP (117 строк).
3. Сделаны 4 grilling-вопроса, зафиксированы ответы:
   - Destination = «вся governance-цепочка» (не только AGENTS.md).
   - Success criteria = «структурные метрики».
   - Topology = «лаконично, но ассиметрично (Karaoke-override #1)».
   - Notes = Knowledge-first MUST #0 + OpenProject workflow + Governance-review.
4. Создана карта #101 + 3 child issues.
5. Запущен research-субагент на #102 (background).

### Следующая сессия должна

1. Прочитать эту карту и эту секцию «Notes» для стоячих предпочтений.
2. Дождаться результата субагента на #102 (comment-resolution на тикет #102).
3. Когда #102 закроется — запустить #103 (уже claimed).
4. Когда #103 закроется — взять #104 в работу через grilling с владельцем.

### Out of scope (см. карту)

- AGENTS.md = CLAUDE.md (unification) — запрещено Karaoke-override #1.
- offer.html / docs/architecture-notes-archive.md / TOC-пайплайны TOP — отдельные efforts.
- AGENTS_LOCAL.md (только в TOP) — не нужен в Karaoke.

Создатель сессии: ai-agent, Pass 379 (2026-09-14).
