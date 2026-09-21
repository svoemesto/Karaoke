<!-- comment to OpenProject #99 — wayfinder progress report -->

## Прогресс по задаче #99 «Агентская работа в Karaoke и TOP» (Pass 379)

### Что сделано в этой сессии

Принята карта wayfinder **#101** «Переписать governance Karaoke по образцу TOP» —
roadmap для всего effort'а. Карта в OpenProject с parent-link и blocking relations.

**Дочерние тикеты карты**:

| ID | Subject | Статус | Что внутри |
|---|---|---|---|
| #102 | `[wayfinder:research]` Структурное сравнение governance Karaoke vs TOP | **Closed** ✅ | 50+ страниц структурного diff'а между двумя проектами |
| #103 | `[wayfinder:research]` Rule duplication map | **Closed** ✅ | 50 правил (R-01…R-50) с cross-reference table по 7 файлам |
| #104 | `[wayfinder:grilling]` Scope of governance rewrite | New (HITL) | Ожидает grilling-раунда с владельцем |

### Ключевые находки

- **AGENTS.md в Karaoke = 600 строк**, в TOP = **117 строк** (×5.1 разница).
- **10 (позже 12) правил** в Karaoke дублируются в 2+ файлах.
  HIGH severity: **R-01 (MUST #0)** живёт в **5 файлах**, **R-07 (JPA/Hibernate)** — в **4**.
- **Только 6 из 50 правил** в Karaoke имеют machine-readable guard-скрипт.
  (В TOP — **0 guard-скриптов**, все правила declarative.)
- **Конфликт governance-философии**: TOP «Symmetric Instructions» = AGENTS.md = CLAUDE.md,
  Karaoke-override #1 «Ассиметрию НЕ чинить» = разные файлы намеренно.
  Это **зафиксировано** в карте #101 как нерезолвимое design-intent и **не меняется** в этой карте.
- **Real content conflict** (не design-intent): R-39 vs R-40 — две матрицы
  машинно-специфичных исключений (nsa-i9 vs dev-pc) написаны в
  разных файлах и описывают одну машину. Требует единой матрицы.

### Что ВЫ (владелец) должны сделать дальше

Тикет **#104** ждёт grilling-раунда. Когда будете готовы — откройте эту
карту и:

1. Запустите skill `grilling` (любой агент это умеет).
2. Факты уже собраны в `specs/_wayfinder-99-governance-rewrite/` —
   сессия grilling'а может сразу перейти к **decision** (что удалить /
   оставить / консолидировать / добавить hard-gate), без новых research.
3. Готовые рекомендации уже лежат в `_charter.md` секции
   «Следующая сессия» → 5 конкретных решений (R-01/R-04-05/R-06/R-07/R-10
   consolidation; 3 на удаление; 3 на hard-gate; conflict R-39 vs R-40).

### Что будет, когда #104 закроется

Из решения #104 graduate-ятся `[wayfinder:task]` тикеты для **реальной
переписки** governance-файлов через `git checkout -b NNN-governance-rewrite`
+ `speckit-*` workflow + governance-review.

### Файлы сессии

- `specs/_wayfinder-99-governance-rewrite/_charter.md` — session summary.
- `specs/_wayfinder-99-governance-rewrite/_charter-map.md` — карта #101 (8914 chars в OpenProject).
- `specs/_wayfinder-99-governance-rewrite/102-research-resolution.md` (22.6 KB).
- `specs/_wayfinder-99-governance-rewrite/103-research-resolution.md` (20.7 KB).

### Когда закроется #99

После того как:
1. Владелец пройдёт grilling #104 (закроется).
2. Все имплементационные задачи будут выполнены и merged через governance-review.
3. Структурные метрики достигнуты (AGENTS.md ≤ 200 строк, нет дублей, hard-gate coverage ≥ 80%).

Тогда #99 автоматически закроется через `tools/tracker.sh close-issue 99`.

### Status

- **Pass 379, 2026-09-14, ai-agent** (создатель карты).
- Чартирование карты #101 завершено.
- Фронтир карты = {#104}.
- Ожидается HITL grilling с владельцем.
