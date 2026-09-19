# Data Model: 413 — Синхронизация аудио-потомков

**Дата**: 2026-09-19
**Спека**: [spec.md](spec.md)
**Research**: [research.md](research.md)

> **Изменений схемы БД нет.** Механизм использует существующие колонки
> (миграция 25 `audio_parent`, миграция 26 `player_readiness_flags`). Новых
> таблиц/полей не вводится.

## Entity: Song — используемые поля (без изменений схемы)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`
**Таблица**: `tbl_songs`

| Поле Kotlin | Колонка | Роль в фиче |
|---|---|---|
| `id` | `id` | identity |
| `audioParentId` | `audio_parent_id` | связь потомок → родитель (основа выборки) |
| `idStatus` | `id_status` | ворота родителя (≥5), целевой статус потомка (5) |
| `songType` | `song_type` | правило обязательных маркеров для `song` |
| `sourceText` | `source_text` | переносится (JSON по голосам) |
| `resultText` | `result_text` | переносится |
| `sourceMarkers` | `source_markers` | переносится со сдвигом |
| `formattedTextSong` | `formatted_text_song` | переносится |
| `formattedTextTabs` | `formatted_text_tabs` | переносится |
| `formattedTextChords` | `formatted_text_chords` | переносится |
| `audioSimilarityPercent` | `audio_similarity_percent` | обновляется свежей сверкой |
| `audioDeltaMs` | `audio_delta_ms` | обновляется свежей сверкой |
| `audioCompareHistory` | `audio_compare_history` | дополняется записью пары |
| `ms` | `song_ms` | длительность потомка (для END-маркера) |
| `rootFolder` / `fileName` | `root_folder` / `file_name` | путь для `.srt` |

## Value Objects

- **SourceMarkers** (`sourceMarkers`): JSON `List<List<SourceMarker>>` по голосам.
  Сдвиг — `shiftMarkersAndFixEnd(parent.sourceMarkers, deltaMs, child.ms)`.
- **AudioCompareHistoryEntry**: `{id, similarityPercent, deltaMs, ok, comparedAt}`.
- **SongType**: `song` / `instrumental` / `poetry`.

## State transitions (целевые, без изменения кодов)

```
parent.idStatus < 5   ──► sync NOT triggered (FR-002)
parent.idStatus: <5 ─► ≥5, ИЛИ content-diff при parent.idStatus ≥5  ──► enqueue
child.idStatus < 6    ──► (после успешной сверки ≥95%) ──► child.idStatus = 5
child.idStatus ≥ 6    ──► skipped (FR-006)
```

## In-memory модель (не персистится)

**`object SyncAudioDescendants`**:

| Член | Тип | Назначение |
|---|---|---|
| `inSync` | `ThreadLocal<Boolean>` | подавление каскада (FR-020) |
| `pendingParents` | synchronized `LinkedHashSet<Long>` | очередь с дедупликацией (FR-019) |
| `workerRunning` | `@Volatile Boolean` | single-flight воркера (FR-021) |
| `adminPending` | `@Volatile Boolean` | нужна ли SSE-сводка текущего прохода (FR-026) |
| `stats` | счётчики processed/synced/skipped | SSE-сводка admin-функции |

## Результат на песню (для лога/SSE)

```
SyncAudioResult(parentId, processed, synced, skipped, reason)
```

- `synced` — перенос применён (сверка ≥95%, процесс не активен);
- `skipped` — причина: `below_threshold:<N>%`, `no_audio`, `active_process`,
  `not_found`, `empty_markers`, `error:<msg>`.
