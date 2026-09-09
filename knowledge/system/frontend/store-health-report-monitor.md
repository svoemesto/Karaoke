# Vuex stores: HealthReport UI + Monitor UI (Common)

> **Домен**: system (frontend)
> **Компонента**: `Common/HealthReport/store.js` + `Common/Monitor/store.js`.

## Файлы

| Store | Файл | Строк | Назначение |
|---|---|---|---|
| `HealthReport` | `webvue3/.../Common/HealthReport/store.js` | 74 | HealthReport UI (см. [health-report.md](../../domains/health/components/health-report.md)) |
| `Monitor` | `webvue3/.../Common/Monitor/store.js` | 64 | Monitor UI (см. [monitor-checks-detailed.md](../../domains/monitoring/components/monitor-checks-detailed.md)) |

## HealthReport store

```javascript
state: {
    healthReportList: [],           // список HealthReportDTO
    healthReportListIsLoading: false,
}
```

**API** (из KDoc):
- `getHealthReportList(state)` — getter.
- `updateHealthReportList(state, result)` — mutation.
- `setHealthReportListIsLoading(state, isLoading)` — mutation.

**Hot path**: `loadHealthReport(songId)` — fetches `/api/health/list` для
одной песни. **HR-очередь** (Pass 341 P0, `HR_MAX_CONCURRENT=3`).

## Monitor store

```javascript
const SEVERITY_RANK = { INFO: 0, WARNING: 1, ERROR: 2, CRITICAL: 3 }

state: {
    monitorAlerts: [],
}
```

**API**:
- `monitorVisibleAlerts` — getter, **прочитанные** (`read=true`) не
  показываются в модалке.
- `monitorTopSeverity` — getter, max severity среди непрочитанных
  (для цвета светофора).

**Hot path**:
- `/api/monitor/alerts` — periodic polling.
- Состояние синхронизируется с [monitor-checks-detailed.md](../../domains/monitoring/components/monitor-checks-detailed.md)
  (7 чеков).

**`SEVERITY_RANK`** — **должен быть синхронизирован** с
backend `MonitorSeverity` (см. [monitor-core.md](../../domains/monitoring/components/monitor-core.md)):
- INFO=0, WARNING=1, ERROR=2, CRITICAL=3.

## Связь

- [health-report.md](../../domains/health/components/health-report.md) — HealthReport backend.
- [monitor-checks-detailed.md](../../domains/monitoring/components/monitor-checks-detailed.md) — Monitor backend.
- [stores-common.md](stores-common.md) — обзор Common/* stores.

## Changelog

- **Pass 478-480** (2026-09-09): Initial. Автор: agent (Karaoke).