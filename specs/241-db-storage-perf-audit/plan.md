# Implementation Plan: Аудит производительности БД и хранилища (prod) — parent

**Branch**: `246-db-storage-perf-audit` | **Date**: 2026-08-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/241-db-storage-perf-audit/spec.md`

## Summary

Эта спека — **parent (каталог)** для Tier-1 P0 оптимизаций. Не реализует код, а собирает hotspots в Приложении A с file:line и severity. Все 4 P0 пункта реализованы как отдельные дочерние фичи:

* **FR-101** → `242-db-sync-batch-worker` (PR #364)
* **FR-102** → `243-db-table-schema-cache` (PR #365)
* **FR-103** → `244-songs-createkaraokeall-batch` (PR #366)
* **FR-104** → `245-storage-download-streaming` (PR #367)

См. детали в spec.md, раздел A.5 (План оптимизации).

## Technical Context

Спека аналитическая — никаких технических решений не принимается.

## Constitution Check

Спека PASS — Tier-2/Tier-3 hotspots в backlog, никаких нарушений Principle II «Сырой JDBC».

## Definition of Done для этой спеки

- [x] Каталог hotspots в Приложении A (file:line + severity).
- [x] План оптимизации по приоритетам P0/P1/P2.
- [x] Specs + plans + tasks для каждой Tier-1 фичи (242/243/244/245).
- [x] PR-цикл для 4 Tier-1 фич.
- [x] LiveDoc для parent спеки (этот PR).

## Next Steps

После мёрджа всех 4 дочерних фич — эта спека готова. Tier-2/Tier-3 hotspots — отдельные будущие фичи.