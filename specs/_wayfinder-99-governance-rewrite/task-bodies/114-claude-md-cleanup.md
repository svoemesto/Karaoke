<!-- description task-тикета #114 (Step D: каскадный cleanup) -->

## Question

Удалить «🚦 НЕ делать» секции в `CLAUDE.md`:
- L208-218 — список 8 запретов (все дублируют R-XX).
- L155-169 — «Git workflow» (дубликат R-16, R-49).

Зачем:
- Это каскад от #109 (MUST-CHECKLIST удалён). При удалении этих
  секций CLAUDE.md станет ещё короче, попадая в «≤ 100 строк».
- Прямо помогает success criteria карты.

**Что должно быть в ответе**:

1. Удалить CLAUDE.md L208-218 (секция `🚦 НЕ делать`).
2. Удалить CLAUDE.md L155-169 (Git workflow).
3. Заменить каждую на однострочный cross-ref `→ AGENTS.md § <related rule>`.
4. CLAUDE.md должно остаться ≤ 100 строк.

## Notes

- Зависит от #113 (главный rewrite). После #113 в новом AGENTS.md
  есть все hard-gates с явными failure-stop — CLAUDE.md становится
  рекомендательным приложением.

## Тип

`[wayfinder:task]`.
