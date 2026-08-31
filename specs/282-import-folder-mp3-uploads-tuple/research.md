# Research: 282 — Кортеж заданий при «Добавить файлы из папки»

**Дата**: 2026-08-31 | **Спека**: [spec.md](spec.md) | **План**: [plan.md](plan.md)

> **Замечание.** Все `[NEEDS CLARIFICATION]` в спеке уже закрыты на этапе `/speckit.clarify` (см. Clarifications в spec.md: Q1 → A, Q2 → B). Этот документ фиксирует технические решения, принятые на основе исследования существующего кода проекта; альтернативы рассматриваются только там, где они меняли бы дизайн кортежа.

## R-001. Дедупликация `UPLOAD_*` через `karaokeFileType`

**Decision**: Использовать существующую дедупликацию `KaraokeProcess.createProcess` (`KaraokeProcess.kt:1001-1007`), где для `UPLOAD_TO_LOCAL_STORE`/`UPLOAD_TO_REMOTE_STORE` ключ поиска дублей включает `process_args = "karaokeFileType=<имя_типа>"`. Передавать этот параметр через `context["karaokeFileType"]` (см. `KaraokeProcess.kt:1004-1006`).

**Rationale**:
- Это позволяет ставить в одной песне `UPLOAD_TO_LOCAL_STORE` для разных типов файлов (`PICTURE_ALBUM`, `MP3_VOCAL`, `MP3_ACCOMPANIMENT`, и т. д.) без задвоения — каждый тип имеет свой ключ.
- Существующий код в `HealthReport.kt:617-630` (local) и `HealthReport.kt:911-921` (remote) уже формирует тот же контекст, что и мы планируем; никаких новых полей в `context` не требуется.
- Если бы НЕ использовали `karaokeFileType` в ключе — задвоение `UPLOAD_TO_LOCAL_STORE` для разных типов в одной песне было бы неизбежно (т.к. `process_type`+`thread_id`+`song_id` совпадают), что ломало бы HealthReport.

**Alternatives considered**:
- *Не передавать `karaokeFileType` в context (т.е. использовать дефолтную дедупликацию только по `song_id`+`process_type`+`thread_id`)* — отклонено: задвоение всех UPLOAD_* одной песни, ломает HealthReport, регрессия.
- *Передавать `karaokeFileType` в `context`, но добавлять кастомную логику дедупликации в `KaraokeProcess.createProcess`* — отклонено: дублирование уже существующего механизма, лишний код, риск рассинхрона.

## R-002. Порядок шагов кортежа через приоритеты в одном lane

**Decision**: Использовать `threadId = 1` (`KaraokeProcess.THREAD_LANE_HEALTH_REPORT`) для всех 6 новых заданий + `prior = -1` для `FF_MP3_*` и `prior = -2` для `UPLOAD_*`. Существующий `DEMUCS2` в этом же кортеже использует `prior = -1`.

**Rationale**:
- В `KaraokeProcessWorker` (`KaraokeProcessWorker.kt:806` — `WHERE process_status = 'WAITING' ORDER BY process_priority, process_order, id LIMIT 1`) задания с более низким `priority` (включая отрицательные) выбираются первыми внутри lane; `process_priority` — это `prior` в `createProcess`.
- Все задания с `prior = -1` (`DEMUCS2`, `FF_MP3_ACCOMPANIMENT`, `FF_MP3_VOCAL`) идут **в любом порядке друг относительно друга** в одном lane (при равенстве `priority` сортировка по `process_order`, затем `id`). Это соответствует Q2 из Clarifications: «сначала все `FF_MP3_*`» — гарантируется, что они все будут в очереди до того, как первое `UPLOAD_*` (`prior = -2`) начнёт обрабатываться.
- Соответствует паттерну `HealthReport.actionsLocalStorage` и `actionsRemoteStorage` (`HealthReport.kt:622, 681, 728, 911, 973, 1021` — все используют `prior = -2`).
- Сохраняет совместимость с комментарием `// threadId=1 (THREAD_LANE_HEALTH_REPORT) - тот же лейн, в котором HealthReport.actions() по умолчанию ставит все дальнейшие шаги каскада (FF_MP3_*/UPLOAD_*), иначе кортеж расползётся по разным лейнам` (`Song.kt:8196-8200`) — кортеж не «расползается».

**Alternatives considered**:
- *Использовать `THREAD_LANE_REMOTE_STORE_UPLOAD = -2` для `UPLOAD_TO_REMOTE_STORE`* — отклонено: `HealthReport` сейчас использует для всех UPLOAD единый `threadId = 1`; введение отдельного лейна для remote-store создаст расхождение и потенциальную десинхронизацию (HealthReport поставит UPLOAD в `threadId = 1`, а кортеж в `-2`).
- *Использовать `doWait = false` (т.е. `KaraokeProcessStatuses.CREATING`)* — отклонено: теряем синхронность HTTP-ответа с состоянием `WAITING` (оператор увидит «пустоту» в UI до завершения `createProcess`), а существующие `DEMUCS2` и `KEY_BPM_FROM_FILE` в кортеже уже используют `doWait = true`.

## R-003. `HealthReport.startRepairAll` сохраняется как fallback

**Decision**: Оставить вызов `HealthReport.startRepairAll(newSong, WORKING_DATABASE, storageService, storageApiClient)` в `ApiController.doCreateFromFolder` (`ApiController.kt:5511`) без изменений. Не дублировать и не удалять.

**Rationale**:
- `HealthReport` покрывает **другие** аспекты пост-обработки, не только mp3: `PICTURE_ALBUM`, `PICTURE_ALBUM_PREVIEW`, `PICTURE_AUTHOR`, `PICTURE_AUTHOR_PREVIEW`, восстановление из хранилища при отсутствии файла на диске (см. `HealthReport.kt:1306-1900+` — блоки для каждого типа файла).
- Дедупликация в `HealthReport` (`HealthReport.kt:569-580` для local, `857-868` для remote) проверяет `inProgress`-задания через `KaraokeProcess.loadList` с `process_type` и `karaokeFileType` в `process_args` — то есть для уже идущих `UPLOAD_*` из кортежа HealthReport **не** создаст дублей (`IN_PROGRESS` → `canBeResolved = false`, см. `HealthReport.kt:598-601`).
- Дедупликация для `FF_MP3_*` (`HealthReport.kt:1458-1466` для accompaniment, `1562-1570` для vocal) использует `inProgressOwnArgs` с `process_type = KaraokeProcessTypes.FF_MP3_*.name` — также отрабатывает корректно для уже идущих из кортежа.
- Удаление `HealthReport.startRepairAll` сломает: а) картинки автора/альбома, б) восстановление из хранилища для уже существующих песен (если они попали в импорт через другие пути).

**Alternatives considered**:
- *Удалить `HealthReport.startRepairAll` и полностью перейти на кортеж* — отклонено: теряем покрытие других типов файлов и восстановление из хранилища; большой объём работы, выходящий за скоуп фичи.
- *Оставить, но добавить флаг «уже обработано кортежем»* — отклонено: HealthReport уже корректно дедуплицирует через `inProgress`-проверку; дополнительный флаг избыточен и создаёт новый источник рассинхрона.

## R-004. Удаление закомментированных `FF_MP3_KAR`/`FF_MP3_LYR`

**Decision**: Удалить закомментированные блоки `FF_MP3_KAR` и `FF_MP3_LYR` в `Song.createFromPath()` (`Song.kt:8208-8222`), чтобы код не вводил в заблуждение будущих читателей.

**Rationale**:
- Эти legacy-типы помечены как устаревшие: в самом `KaraokeProcess.kt:1648-1690` (блоки `KaraokeProcessTypes.FF_MP3_KAR` и `FF_MP3_LYR` закомментированы, `args = listOf(...)` для них не определены).
- Заменой служат `FF_MP3_ACCOMPANIMENT` + `FF_MP3_VOCAL` (актуальный караоке-пайплайн с Demucs + mp3-аккомпанемент + mp3-вокал), а не legacy-`FF_MP3_KAR`/`FF_MP3_LYR` (эти типы предназначались для старого пайплайна с FFmpeg-минусовкой без Demucs).
- LiveDoc 082 явно говорит про актуальный кортеж «demucs → mp3 → upload» — закомментированные блоки противоречат этому.

**Alternatives considered**:
- *Оставить закомментированными* — отклонено: вводит в заблуждение (читатель может подумать «это второй вариант»), замедляет понимание кода.

## R-005. Участие `tbl_processes` в `SyncRegistry.all`

**Decision**: **НЕ добавлять** `tbl_processes` в `SyncRegistry.all` в рамках этой фичи; проверить, что новые записи в `tbl_processes` корректно обрабатываются существующей sync-логикой (либо через SyncRegistry, либо как append-only лог).

**Rationale**:
- Фича только создаёт **новые** записи в `tbl_processes` (через `KaraokeProcess.createProcess`), никак не модифицирует схему таблицы, не меняет существующие записи и не удаляет их.
- Если `tbl_processes` уже синхронизируется — новые записи подхватываются автоматически.
- Если НЕ синхронизируется — это текущее поведение, не зависящее от этой фичи; добавление таблицы в `SyncRegistry.all` требует написания отдельного `KaraokeDbTable`-класса, миграции и approval (см. Constitution v2.1.0 § III «Двух-БД синхронизация через SyncRegistry», плюс Sync Impact Report: «TblProcesses требует отдельного обсуждения, см. ADR backlog»).
- Выходит за скоуп фичи.

**Action item (для реализатора)**: перед коммитом проверить `git log -p sync/SyncTarget.kt | head` — если `TblProcesses` уже там, фича работает без изменений; если нет — оставить как есть (без новых sync-флагов) и зафиксировать в коммит-сообщении: «sync для tbl_processes не затрагивается; см. ADR backlog».

**Alternatives considered**:
- *Добавить `tbl_processes` в `SyncRegistry.all` в этой же фиче* — отклонено: выходит за скоуп (требует миграции + approval); риск поломки sync на проде (18k+ песен).
- *Игнорировать sync-аспект полностью* — отклонено: фича создаёт реальные новые записи в таблице, которая может участвовать в sync; ответственное отношение требует хотя бы верифицировать существующее поведение.

## R-006. Обновление LiveDoc 082

**Decision**: В том же PR обновить `livedocs/features/082-fix-import-folder-oom.md` — секции «Что делает» (Кортеж задач) и «Acceptance Criteria» (AC3: «Кортеж задач — 3 задания в одном lane» → «Кортеж задач — 7 заданий в одном lane»), плюс «Связанные LiveDocs» (cross-link на новый spec.md).

**Rationale**:
- Constitution v2.1.0 § «FR-014 Sync-обязательства»: при изменении bounded context или C4 уровня — обновить LiveDoc в том же PR.
- AGENTS.md § «Обновление LiveDocs (FR-014)»: «при изменении bounded context или C4 уровня — обновить LiveDoc в том же PR».
- LiveDoc 082 сейчас описывает желаемое поведение кортежа (3 задания), а реальный код после фичи станет содержать 7 заданий — это расхождение нужно зафиксировать.

**Alternatives considered**:
- *Не обновлять LiveDoc* — отклонено: нарушает FR-014 Constitution + AGENTS.md.
- *Обновлять LiveDoc отдельным PR* — отклонено: теряется связность изменения с документацией.

## R-007. Поведение при `DEMUCS2 = ERROR` (деградация)

**Decision**: Не добавлять специальных «пропусков» при ошибке `DEMUCS2`; кортеж продолжает формироваться, `FF_MP3_*` и `UPLOAD_*` падают сами (нет исходных flac-стемов → `FF_MP3_*` падает → `UPLOAD_*` падает на отсутствии файла).

**Rationale**:
- Существующее поведение через `HealthReport.startRepairAll` тоже не имеет специальной обработки ошибки `DEMUCS2` — `HealthReport` для каждого типа файла просто проверяет условия (например, `existsInLocalFileSystem = File(pathToFile).exists()`) и падает с понятной ошибкой при их нарушении.
- Деградация «ничего не загружается» ожидаема для оператора и видна в UI (HealthReport покажет проблему).
- Добавление специальных «пропусков» создаст неожиданное поведение (например, `FF_MP3_VOCAL` «успешно завершается» без файла на диске — это вводит в заблуждение при последующем HealthReport-пересчёте).

**Alternatives considered**:
- *Добавить проверку статуса предыдущего шага кортежа перед постановкой следующего* — отклонено: усложняет код, создаёт race condition между проверкой и фактическим завершением, дублирует логику KaraokeProcessWorker (он сам проверяет статусы при выборке).

## R-008. Производительность при импорте папки

**Decision**: Не оптимизировать параллельность кортежа в этой фиче; положиться на существующий механизм `KaraokeProcessWorker` (многопоточный воркер, см. `KaraokeProcessWorker.kt`).

**Rationale**:
- `KaraokeProcessWorker` уже обрабатывает `threadId = 1` параллельно по нескольким песням (см. конституцию § IV «Async-очередь задач с парсингом stdout»: «Задания имеют приоритет и `threadId`-лейны»).
- Между 6 новыми заданиями одной песни порядок гарантируется приоритетами; между песнями — параллельность.
- LiveDoc 082 уже описывает «1000+ файлов» — эта фича не ухудшает показатели, т.к. добавляет только `O(N)` новых записей в `tbl_processes` (N — число песен).
- Дополнительная оптимизация (например, батчинг UPLOAD) — выходит за скоуп, требует отдельной фичи.

**Alternatives considered**:
- *Батчить UPLOAD нескольких песен в один процесс* — отклонено: усложняет воркер, требует нового `KaraokeProcessTypes` и UI; не блокирует основной сценарий фичи.

---

## Сводка решений

| ID | Решение | Эффект на скоуп |
|----|---------|-----------------|
| R-001 | Использовать `karaokeFileType` в context для дедупликации UPLOAD_* | Без изменений скоупа |
| R-002 | `threadId=1` для всех 6 новых заданий, `prior=-1`/`-2` | Без изменений скоупа |
| R-003 | `HealthReport.startRepairAll` остаётся без изменений | Без изменений скоупа |
| R-004 | Удалить закомментированные `FF_MP3_KAR`/`FF_MP3_LYR` | Минимальный cleanup |
| R-005 | НЕ добавлять `tbl_processes` в SyncRegistry; только верифицировать | Action item для реализатора |
| R-006 | Обновить LiveDoc 082 в том же PR | Требуется документация |
| R-007 | Не добавлять специальных пропусков при DEMUCS2=ERROR | Без изменений |
| R-008 | Не оптимизировать параллельность; положиться на KaraokeProcessWorker | Без изменений |

Все 8 решений — без расширения скоупа фичи (только R-004 как cleanup и R-006 как обязательное обновление документации).
