# Implementation Plan: Синхронизация аудио-потомков (#141)

**Branch**: `413-sync-audio-descendants` | **Date**: 2026-09-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/413-sync-audio-descendants/spec.md`
+ [wayfinder-decisions.md](wayfinder-decisions.md) (28 решений гриля).

## Summary

При сохранении песни-родителя (статус ≥5) с изменённым контентом или при переходе
статуса `<5 → ≥5` — асинхронно синхронизировать её прямых аудио-потомков
(`audio_parent_id = parent.id`, `id_status < 6`): повторно аудіо-сверить пару
(порог 95 %), при успехе перенести текст/маркеры (со сдвигом)/форматирование,
обновить аудио-метрики и историю, выставить статус 5, перегенерировать `.srt`.
Плюс admin-кнопка массового прохода. Точка перехвата — `Song.saveToDb()` с гейтом
по diff; механика — очередь с дедупликацией + single-flight воркер
(`object SyncAudioDescendants`).

## Technical Context

**Language/Version**: Kotlin 1.x, JDK 17, Spring Boot (karaoke-app).
**Primary Dependencies**: сырой JDBC (`KaraokeConnection`), kotlinx.serialization,
ffmpeg (через `WaveformCompare`), SLF4J? — в karaoke-app преимущественно `println`
(см. conventions).
**Storage**: PostgreSQL (`tbl_songs`, существующие колонки; схема не меняется).
**Testing**: Gradle unit/integration (`karaoke-app/src/test`), большинство
интеграционных `@Disabled`; основные проверки — compile + ktlint + manual quickstart.
**Target Platform**: admin-машина (karaoke-app в Docker).
**Project Type**: Web application (backend karaoke-app + admin frontend webvue3).
**Performance Goals**: не блокировать сохранение родителя; сверка пары — тяжёлая
(ffmpeg), одна за раз; admin-проход последовательный.
**Constraints**: общий single-flight; без push на прод; только LOCAL-БД.
**Scale/Scope**: ~18k песен; количество пар потомок↔родитель — подмножество.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Как соблюдается |
|---|---|
| **I. Self-contained автопайплайн** | PASS — ffmpeg локально через `WaveformCompare`; новых SaaS нет. |
| **II. Сырой JDBC + diff по хэшам** | PASS — только `KaraokeConnection` + существующий `getDiff`/`recordDiffName`; JPA нет (`check-no-jpa-imports.sh`). |
| **III. Двух-БД sync через SyncRegistry** | PASS — схема не меняется, новых syncable-сущностей нет. `audio_*` поля уже в sync-diff. |
| **IV. Async-очередь + redirectErrorStream** | Частично: фон реализуется потоком+очередью (прецедент `customFunction`), не `KaraokeProcess` — обосновано в research R5. `WaveformCompare.computeEnvelope` уже использует `redirectErrorStream(true)` — не трогаем. |
| **V. Два фронтенда** | PASS — изменение только в `webvue3` (admin). `karaoke-public` не трогаем. |
| **VI. Code Standards (KDoc/ktlint/per-feature doc)** | Требует: KDoc на новом объекте/функциях + `@see docs/features/sync-audio-descendants.md`; per-feature документ в том же PR; ktlint. |
| **VII. Cross-Machine Setup** | PASS — локальные конфиги не трогаем. |
| **VIII. Секреты и git-гигиена** | PASS — секретов нет; pre-commit check не затрагивается. |
| **IX. Knowledge-first** | PASS — pre-flight в spec.md; требуется SSoT-обновление (research R11). |

**Итог**: гейт пройден. Нарушений, требующих Complexity Tracking, нет.
`KaraokeProcess`-отступление (Principle IV) — это **предпочтение**, не
NON-NEGOTIABLE-нарушение: очередь задач реализуется in-memory по прямому решению
владельца (Q4) и прецеденту `customFunction`/`findAudioParentForAuthor`.

## Project Structure

### Documentation (this feature)

```text
specs/413-sync-audio-descendants/
├── spec.md
├── wayfinder-decisions.md
├── plan.md          # this file
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── http-endpoint-syncaudioparents.md
│   └── internal-mechanism.md
└── tasks.md         # /speckit.tasks
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── SyncAudioDescendants.kt            # NEW — object: гейт, очередь, воркер, syncAll
├── model/Song.kt                      # EDIT — вызов хука в saveToDb()/saveToDbLocked()
└── controllers/ApiController.kt       # EDIT — POST /utils/syncaudioparents

webvue3/src/
├── components/Songs/store.js          # EDIT — syncAudioParentsPromise()
└── views/HomeView.vue                 # EDIT — кнопка + модалка + method

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/
└── SyncAudioDescendantsTest.kt        # NEW — гейт + очередь (unit)

knowledge/domains/catalog/
├── domain.md                          # EDIT — ссылка на новый компонент
└── components/
    ├── audio-descendant-sync.md       # NEW — компонент синхронизации
    ├── song-lifecycle.md              # EDIT — статус 5 как цель
    ├── song-entity.md                 # EDIT — хук saveToDb
    └── dictionaries.md                # EDIT — заметка про target статус (ssot-map)
docs/features/sync-audio-descendants.md  # NEW — per-feature (FR-009)
```

**Structure Decision**: Web application. Backend-логика — новый object в корне
пакета `com.svoemesto.karaokeapp` (рядом с `Utils.kt`, где живут
`findAudioParentByWaveform`/`applyAudioParentMarkers`). Фронт — admin (`webvue3`).

## Design Decisions (ключевые, из research)

1. **Точка перехвата** — `Song.saveToDb()` UPDATE-ветка; в `saveToDbLocked()` —
   после `commit()`, pre-commit вызов подавлен (R1).
2. **Гейт** — `idStatus >= 5` родителя; content-diff ИЛИ переход `<5→≥5`; непустые
   маркеры для `songType == SONG`; `inSync`-подавление (R1/R2/R5).
3. **Очередь** — `LinkedHashSet<Long>` с дедупликацией, один воркер, single-flight
   для auto+admin (R5).
4. **Сверка** — `WaveformCompare.compareWaveforms(child, parent)`, порог
   `AUDIO_PARENT_THRESHOLD` (R3).
5. **Перенос** — helper `syncAudioDescendantFromParent` (набор
   `applyAudioParentMarkers` + метрики/history + `.srt`), один `saveToDbLocked()`
   (R4).
6. **Admin** — `SELECT DISTINCT audio_parent_id …`, дедупликация по родителю,
   SSE-сводка (R6/R8).

## Phase 1 re-check после дизайна

Принципы остаются PASS: контракты не вводят JPA/sync-изменений/секретов; новые
артефакты knowledge спланированы (R11). Единственное отступление (не
`KaraokeProcess`) обосновано выше и решением владельца.

## Complexity Tracking

> Нарушений Constitution Check, требующих justified-violation, нет.

## Следующие фазы

- `/speckit.tasks` — разбить на задачи (backend object + хук + эндпоинт;
  frontend; тесты; knowledge/docs).
- `/speckit.implement` — реализация на этой же ветке; PR с CI 7/7.
