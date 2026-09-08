# Implementation Plan: Настраиваемый таймаут между поисковыми запросами (iter #316)

**Branch**: `316-search-timeout-configurable` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/316-search-timeout-configurable/spec.md`

**rev 3** (2026-09-08): UI fix + авто-подбор таймаута. SearchTimeoutDialog.vue **УДАЛЁН** — поле таймаута в существующих `<custom-confirm>` (SongsTable.vue + HomeView.vue). Добавлены FR-009 (мин. интервал между успешными) и FR-010 (лог + SSE-нотификация).

## Summary

Реализация настраиваемого таймаута между поисковыми запросами. Backend (`karaoke-app`) получает timeout через параметр и применяет его в двух точках: (a) `searchsongtextall` (серийный цикл — `Thread.sleep`), (b) `createfromfolder` (параллельный executor — rate-limit перед `submit`). **rev 3**: добавлено измерение `minIntervalMs` (FR-009) + backend-лог + SSE-нотификация (FR-010) для авто-подбора. Frontend (`webvue3`) **не создаёт** новый `SearchTimeoutDialog.vue` (rev 1-2) — вместо этого добавляет поле таймаута в **существующие** `<custom-confirm>` (SongsTable.vue:1289-1314 «Подтвердите поиск текста» + HomeView.vue:255-268 «Добавление файлов из папки»). Принцип «сделай так же, как рядом».

## Technical Context

**Language/Version**: Kotlin 1.9 + JDK 17 (backend), Vue 3 + Vite + JavaScript (frontend)

**Primary Dependencies**: Spring Boot (backend), Vue 3 + Vuex + Bootstrap-vue-next (frontend), PostgreSQL via сырой JDBC (не нужно для этой фичи)

**Storage**: backend, через существующий `KaraokeProperties` (`karaoke-app/.../KaraokeProperties.kt:25`, файл `/sm-karaoke/system/Karaoke.properties` — `PATH_TO_KARAOKE_PROPERTIES_FILE` стр. 18, base64-encoded). Новый ключ `lyricsSearchTimeoutSeconds`. Нет новых таблиц/миграций/web-storage.

**Testing**: `karaoke-app/src/test` — `@Disabled`. Verify через quickstart.md manual scenarios владельцем.

**Target Platform**: Linux server (admin SPA + backend), single admin user (owner)

**Project Type**: Web-application (admin SPA + backend controller)

**Performance Goals**: SC-001..008 (включая авто-подбор: `minIntervalMs` в логе и SSE за < 1 сек после завершения).

**Constraints**:
- Хранение через существующий `KaraokeProperties`. **НЕ** через `setWebvueProp`/`webvue_properties.txt` (rev 1 отклонён владельцем).
- Редактирование через Properties UI/API. Прямые правки файла в обход UI — только с согласия владельца.
- **UI placement (rev 3)**: поле таймаута в существующих `<custom-confirm>`. **Никаких** отдельных модальных окон (принцип «сделай так же, как рядом»).
- 5-step verification канон `brief.md:59-63` после ЛЮБОГО изменения кода (NON-NEGOTIABLE).
- 0 коммитов/push'ей — вся работа в working tree до явного указания владельца (правило 2026-09-05).
- **Governance**: агенты НЕ перезапускают контейнер `karaoke-app`. Smoke-test только владелец.

**Scale/Scope**: маленькая фича. ~7 точек изменения: 1 KaraokeProperties поле, 1 backend endpoint для read/write, 2 точки внедрения паузы (searchsongtextall + createfromfolder), 2 custom-confirm дополнения (SongsTable.vue + HomeView.vue), 1 SSE notification (rev 3), livedoc + INDEX.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principle I (Self-contained автопайплайн)
✅ N/A — фича НЕ добавляет внешних SaaS зависимостей.

### Principle II (Сырой JDBC + дифф по хэшам)
✅ N/A — нет БД-изменений.

### Principle III (Двух-БД синхронизация через SyncRegistry)
✅ N/A — нет БД-изменений.

### Principle IV (Async-очередь задач с парсингом stdout)
✅ COMPLIANT — пауза в backend-цикле НЕ меняет async-логику Worker'а; `lyricsSearchExecutor` rate-limit не блокирует submit-очередь (только добавляет задержку).

### Principle V (Двух-фронтенд)
✅ COMPLIANT — хранение через `KaraokeProperties` (existing pattern; backend, не web-storage).

### Principle VI (Code Standards)
✅ COMPLIANT — KDoc для нового параметра в `Karaoke.kt`, JSDoc на frontend изменения в `<custom-confirm>`; ktlint и ESLint в 5-step.

### Principle VII (Cross-Machine Setup)
✅ COMPLIANT.

### Principle VIII (Секреты и git-гигиена)
✅ COMPLIANT — нет секретов.

**Constitution Check verdict**: ✅ PASS.

## Project Structure

### Documentation (this feature)

```text
specs/316-search-timeout-configurable/
├── spec.md              # Спецификация (rev 3, 9 FR, 8 SC, 10 Assumption)
├── plan.md              # Этот файл
├── research.md          # Phase 0: 10 решений R-001..R-010
├── data-model.md        # Phase 1: MassSearchTimeoutSetting (backend, KaraokeProperties)
├── contracts/           # Phase 1: см. R-004 (изменение существующих endpoints) + R-008 (новый endpoint) + R-010 (SSE)
├── quickstart.md        # Phase 1: 7 validation scenarios (включая импорт папки + Properties UI)
├── checklists/
│   └── requirements.md  # Quality checklist
├── notes/               # (нет — фича не требует boss self-verify через psql, см. spec Assumptions)
├── tasks.md             # Phase 2 (T001..T017)
├── REPORT.md            # Финальный отчёт (после Stage 10)
└── REVIEW.md            # Опционально — отдельный отчёт Кирилла/Марка
```

### Source Code (repository root)

```
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── Karaoke.kt                                # MODIFY: добавить lyricsSearchTimeoutSeconds (getter+setter) — T003
├── KaraokeProperties.kt                       # MODIFY: регистрация lyricsSearchTimeoutSeconds в listKaraokeProperties — T018 (B1 fix)
├── controllers/
│   ├── ApiController.kt                      # MODIFY: 3 точки
│   │   ├── getSearchSongTextAll (kнопка «Песни»): @RequestParam timeout + Thread.sleep + forEachIndexed + сбор minIntervalMs + log + SSE — T004+T012+T018+T019
│   │   ├── doCreateFromFolder (импорт папки): @RequestParam timeout + Thread.sleep ПЕРЕД submit + сбор minIntervalMs + log + SSE — T006+T013+T018+T019
│   │   └── GET/POST /api/lyrics-search-timeout — T007
│   └── MainController.kt                     # MODIFY: симметрично (deprecated)
├── services/
│   └── SseNotificationService.kt             # MODIFY: добавить broadcast-тип 'massSearchSummary' в addressedTypes — T020

webvue3/src/
├── components/Songs/
│   ├── store.js                              # MODIFY: actions getLyricsSearchTimeout/setLyricsSearchTimeout + добавить timeout в searchTextForAll — T009+T010+T011
│   └── SongsTable.vue                        # MODIFY: добавить поле таймаута в customConfirmParams.fields (НЕ создавать SearchTimeoutDialog.vue!) — T012
├── views/
│   └── HomeView.vue                          # MODIFY: добавить поле таймаута в customConfirmParams.fields (НЕ создавать SearchTimeoutDialog.vue!) — T013
└── App.vue                                   # MODIFY (опционально): добавить case SseNotificationType.MASS_SEARCH_SUMMARY в switch SSE — T020

livedocs/
├── INDEX.md                                  # MODIFY: добавить строку-ссылку на новую фичу — T017
└── features/
    └── 316-search-timeout-configurable.md     # NEW: livedoc по конвенции — T017
```

### Tests (опционально)

Нет новых тестов (как в iter #5 — CI тестов нет, manual verify владельцем).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений.

## Re-evaluation after Phase 1 design

**Constitution Check verdict**: ✅ PASS.

## Phase 0 + Phase 1 deliverables

- ✅ research.md (10 решений R-001..R-010).
- ✅ data-model.md (MassSearchTimeoutSetting, backend, KaraokeProperties).
- ✅ plan.md (этот файл, rev 3).
- ✅ quickstart.md (7 scenarios, включая createfromfolder и Properties UI).
- ✅ design.md (Алина rev 3, переделывается под UI fix + авто-подбор).

## Что нового в iter #316 (rev 3)

- **UI fix (rev 3)**: SearchTimeoutDialog.vue **УДАЛЁН**. Поле таймаута в существующих `<custom-confirm>`:
  - SongsTable.vue «Подтвердите поиск текста» — добавляем поле `{ fldName: 'timeout', fldLabel: 'Таймаут (сек)', fldValue: <default> }` в `customConfirmParams.fields` рядом с `engine`.
  - HomeView.vue «Добавление файлов из папки» — аналогично.
  - Callback получает `ret.timeout` (и `ret.engine` в SongsTable).
- **Backend — KaraokeProperties**: новое поле `lyricsSearchTimeoutSeconds` (Int, default 10) в `Karaoke.kt` рядом с `checkSearchAsync`. Read через `KaraokeProperties.getInt("lyricsSearchTimeoutSeconds").takeIf { it >= 1 } ?: 10` в момент каждого запуска поиска (НЕ кэшируется).
- **Backend — searchsongtextall (R-002)**: `ApiController.getSearchSongTextAll` (стр. 4771). Серийный цикл `ids.forEachIndexed { index, id -> ... }`. Пауза `Thread.sleep(timeout * 1000)` после `getLyricsSearch` (кроме последней итерации).
- **Backend — createfromfolder (R-002a)**: `ApiController.doCreateFromFolder` (стр. 5302). `Thread.sleep` ПЕРЕД `lyricsSearchExecutor.submit` (rate-limit на submit-уровне). Пул 4 не менять.
- **Backend — endpoint (R-008)**: `GET/POST /api/lyrics-search-timeout` для UI диалога.
- **Backend — авто-подбор (R-009, FR-009)**: в обоих путях замерять `delta = now - lastSuccessTime` между успешными `getLyricsSearch`, хранить `minIntervalMs = min(minIntervalMs, delta)`. Упавшие запросы НЕ учитываются (нет пары).
- **Backend — нотификация (R-010, FR-010)**: после завершения цикла:
  - Backend-лог: `[lyrics-search-summary] path=A|B count=N successful=M minIntervalMs=X|null totalDurationMs=Y` (через `logger.info(...)` или `println(...)` — выбрать консистентно с Karaoke).
  - SSE: `sseNotificationService.send(SseNotification(SseNotificationType.MASS_SEARCH_SUMMARY, mapOf(...)))`. Тип — **enum** в `model/SseNotificationType.kt` (УЖЕ существует, broadcast по умолчанию — НЕ в `addressedTypes`).
- **Frontend — SSE (R-010)**: `App.vue:281` `EventSourcePolyfill` уже подписан на broadcast. Добавляем ветку `case SseNotificationType.MASS_SEARCH_SUMMARY` в switch (опционально — может быть просто лог).
- **Хранение**: через существующий `KaraokeProperties` (НЕ `setWebvueProp`).

## Следующий шаг

**Stage 5/6 round 3** (Кирилл ревью plan + design + tasks rev 3) → после APPROVE → Stage 8 (Алина implement, batch mode). Stage 9 v3 (Марк ревью кода).

— Илья (boss)
