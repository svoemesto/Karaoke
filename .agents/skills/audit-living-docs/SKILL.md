---
name: audit-living-docs
description: Use when performing a deep architectural consistency check of the Living Documentation (SSoT) in `knowledge/` across different abstraction levels (C4 L1-L3).
disable-model-invocation: true
variant: karaoke
---
---

# Audit Living Documentation (Karaoke-adapted)

> **Karaoke-вариант.** Намеренное отличие от общего навыка living-docs (структура `knowledge/` вместо `docs/`, линтер `tools/lint-knowledge.py`). Панель скиллов показывает его как «проектный вариант», а не как устаревшую копию.

> **Karaoke-adapted** версия. Исходный skill (generic-bootstrap)
> предполагал структуру `docs/`. В Karaoke SSoT живёт в `knowledge/`.
> Все остальные отличия — ниже.

## Overview

Architectural audit — процесс проверки того, что Living Documentation
консистентна, непротиворечива и точно отражает границы системы на всех
уровнях абстракции.

**Core Principle:** Trust nothing. Move from "confirming what is there"
to "hunting for what is wrong."

## Karaoke-overrides (явные отличия от generic)

Перед использованием этого skill **обязательно** прочитай
[`AGENTS.md`](../../../AGENTS.md).

Ключевые отличия:

| Тема | Generic (docs/) | Karaoke (knowledge/) |
| --- | --- | --- |
| SSoT каталог | `docs/` | `knowledge/` |
| Линтер | `python3 scripts/lint-docs.py` | `python3 tools/lint-knowledge.py` |
| Domain | `docs/domains/<domain>/` | `knowledge/domains/<domain>/{domain.md, components/}` |
| Hierarchy | `L1 → L2 → L3` | **`L3 → L2 → L1`** (Linking Protocol в Karaoke-override p.5) |
| **Git workflow** | человек коммитит (skill требует) | **агент делает полный git-цикл** (см. AGENTS.md) — в Karaoke **игнорируй** последнее правило skill |
| Слайсинг | "max 25 domains/slice" | 9 доменов всего — 1 slice, 1 инспектор |

- **Не запускать `bootstrap-living-docs`** — SSoT в Karaoke уже развёрнут
  на `knowledge/` (спеки 322, 326–336). Навык подключён как общий и нужен только
  для новых проектов. Для обновления — [`managing-living-docs`](../managing-living-docs/SKILL.md),
  для глубокой проверки — этот skill.

## Когда использовать

- Перед **большими архитектурными изменениями** (новый C4-слой, новый
  домен, миграция).
- При **onboarding новых доменов** (проверить, что не нарушен Linking Protocol).
- При обнаружении **парадокса** (два похожих запроса ведут себя по-разному).
- При **периодическом QA** (раз в N дней / при крупных спеках).
- **НЕ** для ежедневной работы — используй [`managing-living-docs`](../managing-living-docs/SKILL.md).

## Bulk Reading Protocol (MANDATORY)

Чтобы не замусоривать контекст и не тратить лишние tool latency, **запрещено**
использовать индивидуальные `Read` для каждого файла. Используй **один**
`Bash` для агрегации всех файлов аудируемых доменов.

**Protocol:**

1. Определи набор доменов для аудита.
2. Выполни **одну** `Bash` команду для сбора:
   ```bash
   find knowledge/domains/<domain1> knowledge/domains/<domain2> -type f \
     -exec echo "================================================================================" \; \
     -exec echo "FILE: {}" \; \
     -exec echo "================================================================================" \; \
     -exec cat {} \; \
     -exec echo "--------------------------------------------------------------------------------" \;
   ```
3. Анализируй результат как единый источник истины для этого среза.

## Audit Methodology (The Double-Pass + Verification)

Чтобы избежать confirmation bias, **обязательно** выполняй аудит в
двух противоположных направлениях + финальная техническая проверка.

### Pass 1: Top-Down (L3 → L2 → L1)

> **Karaoke-override**: в Karaoke Linking Protocol идёт снизу вверх
> (L3 → L2 → L1), а не сверху вниз. Поэтому audit проводим снизу.

**Goal:** Verify that L3 components are properly aggregated in L2,
and L2 domains are properly listed in L1.

1. **Data Collection**: Bulk Reading Protocol для всех файлов целевых доменов.
2. **Trace Entities**: Возьми каждый компонент в L3 (`knowledge/domains/<name>/components/*.md`) → Найди упоминание в L2 (`domain.md`) → Найди упоминание домена в L1 (`knowledge/README.md`).
3. **Dependency Check**: Каждая внешняя система в L1 должна иметь соответствующую integration-точку в L3.
4. **Constraint Propagation**: Если L2 определяет бизнес-инвариант (например, "Email уникален"), проверь что L3 действительно enforce'ит это.

### Pass 2: Bottom-Up (L1 → L2 → L3)

**Goal:** Идентифицировать "shadow architecture" (фичи реализованы в коде/L3, но забыты в specs).

1. **Data Collection**: Bulk Reading Protocol.
2. **Component Mapping (L3 → L2)**: Список каждого файла в `components/`. Проверь, что **каждый** явно упомянут и описан в L2 `domain.md` И имеет краткое описание ответственности. Список имён файлов без описаний — failure.
3. **Domain Mapping (L2 → L1)**: Список всех директорий в `knowledge/domains/`. Проверь, что каждый домен явно упомянут в `knowledge/README.md` и `knowledge/domains/README.md`.
4. **Boundary Audit**: Идентифицируй каждый cross-domain call в L3. Проверь, что это взаимодействие задокументировано как зависимость в L2.
5. **Abstraction Leakage**: Проверь, содержат ли L3 компоненты логику, принадлежащую более высокому домену (нарушение DDD).
6. **Epic Linkage Audit**: Найди все ссылки на Epics (`knowledge/epics/*.md`). Проверь, что каждый файл существует на диске. Битые ссылки — critical defects.

### Pass 3: Technical Verification (Linting)

**Goal:** Убедиться, что документация соответствует архитектурному стилю.

1. **Action**: Запусти **все три** линтера:
   ```bash
   bash tools/check-knowledge-structure.sh        # структура, cross-link в README
   bash tools/check-knowledge-cross-links.sh      # ../X.md и related:
   python3 tools/lint-knowledge.py                # эмодзи, headers, L2→L1 linking
   ```
2. **Requirement**: Все identified errors должны быть перечислены в финальном отчёте как critical defects.

## Hunting for Defect Patterns

| Pattern | Что искать | Red Flag |
| :--- | :--- | :--- |
| **Semantic Drift** | То же понятие, разные имена на L1-L3. | L1 — "StatBySong", L2 — "SiteStats", L3 — `StatBySong` (но контексты разные). |
| **Silent Contradiction** | Два файла описывают один процесс по-разному. | File A говорит "Server-side check", File B говорит "Client-side intersection". |
| **The Ghost Feature** | Подробная L3 реализация без упоминания в L2/L1. | Сложная validation логика в L3, но L2 говорит "simple validation". |
| **Boundary Bleed** | Домен A напрямую манипулирует внутренним состоянием домена B. | `SongCore` напрямую обновляет `User` status вместо через `UserService`. |
| **Magic Codes** | Технические коды, ID, константы захардкожены в L3. | DTO `RegRightsCustomer` использует код `558401080100` вместо ссылки на `dictionaries.md`. |
| **Stale Reference** | Ссылка на домен/файл, который больше не существует. | В L3 ссылка на ADR, который был удалён. |

## Audit Report Structure

Отчёт **обязательно** evidence-based. Не используй расплывчатые формулировки
вроде "кажется, неконсистентно".

1. **Linting Status**: результат **всех трёх** линтеров (Pass/Fail + список ошибок).
2. **Consistency Matrix**:
   - Entity/Flow → L3 [OK/Gap] → L2 [OK/Gap] → L1 [OK/Gap]
3. **Critical Contradictions**:
   - `[File A:Line X]` vs `[File B:Line Y]` → `[Conflict Description]`
4. **Boundary Violations**:
   - `[Component A]` → `[Component B]` (Cross-domain) → `[Reason why it violates DDD]`
5. **Missing Links**:
   - "L3 component `X` не имеет определения в L2."
6. **Epic Linkage Status**: список битых ссылок на эпики или "All links verified".

## Common Mistakes

- **Confirmation Bias**: искать доказательства, что документация правильна. **Fix:** активно ищи причины, почему она неправильна.
- **Superficiality**: пометить зависимость как "OK" потому что слово встречается один раз. **Fix:** проследи полный жизненный цикл взаимодействия.
- **Ignoring the "Silent"**: сообщать только о явных ошибках. **Fix:** сообщай о пропущенной информации как "Gap" (например, отсутствующая state transition matrix).
- **Inefficient Reading**: читать файлы один за другим. **Fix:** ВСЕГДА следуй Bulk Reading Protocol.
- **Context Overflow**: пытаться аудитировать всю систему в одной сессии. **Fix:** используй Scaling Strategy ниже.

## Karaoke-специфичные ошибки

- **Линтер падает на эмодзи в ADR** → не чини ADR (append-only), используй Karaoke-override в `lint-knowledge.py` (уже настроен).
- **Cross-links broken в ADR** → ADR содержит legacy ссылки на `livedocs/architecture/...` (намеренно). Не «чини» — ADR append-only.
- **`AGENTS.md` или `CONTRIBUTING.md` упоминает `livedocs/`** → должно быть `knowledge/` или `archive/docs/architecture-notes.md`. Это governance-долг — отдельная правка через `0XX-agents-md-update`.

## Scaling Strategy (Mandatory)

Чтобы избежать context overflow и сохранить глубину аудита, **все аудиты
выполняются dedicated Inspector субагентами**. Главный агент — Synthesizer.

1. **Slicing Rule**: раздели общее число доменов на слайсы **максимум по 25**.
2. **Agent Calculation**: число инспекторов = ⌈total_domains / 25⌉.
   - 1-25 доменов → 1 Inspector.
   - 26-50 → 2 Inspector'а.
3. **Execution**: даже для одного домена — dedicated Inspector (для context isolation).
4. **Synthesis**: Synthesizer собирает отчёты всех Inspector'ов и делает финальный cross-domain анализ.

**Karaoke-примечание**: у нас всего **9 доменов** — это **один слайс**, нужен
**один Inspector** субагент.

## Git workflow (Karaoke-override)

> ⚠️ **Karaoke-override**: этот skill говорит "Git commits must be handled
> by the human partner" — **игнорируй** это правило в Karaoke.
> В Karaoke **агент делает полный git-цикл** (см. AGENTS.md §
> «Git — CI-gate для master»). Используй `tools/reserve-branch-number.sh`,
> `gh pr create`, `gh pr merge`.

## См. также

- [`AGENTS.md`](../../../AGENTS.md) — runtime-инструкции для AI-агента.
- [`managing-living-docs`](../managing-living-docs/SKILL.md) — для
  регулярного обновления knowledge/ при изменении кода.
- [`bootstrap-living-docs`](../bootstrap-living-docs/SKILL.md) — **НЕ
  применять** (Karaoke уже бутстрапнут).
- `knowledge/README.md` — главная карта SSoT.
