# Implementation Plan: 357 — Folder Import Overwrite (Audit #73)

**Branch**: `357-folder-import-overwrite` | **Date**: 2026-09-10 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/357-folder-import-overwrite/spec.md`

## Summary

Провести **полный аудит** всех 49 мест `Song.saveToDb()` в `karaoke-app/src/main/kotlin/` (НЕ `saveToDbLocked`). Классифицировать каждое место по 3 категориям (A/B/C) и перевести **30 мест категории B1** (долгие процессы, где объект `song` живёт в памяти > 100 мс и есть окно для гонки с ручной правкой через `SongEdit.vue`) на `saveToDbLocked()` — это атомарная защита через `SELECT ... FOR NO KEY UPDATE` + UPDATE в одной транзакции, уже реализованная в спеке 299. Добавить WARN-лог `song.locked_save_diff_overlap` для диагностики регрессий. Все 30 мест — в одном PR.

## Technical Context

**Language/Version**: Kotlin 2.x, JDK 17, Spring Boot 3.x.

**Primary Dependencies**:
- PostgreSQL JDBC (raw, без JPA/Hibernate — Constitution §II).
- Существующий `Song.saveToDbLocked()` (спека 299, PR #395 смержен 2026-09-04).

**Storage**: PostgreSQL 15+ (SELECT FOR NO KEY UPDATE поддерживается с 9.3+).

**Testing**: ручные тесты из `contracts/manual-test-checklist.md` (наследует 5 шагов спеки 299 + 5 новых из спеки 357). Unit-тесты НЕ добавляются (Q2 — `saveToDbLocked` уже покрыт тестами спеки 299).

**Target Platform**: Linux server, deploy через Docker (`karaoke-app` контейнер на `nsa-i9`).

**Project Type**: backend library / web-service (Karaoke pipeline).

**Performance Goals**: P95 latency `saveToDbLocked()` ≤ `saveToDb() + 50 мс` (SC-006). Overhead на `SELECT FOR NO KEY UPDATE` + транзакцию — единицы миллисекунд.

**Constraints**:
- Сырой JDBC без JPA/Hibernate (Constitution §II).
- KDoc coverage ≥ 50% (CI gate).
- ktlintCheck без warnings.
- Не пересобирать `karaoke-app` без явного согласия (AGENTS.md § «Машинно-специфичные исключения»).

**Scale/Scope**: ~30 мест кода перевести в одном PR. Diff ожидается ~150-300 строк (включая KDoc-комментарии). Файлы: `Song.kt`, `Utils.kt`, `UtilsAI.kt` (комментарии), `controllers/ApiController.kt`, `controllers/MainController.kt`, `services/VkAutoPublishService.kt`, `services/PremiumAutoPublishScheduler.kt`, `services/SongReleaseAnnouncementService.kt`, `services/TelegramUpdatesConsumer.kt`, `HealthReport.kt`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Compliance | Notes |
|---|---|---|
| **I. Self-contained автопайплайн** | ✅ Pass | Никаких внешних API, всё в `karaoke-app`. |
| **II. Сырой JDBC + дифф по хэшам** | ✅ Pass | `saveToDbLocked()` использует `Connection.setAutoCommit(false)` + `SELECT ... FOR NO KEY UPDATE` — сырой JDBC, без JPA. Diff по хэшам через `getDiff()`. |
| **III. Двух-БД синхронизация через SyncRegistry** | ✅ Pass | `saveToDbLocked()` корректно работает с SyncRegistry (вызывает `recordChange` так же, как `saveToDb()`). |
| **IV. Async-очередь задач с парсингом stdout** | ✅ Pass | Не трогаем KaraokeProcess / KaraokeProcessWorker. |
| **V. Двух-фронтенд: админка и публичный сайт — разные приложения** | ✅ Pass | `SongEdit.vue` — admin-only фича. |
| **VI. Code Standards** | ✅ Pass | KDoc coverage ≥ 50%, KDoc на каждое место B1 со ссылкой на спеку 357. ktlintCheck без warnings. |
| **VII. Cross-Machine Setup** | ✅ Pass | Не трогаем machine-specific setup. |
| **VIII. Секреты и git-гигиена** | ✅ Pass | Никаких секретов в diff. |
| **IX. Knowledge-first при разработке фич** | ✅ Pass | Knowledge-first выполнен ДО codegraph_explore: прочитаны `knowledge/domains/health/components/race-fixed-65.md`, `knowledge/domains/catalog/components/song-entity.md`, ADR `local-0002-save-exception-handling.md` и `local-0005-structured-logging-karaoke-app.md`, документы `docs/features/process-bulk-actions.md` и `idempotent-path-sanitize.md`, спека 299. |

**GATE: ✅ PASS** — все принципы соблюдены, никаких исключений не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/357-folder-import-overwrite/
├── spec.md                       # Спецификация (FR-001..FR-180, 4 US, 7 SC)
├── plan.md                       # Этот файл
├── research.md                   # Аудит 49 мест, классификация A/B/C
├── data-model.md                 # Контракт Song.saveToDbLocked() + новый WARN-лог
├── quickstart.md                 # 6 сценариев валидации
├── contracts/
│   ├── manual-test-checklist.md  # Чек-лист sign-off (наследует спеку 299)
│   └── log-format.md             # Формат WARN-лога song.locked_save_diff_overlap
└── checklists/
    └── requirements.md           # Quality checklist (12/16 passing)
```

### Source Code Changes

**Файлы для модификации** (10 файлов в `karaoke-app/src/main/kotlin/`):

| Файл | Кол-во B1 мест | Тип изменения |
|---|---|---|
| `model/Song.kt` | 5 | 5× `saveToDb()` → `saveToDbLocked()` + KDoc |
| `model/Song.kt` (saveToDbLocked) | 1 | Добавить WARN-логирование diff_overlap |
| `Utils.kt` | 11 | 11× `saveToDb()` → `saveToDbLocked()` + KDoc |
| `controllers/ApiController.kt` | 5 | 5× замен + KDoc |
| `controllers/MainController.kt` | 2 | 2× замен + KDoc |
| `services/VkAutoPublishService.kt` | 2 | 2× замен + KDoc |
| `services/PremiumAutoPublishScheduler.kt` | 1 | 1× замен + KDoc |
| `services/SongReleaseAnnouncementService.kt` | 1 | 1× замен + KDoc |
| `services/TelegramUpdatesConsumer.kt` | 1 | 1× замен + KDoc |
| `HealthReport.kt` | 1 | 1× замен + KDoc |
| **Итого** | **30 замен** | + WARN-лог + ~30 KDoc |

### Knowledge Updates

- [`knowledge/domains/catalog/components/song-entity.md`](../../knowledge/domains/catalog/components/song-entity.md) — раздел «Методы» дополнить описанием `saveToDbLocked()` как рекомендуемого пути для долгих процессов (race protection).
- [`docs/architecture-notes.md`](../../docs/architecture-notes.md) — добавить запись «Pass 357: спека 357 — Folder Import Overwrite (Audit #73)».

### New Files

- `specs/357-folder-import-overwrite/report.md` — REQUIRED артефакт governance (см. AGENTS.md § Issue-tracker OpenProject). Создаётся автоматически через `tools/tracker-implement-done.sh` или вручную после merge.

## Complexity Tracking

> Нет нарушений Constitution — все принципы соблюдены, никаких trade-offs не требуется.

## Related Decisions

- **Спека 299** (`specs/299-song-fields-overwrite-race-condition`) — базовая спека, вводит `saveToDbLocked()` / `loadFromDbByIdForUpdate`. Спека 357 наследует FR-001..FR-060 и расширяет их FR-100..FR-180 для аудита оставшихся мест `saveToDb()`.
- **Pass 281** (`specs/281-find-lyrics-overwrites-key-bpm`) — предыдущая итерация защиты (reload-from-db-before-save). Спека 357 её НЕ отменяет, она дополняет защиту до уровня `saveToDbLocked()`.
- **Q1 (Resolved 2026-09-10)**: Один большой PR со всеми ~30 местами B1 (см. Clarifications).

## Open Architectural Questions

Нет. Все архитектурные вопросы решены в спеке 299 и подтверждены в спеке 357 (Clarifications Q1).

## References

- [research.md](research.md) — полный аудит 49 мест, классификация A/B/C, обоснование.
- [data-model.md](data-model.md) — контракт `Song.saveToDbLocked()` + новый WARN-лог.
- [contracts/log-format.md](contracts/log-format.md) — формат `song.locked_save_diff_overlap`.
- [contracts/manual-test-checklist.md](contracts/manual-test-checklist.md) — чек-лист sign-off.
- [quickstart.md](quickstart.md) — 6 сценариев валидации.
- [`specs/299-song-fields-overwrite-race-condition/spec.md`](../../specs/299-song-fields-overwrite-race-condition/spec.md) — базовая спека.
- [`knowledge/domains/health/components/race-fixed-65.md`](../../knowledge/domains/health/components/race-fixed-65.md) — прецедент Pass 343.
- [`docs/ops/log-correlation.md`](../../docs/ops/log-correlation.md) — контракт WARN/INFO для `infra.prod.*`.
