# Specification Quality Checklist: 282 — Кортеж заданий при «Добавить файлы из папки» (mp3 голоса/аккомпанимента → локальное + удалённое хранилище)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-31
**Feature**: [specs/282-import-folder-mp3-uploads-tuple/spec.md](../spec.md)

## Content Quality

- [x] **CQ-001** No implementation details (languages, frameworks, APIs) — спека описывает **что** и **зачем**, не указывая конкретные языки/фреймворки; упоминания `Kotlin`-модулей и `KaraokeProcessTypes` — это привязка к существующей доменной модели (тоже приемлемо, как и в спеках 082/238/278), а не введение новых технологий.
- [x] **CQ-002** Focused on user value and business needs — спека описывает ценность для оператора (mp3 в хранилище без ручных шагов) и для публичного плеера (готовность голоса/аккомпанимента).
- [x] **CQ-003** Written for non-technical stakeholders — сценарии приёмки и критерии успеха сформулированы в терминах результата для пользователя/системы, без погружения в API/ORM.
- [x] **CQ-004** All mandatory sections completed — присутствуют: User Scenarios & Testing, Requirements (с FR и Key Entities), Success Criteria, Assumptions; Edge Cases встроены в User Scenarios.

## Requirement Completeness

- [x] **RC-001** No `[NEEDS CLARIFICATION]` markers remain — в спеке нет таких маркеров; все спорные места закрыты явными допущениями (A-001..A-010).
- [x] **RC-002** Requirements are testable and unambiguous — FR-001 задаёт точный порядок из 6 вызовов `KaraokeProcess.createProcess` с конкретными аргументами, FR-002..FR-010 — проверяемые инварианты поведения.
- [x] **RC-003** Success criteria are measurable — SC-001/004 считают количество записей в `tbl_processes`, SC-002 проверяет наличие файлов в MinIO, SC-006 — отсутствие изменений во фронтенде.
- [x] **RC-004** Success criteria are technology-agnostic — SC формулируются через «что должно быть в БД/хранилище/UI», а не через внутренние детали реализации (хотя имена таблиц и MinIO упомянуты как часть домена — это приемлемо).
- [x] **RC-005** All acceptance scenarios are defined — для US1 дано 4 сценария приёмки, для US2 — 4 сценария приёмки; граничные случаи покрыты 8 кейсами.
- [x] **RC-006** Edge cases are identified — 8 граничных случаев в отдельной секции (ошибка demucs, пустая папка, повторный импорт, параллельный каскад, отключённый remote, race на storageFileName, чужие UPLOAD_*-задачи, отсутствующая директория модели).
- [x] **RC-007** Scope is clearly bounded — фича ограничена `Song.createFromPath()` (внутренняя функция импорта), не затрагивает фронтенд и другие эндпоинты создания песен (последние покрыты сохранённым `HealthReport.startRepairAll`).
- [x] **RC-008** Dependencies and assumptions identified — в разделе Assumptions перечислены 10 допущений (включая связь с LiveDoc 082, дедупликацию через `KaraokeProcess.createProcess`, сохранение `KEY_BPM_FROM_FILE` в отдельном лейне и т.д.).

## Feature Readiness

- [x] **FR-Ready-001** All functional requirements have clear acceptance criteria — каждый FR явно ссылается на существующую инфраструктуру (`KaraokeProcess.createProcess`, `Song.accompanimentNameMp3`, `Song.storageBucketName`), поведение которой уже валидировано на проде (18k+ записей).
- [x] **FR-Ready-002** User scenarios cover primary flows — US1 покрывает основной happy-path (импорт → кортеж → файлы в MinIO); US2 — конкурентный/повторный сценарий; Edge Cases — деградации.
- [x] **FR-Ready-003** Feature meets measurable outcomes defined in Success Criteria — все 7 SC проверяются через прямые SQL/HTTP-проверки после применения фикса, без необходимости в сложной инструментовке.
- [x] **FR-Ready-004** No implementation details leak into specification — упоминания конкретных имён (`FF_MP3_ACCOMPANIMENT`, `UPLOAD_TO_LOCAL_STORE`, `KaraokeProcess.THREAD_LANE_HEALTH_REPORT`) — это **доменные идентификаторы** существующей системы задач, без них FR-001/FR-002 невыразимы. Не путать с «introducing new tech».

## Notes

- Items are all marked complete (no blockers).
- Единственный открытый момент для обсуждения с пользователем — интерпретация A-002: если пользователь имел в виду, что `FF_MP3_*` уже в кортеже (и хочет добавить только `UPLOAD_*`), достаточно убрать шаги 1–2 из FR-001. Это не блокирует переход к `/speckit.plan`, но должно быть подтверждено на этапе clarify или сразу в плане.
- Спека 082 (`livedocs/features/082-fix-import-folder-oom.md`) описывает желаемое поведение кортежа «demucs → mp3 → upload» как ожидаемое; эта фича приводит реальный код в соответствие с этим LiveDoc. После реализации рекомендуется обновить LiveDoc 082 (в том же PR, согласно FR-014 Constitution Principle и AGENTS.md «Обновление LiveDocs»).
- Готова к переходу в `/speckit.clarify` (опционально) или сразу в `/speckit.plan`.
