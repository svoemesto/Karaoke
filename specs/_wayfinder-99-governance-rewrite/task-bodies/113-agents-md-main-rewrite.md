<!-- description task-тикета #113 (Step C: главный rewrite) -->

## Question

**Главный rewrite `AGENTS.md` в TOP-стиль плотный формат, ~150 строк**.

Зачем:
- Из Q7 (Pass 379, #104 resolution): target = TOP-стиль плотный, ~150 строк,
  density ~5 строк/правило, формат Rule/Protocol/Failure.
- AGENTS.md 600 → ~150 строк = -75%.

**Что должно быть в ответе**:

1. **Новая структура** AGENTS.md:
   ```
   # Project Instructions (AGENTS.md)
   
   ## АБСОЛЮТНОЕ ПРАВИЛО: язык общения (R-48)
   - Rule: Russian only.
   
   ## Knowledge-first MUST #0 (R-01, NON-NEGOTIABLE)
   - Rule: см. knowledge/README.md.
   - Protocol: 5 шагов.
   - Failure: спека возвращается на /speckit.clarify.
   - Enforcement: tools/spec-knowledge-preflight.sh.
   
   ## Как обновлять этот файл (R-42)
   - …
   
   ## Машинно-специфичные исключения (Pass 282, R-39+R-40 → матрица)
   - см. #110.
   
   ## Issue-tracker OpenProject (R-17, R-18)
   - …
   
   ## Git — CI-gate для master (R-16)
   - 3 уровня.
   
   ## Gradle/Docker/Frontend config (R-10, R-13, R-14, R-15)
   - cross-ref architecture-conventions.md.
   
   ## 🚦 HARD GATES (NON-NEGOTIABLE)
   - Список.
   
   ## Hard Gate: Machine-Specific Exceptions
   - см. #110.
   ```

2. **Удалить / переместить**:
   - Удалить `## MUST-CHECKLIST при старте сессии` (R-01 sub).
   - Удалить `## 🚦 НЕ делать` секции.
   - Удалить `## Диагностика на локальной машине` (Pass 358), переместить в `docs/ops/log-correlation.md` (cross-ref).
   - Удалить `## Машинно-специфичные исключения (Pass 282)` старые — заменит #110.
   - Удалить «🚦 nginx:alpine/node:latest» — cross-ref architecture-conventions.md (#108).

3. **Сохранить**:
   - АБСОЛЮТНОЕ ПРАВИЛО (русский язык).
   - Git workflow.
   - Hard-gates с явными failure-stop.

4. **Финальная проверка**: AGENTS.md ≤ 200 строк (target ≤ 150).
   - `wc -l AGENTS.md` — должно быть ≤ 150.
   - Каждое правило имеет явный failure-stop.
   - Каждое правило — в формате **Rule** / **Protocol** / **Failure**.

5. **CI 7/7 PASS**: все проверки зелёные.

## Notes

- **ГЛАВНЫЙ тикет карты**. Включает semver bump AGENTS.md (2.7.0 → 3.0.0).
- Governance-review обязателен (CLAUDE.md § Governance-review):
  semver + секция «Governance Impact» + одобрение владельца.
- Зависит от #105, #106, #107 (новые guard-скрипты, чтобы
  enforcement остался после rewrite).
- Зависит от #109, #110, #111, #112 (cleanup других файлов).
- Это **последний** шаг имплементации.

## Тип

`[wayfinder:task]` (governance-review required).
