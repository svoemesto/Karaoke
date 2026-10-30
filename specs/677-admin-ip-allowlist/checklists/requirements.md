# Specification Quality Checklist: Автоматизация allow-list админ-машины

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-30
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — **частично, отклонение зафиксировано**: фича инфраструктурная, её «пользователь» — владелец-оператор, а носитель требований — сам прод-хоста. FR сформулированы как возможности и правила поведения, а не как «написать такой-то код»; конкретика (порты, nginx, таймер, PR) названа там, где без неё требование непроверяемо (FR-001, FR-006, FR-007, FR-008).
- [x] Focused on user value and business needs — снятие простоя конвейера и ложных ночных алермов.
- [x] Written for non-technical stakeholders — **частично, отклонение зафиксировано**: тот же характер, что и в предыдущих инфраструктурных спеках (`676-oom-karaoke-app`).
- [x] All mandatory sections completed — Tracking, Knowledge References, User Scenarios, Edge Cases, Requirements, Success Criteria, Assumptions, Out of Scope.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — открытые решения вынесены в таблицу «Решения владельца» (Д-1…Д-3) и будут сняты с владельцем до перехода в `/speckit-plan`; у каждого решения указана рекомендация с обоснованием и альтернативы.
- [x] Requirements are testable and unambiguous — у каждого FR есть проверяемое условие; проверяемость вынесена в SC и в тест-кейс «IP сменился» (FR-018, SC-007).
- [x] Success criteria are measurable — временные интервалы (15 мин, 30 мин, 1 мин), нулевые счётчики ложных правок и ручных правок.
- [x] Success criteria are technology-agnostic — **частично, отклонение зафиксировано**: SC-001/SC-005/SC-007 неизбежно ссылаются на проверяемые интерфейсы (доступ к хранилищу и БД прод-хоста) — иначе критерий нельзя проверить.
- [x] All acceptance scenarios are defined — по 2-3 сценария на каждую из 4 историй.
- [x] Edge cases are identified — 13 случаев, включая расхождение источников адреса, гонку смены адреса, недоступность SSH/CI и превышение лимита частоты.
- [x] Scope is clearly bounded — раздел «Out of Scope» закрывает Уровень 1, третий allow-list (`setproperty`), удаление старых адресов и правки вне allow-list.
- [x] Dependencies and assumptions identified — ssh-доступ, Forgejo-токен, host-nginx, резервный источник адреса, права пользователя на прод-хосте.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — 20 FR покрыты 4 историями, 13 edge cases и 8 SC.
- [x] User scenarios cover primary flows — P1 (самовосстановление), P2 (прослеживаемость), P2 (обратимость), P3 (согласованность с репозиторием).
- [x] Feature meets measurable outcomes defined in Success Criteria — да.
- [x] No implementation details leak into specification — см. отклонение выше.

## Известные отклонения и риски

1. **Правило «спека — контракт»**: FR-001…FR-020 и SC-001…SC-008 зафиксированы как контракт. Изменение любого из них — только по слову владельца.
2. **Д-1 — источник истины**: выбранный по умолчанию вариант (прод — операционная истина, репозиторий догоняет через PR) расходится с предложенным в постановке задачи порядком «правка канона → PR → merge → scp». Причина — зафиксированный факт: Forgejo отклоняет одобрение собственного PR (`approve your own pull is not allowed`), поэтому автоматизация с токеном `owner` не может закрыть PR сама, а механизма доставки `nginx.conf` из репозитория на прод нет вовсе. Альтернатива (репозиторий как истина) требует второго аккаунта-согласователя.
3. **Прод-конфиг сейчас не совпадает с постановкой задачи**: в allow-list на прод-хосте нет адреса выхода VPN `87.58.202.244`, хотя в постановке задачи указано обратное. Это отдельный операционный факт, не FR: автоматизация сработает при следующей недоступности, но текущую дыру можно закрыть вручную по согласию владельца.
4. **Побочный эффект FR-019** (access-журнал stream-серверов): включается в конфигурацию прод-хоста и требует согласия владельца на изменение прода.

## Notes

- Пункты, помеченные как «частично», — осознанные отклонения от шаблона: инфраструктурная фича, где отказ от конкретики сделал бы требования непроверяемыми. Фактические отклонения перечислены выше; каждое требует слов владельца при утверждении спеки.
- Спека уходит владельцу; цепочка `plan → tasks → analyze → implement` не начинается без его слова (AGENTS.md, Tier-1 «Процесс: wayfinder → исполнение»).
