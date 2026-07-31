# Data Model: Повышение порога аудио-похожести и демотация статуса при импорте из папки

**Branch**: `100-audio-similarity-threshold` | **Date**: 2026-07-31 | **Spec**: [spec.md](./spec.md)

> Схема БД **не меняется**. Все колонки, затрагиваемые фичей, уже существуют в `tbl_songs`. 
> Ниже — референс затрагиваемых полей и их семантика после изменения (для контекста задач и ревью).

## Сущность: `Song` (таблица `tbl_songs`)

Затрагиваемые поля (без изменения схемы):

| Поле | Тип | Назначение | Изменение фичей |
|---|---|---|---|
| `id_status` | integer | Жизненный цикл готовности (0–6, см. specs/022-song-status-lifecycle). 0=NONE, 1=TEXT_CREATE, 2=TEXT_CHECK, 3=WORDS_CHECK, 4=MARKERS_CREATE, 5=MARKERS_CHECK, 6=READY. | При импорте из папки с аудио-родителем ≥6 и сходством ≥95%: теперь ставится **5** (было 6). Иные пути статуса 6 не затрагиваются. |
| `audio_parent_id` | bigint | Ссылка на аудио-родителя (плоское дерево, глубина 1). 0 = корень/нет родителя. | Назначается `findAudioParentByWaveform` при `audio_similarity_percent >= AUDIO_PARENT_THRESHOLD` (теперь 95, было 85). |
| `audio_similarity_percent` | integer | % сходства (0–100) с аудио-родителем по `WaveformCompare`. | Значения per-song не пересчитываются (см. research.md R5). Только новые сверки используют порог 95. |
| `audio_delta_ms` | bigint | Сдвиг таймлайна аудио-родителя к текущей песне (мс). | Не меняется. |
| `audio_compare_history` | text (JSON) | Кэш истории сравнений (`AudioCompareHistoryEntry[]`) — не гонять `WaveformCompare` повторно для уже свереных пар. | Не меняется. История со старыми результатами (85–94%) сохраняется; при следующем вызове `findAudioParentByWaveform` `best` отбирается по новому порогу 95, но уже записанные `audio_parent_id` не сбрасываются автоматически. |
| `source_text`, `result_text` | text | Текст песни. | Копируются из аудио-родителя при `applyAudioParentMarkers` (как раньше). |
| `source_markers` | text (JSON) | Разметка маркеров (список голосов → список маркеров). | Копируются из аудио-родителя со сдвигом `shiftMarkersAndFixEnd(..., deltaMs, currentMs)` (как раньше). |

## Константа в коде (не в БД)

| Константа | Файл | Было | Стало |
|---|---|---|---|
| `AUDIO_PARENT_THRESHOLD` | `Utils.kt:4612` | `85` | `95` |

## Дефолты параметров (не в БД)

| Параметр | Файл | Было | Стало |
|---|---|---|---|
| `autoAssignOriginalByWaveform(..., threshold: Int = ...)` | `Utils.kt:4536` | `85` | `95` |
| `@RequestParam(required = false) threshold: Int = ...` (`/songs/autoassignoriginalall`) | `ApiController.kt:4878` | `85` | `95` |

## Literals в коде

| Literal | Файл | Было | Стало |
|---|---|---|---|
| `settings.fields[SongField.ID_STATUS] = "..."` | `Utils.kt:4406` (`applyAudioParentMarkers`) | `"6"` | `"5"` |

## State transitions (затрагиваемые)

Только переход из аудио-пути импорта из папки:

```text
[импорт из папки] 
  → findDuplicateOriginal (если обычный родитель) → id_status = 1
  → findAudioParentByWaveform (≥95% и audioParent.idStatus >= 6)
    → applyAudioParentMarkers: копирование текста/маркеров + сдвиг
    → id_status = 5  (было 6)   ← единственное изменение
  → [если аудио-родитель не найден или < 6] статус остаётся 1 (от обычного родителя) или 0
  → [дальше] Яндекс.Музыка / фоновый SearXNG — без изменений
```

Иные переходы в статус 6 (ручное кураторское продвижение в `SongEditorController`, переход через порог публикации в `Song.kt:5014`, и т.д.) — **не затрагиваются** (FR-008, research.md R3).

## Валидационные правила (из требований)

- `AUDIO_PARENT_THRESHOLD` — включительный порог: кандидат со сходством ровно 95 принимается (`>= 95` → accept; `< 95` → reject). Реализация: `best.similarityPercent < AUDIO_PARENT_THRESHOLD` → reject (строгое `<`), значит `95` accept. FR-002.
- Копирование маркеров аудио-путём — только если `audioParent.idStatus >= 6` (FR-004: иначе не копировать, статус не менять). Реализация: `ApiController.kt:5285` `if (audioParent != null && audioParent.idStatus >= 6)`.
- Кандидат обязан иметь непустые маркеры (FR-007, защита от копирования пустоты). Реализация: `autoAssignOriginalByWaveform` `Utils.kt:4545` фильтрует `c.sourceMarkersList.any { it.isNotEmpty() }`; `findAudioParentByWaveform` проверяет через `best` (история сверки сохраняет `ok`-флаг). Не меняется.
- Демотация 6→5 — ТОЛЬКО в `applyAudioParentMarkers` (FR-008). Единственный caller — `doCreateFromFolder` (`ApiController.kt:5286`).

## Миграции

**Нет.** Схема `tbl_songs` не меняется. `recordhash`-триггеры не затрагиваются (меняются значения полей, не структура — Principle III конституции). Уже назначенные `audio_parent_id`/`audio_similarity_percent` не пересчитываются (research.md R5).