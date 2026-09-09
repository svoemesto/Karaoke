# Component: song-lifecycle

> **Домен**: [catalog](../domain.md)
> **Компонент**: жизненный цикл `Song` по `IdStatus`, доменные события
> при переходах, роли участников пайплайна.

## Ответственность | Responsibility

Эта компонента описывает **end-to-end pipeline** одной песни: от момента
попадания mp3 в папку импорта до публикации MP4 на публичном сайте.
Каждый переход `IdStatus` — это **доменное событие** с явными
триггерами и участниками.

## Интерфейсы и Контракты | Interfaces and Contracts

### Переходы `IdStatus`

Полная таблица переходов (см. [dictionaries](dictionaries.md) для кодов):

| Из | В | Доменное событие | Триггер | Участник |
| --- | --- | --- | --- | --- |
| — | `NEW (1)` | `SongAdded` | mp3 появился в папке импорта | `SongService` (catalog) |
| `NEW (1)` | `PROCESSING (2)` | `SongStatusChanged` | редактор взял задание | `Identity` editor + [processing](../../processing/domain.md) |
| `PROCESSING (2)` | `STEMS_READY (3)` | `SongStatusChanged` | обработка завершена (см. drift note) | [processing](../../processing/domain.md) |
| `STEMS_READY (3)` | `MARKED (4)` | `SongStatusChanged` | редактор расставил маркеры | `Identity` editor |
| `MARKED (4)` | `RENDERED (5)` | `SongStatusChanged` | MP4 готов, лежит в MinIO | [rendering](../../rendering/domain.md) (melt worker) |
| `RENDERED (5)` | `APPROVED (6)` | `SongStatusChanged` | редактор одобрил | `Identity` editor |
| любой | `SongPublished` | — | `publishDate` истёк | `SongService.isContentReady()` |
| любой | `SongSkipped` | — | добавлен тег `SKIP` | `Identity` admin |

**Контракт**:

- Переходы **только вперёд** (1→2→3→4→5→6). Откат требует отдельного
  эпика (например, «re-render после бага»).
- Каждый переход атомарен и логируется в `audit_log`.

## Логика и Алгоритмы | Logic and Algorithms

### Шаг 1. Импорт (`SongAdded`)

1. mp3 появляется в `import/` директории.
2. `SongService` создаёт запись `Song` с `idStatus=1`, метаданными из
   тегов файла.
3. Доменное событие `SongAdded` публикуется.

### Шаг 2. Обработка (`PROCESSING`)

1. Редактор (через админку) берёт задание на обработку (`canSelfAssign`).
2. `Song.idStatus = 2`.
3. `processing` workers стартуют в фоне через [processing domain](../../processing/domain.md).
4. Доменное событие `SongStatusChanged(1→2)`.

### Шаг 3. Стемы готовы (`STEMS_READY`)

> **Drift note** (Pass 341): в текущей кодовой базе отдельного
> Demucs-степа нет. `AudioAnalize` (локальный CLI) делает аудио-анализ,
> после чего `idStatus=3` (см. gaps в processing/domain.md). Название
> `STEMS_READY` сохраняется для обратной совместимости с JS-фильтрами
> webvue3.

1. Обработка (`AudioAnalize` + смежные workers) завершена.
2. Результаты складываются в MinIO (для стемов — `stems/<songId>/`).
3. `Song.idStatus = 3`.
4. Доменное событие `SongStatusChanged(2→3)`.

### Шаг 4. Разметка (`MARKED`)

1. Редактор открывает веб-плеер, слушает стемы, расставляет
   `sourceMarkers` (время начала/конца секций).
2. Сохраняет маркеры через `SongService.updateSourceMarkers()`.
3. `Song.idStatus = 4`.
4. Доменное событие `SongStatusChanged(3→4)`.

### Шаг 5. Рендер (`RENDERED`)

1. `rendering` worker стартует melt с `MltProp` (~150 параметров из
   `KaraokeProperties.kt`).
2. MP4 (одна или три версии: LYRICS/KARAOKE/DEMO) складывается в
   MinIO `done_files/<songId>/`.
3. `Song.idStatus = 5`.
4. Доменное событие `SongStatusChanged(4→5)`.
5. См. [rendering domain](../../rendering/domain.md) для деталей.

### Шаг 6. Approve (`APPROVED`)

1. Редактор просматривает результат, нажимает Approve в админке.
2. `Song.idStatus = 6`.
3. `publishDate` остаётся прежним (если не изменён вручную).
4. Доменное событие `SongStatusChanged(5→6)`.

### Шаг 7. Публикация (`SongPublished`)

1. После истечения `publishDate` песня становится доступной публично.
2. Проверка: `idStatus == 6 && publishDate <= now()`.
3. Доменное событие `SongPublished`.

## Зависимости | Dependencies

- → [dictionaries](dictionaries.md) — `IdStatus` коды 1..6.
- → [domain](../domain.md) — AR `Song` хранит `idStatus`.
- → [identity](../../identity/domain.md) — `Identity` editor с
  `canSelfAssign=true` берёт задания.
- → [processing](../../processing/domain.md) — workers (`AudioAnalize`, melt, render).
- → [rendering](../../rendering/domain.md) — melt workers.
- → [publishing](../../publishing/domain.md) — `publishDate` управляется
  в publishing-контексте.

## Связанные ADR | Related ADRs

- ADR-0002 (MLT вместо ffmpeg) — про шаг 5.
- ADR по [processing workers](../../processing/domain.md) (TODO).

## Связанные фичи

- `182-editor-self-assign-tasks` — шаги 2, 4, 6 (редактор берёт задание).
- `184-approve-status-choice` — шаг 6 (выбор `idStatus` 5/6).
- `186-zakroma-songs-fast-load` — оптимизация загрузки списка песен.
