---
status: Active
slug: 305-replace-systemerr-with-logger
related:
  - ../../specs/305-replace-systemerr-with-logger/spec.md
---

# 305 — Замена `System.err.println` на структурное логирование (LiveDoc)

> Drill-down — [specs/305-replace-systemerr-with-logger/spec.md](../../specs/305-replace-systemerr-with-logger/spec.md).

## Что делает

Заменяет единственный вызов `System.err.println` в production-коде
`karaoke-app` на SLF4J `logger.error`. Цель — структурность логов
(уровень/имя/MDC), корректная маршрутизация через logback, и
консистентность с 27 другими файлами, где уже используется SLF4J.

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPlaywright.kt:77`.

## User Stories (краткий список)

- **US1** (P1): оставшийся `System.err.println` заменён на `logger.error`.

## Functional Requirements (указатель)

См. [spec.md](../../specs/305-replace-systemerr-with-logger/spec.md) (FR-1..FR-8).

## Acceptance Criteria

- [ ] AC1: В `UtilsPlaywright.kt` нет `System.err.println` — есть `log.error(...)`.
- [ ] AC2: Compile/lint чисто.

## Связанные LiveDocs

- Domain: [monitoring.md](../domain/monitoring.md) — логирование.
- Architecture: [L3-components.md](../architecture/L3-components.md).
- Документация: [docs/ops/log-correlation.md](../../docs/ops/log-correlation.md).

## Код

- Модуль: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPlaywright.kt`.

## История

- Создан: 2026-09-04 (Pass 305).
- Реализация: коммит `9b778199` — `fix(playwright): replace System.err.println with SLF4J logger.error`.
- Мёрж: PR #414, PR #415 (фиксы документации).
- LiveDoc: создан post-hoc для соответствия 1:1 со спекой (Pass 307 — coverage fix).
