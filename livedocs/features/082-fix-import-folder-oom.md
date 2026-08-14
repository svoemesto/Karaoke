---
status: Active
slug: 082-fix-import-folder-oom
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../domain/processing.md
  - ../architecture/L3-components.md
  - ../../specs/082-fix-import-folder-oom/spec.md
---

# 082 — Устойчивый импорт из папки без OOM (LiveDoc)

> Drill-down — [specs/082-fix-import-folder-oom/spec.md](../../specs/082-fix-import-folder-oom/spec.md).

## Что делает

При нажатии «Добавить файлы из папки» если в папке (и подпапках) много файлов
— вылетал `Java Heap Out of Space`. Импорт падал по памяти.

**Корневая причина**: перебор файлов рекурсивно + попытка держать всё в
памяти (List<FileInfo>).

**Фикс**:
- **Streaming**: обрабатывать файлы по одному (или батчами N=50), не накапливая
  в памяти.
- **Кортеж задач**: правильное формирование — для каждой новой песни:
  1. Demucs (стем-сепарация).
  2. Создание MP3 (acc + vocals).
  3. Загрузка MP3 в локальное + удалённое хранилище.
- Все три — в **одном lane** (`STEM_JOBS`).

## User Stories (краткий список)

- **US1** (P1): Импорт 1000+ файлов не приводит к OOM.
- **US2** (P2): Кортеж задач формируется правильно (демус → mp3 → upload).

## Functional Requirements (указатель)

- **FR-001**: Streaming подход (не accumulator).
- **FR-002**: Батч = N=50 файлов в памяти одновременно.
- **FR-003**: Кортеж (demucs + mp3 + upload) — единая транзакция задач.
- **FR-004**: Прогресс-бар (через SSE).

## Acceptance Criteria

- [ ] **AC1**: 1000+ файлов → импорт успешен (без OOM).
- [ ] **AC2**: Memory usage — стабильный (не растёт с числом файлов).
- [ ] **AC3**: Кортеж задач — 3 задания в одном lane.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md), [editorial.md](../domain/editorial.md), [processing.md](../domain/processing.md)
- Architecture: [L3-components.md](../architecture/L3-components.md) (Queue + Async)

## Код

- Backend: `karaoke-web/.../controllers/ImportController.kt` — streaming import
- Backend: `karaoke-app/.../service/ImportService.kt` — batched processing
- Backend: `karaoke-app/.../service/ImportJobFactory.kt` — кортеж задач

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14