---
status: Active
slug: 082-fix-import-folder-oom
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../domain/processing.md
  - ../architecture/L3-components.md
  - ../../specs/082-fix-import-folder-oom/spec.md
  - ../../specs/282-import-folder-mp3-uploads-tuple/spec.md
---

# 082 — Устойчивый импорт из папки без OOM (LiveDoc)

> Drill-down — [specs/082-fix-import-folder-oom/spec.md](../../specs/082-fix-import-folder-oom/spec.md), расширение кортежа — [specs/282-import-folder-mp3-uploads-tuple/spec.md](../../specs/282-import-folder-mp3-uploads-tuple/spec.md).

## Что делает

При нажатии «Добавить файлы из папки» если в папке (и подпапках) много файлов
— вылетал `Java Heap Out of Space`. Импорт падал по памяти.

**Корневая причина**: перебор файлов рекурсивно + попытка держать всё в
памяти (List<FileInfo>).

**Фикс**:
- **Streaming**: обрабатывать файлы по одному (или батчами N=50), не накапливая
  в памяти.
- **Кортеж задач**: правильное формирование — для каждой новой песни:
  1. Demucs (стем-сепарация) — `DEMUCS2`.
  2. Создание MP3 — `FF_MP3_ACCOMPANIMENT` + `FF_MP3_VOCAL`.
  3. Загрузка MP3 в локальное хранилище — `UPLOAD_TO_LOCAL_STORE` × 2 (acc + vocals).
  4. Загрузка MP3 в удалённое хранилище — `UPLOAD_TO_REMOTE_STORE` × 2 (acc + vocals).
- Все 7 шагов кортежа — в **одном lane** (`THREAD_LANE_HEALTH_REPORT` = 1). Создание
  MP3 (шаг 2) выполняется с `prior = -1`, загрузка (шаги 3-4) — с `prior = 0`
  (см. Pass 285: в `KaraokeProcessWorker` сортировка `ORDER BY process_priority ASC` —
  меньше `priority` = раньше; поэтому `UPLOAD_*` получают `prior = 0 > -1` для
  запуска **после** создания mp3). Порядок «сначала всё готовим, потом всё
  загружаем». Расширение кортежа (шаги 2-4) — спека
  [282-import-folder-mp3-uploads-tuple](../../specs/282-import-folder-mp3-uploads-tuple/spec.md).

## User Stories (краткий список)

- **US1** (P1): Импорт 1000+ файлов не приводит к OOM.
- **US2** (P2): Кортеж задач формируется правильно (demucs → mp3 → upload).

## Functional Requirements (указатель)

- **FR-001**: Streaming подход (не accumulator).
- **FR-002**: Батч = N=50 файлов в памяти одновременно.
- **FR-003**: Кортеж (demucs + 2 × FF_MP3 + 4 × UPLOAD) — единая транзакция задач
  в одном lane (threadId = 1, приоритеты `-1` для создания, `-2` для загрузки).
  Расширение — спека 282.
- **FR-004**: Прогресс-бар (через SSE).

## Acceptance Criteria

- [ ] **AC1**: 1000+ файлов → импорт успешен (без OOM).
- [ ] **AC2**: Memory usage — стабильный (не растёт с числом файлов).
- [ ] **AC3**: Кортеж задач — 7 заданий в одном lane (1 × DEMUCS2 + 2 × FF_MP3 + 4 × UPLOAD).
- [ ] **AC4** (из спеки 282): mp3 голоса/аккомпанимента попадают в локальный и удалённый MinIO без ручных запусков.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md), [editorial.md](../domain/editorial.md), [processing.md](../domain/processing.md)
- Architecture: [L3-components.md](../architecture/L3-components.md) (Queue + Async)
- Расширения: [282-import-folder-mp3-uploads-tuple](../../specs/282-import-folder-mp3-uploads-tuple/spec.md) — добавление `FF_MP3_*` и `UPLOAD_*` в кортеж

## Код

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` — метод
  `Song.createFromPath(...)` формирует кортеж задач (KEY_BPM_FROM_FILE → DEMUCS2
  → FF_MP3_ACCOMPANIMENT → FF_MP3_VOCAL → 4 × UPLOAD_*).
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` —
  `doCreateFromFolder` (`/utils/createfromfolder`) — точка входа UI; legacy
  `MainController.doCreateFromFolder` через те же общие функции.

## История

- Создан: 2026-08-14
- 2026-08-31 (Pass 282): обновлено — кортеж расширен до 7 шагов (`DEMUCS2` + `FF_MP3_*` + `UPLOAD_*`); cross-link на спеку 282.