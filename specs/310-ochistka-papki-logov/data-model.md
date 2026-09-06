# Data Model: 310 — Очистка папки логов

**Branch**: `310-ochistka-papki-logov` | **Date**: 2026-09-06
**Spec**: [specs/310-ochistka-papki-logov/spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)
**Research**: [research.md](./research.md)

## Entities

### E1. Retention Constant (compile-time)

| Field | Type | Value | Source |
|---|---|---|---|
| `LOG_RETENTION_DAYS` | `const val Int` | `30` | `Constants.kt` |

**Validation**:
- Must be positive (≥ 1). Compile-time enforced by Kotlin type system (Int literal).
- Must be `const val` (per AGENTS.md v2.2.0 convention guard).

### E2. Cleanup Function (runtime, internal)

| Field | Type | Source |
|---|---|---|
| Function name | `cleanupOldLogs()` (public, companion) | `KaraokeProcessWorker.kt` |
| Function name | `cleanupOldLogsIn(dir: Path, retentionDays: Int)` (internal, companion) | `KaraokeProcessWorker.kt` |
| Trigger | Called after successful `File.writeText` + `chmod 666` in `KaraokeProcessWorker.run()` | `KaraokeProcessWorker.kt:287` |

**Validation (logic invariants)**:
- `cleanupOldLogs()` is fail-open (FR-4): inner try/catch on per-file IOException + outer try/catch on Exception.
- `cleanupOldLogsIn()` testable directly via `@TempDir` + explicit `retentionDays` parameter.
- Returns nothing (`Unit`); logs to stdout for observability.

### E3. File Filter (per-file decision)

| Condition | Action | Rationale |
|---|---|---|
| `Files.isRegularFile(p) == false` | Skip | FR-2 — subdirs, symlinks, sockets игнорируются |
| `mtime > Instant.now()` | Skip | FR-3 — clock skew защита |
| `mtime <= now - retentionDays * 24h` AND `mtime <= now` | Delete | FR-1 + FR-3 |
| Otherwise (file ≤ retention) | Skip | Keep fresh files (US-2) |

### E4. Boundary Semantics

| Case | mtime vs threshold | Result |
|---|---|---|
| File age = 30 days exactly | `mtime == threshold` | **Delete** (strict `>` on KEEP) |
| File age < 30 days | `mtime > threshold` | Keep |
| File age > 30 days | `mtime < threshold` AND `mtime <= now` | Delete |
| File with future mtime | `mtime > now` | Keep (skip) |

## Data flow (during cleanup)

```
1. Read `LOG_RETENTION_DAYS` (compile-time const) → 30
2. Compute `threshold = Instant.now() - 30 * 24h`
3. `Files.list(PATH_TO_LOGS)` → Stream<Path>
4. For each `p` in stream:
   a. if !`Files.isRegularFile(p)` → skip
   b. read `mtime = Files.getLastModifiedTime(p).toInstant()`
   c. if `mtime.isAfter(now)` → skip (clock skew)
   d. if !`mtime.isAfter(threshold)` → `Files.delete(p)`, increment counter
   e. catch `IOException` → `println("cleanup: failed to process <name>: <msg>")`, continue
5. After stream exhausted: if counter > 0 → `println("cleanup: removed N files older than N days from <dir>")`
6. Outer catch `Exception` → `println("cleanup: unexpected error: <msg>")`
```

## State Transitions

Нет — фича stateless. Каждый вызов cleanup независим.

## Relationships

- `KaraokeProcessWorker.run()` → (при успешной записи) → `KaraokeProcessWorker.cleanupOldLogs()` → `cleanupOldLogsIn(PATH_TO_LOGS, LOG_RETENTION_DAYS)` → удаление файлов в `Constants.PATH_TO_LOGS`.

## Testing model

Tests (FR-6) используют:
- `@TempDir Path` для изоляции filesystem
- `Files.writeString(p, "x")` для создания файла
- `Files.setLastModifiedTime(p, FileTime.from(mtime))` для контроля возраста
- `KaraokeProcessWorker.cleanupOldLogsIn(tempDir, retentionDays = 30)` (явный retention для теста)
- Assertions: `assertTrue(Files.exists(p))` / `assertFalse(Files.exists(p))`

Покрытие:
- SC-2: файл с mtime = now − 25 дней остаётся; файл с mtime = now − 31 день удаляется.
- FR-3: файл с mtime = now + 1h не удаляется.
- FR-4: per-file IOException → этот файл пропускается, остальные удаляются.

## References

- Spec: [specs/310-ochistka-papki-logov/spec.md](./spec.md) — FR-1..FR-6, SC-1..SC-5
- Plan: [plan.md](./plan.md)
- Research: [research.md](./research.md) — обоснование design choices
- Constants.kt convention: AGENTS.md v2.2.0 + Кирилл review #310 v1 (M1, softened Dependencies wording)
