<!-- description task-тикета #109 (Step B: content rewrite) -->

## Question

Удалить MUST-CHECKLIST (CLAUDE.md L50-73) и заменить на однострочный
cross-ref в AGENTS.md.

Зачем:
- Из Q1 (Pass 379, #104 resolution): R-01 (MUST #0 / Knowledge-first) — канон
  AGENTS.md, остальные файлы — cross-ref.
- Это даст −~25 строк в CLAUDE.md и устранит 2-й по важности дубль в Karaoke.

**Что должно быть в ответе**:

1. Удалить `CLAUDE.md` L50-73 (секция `🚦 MUST-CHECKLIST при старте сессии`).
2. Заменить на:
   ```
   > MUST #0 (Knowledge-first) — см. AGENTS.md § MUST #0.
   ```
3. Запустить pre-commit + ktlintCheck + ESLint baseline — все должны пройти.
4. Никаких изменений в `constitution.md` (это другой тикет) или
   `knowledge/README.md` (Pass 350+).

## Notes

- Зависит от #8 (главный rewrite AGENTS.md) — сначала там должен
  появиться чёткий § MUST #0.

## Тип

`[wayfinder:task]`.

## Блокирует

Ничего.

## Заблокирован

#113 (главный rewrite).
