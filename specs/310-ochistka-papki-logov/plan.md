# Implementation Plan: 310 — Очистка папки логов

**Branch**: `310-ochistka-papki-logov` | **Date**: 2026-09-06 | **Spec**: [specs/310-ochistka-papki-logov/spec.md](./spec.md)

**Input**: Feature specification from `/home/nsa/Karaoke/specs/310-ochistka-papki-logov/spec.md`

## Summary

Реализовать автоматическую очистку лог-файлов KaraokeProcess в `/sm-karaoke/system/logs` по возрасту. При записи нового лога — в фоне удалить регулярные файлы старше 30 дней. Fail-open, защита от clock skew, без внешних зависимостей (cron/scheduled task). Жёсткий порог 30 дней — `const val LOG_RETENTION_DAYS = 30` в `Constants.kt` (конвенция файла).

## Technical Context

**Language/Version**: Kotlin 1.9.x (на nsa-i9 актуально по brief.md 5-step)
**Primary Dependencies**: JDK 17, Karaoke karaoke-app module (Spring Boot), Java NIO (`java.nio.file.Files`, `java.nio.file.Path`)
**Storage**: Файловая система (папка `/sm-karaoke/system/logs` — обычные log-файлы)
**Testing**: JUnit 5 (`org.junit.jupiter.api.Test`), `@TempDir`, `kotlin.test.*` (assertions)
**Target Platform**: Linux server (karaoke-app production deployment на nsa-i9)
**Project Type**: Backend library/module (часть существующего karaoke-app Spring Boot приложения)
**Performance Goals**: SC-4: один цикл cleanup ≤1 секунда на 1000 файлов
**Constraints**: fail-open (FR-4) — ошибки cleanup не блокируют запись нового лога; convention `Constants.kt` — все простые статические значения как `const val`
**Scale/Scope**: SC-1: папка ≤3000 файлов, ≤100 МБ на типичной нагрузке (100 процессов/день × 30 дней)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Применимость | Compliance |
|---|---|---|
| I. Self-contained автопайплайн | ✅ Внутренняя фича karaoke-app, не требует внешних сервисов | PASS |
| II. Сырой JDBC + дифф по хэшам | N/A — фича не работает с БД | N/A |
| III. Двух-БД синхронизация через SyncRegistry | N/A — фича не в sync-домене | N/A |
| IV. Async-очередь задач с парсингом stdout | N/A — фича не async-задача | N/A |
| V. Двух-фронтенд | N/A — backend-only | N/A |
| VI. Code Standards | ✅ Kotlin convention; ktlint проходит | PASS (через 5-step) |
| VII. Cross-Machine Setup | ✅ Не зависит от окружения, чистый файл-код | PASS |
| VIII. Секреты и git-гигиена | ✅ Не добавляет секретов; `git ls-files \| grep -iE '\.env$'` пусто | PASS |

**Brief.md 5-step (обязательная проверка):**
- compileKotlin: ✅ через `./gradlew :karaoke-app:compileKotlin`
- ktlintCheck: ✅ **запустить оба модуля** — `./gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck` (per Кирилл review M1 — для надёжного baseline coverage)
- (eslint не применимо — backend-only)
- bootJar: ✅ **на nsa-i9 — оба модуля** — `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar` (per AGENTS.md «машинно-специфичные исключения Pass 282»)
- (Vite build не применимо — backend-only)
- (Docker build не применимо — изменения в Docker не вносятся)

**Constitution VI FR-006 (Code Standards)**: новые public API (`cleanupOldLogs()`, `cleanupOldLogsIn()`) требуют KDoc с `@see` на spec.md (per Кирилл review M2). Implementation phase добавит по Karaoke-конвенции, tasks.md зафиксирует явно.

## Project Structure

### Documentation (this feature)

```text
specs/310-ochistka-papki-logov/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (skip — internal feature, нет external API)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

Изменения:
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Constants.kt` (+1 const val LOG_RETENTION_DAYS = 30)
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` (+5 imports, +2 функции, +1 вызов после writeText)
- `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/KaraokeProcessCleanupTest.kt` (новый файл — тесты)
- `livedocs/features/310-ochistka-papki-logov.md` (новый livedoc)
- `livedocs/features/README.md` (+1 строка INDEX)

## Phase 0: Research (резюме)

`research.md` создан ниже. Основные решения:

| Решение | Rationale |
|---|---|
| `LOG_RETENTION_DAYS` как `const val = 30` в `Constants.kt` | Конвенция файла (для простых статических значений); урок #309 v1 (не вводить `val`/`env-var`) |
| Cleanup через `Files.list` + `Files.getLastModifiedTime` + `Files.delete` | Java NIO стандарт; fail-open через try/catch на per-file уровне |
| Per-file error isolation | Один залоченный файл не должен мешать удалению остальных |
| Clock skew защита: skip файлов с mtime > now | Защита от рассинхронизации часов / NFS |
| Boundary: strict `>` на KEEP (mtime == threshold удаляется) | Соответствует SC-2 «файл с mtime ровно 30 дней удаляется» |
| Триггер: после успешного `File.writeText` + `chmod 666` | Тот же try-блок, нет внешних зависимостей |
| Только `Files.isRegularFile(p)` | Защита от subdirs, symlinks, sockets (FR-2) |

## Phase 1: Design & Contracts

`data-model.md` создан ниже. Простая модель: одна константа порога + одна функция cleanup.

`/contracts/` пропущен — фича внутренняя, нет external API.

`quickstart.md` создан ниже — manual integration test scenario.

## Generated artifacts

После Phase 0 + Phase 1:
- `research.md` — Phase 0 research consolidation
- `data-model.md` — entities и validation rules
- `quickstart.md` — validation scenarios

После `/speckit.tasks` (фаза 6, следующая):
- `tasks.md` — конкретные шаги для implementer'а

## Done When

- [x] Plan workflow выполнен (Phase 0 + Phase 1)
- [x] Constitution Check пройден (PASS по всем применимым принципам)
- [x] research.md, data-model.md, quickstart.md созданы
- [ ] Готово к `/speckit.tasks` (фаза 6) после APPROVE Кирилла (фаза 5)
