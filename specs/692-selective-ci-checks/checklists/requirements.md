# Specification Quality Checklist: Выборочный запуск CI-проверок по зонам интереса

**Purpose**: Проверить полноту и качество спецификации до перехода к планированию
**Created**: 2026-11-01
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — требования сформулированы через
      поведение («проверка запускается / помечается неприменимой», «гейт отказывает»); конкретная
      конструкция вынесена в карту решений KARAOKE-364, которая является входом для планирования
- [x] Focused on user value and business needs — ценность: меньше лишних проверок на PR, отсутствие
      ложных блокировок (34.6 % красных PR — из-за проверки вне зоны), честный гейт
- [x] Written for non-technical stakeholders — аудитория — разработчик и лид проекта; термины
      «зона интереса», «применимая проверка», «неприменимо» определены в FR-016 и в разделе словаря
- [x] All mandatory sections completed — все обязательные разделы шаблона заполнены
      (OpenProject Tracking, Knowledge References, User Scenarios, Requirements, Success Criteria,
      Assumptions)

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — маркеров нет: все неоднозначности закрыты решениями
      владельца (раунд вскрытия 2026-11-01) и фактами двух исследований
- [x] Requirements are testable and unambiguous — FR-001…FR-017: у каждого проверяемое состояние
      (например, FR-002: «запускается тогда и только тогда, когда изменение затрагивает зону»)
- [x] Success criteria are measurable — SC-001…SC-008 с числами из базовой статистики (KARAOKE-373):
      93.9 % тяжёлых исполнений, −51.8 % wall-clock, 34.6 % → 0 ложных блокировок
- [x] Success criteria are technology-agnostic (no implementation details) — критерии про наблюдаемое
      поведение прогона и гейта, без указания файлов и инструментов
- [x] All acceptance scenarios are defined — 6 пользовательских историй, 16 acceptance-сценариев
- [x] Edge cases are identified — 13 краевых случаев (путь вне зон, переименование, форк, shallow-клон,
      неизвестная версия манифеста, группы, конкуренция прогонов, ручной запуск)
- [x] Scope is clearly bounded — «Out of scope» карты KARAOKE-364; в спецификации зафиксировано, что
      состав проверок не меняется, кроме переноса одной проверки истории git
- [x] Dependencies and assumptions identified — раздел Assumptions: база диффа, поддержка расписания,
      один раннер на машине разработчика, неизменность pre-commit, вход — карта решений

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — FR ↔ сценарии: FR-002 → US1/US2,
      FR-003/FR-010/FR-011 → US3, FR-006/FR-012/FR-013 → US4, FR-007 → US5, FR-005 → US6,
      FR-017 → SC-005
- [x] User scenarios cover primary flows — правка документации (US1), правка одного фронтенда (US2),
      слияние PR (US3), регистрация новой проверки (US4), местный прогон (US5), страховка (US6)
- [x] Feature meets measurable outcomes defined in Success Criteria — SC покрывают все шесть историй
- [x] No implementation details leak into specification — конструкция (манифест, джоб `changes`,
      агрегат `ci-summary`) описана в карте решений и в разделе Assumptions как вход планирования,
      а не как требование

## Notes

- Проверка выполнена лидом 2026-11-01 по списку из скилла `speckit-specify`; один пункт получил
  уточнение: «Written for non-technical stakeholders» — спецификация по природе описывает
  инфраструктурное поведение, поэтому формулировки даны через наблюдаемые эффекты (сколько проверок
  запускается, что делает гейт), без названий файлов и инструментов.
- Спецификация описывает требуемое поведение, а не выбранную конструкцию: конструкция зафиксирована
  в карте KARAOKE-364 (11 закрытых тикетов, «Decisions so far») и передаётся в планирование как
  вход.

## Knowledge Compliance *(MANDATORY — see Constitution Principle IX)*

- [x] Knowledge pre-flight выполнен ДО `codegraph_explore` / grep по `src/` — pre-flight выполнен
      2026-11-01 (`tools/spec-knowledge-preflight.sh --create-marker selective-ci-checks`, 12 ссылок,
      маркер `.specify/.preflight-selective-ci-checks.ok`)
- [x] `knowledge/README.md` + `knowledge/domains/README.md` прочитаны **полностью** — прочитаны оба
      (реестр доменов, слои знаний, правило жизненного цикла документации)
- [x] Релевантные домены определены через `grep -r '<keyword>' knowledge/` (минимум 3 запроса) —
      5 grep-запросов зафиксированы в `spec.md § Knowledge References → Pre-flight log`
- [x] Все `domain.md` + `components/*.md` релевантных доменов прочитаны — релевантных продуктовых
      доменов нет: фича целиком в слое инфраструктуры (`knowledge/system/infra/`); прочитаны
      `knowledge/system/README.md`, `knowledge/system/infra/ci-tools.md`,
      `knowledge/guidelines/architecture-conventions.md`, `knowledge/system/infra/pre-commit-config.md`
- [x] Все `local-*.md` ADR в `knowledge/adr/` прочитаны — прочитаны `local-0011` (Forgejo/CI),
      `local-0012` (трекер Plane), `local-0016` (наблюдаемость и след); остальные ADR перебраны
      grep'ом по ключевым словам (`CI`, `workflow`, `runner`)
- [x] В `spec.md` заполнена секция «Knowledge References» с конкретными путями — 9 документов с
      указанием, зачем прочитан
- [x] Если Knowledge противоречит реквесту — это явно зафиксировано в «Open Questions» —
      противоречий нет; принятые решения (R-47, R-48, ADR local-0011) учтены
- [x] Если grep по `knowledge/` ничего не дал — зафиксировано явное «no relevant docs» — зафиксировано
      в `spec.md § Knowledge References`: по продуктовым доменам релевантных документов нет, поиск
      шёл по 5 запросам с ключевыми словами `workflow|CI|pipeline`, `paths:|changed-files`,
      `Forgejo [0-9]|act_runner`, `R-47|R-48`, `merge-pr|status-чеки`
