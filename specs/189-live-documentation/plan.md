# Implementation Plan: Live Documentation (LiveDocs)

**Branch**: `189-live-documentation` | **Date**: 2026-08-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/189-live-documentation/spec.md`

## Summary

Создать в репозитории проекта Karaoke систему **LiveDocs** — единый актуальный
каталог знаний о проекте для AI-агентов и разработчиков, объединяющий три подхода
(SDD / DDD / C4) в общем каркасе `livedocs/`. Цель: сократить стартовый контекст
AI-агента с ~40K токенов до ≤ 5K (SC-001), устранить дублирование между
`AGENTS.md` / `constitution.md` / per-feature документами и дать разработчику
быстрый (≤ 30 сек) поиск описания любого модуля.

**Технический подход**: чистый Markdown + YAML frontmatter + Mermaid-диаграммы,
без runtime-зависимостей. Версионируется через git, валидируется через
`tools/check-livedocs-structure.sh` в GitHub Actions CI.

## Technical Context

**Язык/формат**: Markdown (CommonMark) + YAML frontmatter + Mermaid (диаграммы).
Версии: Markdown не имеет версии (CommonMark), Mermaid ≥ 9.x поддерживается
GitHub из коробки. **Никакого нового runtime/toolchain не вводится.**

**Основные зависимости**:
- Git (уже есть) — версионирование.
- GitHub Actions (уже есть) — CI для `check-livedocs-structure.sh`.
- Bash (POSIX) — скрипт валидации.
- Никаких npm/Gradle-плагинов для генерации документации (FR-018).

**Хранение**: git-репозиторий (файлы `.md` в `livedocs/`). Никаких внешних БД,
никакого отдельного хранилища — документация живёт рядом с кодом, как `README.md`
и `docs/`.

**Тестирование**: bash-скрипт `tools/check-livedocs-structure.sh` (проверяет
наличие всех обязательных файлов/директорий) + ручная валидация (чтение
сгенерированных документов человеком и AI-агентом).

**Целевая платформа**: Linux/macOS/Windows — Markdown кроссплатформенный, Mermaid
рендерится GitHub автоматически, скрипт валидации — POSIX bash.

**Тип проекта**: **internal-documentation-system** (не библиотека, не сервис).
Файлы `.md` живут в репозитории, читаются IDE/Markdown preview / GitHub.

**Performance Goals**:
- SC-001: AI-агент при старте сессии читает ≤ 5K токенов на онбординг
  (livedocs/README + livedocs/INDEX + AGENTS.md + constitution).
- SC-010: время поиска описания модуля ≤ 30 сек (открыть INDEX → найти файл).

**Ограничения**:
- Без новых зависимостей (FR-018) — никаких MkDocs, Docusaurus, Hugo, Antora.
- Минимальный YAML frontmatter (только то, что нужно для CI валидации).
- Язык — русский (соответствует `AGENTS.md`, раздел «АБСОЛЮТНОЕ ПРАВИЛО»).
- Не ломать существующие `docs/features/*.md` (FR-017) — только интегрировать.

**Scale/Scope** (для первого merge):
- 1 директория `livedocs/` в корне.
- ~25-35 файлов в `livedocs/`:
  - 2 манифеста (README, INDEX).
  - 3-5 шаблонов (templates/).
  - 5 сводок фич (livedocs/features/).
  - 5 bounded contexts (livedocs/domain/).
  - 3-5 C4-документов (livedocs/architecture/L1, L2, L3, +1-2 topic).
  - 1 скрипт валидации (tools/check-livedocs-structure.sh).
- 1 изменение в `.github/workflows/lint.yml`.
- 1-3 правки в `AGENTS.md` (сокращение ~230 → ≤ 100 строк).
- 1 новый документ `docs/livedocs-conventions.md` (FR-012).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---------|--------|-------------|
| **I. Self-contained автопайплайн** | ✅ N/A | LiveDocs — документация, не runtime. Не вводит внешних API. |
| **II. Сырой JDBC + дифф по хэшам** | ✅ N/A | Нет работы с БД. |
| **III. Двух-БД синхронизация через SyncRegistry** | ✅ N/A | Нет сущностей для sync. |
| **IV. Async-очередь задач** | ✅ N/A | Нет длительных процессов. |
| **V. Двух-фронтенд** | ✅ N/A | Нет frontend-кода. |
| **VI. Code Standards** (FR-006, FR-007, FR-009) | ✅ Compliant | LiveDocs **усиливают** FR-009 (per-feature документация) — дают единый каталог для drill-down. Не нарушают FR-006/007. |
| **VII. Cross-Machine Setup** (VII.1-VII.4) | ✅ Compliant + усиливает | **VII.4** прямо требует «cross-machine документация» — LiveDocs как раз и есть этот слой. VII.1/VII.2/VII.3 — не затрагиваются. |
| **VIII. Секреты и git-гигиена** | ✅ N/A | В LiveDocs нет секрет-файлов (только `.md`). |

**Verdict**: ни один NON-NEGOTIABLE принцип не нарушен. **Plan проходит Constitution Check.**

## Project Structure

### Documentation (this feature)

```text
specs/189-live-documentation/
├── plan.md              # Этот файл
├── research.md          # Phase 0 output — design decisions + обоснования
├── data-model.md        # Phase 1 output — описание сущностей LiveDoc
├── quickstart.md        # Phase 1 output — сценарии проверки
├── contracts/           # Phase 1 output — frontmatter schema + шаблоны
│   ├── feature-summary-template.md
│   ├── bounded-context-template.md
│   ├── c4-level-template.md
│   └── frontmatter-schema.md
├── checklists/
│   └── requirements.md  # Уже создан /speckit.specify
└── spec.md              # Уже создан /speckit.specify + обновлён /speckit.clarify
```

### Source Code (repository root)

```text
Karaoke/
├── livedocs/                        # NEW: каталог LiveDocs
│   ├── README.md                    # Манифест + навигация (≤ 1 страница)
│   ├── INDEX.md                     # Карта слоёв + decision tree
│   ├── templates/                   # Шаблоны для новых записей
│   │   ├── feature-summary.md
│   │   ├── bounded-context.md
│   │   ├── c4-level-L1.md
│   │   ├── c4-level-L2.md
│   │   ├── c4-level-L3.md
│   │   └── README.md                # Index шаблонов
│   ├── features/                    # SDD: сводки фич
│   │   ├── README.md                # Index + конвенции
│   │   ├── 182-editor-self-assign-tasks.md
│   │   ├── 184-approve-status-choice.md
│   │   ├── 185-song-dto-audit-sponsr-remove.md
│   │   ├── 186-zakroma-songs-fast-load.md
│   │   └── 187-site-traffic-anomaly-investigation.md
│   ├── domain/                      # DDD: bounded contexts
│   │   ├── README.md                # Index + ubiquitous language
│   │   ├── catalog.md               # Песня, Альбом, Исполнитель, Жанр
│   │   ├── processing.md            # Караоке-видео, MLT, Demucs
│   │   ├── publishing.md            # Эфир, Подписка, Premium
│   │   ├── identity.md              # Пользователь, Авторизация
│   │   └── editorial.md             # Редакторы, Задания, Пайплайн
│   └── architecture/                # C4: архитектурные диаграммы
│       ├── README.md                # Index + навигация по уровням
│       ├── L1-system-context.md     # Karaoke ↔ внешние системы
│       ├── L2-containers.md         # karaoke-app, karaoke-web, etc.
│       ├── L3-components.md         # Внутри karaoke-app
│       ├── data-sync.md             # LOCAL ↔ SERVER (детальная)
│       └── queue-lanes.md           # ThreadId lanes + priority (детальная)
├── tools/
│   └── check-livedocs-structure.sh  # NEW: CI-валидация (FR-011, FR-015)
├── .github/workflows/
│   └── lint.yml                     # UPDATE: добавить шаг check-livedocs
├── AGENTS.md                        # UPDATE: сократить до ≤ 100 строк (SC-002)
├── .specify/memory/
│   └── constitution.md              # Без изменений (≤ 150 строк уже)
└── docs/
    └── livedocs-conventions.md      # NEW: правила именования и процесса (FR-012)
```

**Structure Decision**: одна новая директория `livedocs/` в корне, плюс один
новый bash-скрипт `tools/check-livedocs-structure.sh`, плюс 2 изменения в
существующих файлах (`.github/workflows/lint.yml`, `AGENTS.md`), плюс один
новый документ `docs/livedocs-conventions.md`. Это сохраняет простоту
существующей структуры проекта (Spring Boot multi-module + два Vue SPA) и не
вводит дополнительных Gradle/npm-проектов.

## Complexity Tracking

> **Не заполнено** — Constitution Check не выявил нарушений. Все принципы
> либо N/A, либо compliant, либо усиливаются (VII.4). Simpler alternatives
> не рассматривались — текущий подход минимален (Markdown + git + bash).