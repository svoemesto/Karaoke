<!-- description тикета #102 — child of map #101 -->

## Question

Провести **side-by-side структурное сравнение** governance-документов между
проектом Karaoke (`/home/nsa/Karaoke`) и проектом TOP (`/home/nsa/top`),
чтобы выявить **конкретные структурные различия**, которые могут объяснять
эмпирическое наблюдение владельца: «модель нарушает правила в Karaoke, но
соблюдает в TOP».

**Что должно быть в ответе** (succinct, не более 1 страницы):

1. **Длины файлов и распределение содержимого.**
   Для каждого файла:
   - AGENTS.md (Karaoke) vs AGENTS.md (TOP)
   - CLAUDE.md (Karaoke) vs CLAUDE.md (TOP)
   - .specify/memory/constitution.md (Karaoke) vs constitution.md (TOP)

   Если в TOP чего-то нет — зафиксировать как «отсутствует».

2. **Rule duplication index** для Karaoke: какие правила упоминаются
   в **двух или более** файлах? Конкретные пары (например, «MUST #0
   в AGENTS.md ↔ MUST-CHECKLIST в CLAUDE.md ↔ Link в
   knowledge/README.md»). Список пар как таблица, не нарратив.

3. **Failure-stop coverage**: какие правила в AGENTS.md / CLAUDE.md
   (Karaoke) **не** имеют явного failure-stop? Группировать по разделам
   («нет failure-stop вообще» / «failure-stop слабый (не описано
   что делать)» / «failure-stop есть, но не enforceable»).

4. **Hard-gate enumeration**: какие правила в AGENTS.md (TOP) сформулированы
   как «Hard Gate» или «MUST NOT», и **отсутствуют** в AGENTS.md (Karaoke)?
   Сравнивать через grep по разделам «Hard Gate», «MUST NOT», «Protocol».

5. **Anti-pattern candidates** (без вердикта, just observation):
   - **«Слишком длинный → внимание рассеивается»** (Karaoke 600 / TOP 117)
   - **«Дублирование правил → модель не знает, какое применять»**
   - **«Неинструментабельные правила → нет failure-stop»**

Каждый anti-pattern — отдельной строкой с evidence (конкретные места
в файлах).

## Notes

- Запускается субагентом с `run_in_background: true`.
- Субагент MUST прочитать `knowledge/README.md` + `domains/README.md`
  перед grep/Read.
- Результат — файл `specs/<NNN>-governance-rewrite-comparison/diff.md`
  (NNN — спека-карты #101) или markdown-комментарий через
  `tools/tracker.sh add-comment 102 --file diff.md`.
- **Не** интерпретировать причины — только факты. Интерпретация — в
  следующем тикете (grilling).

## Тип

`[wayfinder:research]` (AFK, research-субагент).

## Блокирует

- Тикет №103 (grilling: «scope of governance rewrite»)
- Тикет №104 (grilling: «rule-by-rule audit»)

## Заблокирован

Тикет №101 (карта) — claimed/closed? Нет, но карта — parent, не blocker
в смысле OpenProject relation. Этот тикет может стартовать сразу после
создания.
