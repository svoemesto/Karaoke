# Specification Quality Checklist: Выбор статуса песни при апруве задания (5 или 6)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-13
**Feature**: [spec.md](spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — упоминается Spring Boot только в контексте «как парсить query-параметр», что является API-контрактом, а не implementation detail
- [x] Focused on user value and business needs — основная ценность: «возможность отложить рендер без костылей с ручным даунгрейдом»
- [x] Written for non-technical stakeholders — US1/US2/US3 описаны в терминах действий админа («выбрать radio», «нажать кнопку»), FR-001..FR-010 — функциональные контракты
- [x] All mandatory sections completed — User Scenarios, Requirements, Success Criteria, Assumptions, Clarifications

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — все 12 вопросов прояснены в секции Clarifications (10 в первой сессии + 2 в ревизии по факту кода)
- [x] Requirements are testable and unambiguous — каждый FR имеет чёткий acceptance-критерий (можно ли вызвать, что вернётся, какой статус). **Pass 2 fix**: устранено противоречие «downgrade → 400» (Edge Case) vs «downgrade → тихо игнорировать» (FR-002 + Assumptions) — оставлен второй вариант, зафиксирован в FR-012
- [x] Success criteria are measurable — SC-001..SC-006 содержат конкретные метрики (время, процент, конкретное поведение)
- [x] Success criteria are technology-agnostic — формулировки про «idStatus в БД», «рендер в tbl_processes», «новость появилась/нет» — не зависят от фреймворка
- [x] All acceptance scenarios are defined — по 3-6 сценариев в каждом US
- [x] Edge cases are identified — 7 edge cases в US Edge Cases
- [x] Scope is clearly bounded — Assumptions явно отмечают out-of-scope (понижение, другие значения, отдельные таблицы аудита)
- [x] Dependencies and assumptions identified — Assumptions содержит 9 пунктов, Clarifications содержит 10 Q&A

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — каждый FR привязан к US и acceptance scenario
- [x] User scenarios cover primary flows — US1 (основной flow), US2 (UI-ограничения), US3 (observability)
- [x] Feature meets measurable outcomes defined in Success Criteria — каждое SC проверяемо через логи, прямой SQL, ручной сценарий
- [x] No implementation details leak into specification — упоминания `SongEditorController.approve`, `ReviewModal.vue`, `approveAssignment action` сделаны как контракты, а не как «как реализовать»

## Notes

- **Validation pass 1/3** — спека прошла все 16 проверок с первого раза
- **Validation pass 2/3 (ревизия по факту кода, 2026-08-13)** — сверка с реальным кодом
  (`SongEditorController.kt:315-487`, `byId` 266-297, `ReviewModal.vue`, `SongEditor/store.js:200`)
  выявила 2 дефекта, оба исправлены:
  1. **Нереализуемое требование**: FR-007/US2 гейтят radio по `a.idStatus < 5`, но ответ
     `POST /api/songeditor/byId` статуса песни НЕ содержит вообще (только `status` ЗАДАНИЯ).
     → Добавлен **FR-011** (аддитивное поле в `/byId`) + fallback в FR-007.
  2. **Противоречие**: Edge Case требовал `400 invalid_idstatus` при попытке понизить 6→5,
     а FR-002 + Assumptions — «тихо оставить более высокий статус». → Оставлен вариант «тихо
     игнорировать» (400 заблокировал бы приёмку работы редактора из-за второстепенного
     параметра), добавлен **FR-012** (ответ возвращает фактический статус).
- **Validation pass 3/3 (Pass 51-3.1, ревизия по фидбэку пользователя, 2026-08-13)** —
  пользователь пожаловался, что при открытии задания с `idStatus=6` radio ВООБЩЕ не виден
  (только информационный бейдж «idStatus: 6 (готова)»). Это дефект UX первой итерации:
  US2 скрывал radio для песен в 5/6 «чтобы админ случайно не downgrade'нул», но это убило
  фичу — админ не мог сделать выбор при апруве уже-готовых песен (например, после
  предыдущего одобрения в 6 или при workflow через авто-пайплайн).
  → **US2 переписан в US2.1**: radio ВСЕГДА виден когда `songIdStatus !== null` (т.е. для
  всех известных статусов 0..6). Безопасность downgrade обеспечивается на бэкенде —
  `idStatus downgrade IGNORED` (см. Edge Cases + contracts). FR-007 обновлён.
  → Бейдж в `.se-meta` теперь ВСЕГДА виден при известном статусе (не гейтится).
  → Блок `v-else class="se-idstatus-readonly"` (read-only для 5/6) УДАЛЁН из template.
  → Задачи T011/T012/T015/T016 обновлены под новую семантику. SC-005 обновлён.
- Дополнительно уточнён scope: `ReviewModal` — **общий компонент для 3 точек входа**
  (`SongEditorTable`, `SongsTable`, `SongEdit`), правок в вызывающих компонентах не требуется.
- Все 14 уточнений прояснены в секции Clarifications, нет [NEEDS CLARIFICATION] маркеров
- Готово к `/speckit.clarify` (если потребуются доп. вопросы) или сразу к `/speckit.plan`
