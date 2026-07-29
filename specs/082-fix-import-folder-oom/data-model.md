# Data Model: Устойчивый импорт файлов из папки

**Feature**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

Схема БД не меняется (см. `research.md` — фикс не требует новых
колонок/миграций). Ниже — концептуальные сущности из спеки (`Key Entities`)
в привязке к уже существующим полям/классам.

## Песня (существующая сущность, `tbl_songs` / `Song`)

Изменений схемы нет. Значимые для этой фичи поля:

| Поле (домен) | Источник | Роль в фиче |
|---|---|---|
| id | `Song.id` | ключ для кортежа заданий (`settings_id` в `tbl_processes`) |
| fileName/rootFolder | распознаётся из пути файла (`createFromPath`, regex по имени) | используется для дедупликации при повторном импорте (`loadListFromDb` по `file_name`+`root_folder`) |
| author/year/album/track/name | распознаётся из имени файла и папки-альбома | не меняется этой фичей |
| albumId | `Album.findOrCreateForSongImport(...)` | не меняется этой фичей |

## Папка импорта (концептуальная сущность, не персистентная)

Не хранится как строка БД — параметр запроса (`folder: String`) и результат
рекурсивного обхода файловой системы.

| Атрибут | Источник (после фикса) | Роль в фиче |
|---|---|---|
| путь файла | элемент, отдаваемый `Files.walk(...)` **лениво**, отфильтрованный по расширению (flac/mp3/m4a) во время обхода | единица обработки; FR-001 требует не накапливать нефильтрованный список путей всей папки в памяти |
| Stream обхода | `Files.walk(Path(pathToFolder))` | ДОЛЖЕН закрываться (`.use { }`) независимо от результата обхода — FR-002 |

## Задание очереди (существующая сущность, `tbl_processes` / `KaraokeProcess`)

Изменений схемы нет. Значимые для этой фичи поля:

| Поле (домен) | Поле в БД | Роль в фиче |
|---|---|---|
| id | `id` | используется дедупом `createProcess` (по `settings_id`+`process_type`+`thread_id`) — FR-007 |
| тип задания | `process_type` (`KaraokeProcessTypes`) | значимые для этой фичи значения: `KEY_BPM_FROM_FILE`, `DEMUCS2`, `FF_MP3_ACCOMPANIMENT`, `FF_MP3_VOCAL`, `UPLOAD_TO_LOCAL_STORE`, `UPLOAD_TO_REMOTE_STORE` |
| thread-лейн | `thread_id` | ДОЛЖЕН быть `KaraokeProcess.THREAD_LANE_HEALTH_REPORT` (=1) для **всех** шагов кортежа одной новой песни — FR-006/FR-008; уже так и есть (проверено). `KEY_BPM_FROM_FILE` намеренно в другом лейне (`THREAD_LANE_STEM_JOBS`) — не входит в кортеж, см. Находку C в `research.md` |
| статус | `process_status` | `WAITING → WORKING → {DONE\|ERROR}`; не меняется этой фичей |

## Кортеж заданий лейна (концептуальная сущность — упорядоченная последовательность `KaraokeProcess` для одной песни)

Не отдельная таблица — производная от `settings_id` + `thread_id` +
`HealthReport.HR_REPAIR_PROCESS_TYPES`. **Кортеж, как его определяет**
`spec.md` **FR-006, не включает** `KEY_BPM_FROM_FILE`: это отдельное,
независимое задание (определение key/BPM), которое не блокирует и не
блокируется дальнейшими шагами — намеренно ставится в свой собственный
лейн (`threadId=2`, `THREAD_LANE_STEM_JOBS`), параллельно кортежу, а не
внутри него (см. Находку C в `research.md`; уточнено пользователем при
ревью планирования). Сам кортеж формируется в два этапа:

1. **Немедленно** при импорте (`Song.createFromPath`): `DEMUCS2` —
   `threadId = THREAD_LANE_HEALTH_REPORT` (первый и единственный шаг
   кортежа, создаваемый сразу; `KEY_BPM_FROM_FILE` создаётся тут же, но
   отдельно, в `threadId=THREAD_LANE_STEM_JOBS`, вне кортежа).
2. **Каскадно**, по мере готовности предыдущих шагов
   (`HealthReport.startRepairAll` → `onRepairProcessFinished`, хук из
   `KaraokeProcessWorker.kt:360` на завершение каждого задания из
   `HR_REPAIR_PROCESS_TYPES`): `FF_MP3_ACCOMPANIMENT`/`FF_MP3_VOCAL` (после
   готовности `DEMUCS2`), затем `UPLOAD_TO_LOCAL_STORE`/`UPLOAD_TO_REMOTE_STORE`
   для каждого готового файла — все в том же `THREAD_LANE_HEALTH_REPORT`
   (наследуется по умолчанию, `HealthReport.kt:335`, либо от «родителя в
   процессе», строка 348 — «родителем» для этих шагов всегда выступает
   предыдущий шаг того же кортежа, никогда `KEY_BPM_FROM_FILE`).

Отслеживание прогресса каскада — `HealthReport.autoRepairSongIds`
(`ConcurrentHashMap.newKeySet()`, in-memory, потокобезопасный набор
`settingsId`) — уже потокобезопасен для одновременного массового импорта
многих песен (FR-008), правок не требует.

```text
KEY_BPM_FROM_FILE (threadId=THREAD_LANE_STEM_JOBS — намеренно ВНЕ кортежа,
                    независимое задание, ничего не блокирует и не ждёт)

DEMUCS2 (threadId=THREAD_LANE_HEALTH_REPORT)  ← первый шаг кортежа
        │
        ▼ (по завершении DEMUCS2, каскад HealthReport)
FF_MP3_ACCOMPANIMENT + FF_MP3_VOCAL   (threadId=THREAD_LANE_HEALTH_REPORT)
        │
        ▼ (по завершении, файлы появляются на диске)
UPLOAD_TO_LOCAL_STORE (муз. + голос)  (threadId=THREAD_LANE_HEALTH_REPORT)
        │
        ▼
UPLOAD_TO_REMOTE_STORE (муз. + голос) (threadId=THREAD_LANE_HEALTH_REPORT)
```

## Итоговая сводка импорта (новая, не персистентная — ответ операции)

Расширение уже существующего SSE-уведомления
(`ApiController.doCreateFromFolder`, `SNS.send(SseNotification.message(...))`)
— формат сообщения не меняется структурно (текст), но текст должен отражать
не только «добавлено», но и «пропущено» (FR-005/SC-004):

| Атрибут | Роль |
|---|---|
| добавлено | число файлов, успешно ставших новыми песнями (как и сейчас — `createdList.size`) |
| пропущено | число файлов, не прошедших разбор имени/конвертацию — ранее нигде не учитывалось отдельно |

## Валидационные правила (из Functional Requirements)

- FR-001/FR-002/FR-003: обход папки не должен материализовывать
  нефильтрованный список всех файлов дерева и обязан закрывать `Stream`
  независимо от исхода — см. Находку A.
- FR-004: фоновые запуски пост-обработки песни (поиск текста) — через
  ограниченный по конкурентности исполнитель, не через неограниченный
  `thread(start = true)` на каждую песню.
- FR-006/FR-007/FR-008: `threadId` всех заданий **кортежа** (`DEMUCS2` →
  `FF_MP3_*` → `UPLOAD_*`) — `THREAD_LANE_HEALTH_REPORT`, без исключений;
  уже так в текущем коде (см. Находку C). `KEY_BPM_FROM_FILE` — намеренно
  вне кортежа, в отдельном лейне, не в счёт этого правила. Дедуп
  `createProcess` (по `settings_id`+`process_type`+`thread_id`) и
  каскадная проверка `inProgressOwnArgs`/`HealthReport` (по
  `settings_id`+`process_type`, без `thread_id`) вместе гарантируют
  отсутствие задвоения шагов кортежа.
