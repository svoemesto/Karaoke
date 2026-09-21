<!-- description тикета #103 — child of map #101 -->

## Question

Построить **«rule duplication map»** для governance в Karaoke:
для каждого правила определить, в скольких местах оно появляется и
где живёт его канонический источник.

**Зачем**: если одно правило живёт в 3 файлах (например, MUST #0 в
AGENTS.md, MUST-CHECKLIST в CLAUDE.md, и section в knowledge/README.md),
модель может «выбирать», какой из трёх вариантов соблюдать — это известный
паттерн дрейфа compliance. Цель — найти такие правила и предложить,
где жить канону.

**Что должно быть в ответе** (succinct, таблица + 1 параграф выводов):

1. **Inventory rules** в AGENTS.md (Karaoke): выписать каждое правило
   (MUST/SHOULD/MUST NOT/«при X делай Y»/«при ошибке стоп»). ~30-50
   правил ожидается.

2. **Cross-reference table**:
   ```
   | Rule ID | AGENTS.md | CLAUDE.md | constitution.md | knowledge/ | Comments |
   | -------- | --------- | --------- | ---------------- | ---------- | -------- |
   | R-01     | L100      | L30       | —                | README.md  | MUST #0  |
   | R-02     | L300      | —         | L150             | —          | только в 2|
   ...
   ```

3. **Duplicates (≥2 файлов)**: список ID правил, которые живут
   больше чем в одном файле. Это **первая партия** кандидатов на
   консолидацию.

4. **Orphans (только в одном файле)**: список ID правил, которые живут
   только в одном файле и **не** имеют cross-reference. Это
   **вторая партия** — потенциально «бесхозные» правила.

5. **Source-of-truth proposal** (НЕ финальный — только candidate):
   для каждого duplicate — отметить, какой файл, по-твоему, должен
   стать каноном. Например: «R-01 (MUST #0) — канон AGENTS.md; CLAUDE.md
   должна ссылаться на AGENTS.md, не дублировать».

## Notes

- Запускается субагентом с `run_in_background: true`.
- Субагент MUST следовать **Knowledge-first MUST #0**: сначала
  `knowledge/README.md` + `knowledge/domains/README.md`, **потом**
  grep по `AGENTS.md` / `CLAUDE.md` / `constitution.md` /
  `knowledge/guidelines/architecture-conventions.md`.
- Результат — файл `specs/<NNN>-governance-rewrite-comparison/rule-dup-map.md`
  или markdown-комментарий через
  `tools/tracker.sh add-comment 103 --file rule-dup-map.md`.
- **Не** принимать решения, что консолидировать — это grilling.
- Этот тикет **зависит от тикета #102** (структурное сравнение):
  anti-pattern candidates из #102 должны стать ключом для таблицы в этом
  тикете. Если #102 ещё не закрыт — **ждать**.

## Тип

`[wayfinder:research]` (AFK).

## Блокирует

- Тикет №104 (grilling: «rule-by-rule audit»)

## Заблокирован

- Тикет №102 (структурное сравнение).
