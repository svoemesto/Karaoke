<!-- description task-тикета #112 (Step B: content rewrite) -->

## Question

Сократить `docs/governance/knowledge-first.md` (169 строк, 6-я копия R-01):
оставить только enforcement-секцию L60-89, удалить дублирующие шаги L31-44.

Зачем:
- Из Q1 (Pass 379, #104 resolution): docs/governance/knowledge-first.md — 6-я
  копия R-01, сам факт её существования = признание, что MUST #0 разбросан.
- Это даст −~50 строк в operational памятке.

**Что должно быть в ответе**:

1. Прочитать текущий `docs/governance/knowledge-first.md`.
2. Удалить шаги L31-44 (если они дублируют MUST #0 / Knowledge-first MUST).
3. Оставить enforcement-секцию L60-89 (как раздел про memory protocol agent).
4. Добавить header: `> Operational memory for layer-2 agent activation. Canonical MUST #0 lives in AGENTS.md.`
5. Длина файла: 169 → ~50 строк.

## Notes

- Зависит от #113 (главный rewrite AGENTS.md) — убедиться, что в новом
  AGENTS.md MUST #0 читается без необходимости смотреть в эту памятку.

## Тип

`[wayfinder:task]`.
