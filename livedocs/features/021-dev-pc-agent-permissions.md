---
status: Active
slug: 021-dev-pc-agent-permissions
related:
  - ../features/189-governance.md
  - ../../specs/021-dev-pc-agent-permissions/spec.md
---

# 021 — Unrestricted agent permissions on dev-pc (LiveDoc)

> Drill-down — [specs/021-dev-pc-agent-permissions/spec.md](../../specs/021-dev-pc-agent-permissions/spec.md).

## Что делает

AI-агент, работающий на машине **`dev-pc`** под OS-пользователем **`dev`**,
получает **полные** разрешения на этой машине без дополнительных вопросов:
- Пересборка/перезапуск **любого** локального контейнера (включая `karaoke-app`,
  который для других машин зарезервирован за пользователем).
- Прямые запросы к локальной БД (DDL/DML).

Для **всех остальных** машин/пользователей действует стандартное ограничение
(см. Constitution § «Ограничения и доступы агента»).

Это правило зафиксировано в:
- `.specify/memory/constitution.md` (Раздел «Ограничения и доступы агента», п. 1, 6, 7).
- `AGENTS.md` (Q&A «Ограничения агента»).
- README `tools/agent-permissions.sh` (скрипт проверки).

## User Stories (краткий список)

- **US1** (P1): Rebuild/restart любого локального контейнера без подтверждения.
- **US2** (P1): Прямые запросы к локальной БД без подтверждения.

## Functional Requirements (указатель)

- **FR-001**: `tools/agent-permissions.sh` — скрипт-проверка `hostname == dev-pc && whoami == dev`.
- **FR-002**: Документация в `constitution.md` (NON-NEGOTIABLE, см. п. 1 исключение).

## Acceptance Criteria

- [ ] **AC1**: На `dev-pc/dev` — `tools/agent-permissions.sh` возвращает `0` (полные права).
- [ ] **AC2**: На другой машине — `1` + сообщение «нужно спросить пользователя».
- [ ] **AC3**: Constitution зафиксировано (см. p. 6 «Разрешено»).

## Связанные LiveDocs

- Это **governance-фича** (правила для агента). Не привязана к одному Domain.
- Дополнительно: см. главу «Ограничения агента» в `AGENTS.md`.

## Код

- Script: `tools/agent-permissions.sh` (новый)
- Doc: `.specify/memory/constitution.md` (п. 1, 6, 7)
- Doc: `AGENTS.md` (секция «Ограничения агента»)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14