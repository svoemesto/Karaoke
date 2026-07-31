# Implementation Plan: Повышение порога аудио-похожести и демотация статуса при импорте из папки

**Branch**: `100-audio-similarity-threshold` | **Date**: 2026-07-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/100-audio-similarity-threshold/spec.md`

## Summary

Два точечных изменения в бэкенде `karaoke-app` (Kotlin):

1. **Порог** `AUDIO_PARENT_THRESHOLD`: 85 → 95 (включительно, `>=`). Единая константа в `Utils.kt`, транзитивно покрывает три пути: импорт из папки (`doCreateFromFolder` → `findAudioParentByWaveform`), пакетная `customFunction`, ручной `/song/findaudioparent`. Отдельно, дефолт параметра `threshold` функции `autoAssignOriginalByWaveform` / эндпоинта `/songs/autoassignoriginalall` поднимается 85 → 95 (параметр остаётся параметризованным — куратор MAY передать `?threshold=<N>`).
2. **Демотация статуса** в `applyAudioParentMarkers` (`Utils.kt:4406`): `"6"` → `"5"`. Применяется ТОЛЬКО к пути импорта из папки (`doCreateFromFolder`). Иные пути статуса 6 не затрагиваются. Копирование текста/маркеров и логика сдвига `shiftMarkersAndFixEnd` сохраняются без изменений.

Сопутствующая работа: обновление KDoc-комментариев, упоминающих «85%» и «статус 6» в связи с аудио-родителем (`Utils.kt:4384-4394`, `Utils.kt:4520-4530`, `Utils.kt:4611`, `Utils.kt:4635`, `ApiController.kt:752-754`, `ApiController.kt:5266-5269`), и человекочитаемых `reason`-строк в `findAudioParentByWaveform`/`autoAssignOriginalByWaveform`, где порог интерполируется из константы/параметра (уже ссылается на значение, не хардкод — проверяется при ревью).

## Technical Context

**Language/Version**: Kotlin (JDK 17, Spring Boot 2.x/3.x, Gradle multi-module). См. constitution.md «Технологический стек».

**Primary Dependencies**: Spring Boot, сырой JDBC (`KaraokeConnection`), `WaveformCompare` (кросс-корреляция огибающих вокала через ffmpeg-декод), `SNS`/`SseNotificationService` (SSE-тосты), `Song`/`Settings` (доменная модель, `KaraokeDbTable`).

**Storage**: PostgreSQL через сырой JDBC. Колонки `tbl_songs`: `audio_parent_id`, `audio_similarity_percent`, `audio_delta_ms`, `audio_compare_history`, `id_status`, `source_markers`, `source_text`, `result_text`. **Миграции БД не требуется** — значения порога и статуса хранятся в коде, не в схеме; уже назначенные аудио-родители (`audio_similarity_percent` per-song) не пересчитываются автоматически (см. Assumptions в spec.md).

**Testing**: CI-тестов нет (конституция: «Тесты: в CI нет»). Проверка — ручная, через `quickstart.md` (импорт папки с известной песней-кандидатом, сверка статуса и `audio_parent_id` в БД). ktlint + KDoc-coverage в CI — MUST пройти (FR-006/FR-007 конституции).

**Target Platform**: admin-машина (LOCAL Postgres, `karaoke-app`). На проде `karaoke-app` не разворачивается (конституция); изменение влияет только на admin-пайплайн импорта + на `karaoke-web`-эндпоинты `/song/findaudioparent` и `/songs/autoassignoriginalall`, которые проксируют те же функции.

**Project Type**: web-service (Spring Boot backend), multi-module Gradle. Изменение локализовано в `karaoke-app`.

**Performance Goals**: N/A — изменение порога не влияет на асимптотику (WaveformCompare уже O(n) по кандидатам с кэшем `audio_compare_history`). Повышение порога лишь уменьшает число копирований, не добавляя работы.

**Constraints**: 
- НЕ ломать обратную совместимость: возврат к 85/6 — двумя правками (Assumptions spec.md).
- НЕ пересчитывать уже назначенные аудио-родители при повышении порога (out-of-scope, см. Assumptions).
- `redirectErrorStream(true)` для любых новых `ProcessBuilder` (конституция Principle IV) — в данной фиче новых подпроцессов нет, `WaveformCompare` уже корректен.

**Scale/Scope**: 3 правки констант/значений + ~6 KDoc/`reason`-строк. Один файл `Utils.kt` + один `ApiController.kt`. Фронта не касается (UI статуса/аудио-родителя уже отображает любое значение 0–6 и любой percent — см. specs/022-song-status-lifecycle).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Статус | Примечание |
|---|---|---|
| I. Self-contained автопайплайн | ✅ PASS | Внешних SaaS не добавляется. `WaveformCompare` — локальный ffmpeg. |
| II. Сырой JDBC + diff по хэшам | ✅ PASS | Новых таблиц/колонок нет. Запись статуса — через `settings.fields[SongField.ID_STATUS]` + `saveToDb()` (штатный `KaraokeDbTable.save()`), не через прямой SQL. |
| III. SyncRegistry | ✅ PASS | Схема `tbl_songs` не меняется; `recordhash`-триггер не затронут (меняются только значения полей, не структура). `tbl_songs` уже в `SyncRegistry`. |
| IV. Async-очередь | ✅ PASS | Новых подпроцессов нет. Импорт из папки уже синхронный по постобработке (`doCreateFromFolder`); тяжёлая сверка `WaveformCompare` уже существует. |
| V. Двух-фронтенд | ✅ PASS | Фронт не трогается. UI статуса уже поддерживает 0–6 (specs/022). |
| VI. Code Standards (FR-006/007/009) | ⚠️ ACTION | MUST обновить KDoc с `@see` там, где они упоминают «85%»/«статус 6» аудио-родителя (FR-006). ktlint MUST пройти (FR-007). FR-009 (per-feature документ): аудио-родитель **не входит** в 13 ключевых подсистем `docs/features/README.md` — обновление per-feature документа не требуется; обновляется только KDoc в коде. |
| VII. Cross-Machine Setup | ✅ PASS | Не затронуто. |

**Gates**: нарушений нет. ACTION по Principle VI — обновление KDoc включено в план как задача (Phase 1 contracts + tasks.md).

## Project Structure

### Documentation (this feature)

```text
specs/100-audio-similarity-threshold/
├── spec.md              # /speckit.specify (готов)
├── plan.md              # этот файл
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/           # Phase 1
│   └── behavior-contract.md
└── tasks.md             # Phase 2 (/speckit.tasks — не создаётся здесь)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── Utils.kt                     # AUDIO_PARENT_THRESHOLD (85→95), applyAudioParentMarkers (6→5),
│                                # autoAssignOriginalByWaveform default threshold (85→95),
│                                # KDoc обновления
└── controllers/ApiController.kt # /songs/autoassignoriginalall default threshold (85→95),
                                 # KDoc/комментарии про "порог 85%" и "idStatus >= 6"
```

**Structure Decision**: Single-project layout, изменение локализовано в модуле `karaoke-app`. Фронт (`webvue3`, `karaoke-public`), `karaoke-web`, `deploy/` — не трогаются. Per-feature документ `docs/features/` не создаётся (аудио-родитель — не ключевая подсистема; FR-009 не применим).

## Complexity Tracking

> Не заполняется — нарушений Constitution Check нет, все принципы PASS/ACTION (ACTION по Principle VI учтено в задачах).