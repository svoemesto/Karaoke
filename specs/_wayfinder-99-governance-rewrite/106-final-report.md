# #106 Final Resolution — docker-image-tags guard (R-04, R-05)

## ✅ Status: done (PR #485 MERGEABLE)

## Implementation Summary

**Branch**: `387-docker-image-tags-guard`
**PR**: https://github.com/svoemesto/Karaoke/pull/485
**Commit**: `287dbb5d` (final, after amend removing Pass 372-375 guards)

## Files

1. `tools/check-docker-image-tags.sh` (194 строк, chmod +x) — guard для R-04
   (`nginx:stable` ≠ `nginx:alpine`) и R-05 (`node:22-alpine` ≠ `node:latest`).

   **Whitelist**: `nginx:stable`, `nginx:stable-alpine`, `nginx:1.27-alpine`,
   `node:22-alpine`, `node:20-alpine`, `node:20-bookworm-slim`.
   **Forbidden**: `nginx:alpine`, `nginx:latest`, `node:alpine`,
   `node:latest`, `node:lts`.

   Backup-файлы (DockerfileOld/Backup/Full/Demucs) исключены через
   `git ls-files | grep -vE 'Dockerfile(Backup|Old|Full|Demucs)'`.

2. `.pre-commit-config.yaml` — добавлен hook `docker-image-tags-guard`
   (Pass 379, R-04, R-05).

3. `.github/workflows/lint.yml` — добавлен job `docker-image-tags-guard`
   со step `bash tools/check-docker-image-tags.sh --quiet`. **Без**
   Pass 372-375 guards.

## Exit codes

- Локальный прогон: **exit 0** (все FROM в whitelist).
- Synthetic negative (`nginx:alpine`, `node:latest`): exit 1 (guard ловит).
- Pre-commit: Passed.

## FROM в tracked Dockerfile'ах (4 строки)

| Файл | FROM | Статус |
|---|---|---|
| `deploy/karaoke-public/Dockerfile:1` | `node:22-alpine` | ✅ OK |
| `deploy/karaoke-public/Dockerfile:16` | `nginx:stable` | ✅ OK |
| `deploy/karaoke-webvue3/Dockerfile:1` | `node:22-alpine` | ✅ OK |
| `deploy/karaoke-webvue3/Dockerfile:16` | `nginx:stable` | ✅ OK |

**Никаких нарушений R-04/R-05 в tracked Dockerfile'ах**.

## CI Status

10/10 SUCCESS (включая **`docker-image-tags guard (Pass 379, R-04, R-05)`**).

## Hard-gate coverage

7 → **8 из 50 правил** (14% → 16%) — R-04, R-05.

## Compliance

- [x] Knowledge-first MUST #0 выполнен.
- [x] R-04, R-05 enforce'ятся machine-readable.
- [x] Hard-gate coverage 7 → 8.
- [x] CI 7/7 зелёная.

— Implementation-субагент для OP #106.
