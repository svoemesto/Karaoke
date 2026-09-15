# Karaoke Project Guidelines

## Project Overview
"Karaoke" (svoemesto) is a self-hosted pipeline for automated karaoke video production.

## Key Documentation Files
This project has two main documentation files that contain detailed technical information:

1. **DEVELOPMENT.md** — Main development guide with:
   - Project structure and modules
   - Build and deployment commands
   - Architecture notes and key invariants
   - Common pitfalls and solutions

2. **docs/architecture-notes-archive.md** — Detailed history of features and bug fixes:
   - Chronological records of implemented features
   - Debugging notes and troubleshooting guides
   - Technical decisions and their rationale

## Working with Documentation
When you need to understand:
- How the project is structured → read DEVELOPMENT.md
- Why a specific feature works the way it does → check docs/architecture-notes-archive.md
- Common issues and their fixes → both files contain relevant information

**Important:** Before making significant changes, consult these files to understand the existing patterns and avoid known pitfalls.

## Tech Stack
- Backend: Kotlin/Spring Boot, Gradle, JDK 17
- Frontend: Vue 3 + Vite (webvue3 for admin, karaoke-public for public site)
- Database: PostgreSQL
- Storage: MinIO
- Video rendering: MLT framework (melt CLI)
- Audio processing: Demucs, ffmpeg, Sheetsage

## Development Workflow
- All build/deploy commands are in `deploy/do.sh`
- Always run commands from the `deploy/` directory
- Check DEVELOPMENT.md for specific command syntax and common issues
- The project uses a dual-database sync system (LOCAL ↔ SERVER)

## Code Style
- Follow existing patterns in the codebase
- Use nullable types for database columns that allow NULL
- Avoid `is*` prefix for boolean fields in DTOs (Jackson serialization issue)
- Always URL-encode query parameters with special characters

---

## 🚦 MUST-CHECKLIST при старте сессии

> **Single source of truth**: см. **AGENTS.md § MUST #0** (Knowledge-first pre-flight).
> Этот файл — рекомендация для Claude Code, **не** дублирующая обязательные шаги.
> Перед любыми правками: `cat AGENTS.md | head -100`.

---

## 🚦 Обязательная проверка перед git commit

**Single source of truth**: см. **AGENTS.md § «Hard Gate: Обязательная проверка после ЛЮБОГО изменения»** (Pass 239+245).

5 шагов: compile → lint → bootJar → vite → docker. Полная команда в AGENTS.md.

---

## 🚦 CI 7/7 PASS — обязательно перед merge

**Single source of truth**: `.github/workflows/lint.yml` + `AGENTS.md` § «Hard Gate: Git — CI-gate для master».

7 проверок: ktlint, ESLint webvue3 + karaoke-public, Docs, Baseline, KDoc coverage, JSDoc coverage. Локальные команды в AGENTS.md.

---

## 🚦 TOP-10 ловушек (из реальных багов)

**Single source of truth**: **`knowledge/guidelines/architecture-conventions.md` § «Ловушки»** + **`AGENTS.md` § «Каталог guards»** (R-04, R-05, R-07, R-08, R-11, R-32, R-43, R-44). Подробности в этих файлах.

---

## 🚦 Стратегия проекта (visitor→registration→premium)

**Single source of truth**: **`docs/strategy/growth.md`** (полный список стратегических решений) + **`docs/strategy/growth-audit.md`** (аудит 37+ гипотез).

Краткий focus: visitor → registration (конверсия 0.4%, потенциал ×5-13). Модель D (гибрид), без trial, сайт-центричная модель.

---

## 🚦 Git workflow

**Single source of truth**: **AGENTS.md § «Hard Gate: Git — CI-gate для master»** (Pass 353).

Ключевые правила:
- Только через feature-ветку + PR + CI.
- НЕ коммитить в master напрямую.
- `git commit --no-verify` — только в крайнем случае.
- Force-push в main/master — нельзя.

---

## 🚦 Tech Stack + Documents (краткая выжимка)

**Tech**: Kotlin 2.x + Spring Boot 3.x + JDK 17 + Gradle; Vue 3 + Vite;
PostgreSQL (raw JDBC); MinIO; Docker + docker-compose.

**Deploy**: `deploy/do.sh` (все команды). ВСЕ `./gradlew` с `GRADLE_USER_HOME`.

**Документы** (по приоритету):
- `AGENTS.md` — правила opencode-стиля (читай **обязательно**).
- `.specify/memory/constitution.md` — NON-NEGOTIABLE принципы.
- `DEVELOPMENT.md` / `CONTRIBUTING.md` — архитектура, стиль.
- `docs/strategy/growth.md` — стратегия роста.
- `docs/features/<slug>.md` — per-feature (обновлять при правке, FR-009).
- `docs/claude-code-setup.md` — детали для Claude Code.
- `docs/architecture-notes.md` — changelog PR.

---

## 🚦 MCP-серверы (если доступны)

- **`codegraph`** — read-only индекс символов. **Использовать ТОЛЬКО ПОСЛЕ Knowledge-first pre-flight** (см. MUST #0 в `AGENTS.md`). (Прецедент: spec #339, 2026-09-09.)

**Все «не делать» и governance-review требования** — в `AGENTS.md` (Hard Gate секции).

**Версия**: 1.2.0 (Pass 379 wayfinder #114 — final compaction)
**Изменения 1.2.0**:
- Удалены дублирующие секции (MUST-CHECKLIST, НЕ делать, governance-review).
- Все правила теперь в `AGENTS.md` v3.0.0 + `constitution.md` v2.4.0 (single source of truth).
- Karaoke-override #1 сохранён (AGENTS.md ≠ CLAUDE.md).

**Связанные документы**: `AGENTS.md` v3.0.0 (governance), `constitution.md` v2.4.0 (принципы), `docs/strategy/growth.md` (стратегия).
