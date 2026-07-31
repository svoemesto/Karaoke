# Behavior Contract: Повышение порога аудио-похожести и демотация статуса при импорте из папки

**Branch**: `100-audio-similarity-threshold` | **Date**: 2026-07-31 | **Spec**: [spec.md](../spec.md)

> Проект — Spring Boot web-service. Внешних публичных API-контрактов, меняющих формат, **нет**: 
> эндпоинты сохраняют сигнатуры, DTO — поля, БД-схема — колонки. 
> Ниже — контракты **поведения** (postconditions) для каждого из затрагиваемых путей, 
> выраженные через существующие эндпоинты/функции. Это то, что проверяется в `quickstart.md`.

## Контракт 1: Импорт песен из папки — `POST /api/utils/createfromfolder`

**Сигнатура**: не меняется (`@RequestParam folder: String`).

**Постусловия после изменения** (для каждой добавленной песни `Y`):

| Условие | Постусловие | Было | Стало |
|---|---|---|---|
| В коллекции есть `X` с `id_status >= 6` и `WaveformCompare(Y, X).similarityPercent >= 95` и непустыми маркерами `X` | `Y.audio_parent_id` = resolved root `X`; `Y.source_text`/`result_text`/`source_markers` скопированы из `X` (со сдвигом `deltaMs`); `Y.id_status = 5` | `id_status = 6` | `id_status = 5` |
| То же, но `similarityPercent ∈ [85, 95)` | Аудио-родитель НЕ назначается; копирование аудио-путём не выполняется; `Y.id_status` не повышается аудио-путём | назначался (85 — порог) | НЕ назначается (95 — порог) |
| `X` найден, но `X.id_status < 6` | Маркеры НЕ копируются; `Y.id_status` не меняется аудио-путём (сохраняется прежнее правило) | то же | то же (без изменений) |
| Нет кандидата ≥ 95% | Аудио-путь пропускается; работает обычный путь `findDuplicateOriginal` (статус 1) + поиск текста (Яндекс.Музыка, SearXNG) — без изменений | то же | то же (без изменений) |
| У `Y` есть обычный родитель (по названию) → статус 1, И затем найден аудио-родитель ≥6 ≥95% | Аудио-путь перекрывает: текст/маркеры из аудио-родителя, `id_status = 5` (перетирает 1 от обычного) | `id_status = 6` | `id_status = 5` |

**SSE-сообщение завершения**: формат не меняется (`"Добавлено файлов из папки «<folder>»: N (пропущено: M)"`).

## Контракт 2: Поиск аудио-родителя (ручной) — `POST /api/song/findaudioparent`

**Сигнатура**: не меняется (`@RequestParam id: Long`).

**Постусловия**:

| Условие | `FindAudioParentResultDto` | Было | Стало |
|---|---|---|---|
| Лучший кандидат `best.similarityPercent >= 95` и resolved root ≠ `id` | `matched = true`, `audioParentId` = resolved root, `audioSimilarityPercent` = best%, `reason` содержит «(NN%, сдвиг ...)» | порог 85 | порог 95 |
| `best.similarityPercent ∈ [85, 95)` | `matched = false`, `reason` = `"Лучшее совпадение NN% (id=...) ниже порога 95%"` | «…ниже порога 85%» | «…ниже порога 95%» (интерполяция из `AUDIO_PARENT_THRESHOLD`) |
| Нет кандидатов / нет аудио | `matched = false`, `reason` без упоминания порога | то же | то же (без изменений) |

**Побочный эффект**: `audio_parent_id`/`audio_similarity_percent`/`audio_delta_ms`/`audio_compare_history` записываются в `tbl_songs` через `saveToDb()` — как раньше. Статус песни эта функция **не меняет** (и не меняла) — без изменений.

## Контракт 3: Пакетная «Поиск родителей и аудио-родителей» — `customFunction` (кнопка «Custom Function»)

**Сигнатура**: `customFunction(storageService, lyricsFinderService, storageApiClient)` — не меняется.

**Постусловия**:
- Фаза 1 (родители по названию) — без изменений.
- Фаза 2 (аудио-родители по `findAudioParentByWaveform`) — порог отбора кандидатов 95 (было 85). `customFunction` статусы 6/5 не выставляет (она только ищет и записывает `audio_parent_id`), поэтому **демотация статуса к ней не применяется**.
- SSE-итог `"Обработано N, родитель назначен P … аудио-родитель назначен A из P с родителем"` — формат не меняется; число `A` уменьшится (меньше кандидатов проходят порог 95).

## Контракт 4: Пакетная автопривязка оригинала — `POST /api/songs/autoassignoriginalall`

**Сигнатура**: `@RequestParam(required = false) author: String?`, `@RequestParam(required = false) threshold: Int = 95` (было 85). Параметр `threshold` остаётся параметризованным — куратор MAY передать `?threshold=<N>`.

**Постусловия**:

| Вызов | Порог | Постусловие |
|---|---|---|
| `/songs/autoassignoriginalall` (без `?threshold=`) | **95** (новый дефолт) | Для песен `id_status = 1 AND root_id <> 0`: `autoAssignOriginalByWaveform(..., threshold = 95)`. Кандидат со сходством ≥95 → `applyFamilySongSelection` + `id_status = 2` (TEXT_CHECK). < 95 → пропуск. |
| `/songs/autoassignoriginalall?threshold=80` | 80 (explicit override) | То же с порогом 80 — куратор осознанно снизил. |

**Демотация статуса 6→5 к этому контракту НЕ применяется** (механизм переводит в статус 2, не использует `applyAudioParentMarkers`). Это explicitly оговорено в Clarification (вариант C) и FR-001a.

## Контракт 5: KDoc / человекочитаемые строки (не runtime, но checked by CI)

| Где | Было | Стало |
|---|---|---|
| KDoc `applyAudioParentMarkers` (`Utils.kt:4384-4394`) | «статус выставляется в 6 (READY)» | «в 5 (маркеры проверены)» |
| KDoc `autoAssignOriginalByWaveform` (`Utils.kt:4520-4530`) | «Порог по умолчанию — 85 %» | «95 %» |
| KDoc `AUDIO_PARENT_THRESHOLD` (`Utils.kt:4611`) | упоминание 85 | 95 |
| KDoc `findAudioParentByWaveform` (`Utils.kt:4635`) | «(85%)» | «(95%)» |
| Комментарий `findAudioParent` (`ApiController.kt:752-754`) | «порог 85%» | «95%» |
| Комментарий `doCreateFromFolder` (`ApiController.kt:5266-5269`) | «переводим в статус 6» | «в статус 5» |
| `reason`-строки `findAudioParentByWaveform` / `autoAssignOriginalByWaveform` | интерполируют `AUDIO_PARENT_THRESHOLD` / `threshold` — автоматически «95%» после правки констант/дефолтов (хардкода «85» в строках нет) | автоматически «95%» |

**CI-gate**: ktlint + KDoc-coverage MUST пройти (FR-006/FR-007 конституции). Устаревший «85%»/«статус 6» в KDoc = fail ревью.

## Контракт 6: Фронт (`webvue3`, `karaoke-public`) — без изменений

- `SongEdit.vue`: выпадающий список статусов уже содержит 0–6 с названиями (specs/022-song-status-lifecycle). Статус 5 отображается корректно, UI менять не нужно.
- Отображение `audio_similarity_percent` / `audio_parent_id` — числовое/ссылочное, формат не зависит от порога.
- Кнопки запуска `customFunction` / `/songs/autoassignoriginalall` — без изменений; новый дефолт 95 подхватывается бэком автоматически.