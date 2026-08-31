---
status: Active
slug: 282-import-folder-mp3-uploads-tuple
related:
  - 082-fix-import-folder-oom.md
  - ../domain/processing.md
  - ../architecture/L3-components.md
  - ../../specs/282-import-folder-mp3-uploads-tuple/spec.md
---

# 282 — Кортеж заданий при «Добавить файлы из папки» (mp3 голоса/аккомпанимента → локальное + удалённое хранилище) (LiveDoc)

> Drill-down — [specs/282-import-folder-mp3-uploads-tuple/spec.md](../../specs/282-import-folder-mp3-uploads-tuple/spec.md). Базовое поведение кортежа — [082-fix-import-folder-oom.md](082-fix-import-folder-oom.md).

## Что делает

Расширяет кортеж заданий, формируемый `Song.createFromPath(...)` при импорте папки через UI «Добавить файлы из папки», шестью явными шагами после `DEMUCS2`:

1. `FF_MP3_ACCOMPANIMENT` — создание mp3-минусовки (ffmpeg из flac-стема Demucs).
2. `FF_MP3_VOCAL` — создание mp3-голоса.
3-4. `UPLOAD_TO_LOCAL_STORE` × 2 (acc + vocals) — загрузка mp3 в локальный MinIO.
5-6. `UPLOAD_TO_REMOTE_STORE` × 2 — загрузка mp3 в удалённый MinIO.

Все 7 шагов кортежа — в одном lane `THREAD_LANE_HEALTH_REPORT` (= 1); создание mp3 с `prior=-1`, загрузка с `prior=-2` (порядок «сначала всё готовим, потом всё загружаем»). Дедупликация через `KaraokeProcess.createProcess` (ключ включает `karaokeFileType` для `UPLOAD_*`).

Раньше `FF_MP3_*` и `UPLOAD_*` для mp3 голоса/аккомпанимента добавлялись через `HealthReport.startRepairAll()` в `ApiController.doCreateFromFolder` (вне явного кортежа), что приводило к ситуации, когда mp3 не попадали в MinIO при сбое/гонке с HealthReport. Теперь кортеж явный, mp3 всегда оказываются в хранилищах после завершения обработки.

## Acceptance Criteria

- [x] **AC1**: 11 записей в `tbl_processes` на песню (1 KEY_BPM + 1 DEMUCS + 2 FF_MP3 + 4 UPLOAD).
- [x] **AC2**: mp3 голоса/аккомпанимента — на диске, в локальном и удалённом MinIO — после завершения кортежа, без ручных запусков.
- [x] **AC3**: Повторный импорт той же папки не создаёт дублей в `tbl_processes` (дедупликация через `KaraokeProcess.createProcess`).
- [x] **AC4**: `HealthReport.startRepairAll` сохраняется как fallback для других типов файлов (`PICTURE_*`) и не дублирует уже идущие задачи кортежа.
- [x] **AC5**: UI не меняется (`git diff -- webvue3/ karaoke-public/` пусто).

## Связанные LiveDocs

- Базовое поведение кортежа: [082-fix-import-folder-oom.md](082-fix-import-folder-oom.md)
- Domain: [processing.md](../domain/processing.md) (async-очередь, лейны)
- Architecture: [L3-components.md](../architecture/L3-components.md) (Queue + Async)

## Код

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` — метод `Song.createFromPath(...)`, формирование кортежа (строки ~8188-8287).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt` — дедупликация `createProcess` (строки 995-1018); `FF_MP3_ACCOMPANIMENT`/`FF_MP3_VOCAL` (1692-1732); `UPLOAD_TO_LOCAL_STORE`/`UPLOAD_TO_REMOTE_STORE` (1797-1833).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` — `startRepairAll` (строка 2259) сохранён без изменений как fallback для других типов файлов.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` — `doCreateFromFolder` (`/utils/createfromfolder`, строка 5390), вызывает `Song.createFromPath` + `HealthReport.startRepairAll`.

## История

- Создан: 2026-08-31 (Pass 282).
- Последнее обновление: 2026-08-31.
