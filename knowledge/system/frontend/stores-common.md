# Vuex stores: Common/* (4 stores)

> **Домен**: system (frontend)
> **Компонента**: 4 common Vuex stores для shared UI components.

## Файлы (4 stores)

| Store | Файл | Строк | Что |
|---|---|---|---|
| `Common` | `webvue3/.../Common/store.js` | ? | Общий state (tab selection, modals, и т.д.) |
| `Common/FileExplorer` | `webvue3/.../Common/FileExplorer/store.js` | 72 | Файловый менеджер (UI) |
| `Common/HealthReport` | `webvue3/.../Common/HealthReport/store.js` | 74 | HealthReport UI (см. [health-report.md](../../domains/health/components/health-report.md)) |
| `Common/Monitor` | `webvue3/.../Common/Monitor/store.js` | 64 | Мониторинг UI (см. [monitor-checks.md](../../domains/monitoring/components/monitor-checks.md)) |
| `Common/SmartCopy` | `webvue3/.../Common/SmartCopy/store.js` | 65 | Smart Copy UI |

## Назначение

**Общие UI-компоненты** для админ-SPA. Не привязаны к конкретной
entity — переиспользуются в разных views.

## Hot paths

- **HealthReport UI**: см. [health-report.md](../../domains/health/components/health-report.md) —
  HR-очередь, прогресс.
- **Monitor UI**: см. [monitor-checks.md](../../domains/monitoring/components/monitor-checks.md) —
  7 monitor checks.
- **FileExplorer**: навигация по MinIO файлам.
- **SmartCopy**: копирование между buckets.

## Связь

- [health-report.md](../../domains/health/components/health-report.md) — HealthReport logic.
- [monitor-checks.md](../../domains/monitoring/components/monitor-checks.md) — Monitor checks.
- [storage domain](../../domains/storage/domain.md) — FileExplorer для MinIO.

## Changelog

- **Pass 400** (2026-09-09): Initial. Автор: agent (Karaoke).