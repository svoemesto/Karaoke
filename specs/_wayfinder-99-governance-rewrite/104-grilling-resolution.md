# #104 Resolution — Scope of governance rewrite для Karaoke

> **Resolution submitted**: 2026-09-14, Pass 379.
> **Resolver**: grilling-раунд между владельцем и skill `grilling` (агентом).
> **Skill**: `wayfinder` + `grilling`.
> **Inputs**: findings из #102 (структурное сравнение) + #103 (rule duplication map).
> **Format**: 7 решений, по рекомендациям агента.

---

## Принято владельцем: 7 решений из 7

### Q1 — Канон для R-01 (MUST #0 / Knowledge-first)

**Решение**: ✅ **Канон = `AGENTS.md` L74-122** (с hard-gate + failure-stop).

Действия:
- `CLAUDE.md` L50-73 (MUST-CHECKLIST) → удалить, заменить однострочным cross-ref `→ AGENTS.md § MUST #0`.
- `constitution.md` L257-308 (Principle IX) → сократить до ~10 строк (cross-ref + ключевые моменты).
- `knowledge/README.md` L62-74 → cross-link, не дублировать шаги.
- `docs/governance/knowledge-first.md` → оставить только enforcement-секцию L60-89, удалить дублирующие шаги L31-44.
- `tools/spec-knowledge-preflight.sh` остаётся каноническим enforcement layer.

Ожидаемый результат: −~100 строк governance, −4 дубля R-01.

### Q2 — Канон для build/docker конвенций

**Решение**: ✅ **`architecture-conventions.md` становится primary source** для R-04 (nginx), R-05 (node), R-06 (TOP-10 ловушек), R-08 (sanitizer), R-11 (MP4), R-44 (MLT).

Действия:
- `CLAUDE.md` L123-135 (TOP-10 ловушек) → удалить, оставить однострочный cross-ref.
- `CLAUDE.md` L127, L128, L130, L135 — все про docker/frontend/sanitizer → cross-ref.
- `AGENTS.md` L287 «❌ nginx:alpine / node:latest» → оставить как memory aid (короткое), cross-ref в architecture-conventions.
- `constitution.md` L325-327, L358 — cross-ref.
- `architecture-conventions.md` → расширить (это primary, не файл-приложение).

Ожидаемый результат: −~50 строк в CLAUDE.md, явный single-source для всех build/runtime traps.

### Q3 — Hard-gate coverage

**Решение**: ✅ Создать **3 новых guard-скрипта** для rules, которые сейчас advisory:
1. `tools/check-no-jpa-imports.sh` — для R-07 (JPA/Hibernate запрет).
2. `tools/check-docker-image-tags.sh` — для R-04 + R-05 (nginx:stable, node:22-alpine).
3. `tools/check-no-mp4-mentions.sh` — для R-11 (MP4/скачивание).

Все три → подключить к `.pre-commit-config.yaml` + `.github/workflows/lint.yml`.

Ожидаемый результат: enforcement coverage 12% → 18% (9 из 50 правил с hard-gate).

### Q4 — R-39 vs R-40 (машинно-специфичные исключения)

**Решение**: ✅ **Консолидировать в единую матрицу в `AGENTS.md`**.

Действия:
- Создать новую секцию в `AGENTS.md`: «Машинно-специфичные исключения» (~15-25 строк).
  Формат: таблица hostname × что разрешено/запрещено.
- Перенести содержимое **AGENTS.md L289-295** (`nsa-i9`/`nsa`) в эту секцию.
- Перенести содержимое **constitution.md L342-345, L373-379** (`dev-pc`/`dev`) в эту же секцию.
- Удалить старые секции, оставить cross-ref.
- Decision protocol: если хочется ещё `AGENTS_LOCAL.md` (по TOP-стилю) — отдельный governance-PR, не в этой карте.

Ожидаемый результат: единственный source of truth для machine-specific исключений.

### Q5 — Emoji policy

**Решение**: ✅ **Ограничить emoji до структурных маркеров** в governance-документах.

Правило:
- ✅ В failure-stop секциях — **допустимо** (✅ / ❌ / ⚠️ / 🚦 как hard-gate маркер).
- ❌ В prose (описания, нарративы) — **избегать**.
- 🔧 Существующее использование emoji в L-cайтах (`CLAUDE.md` 🚦 в 10 секциях, `AGENTS.md` ⛔/⚠️) — **оставить как есть** (это scan-friendly маркеры).
- 📜 Добавить в AGENTS.md одно явное правило-маркер: «эмодзи в failure-stop — норма, в prose — избегать».

Ожидаемый результат: устранить внутренний конфликт policy, без mass removal.

### Q6 — Constitution scope

**Решение**: ✅ **(b) Средний scope** — сократить Principles до ~10 строк каждая + удалить дубликаты.

Действия:
- Каждый из 9 Principles (I-IX) сократить до ~10 строк: название + 1-2 строчки сути + cross-ref в подробный документ (architecture-conventions.md, ADR-ы, knowledge/).
- Удалить дубликаты R-04 / R-05 / R-32 / R-43 / R-43 перенаправить в ADR-ы.
- L257-308 (Principle IX, MUST #0) → кросс-реф на AGENTS.md (см. Q1).
- Принцип VII (Cross-Machine) → cross-ref на новую секцию «Машинно-специфичные исключения» в AGENTS.md (см. Q4).
- НЕ переписывать constitution целиком (это вышло бы за scope карты).

Ожидаемый результат: constitution.md 457 → ~280-350 строк (style TOP).

### Q7 — AGENTS.md target format

**Решение**: ✅ **(a) TOP-стиль плотный, ~150 строк**.

Параметры:
- Формат секций: `### <Name>` → `**Rule**: ...` → `**Protocol**: ...` → `**Failure**: ...`.
- Каждая секция ≤ 5-7 строк.
- Длинные примеры → в architecture-conventions.md / ADR-ы / cross-ref.
- Hard-gate sections: ~30% файла (компактно, ~45 строк).
- Knowledge-first MUST #0: занимает ≤ 30 строк (включая failure-stop и enforcement).
- Tracker workflow (R-17, R-18): ≤ 15 строк (OpenProject workflow + auto-hooks).

Ожидаемый результат: AGENTS.md 600 → ~150 строк, 25-30 правил, density ~5 строк/правило.

---

## Сводка для graduate'а

Из этих 7 решений вытекают следующие `[wayfinder:task]` тикеты для имплементации.
Каждый тикет — отдельная git-ветка + speckit-* workflow + governance-review.

### Список task-тикетов (для создания в следующей сессии)

1. **`[wayfinder:task]` Создать `tools/check-no-jpa-imports.sh` + подключить к pre-commit + CI** (R-07)
2. **`[wayfinder:task]` Создать `tools/check-docker-image-tags.sh` + подключить к pre-commit + CI** (R-04, R-05)
3. **`[wayfinder:task]` Создать `tools/check-no-mp4-mentions.sh` + подключить к pre-commit + CI** (R-11)
4. **`[wayfinder:task]` Расширить `architecture-conventions.md` как primary source для R-04/R-05/R-06/R-08/R-11** (Q2)
5. **`[wayfinder:task]` Удалить MUST-CHECKLIST из CLAUDE.md, заменить на cross-ref в AGENTS.md** (Q1)
6. **`[wayfinder:task]` Создать в AGENTS.md секцию «Машинно-специфичные исключения» + удалить старые секции** (Q4)
7. **`[wayfinder:task]` Сократить Principles в constitution.md (I-IX) до ~10 строк + удалить дубликаты R-04/R-05/R-32/R-43** (Q6)
8. **`[wayfinder:task]` Переписать AGENTS.md в TOP-стиль (≤ 150 строк, density ~5 строк/правило)** (Q7) — **главный тикет**, будет требовать governance-review
9. **`[wayfinder:task]` Сократить `docs/governance/knowledge-first.md` (L31-44 убрать, оставить enforcement L60-89)** (Q1)
10. **`[wayfinder:task]` Добавить emoji policy marker в AGENTS.md** (Q5) — мелкий, идёт вместе с #8
11. **`[wayfinder:task]` Удалить «🚦 НЕ делать» секции из CLAUDE.md (L208-218 + L155-169)** — каскад от #5, идёт с #8
12. **`[wayfinder:task]` Запустить CI 7/7 + governance-review для всей переписки** (последний gate)

### Последовательность выполнения

- **Шаг A (параллельно)**: тикеты #1, #2, #3 — новые guard-скрипты (независимы).
- **Шаг B (параллельно)**: тикеты #4, #5, #6, #7 — content-rewrite (независимы друг от друга).
- **Шаг C**: тикет #8 — главный rewrite AGENTS.md, должен быть последним (зависит от #5).
- **Шаг D (финал)**: тикеты #9, #10, #11 — мелкие cleanup, идут с #8.
- **Шаг E**: тикет #12 — CI gate, последний.

### Артефакты сессии (финальные)

- `specs/_wayfinder-99-governance-rewrite/104-grilling-resolution.md` (этот файл).
- `specs/_wayfinder-99-governance-rewrite/_charter.md` — обновлён.
- `specs/_wayfinder-99-governance-rewrite/_charter-map.md` — карта #101 обновлена с 4-м решением.

### Метрики успеха (Success criteria)

После выполнения всех task-тикетов:
- ✅ AGENTS.md ≤ 200 строк (target ~150).
- ✅ CLAUDE.md ≤ 100 строк.
- ✅ Constitution.md ~280-350 строк.
- ✅ R-01 живёт в **1 файле** (AGENTS.md) + 4 cross-ref.
- ✅ R-04, R-05, R-06, R-08, R-11 живут в 1 файле (architecture-conventions.md).
- ✅ R-39, R-40 консолидированы в 1 матрицу.
- ✅ Hard-gate coverage: 9 из 50 правил (18%).
- ✅ Emoji policy: явное правило в AGENTS.md, нет внутренних конфликтов.
- ✅ Все governance-файлы имеют явный failure-stop для NON-NEGOTIABLE rules.

— конец resolution —
