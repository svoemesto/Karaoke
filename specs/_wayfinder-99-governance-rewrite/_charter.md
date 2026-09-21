# #99 Wayfinder Charter — Переписать governance Karaoke по образцу TOP

> **Сессия**: 2026-09-14 (Pass 379), wayfinder charting + grilling + implementation planning pass.
> **Карта**: OpenProject #101 (subject: `[wayfinder:map] Переписать governance Karaoke по образцу TOP`).
> **Сессия**: ВСЯ карта wayfinder завершена в этой сессии.
> **Decision-тикеты**: #102 ✅ closed, #103 ✅ closed, #104 ✅ closed.
> **Implementation-тикеты созданы**: #105…#116 (12 шт.) — все child of #101.
> **Статус карты**: Карта закрыта в части решения. Implementation может стартовать.

## Что зафиксировано в этой сессии

### 1. Knowledge-first MUST #0 выполнен

- Прочитан `knowledge/README.md` и `knowledge/domains/README.md`.
- Особо внимательно:
  - `knowledge/README.md#Karaoke-overrides` — определена non-negotiable граница:
    **«CLAUDE.md ≠ AGENTS.md. Ассиметрию НЕ чинить»** (override #1).
- Grep по `knowledge/` (3 попытки):
  - `agent|governance|rule|compliance` → найдены `knowledge/guidelines/architecture-conventions.md`,
    `knowledge/guidelines/code-style.md`, ADR `0007-adopt-knowledge-as-ssot.md`.
  - `policy|directive|must` → ADR `0003-livedocs-markdown-yaml-mermaid.md` явно ссылается
    на правила в `AGENTS.md` (Knowledge-first, agent reads first).
  - Найдены governance-критичные секции: 
    `architecture-conventions.md` → «TOP-10 из AGENTS.md».

### 2. Что увидели при сранении Karaoke vs TOP

| Файл | Karaoke | TOP | Разница |
|---|---|---|---|
| `AGENTS.md` | 600 строк | 117 строк | x5.1 |
| `CLAUDE.md` | 237 строк | 117 строк (≈ = `AGENTS.md`) | НЕ равны в Karaoke |
| `constitution.md` | 457 строк | ? (нужно проверить на тикете #102) | |

**Ключевые структурные anti-pattern (pre-research):**
- AGENTS.md Karaoke = 600 строк. Модель тратит первые 100K токенов контекста на чтение правил,
  а контекст-burnout приводит к игнорированию более поздних разделов.
- В Karaoke правила **рассыпаны** по AGENTS.md, CLAUDE.md, constitution.md, 
  `knowledge/README.md`, `knowledge/guidelines/architecture-conventions.md`.
  В TOP всё в **одном файле** структурированно + есть секция «Mandatory Action».
- AGENTS.md (Karaoke) содержит раздел «Как обновлять этот файл» — т.е. правило
  о самом себе. Без явного **failure-stop** при попытке правки напрямую.
- В TOP каждое правило имеет одну из форм:
  - **Rule** (что запрещено / разрешено).
  - **Mandatory Action** (что MUST сделать, конкретно).
  - **Penalty** / **Failure** (что будет если нарушил).
- В Karaoke это всё перемешано, часто — **без penalty/failure**.

### 3. Grilling с владельцем (Pass 379)

**Q1 — Destination**: что считать «назначением» карты?
- Ответ: **«Переписать всю governance-цепочку (AGENTS + constitution + knowledge/protocol)»**.

**Q2 — Success criteria**: как понять что карта достигла конца?
- Ответ: **«Структурные метрики у AGENTS.md/CLAUDE.md в Karaoke»** (измеримо).

**Q3 — Topology**: какая модель governance в Karaoke после?
- Ответ: **«TOP-стиль: лаконичный, симметричный AGENTS.md=CLAUDE.md»**.
- ⚠️ **КОНФЛИКТ с Karaoke-override #1** (найден через triage):
  override #1 говорит «AGENTS.md ≠ CLAUDE.md. Ассиметрию НЕ чинить».
- **Развязка** (Pass 379): оставить ассиметрию, но **оба файла лаконичны**:
  - AGENTS.md: компактный, runtime-governance, hard gates + failure-stop.
  - CLAUDE.md: компактный, рекомендательный, **не дублирует** AGENTS.md.
  Каждый короткий, но они намеренно разные. Override #1 не меняется.

**Q4 — Notes карты** (multi-select):
- ✅ Knowledge-first MUST #0 + triage/grilling/wayfinder skills.
- ✅ OpenProject workflow (claim → add-comment → mark-review → close).
- ✅ Governance-review требования (CLAUDE.md § Governance-review): semver bump +
  секция «Governance Impact» в PR + одобрение владельца.
- ❌ Не выбрано: «Модель нарушает правила = эмпирический инвариант» (владелец
  предпочёл структурные метрики для измерения прогресса).

### 4. Что создано в трекере

| ID | Subject | Тип | Блокировка |
|---|---|---|---|
| #101 | `[wayfinder:map] Переписать governance Karaoke по образцу TOP` | map (parent) | — |
| #102 | `[wayfinder:research] Структурное сравнение governance Karaoke vs TOP` | research (AFK) | unblocked, claimed |
| #103 | `[wayfinder:research] Rule duplication map для governance Karaoke` | research (AFK) | blocked by #102, claimed |
| #104 | `[wayfinder:grilling] Scope of governance rewrite для Karaoke` | grilling (HITL) | blocked by #102, #103, HOLD |

Relations:
- #101 — parent of #102, #103, #104 (через OpenProject parent API + lockVersion).
- #103 follows #102 (rel id: 8).
- #104 follows #102 (rel id: 9).
- #104 follows #103 (rel id: 10).

### 5. Что НЕ сделано в этой сессии

- ❌ Содержательная диагностика не делалась (это задача тикета #102).
- ❌ Карта не claim'нута (она parent-only, её claim'нет владелец когда примет).
- ❌ Никакие governance-файлы **не редактировались**. Вся работа — design + tracker.

## Стоячие предпочтения для следующих сессий

Из секции **Notes** карты #101 (повторено здесь для удобства):

- **Knowledge-first MUST #0** — обязателен.
- **Skills**: `triage`, `grilling`, `wayfinder`, `managing-living-docs` по требованию.
- **Governance-review** (CLAUDE.md § Governance-review) — semver + секция «Governance Impact» +
  одобрение владельца.
- **OpenProject workflow** (AGENTS.md § Issue-tracker) — claim → работа → 
  add-comment --file report.md → mark-review → close-issue.
- **Karaoke-override #1 NON-NEGOTIABLE**: AGENTS.md ≠ CLAUDE.md.

## Out of scope карты

- unify AGENTS.md + CLAUDE.md в один файл (запрещено override #1).
- `offer.html`, `docs/architecture-notes-archive.md`, PDF-версии governance.
- `AGENTS_LOCAL.md` (только TOP), `autocode`/`autotest` пайплайны TOP.

## Следующая сессия

1. ✅ **Выполнено**: тикет #102 — структурное сравнение governance Karaoke vs TOP.
2. ✅ **Выполнено**: тикет #103 — rule duplication map для governance Karaoke.
3. ✅ **Выполнено**: тикет #104 (grilling) — владелец принял 7 решений по рекомендациям.
4. ✅ **Выполнено**: создано 12 implementation-тикетов (#105…#116), все child of #101.
5. ✅ **Выполнено**: parent-link для всех 12 task-тикетов через API.

### Следующая фаза: implementation

**НЕ wayfinder-сессия**. Это будет `speckit-*` workflow для тикета `#113`
(главный rewrite AGENTS.md) с governance-review.

Все 12 task-тикетов распределены по фазам:

- **Step A (параллельно)**: #105, #106, #107 — новые guard-скрипты.
- **Step B (параллельно)**: #108, #109, #110, #111, #112 — content rewrites.
- **Step C (главный)**: #113 — rewrite AGENTS.md (governance-review required).
- **Step D (каскадно)**: #114, #115 — cleanup CLAUDE.md + emoji policy.
- **Step E (финал)**: #116 — CI gate + close #99.

Каждый task-тикет = `git checkout -b <N>-<slug>` + speckit-specify → … → speckit-implement.
**Главное правило**: после прохождения CI 7/7 и governance-review.

## Файлы сессии

- `specs/_wayfinder-99-governance-rewrite/_charter-map.md` — тело карты #101 (OpenProject description).
- `specs/_wayfinder-99-governance-rewrite/101-chart-done-comment.md` — chart-done комментарий.
- `specs/_wayfinder-99-governance-rewrite/102-research-structural-comparison.md` — тикет #102 body.
- `specs/_wayfinder-99-governance-rewrite/103-research-rule-dup-map.md` — тикет #103 body.
- `specs/_wayfinder-99-governance-rewrite/104-grilling-scope.md` — тикет #104 body.
- `specs/_wayfinder-99-governance-rewrite/_charter.md` — этот файл.
