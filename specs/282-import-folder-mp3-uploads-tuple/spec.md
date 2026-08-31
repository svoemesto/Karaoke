# Feature Specification: Кортеж заданий при «Добавить файлы из папки» — добавить загрузку mp3 голоса/аккомпанимента в локальное и удалённое хранилище

**Feature Branch**: `282-import-folder-mp3-uploads-tuple`
**Created**: 2026-08-31
**Status**: Draft
**Input**: Описание пользователя: "Админка, главная страница, 'Добавить файлы из папки'. В кортеж заданий не попадают задания на загрузку mp3-файлов голоса и аккомпанимента в локальное и удалённое хранилище - нужно добавить после задания на создание mp3."

## Clarifications

### Session 2026-08-31

- Q1: Какие именно задания должны попасть в явный кортеж `Song.createFromPath()` — только `UPLOAD_*` или и `FF_MP3_*` (создание mp3) тоже? → A: **A — и `FF_MP3_*`, и `UPLOAD_*`**. Все 6 заданий (FF_MP3_ACCOMPANIMENT, FF_MP3_VOCAL, UPLOAD_TO_LOCAL_STORE × 2, UPLOAD_TO_REMOTE_STORE × 2) добавляются в кортеж явно; `HealthReport.startRepairAll()` остаётся как fallback для других аспектов постобработки. Полностью соответствует LiveDoc 082 «demucs → mp3 → upload в одном lane».
- Q2: В каком порядке должны идти задания в кортеже — после каждого `FF_MP3_*` сразу его `UPLOAD_*`, или сначала все `FF_MP3_*`, потом все `UPLOAD_*`? → A: **B — все `FF_MP3_*` сначала, потом все `UPLOAD_*`**. Порядок: `FF_MP3_ACCOMPANIMENT` → `FF_MP3_VOCAL` → `UPLOAD_TO_LOCAL_STORE` (MP3_ACCOMPANIMENT) → `UPLOAD_TO_LOCAL_STORE` (MP3_VOCAL) → `UPLOAD_TO_REMOTE_STORE` (MP3_ACCOMPANIMENT) → `UPLOAD_TO_REMOTE_STORE` (MP3_VOCAL). Логически чище: «сначала всё готовим, потом всё загружаем».

## Пользовательские сценарии и тестирование *(обязательные)*

### Пользовательская история 1 — mp3 голоса и аккомпанимента попадают в кортеж при импорте из папки (Приоритет: P1)

Оператор на главной странице админки (`webvue3`) нажимает «Добавить файлы из папки» и указывает папку с треками (`YYYY - Альбом/YYYY (NN) [Автор] - Песня.flac`). Для каждой новой песни система должна выстроить **кортеж заданий** (последовательность шагов обработки), который:

1. уже определит тональность/BPM (`KEY_BPM_FROM_FILE`, отдельный лейн),
2. уже разделит стемы через `DEMUCS2` (стем-сепарация, минусовка + голос),
3. уже **создаст** mp3-файлы минусовки и голоса (`FF_MP3_ACCOMPANIMENT`, `FF_MP3_VOCAL`),
4. уже **загрузит** эти mp3 в локальное хранилище (`UPLOAD_TO_LOCAL_STORE` × 2),
5. уже **загрузит** их же в удалённое хранилище (`UPLOAD_TO_REMOTE_STORE` × 2).

То есть после завершения кортежа (без ручных запусков и перепроверок) mp3-файлы голоса и аккомпанимента должны присутствовать **и на диске**, **и в локальном MinIO**, **и в удалённом MinIO** — так же, как сейчас это происходит при ручном «Исправить всё» на карточке песни.

**Почему этот приоритет**: сегодня в `Song.createFromPath()` явно прописаны только `KEY_BPM_FROM_FILE` (отдельный лейн) и `DEMUCS2` (первый шаг кортежа), а создание mp3 (`FF_MP3_*`) и загрузка mp3 в хранилища (`UPLOAD_TO_LOCAL_STORE`/`UPLOAD_TO_REMOTE_STORE`) отданы на откуп `HealthReport.startRepairAll()`, который запускается **отдельно** в `ApiController.doCreateFromFolder`. В результате:

- На практике между `Song.createFromPath()` и `HealthReport.startRepairAll()` есть окно, в котором кортеж «выглядит» как `demucs` без продолжения, и оператор не видит в UI списка последующих шагов.
- Если `HealthReport.startRepairAll()` по какой-либо причине не отработает (например, песня уже в каскаде авто-ремонта, или репорт ещё не успел пересчитаться), mp3-файлы остаются только на диске, но **не загружаются в хранилища** — публичный плеер и скачивание не получают mp3.

Явный кортеж в `Song.createFromPath()` гарантирует, что после импорта из папки mp3 голоса и аккомпанимента **всегда** оказываются в локальном и удалённом хранилище без отдельного ручного или фонового шага.

**Независимый тест**: подготовить папку `2026 - TestAlbum/` с 2–3 треками по шаблону импорта. Перед запуском убедиться, что локальный и удалённый MinIO **пусты** для будущих mp3-имён (`storageFileName` вида `Автор/2026 - TestAlbum/<fileName>.accompaniment.mp3` и `.vocals.mp3`). Запустить «Добавить файлы из папки», дождаться завершения всего кортежа (по `tbl_processes` все шаги в статусе `DONE`). Проверить:

- на диске существуют `<rootFolder>/<fileName>-accompaniment.mp3` и `<rootFolder>/<fileName>-vocals.mp3`;
- в локальном MinIO (бакет `karaoke`) есть объекты с именами `<storageFileName>.accompaniment.mp3` и `<storageFileName>.vocals.mp3`;
- в удалённом MinIO есть те же два объекта;
- в UI карточки песни `stemAccompanimentReady`/`stemVocalReady` = `true` (или эквивалентный признак готовности).

**Сценарии приёмки**:

1. **Дано** папка с 2 треками по шаблону `YYYY - Album/YYYY (NN) [Author] - Song.flac`, **Когда** оператор нажимает «Добавить файлы из папки», **Тогда** для каждой новой песни в `tbl_processes` появляется **полный кортеж** в одном lane (threadId=1) в порядке: `DEMUCS2` → `FF_MP3_ACCOMPANIMENT` → `FF_MP3_VOCAL` → `UPLOAD_TO_LOCAL_STORE` (для `MP3_ACCOMPANIMENT`) → `UPLOAD_TO_LOCAL_STORE` (для `MP3_VOCAL`) → `UPLOAD_TO_REMOTE_STORE` (для `MP3_ACCOMPANIMENT`) → `UPLOAD_TO_REMOTE_STORE` (для `MP3_VOCAL`).
2. **Дано** кортеж запущен и `DEMUCS2` уже завершён, **Когда** воркер обрабатывает следующее задание `FF_MP3_ACCOMPANIMENT`, **Тогда** оно стартует **только после** того, как `DEMUCS2` для этой же песни перешёл в `DONE` (порядок сохраняется через приоритеты и лейн, как и сейчас у `DEMUCS2`/`KEY_BPM_FROM_FILE`).
3. **Дано** `FF_MP3_VOCAL` для песни завершился и на диске появился `<fileName>-vocals.mp3`, **Когда** воркер подхватывает следующие задания кортежа, **Тогда** оба `UPLOAD_TO_LOCAL_STORE` (`MP3_ACCOMPANIMENT` и `MP3_VOCAL`) и оба `UPLOAD_TO_REMOTE_STORE` отрабатывают сразу после создания соответствующего mp3, без ручного вмешательства и без отдельной фоновой задачи.
4. **Дано** все шаги кортежа завершились успешно, **Когда** оператор открывает карточку песни в админке, **Тогда** поля готовности плеера для голоса/аккомпанимента (`stemVocalReady`/`stemAccompanimentReady`) отображаются как «готово», а публичные URL'ы mp3 возвращают содержимое (HEAD/GET-запрос к MinIO отдаёт 200 OK и непустой ответ).

---

### Пользовательская история 2 — Кортеж не дублирует уже существующие in-progress задачи (Приоритет: P2)

Оператор импортирует из папки трек, для которого параллельно уже идёт каскад «Исправить всё» (например, оператор сначала нажал «Исправить всё» в карточке, потом запустил импорт — или наоборот; либо предыдущий импорт той же папки не успел завершиться). Система должна корректно отработать: **не создавать дубликаты** тех же заданий, не ломать уже идущие, и в итоге mp3-файлы всё равно оказались в хранилищах.

**Почему этот приоритет**: после добавления явных заданий в `Song.createFromPath()` появится окно, когда `HealthReport.startRepairAll()` (в `ApiController.doCreateFromFolder` сразу после `createFromPath`) может попытаться поставить те же `FF_MP3_*`/`UPLOAD_*` повторно. Без дедупликации это либо задвоит задачи в очереди, либо пересоздаст уже работающее задание и собьёт его прогресс.

**Независимый тест**: импортировать из папки трек, у которого **уже** существуют in-progress (`WAITING` или `WORKING`) процессы `FF_MP3_VOCAL` и `UPLOAD_TO_LOCAL_STORE` (например, от предыдущего неуспешного импорта или ручного рестарта). Запустить «Добавить файлы из папки» ещё раз. Проверить:

- в `tbl_processes` количество `FF_MP3_VOCAL` для этой песни **не увеличилось** (≤1 активной задачи);
- количество `UPLOAD_TO_LOCAL_STORE` с `karaokeFileType=MP3_VOCAL` для этой песни **не увеличилось**;
- итоговый результат (mp3 в MinIO) идентичен тому, как если бы дублирующих задач не было.

**Сценарии приёмки**:

1. **Дано** для песни уже есть процесс `FF_MP3_VOCAL` в статусе `WAITING` или `WORKING`, **Когда** `Song.createFromPath()` пытается добавить ещё один `FF_MP3_VOCAL` в кортеж, **Тогда** новый процесс **не создаётся** (используется существующий).
2. **Дано** для песни уже есть процесс `UPLOAD_TO_LOCAL_STORE` с `karaokeFileType=MP3_ACCOMPANIMENT` в статусе `WAITING` или `WORKING`, **Когда** кортеж пытается добавить ещё один `UPLOAD_TO_LOCAL_STORE` для того же типа, **Тогда** новый процесс **не создаётся** (используется существующий).
3. **Дано** для песни есть завершённый (`DONE`) или упавший (`ERROR`) `FF_MP3_VOCAL`, **Когда** `Song.createFromPath()` пытается добавить такой же, **Тогда** старый процесс пересоздаётся (как это уже делает `KaraokeProcess.createProcess` для не-WORKING-процессов) — это нормальное поведение повторного запуска.
4. **Дано** параллельно с кортежем `HealthReport.startRepairAll()` пытается поставить те же задания, **Когда** `recomputeAndBroadcast` и `executeResolvable` проверяют `inProgressOwnArgs`/`inProgressParentArgs`, **Тогда** дубликаты не создаются (`HealthReport` видит уже идущие задачи через `tbl_processes` и пропускает свои шаги).

---

### Граничные случаи

- Если `DEMUCS2` для этой песни **упал** (`ERROR`) — последующие шаги кортежа (`FF_MP3_*`, `UPLOAD_*`) всё равно ставятся в очередь, но реальной работы не делают (на диске нет исходных flac-стемов → `FF_MP3_*` падает, `UPLOAD_*` падает на отсутствии файла). Поведение деградирует так же, как сейчас через `HealthReport.startRepairAll` — никаких специальных «пропусков» при ошибке demucs добавлять не нужно.
- Если в папке альбома **нет ни одного** трека (или все треки не подходят под regex), `Song.createFromPath()` возвращает пустой `addedSongs` — кортеж не формируется ни для кого; текущее поведение сохраняется.
- Если `Song.createFromPath()` обрабатывает несколько треков одной папки — кортежи строятся **для каждой новой песни независимо**, без взаимных блокировок; параллельность по лейну `threadId=1` сохраняется, как и сегодня для `DEMUCS2`.
- Если оператор повторно запускает «Добавить файлы из папки» по **той же** папке — треки, уже импортированные (по `file_name`+`root_folder` через `loadListFromDb`), пропускаются **до** кортежа (текущая логика `loadListFromDb(...).isEmpty()`), кортеж для них не строится повторно — никакого изменения поведения повторного импорта.
- Если в `Karaoke.properties` отключён какой-то из шагов (например, `UPLOAD_TO_REMOTE_STORE` для конкретной машины — на admin-машине remote-хранилище может быть недоступно) — задание всё равно ставится в кортеж, упадёт с понятной ошибкой, и HealthReport при следующем пересчёте покажет проблему; поведение «задание есть, но падает» совпадает с тем, как сейчас работает `HealthReport.startRepairAll` для тех же типов файлов.
- Если `storageFileName` для песни меняется между моментом постановки `UPLOAD_*` и моментом выполнения (например, параллельно оператор переименовал альбом) — задание отработает с тем `storageFileName`, который был передан в `context` при создании; несоответствие обнаруживается на стороне HealthReport, как и сейчас.
- Если на момент импорта в `tbl_processes` **уже** есть задания `UPLOAD_TO_LOCAL_STORE` с другим `karaokeFileType` (например, `PICTURE_ALBUM`) — дедупликация не должна их затронуть, так как ключ поиска в `KaraokeProcess.createProcess` уже включает `process_args = karaokeFileType=<тип>`.
- Если у песни `pathToResultedModel` ещё не существует (например, очень ранний момент после создания `Song` без `saveToDb`) — `song.accompanimentNameMp3`/`song.vocalsNameMp3` могут вернуть путь к несуществующей директории. Это нормально: `FF_MP3_*` самостоятельно создаёт/перезаписывает файл, как и сейчас в HealthReport.

---

## Требования *(обязательные)*

### Функциональные требования

- **FR-001**: В `Song.createFromPath()` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`, начало — строка 8072) после уже существующего блока `KaraokeProcess.createProcess(... DEMUCS2, ...)` система ДОЛЖНА добавить в кортеж последовательно (в этом порядке — см. Q2 в Clarifications: сначала все `FF_MP3_*`, потом все `UPLOAD_*`):
  1. `KaraokeProcess.createProcess(song, action = FF_MP3_ACCOMPANIMENT, doWait = true, prior = -1, threadId = 1)`;
  2. `KaraokeProcess.createProcess(song, action = FF_MP3_VOCAL, doWait = true, prior = -1, threadId = 1)`;
  3. `KaraokeProcess.createProcess(song, action = UPLOAD_TO_LOCAL_STORE, doWait = true, prior = 0, threadId = 1, context = { pathToFile = song.accompanimentNameMp3, karaokeFileType = "MP3_ACCOMPANIMENT", storageFileName = "${song.storageFileName}.accompaniment.mp3", bucketName = song.storageBucketName })`;
  4. `KaraokeProcess.createProcess(song, action = UPLOAD_TO_LOCAL_STORE, doWait = true, prior = 0, threadId = 1, context = { pathToFile = song.vocalsNameMp3, karaokeFileType = "MP3_VOCAL", storageFileName = "${song.storageFileName}.vocals.mp3", bucketName = song.storageBucketName })`;
  5. `KaraokeProcess.createProcess(song, action = UPLOAD_TO_REMOTE_STORE, doWait = true, prior = 0, threadId = 1, context = { pathToFile = song.accompanimentNameMp3, karaokeFileType = "MP3_ACCOMPANIMENT", storageFileName = "${song.storageFileName}.accompaniment.mp3", bucketName = song.storageBucketName })`;
  6. `KaraokeProcess.createProcess(song, action = UPLOAD_TO_REMOTE_STORE, doWait = true, prior = 0, threadId = 1, context = { pathToFile = song.vocalsNameMp3, karaokeFileType = "MP3_VOCAL", storageFileName = "${song.storageFileName}.vocals.mp3", bucketName = song.storageBucketName })`.

**Важно (Pass 285)**: В `KaraokeProcessWorker` выборка заданий делается через `ORDER BY process_priority ASC` (`KaraokeProcess.kt:803`), то есть **меньше** `priority` = **раньше** в очереди. Поэтому `UPLOAD_*` получают `prior = 0` (а не `-2`!) — это гарантирует, что они стартуют **после** `DEMUCS2`/`FF_MP3_*` (у которых `prior = -1`). Если бы `UPLOAD_*` имели `prior = -2`, они стартовали бы **раньше** создания mp3 и падали бы на отсутствии файла на диске (см. Pass 285 / commit `2c8a1a5c`).

- **FR-002**: Все шесть новых заданий ДОЛЖНЫ использовать `threadId = 1` (`KaraokeProcess.THREAD_LANE_HEALTH_REPORT`), как и существующий `DEMUCS2` в этом же кортеже, чтобы кортеж не «расползался» по разным лейнам и его прогресс можно было читать через существующую инфраструктуру воркера (см. комментарий `// Первый шаг кортежа демукс→mp3→загрузка` рядом с существующим `DEMUCS2` в `Song.createFromPath`).

- **FR-003**: Существующая логика дедупликации `KaraokeProcess.createProcess()` (`KaraokeProcess.kt:985-1018`) ДОЛЖНА применяться и к новым заданиям кортежа — для `UPLOAD_TO_LOCAL_STORE`/`UPLOAD_TO_REMOTE_STORE` ключ уже включает `karaokeFileType` (см. `KaraokeProcess.kt:1001-1007`), что предотвращает задвоение задач для разных типов файлов одной песни (PICTURE_ALBUM / MP3_VOCAL и т. п.) и дублей при повторном запуске импорта/параллельной работе `HealthReport.startRepairAll`.

- **FR-004**: Существующий вызов `HealthReport.startRepairAll(newSong, WORKING_DATABASE, storageService, storageApiClient)` в `ApiController.doCreateFromFolder` (`ApiController.kt:5511`) ДОЛЖЕН быть сохранён без изменений — он покрывает другие аспекты пост-обработки (картинки автора/альбома, другие проверки), а также создаёт fallback-задачи `FF_MP3_*`/`UPLOAD_*` для песен, попавших в импорт через пути, отличные от `Song.createFromPath()` (например, ручное создание песни + импорт файлов). Дедупликация через `inProgressOwnArgs`/`inProgressParentArgs` (`HealthReport.kt:324-345`) предотвращает дублирование уже поставленных через кортеж задач.

- **FR-005**: Существующее задание `KEY_BPM_FROM_FILE` (`threadId = 2`, `prior = -1`) в `Song.createFromPath()` ДОЛЖНО быть сохранено без изменений — это намеренно отдельный от кортежа лейн, не зависящий ни от чего в кортеже и наоборот (см. комментарий `// threadId=2 (THREAD_LANE_STEM_JOBS) - НАМЕРЕННО отдельный от кортежа лейн`, `Song.kt:8184-8194`). Менять его `threadId`/приоритет в этом PR ЗАПРЕЩЕНО.

- **FR-006**: Существующее задание `DEMUCS2` (`threadId = 1`, `prior = -1`) в `Song.createFromPath()` ДОЛЖНО быть сохранено без изменений — оно остаётся первым шагом кортежа, и новые задания (`FF_MP3_*`/`UPLOAD_*`) логически следуют строго после него (порядок обеспечивается тем, что все они в одном `threadId = 1` lane и `FF_MP3_*` имеют приоритет `-1`, как и `DEMUCS2`, а `UPLOAD_*` имеют приоритет `-2` и стартуют после первых).

- **FR-007**: Контекст `context` для `UPLOAD_TO_LOCAL_STORE`/`UPLOAD_TO_REMOTE_STORE` (`pathToFile`, `karaokeFileType`, `storageFileName`, `bucketName`) ДОЛЖЕН быть сформирован по тем же правилам, что и в `HealthReport.kt:617-630` (для local) и `HealthReport.kt:911-921` (для remote) — чтобы аргументы заданий в `tbl_processes.process_args` совпадали по формату и существующая диагностика/HealthReport их корректно распознавали.

- **FR-008**: Существующие закомментированные блоки `FF_MP3_KAR`/`FF_MP3_LYR` в `Song.createFromPath()` (`Song.kt:8208-8222`) ДОЛЖНЫ быть оставлены в закомментированном виде или удалены — заменой служат новые `FF_MP3_ACCOMPANIMENT` + `FF_MP3_VOCAL` (для актуального караоке-пайплайна с Demucs + mp3-аккомпанемент + mp3-вокал), а не legacy-`FF_MP3_KAR`/`FF_MP3_LYR` (эти типы помечены как устаревшие в самой `KaraokeProcess.kt:1648, 1670` и в LiveDoc 082).

- **FR-009**: Внешний интерфейс админки (главная страница, кнопка «Добавить файлы из папки», диалог подтверждения, поведение модалок) НЕ ДОЛЖЕН быть изменён этой доработкой. Изменения только в серверной части `karaoke-app` и невидимы оператору — кортеж просто становится длиннее, и в UI списка процессов появляются новые строки.

- **FR-010**: Никаких новых зависимостей, библиотек, флагов в `Karaoke.properties` или миграций БД эта фича НЕ требует — используются уже существующие `KaraokeProcessTypes` (`FF_MP3_ACCOMPANIMENT`, `FF_MP3_VOCAL`, `UPLOAD_TO_LOCAL_STORE`, `UPLOAD_TO_REMOTE_STORE`), `KaraokeProcess.createProcess()` и существующая схема `tbl_processes`.

### Ключевые сущности

- **`Song`**: доменная сущность, для которой строится кортеж. Поля `accompanimentNameMp3` (`$pathToResultedModel/$fileName-accompaniment.mp3`), `vocalsNameMp3` (`$pathToResultedModel/$fileName-vocals.mp3`), `storageBucketName` (константа `"karaoke"`), `storageFileName` (`$author/$year - $album/$fileName`) — все уже существуют, переиспользуются как есть.
- **`KaraokeProcess`**: запись в `tbl_processes`, представляющая одно задание кортежа. Типы: `FF_MP3_ACCOMPANIMENT`, `FF_MP3_VOCAL`, `UPLOAD_TO_LOCAL_STORE`, `UPLOAD_TO_REMOTE_STORE`. Аргументы (`process_args`) для `UPLOAD_*` содержат `karaokeFileType=<MP3_VOCAL|MP3_ACCOMPANIMENT>`, что используется и для дедупликации, и для HealthReport.
- **Mp3-файл аккомпанимента** (`MP3_ACCOMPANIMENT`): результат `FF_MP3_ACCOMPANIMENT` — mp3-версия `accompanimentNameFlac` (минусовка, полученная после `DEMUCS2`). Хранится на диске, в локальном и удалённом MinIO под именем `${storageFileName}.accompaniment.mp3` в бакете `karaoke`.
- **Mp3-файл голоса** (`MP3_VOCAL`): результат `FF_MP3_VOCAL` — mp3-версия `vocalsNameFlac` (чистый вокал, полученный после `DEMUCS2`). Хранится на диске, в локальном и удалённом MinIO под именем `${storageFileName}.vocals.mp3` в бакете `karaoke`.
- **Локальное хранилище** (`KaraokeStorageService`): MinIO на admin-машине. Используется тот же бакет `karaoke`, что и для остальных файлов песни.
- **Удалённое хранилище** (`StorageApiClient`): удалённый MinIO (прод). Используется тот же бакет `karaoke`, что и для остальных файлов песни.

## Критерии успеха *(обязательные)*

### Измеримые результаты

- **SC-001**: При импорте из папки одной новой песни в `tbl_processes` появляются **ровно 7** новых записей с `thread_id = 1` для этой песни: 1 × `DEMUCS2` (уже было) + 1 × `FF_MP3_ACCOMPANIMENT` + 1 × `FF_MP3_VOCAL` + 2 × `UPLOAD_TO_LOCAL_STORE` (по одному для `MP3_ACCOMPANIMENT` и `MP3_VOCAL`) + 2 × `UPLOAD_TO_REMOTE_STORE`. (`KEY_BPM_FROM_FILE` идёт отдельным `thread_id = 2`, в этот счёт не входит.)

- **SC-002**: После завершения всех 7 заданий кортежа для импортированной песни **существуют**:
  - `<rootFolder>/<fileName>-accompaniment.mp3` и `<rootFolder>/<fileName>-vocals.mp3` на диске;
  - объекты `<storageFileName>.accompaniment.mp3` и `<storageFileName>.vocals.mp3` в локальном MinIO (бакет `karaoke`);
  - объекты `<storageFileName>.accompaniment.mp3` и `<storageFileName>.vocals.mp3` в удалённом MinIO (бакет `karaoke`).

- **SC-003**: При ручном добавлении одной и той же песни через `Song.createFromPath()` дважды (повторный запуск импорта по той же папке — например, тестовый прогон) в `tbl_processes` для неё НЕ появляется **дополнительных** in-progress (`WAITING` или `WORKING`) `FF_MP3_*`/`UPLOAD_*`-заданий сверх одной штуки каждого типа — дедупликация `KaraokeProcess.createProcess` отрабатывает корректно.

- **SC-004**: При импорте из папки 10 треков в `tbl_processes` появляются **ровно 70** новых кортежных заданий (10 × 7), все с `thread_id = 1` для соответствующих песен; ни одно из них не «расползается» в другие лейны; параллельность между песнями сохраняется (как и сейчас для `DEMUCS2`).

- **SC-005**: Существующий вызов `HealthReport.startRepairAll(newSong, ...)` в `ApiController.doCreateFromFolder` (`ApiController.kt:5511`) после фикса продолжает работать без изменений: для **только что импортированной** песни HealthReport при первом `recomputeAndBroadcast` видит уже идущие `FF_MP3_*`/`UPLOAD_*` через `inProgressOwnArgs`/`inProgressParentArgs` и **не** создаёт дублей; для песен, попавших в импорт не через `createFromPath` (например, ручное создание), HealthReport как и раньше создаёт недостающие задания.

- **SC-006**: Внешний интерфейс админки (главная страница, тексты кнопок/диалогов, поведение модалок) остаётся без изменений — `git diff` по `webvue3/src/views/HomeView.vue` и связанным компонентам пуст; единственные правки — в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (внутри `Song.createFromPath()`).

- **SC-007**: Существующие 46+ мест вызова `KaraokeProcess.createProcess()` (включая `ApiController`, `MainController`, `HealthReport`, `KaraokeProcessWorker`, `UtilsAI`, `SongEditorController` и т.д.) не затронуты — изменения локальны для `Song.createFromPath()`. Никаких новых точек входа в `createProcess` не появляется «извне» кортежа.

## Допущения

- **A-001**: «Кортеж заданий» в контексте этой задачи означает последовательность вызовов `KaraokeProcess.createProcess(...)` в одном `threadId`-лейне (для импорта папки — `threadId = 1`, `THREAD_LANE_HEALTH_REPORT`), формируемую в `Song.createFromPath()` при создании новой песни. Это согласовано с LiveDoc 082 (`livedocs/features/082-fix-import-folder-oom.md`, раздел «Что делает» → «Кортеж задач»), где ожидаемая последовательность — demucs → mp3 → upload в одном lane.
- **A-002**: Задания `FF_MP3_ACCOMPANIMENT`/`FF_MP3_VOCAL` (создание mp3) добавляются в кортеж **на равных** с `UPLOAD_*` (см. Q1 → Clarifications). Хотя исторически они добавлялись через `HealthReport.startRepairAll()` в `ApiController.doCreateFromFolder` (`ApiController.kt:5511`), после этой фичи они становятся частью явного кортежа в `Song.createFromPath()`. `HealthReport.startRepairAll()` остаётся как fallback для других аспектов постобработки (картинки автора/альбома и т.п.).
- **A-003**: Загрузка в локальное и удалённое хранилища выполняется **для обоих** типов mp3 (acc + vocals), а не только для одного из них — это соответствует существующему поведению `HealthReport.kt` (см. блоки `MP3_ACCOMPANIMENT` строки 1444-1494 и `MP3_VOCAL` строки 1549-1598), а также расположениям (`locations`) в `KaraokeFileType.kt:59-95` (для обоих типов явно перечислены `LOCAL_STORAGE` и `REMOTE_STORAGE`).
- **A-004**: Дедупликация заданий обеспечивается существующей логикой `KaraokeProcess.createProcess` (`KaraokeProcess.kt:1001-1007`) — для `UPLOAD_TO_LOCAL_STORE`/`UPLOAD_TO_REMOTE_STORE` ключ поиска дублей уже включает `karaokeFileType` (а не только `song_id`+`process_type`+`thread_id`). Это значит, что `HealthReport.startRepairAll()`, вызываемый сразу после `Song.createFromPath()` в `doCreateFromFolder`, не создаст дублей тех же задач — увидит их в `inProgress`-проверке (`HealthReport.kt:569-580` для local, `857-868` для remote) и пропустит свои шаги.
- **A-005**: Порядок задач в кортеже (`DEMUCS2` → `FF_MP3_ACCOMPANIMENT` → `FF_MP3_VOCAL` → `UPLOAD_*`) обеспечивается через единый `threadId = 1` и приоритеты: `DEMUCS2`/`FF_MP3_*` имеют `prior = -1`, `UPLOAD_*` имеют `prior = 0` (поскольку в `KaraokeProcessWorker` сортировка `ORDER BY process_priority ASC` — **меньше = раньше**; Pass 285 исправил начальное значение `-2`, из-за которого `UPLOAD_*` стартовали раньше создания mp3). Точная семантика очереди в `KaraokeProcessWorker` не меняется.
- **A-006**: `KEY_BPM_FROM_FILE` остаётся в **отдельном** `threadId = 2` (как сейчас) и **не** включается в этот кортеж — это намеренное архитектурное решение (LiveDoc 082, FR-003, плюс явный комментарий в `Song.kt:8184-8194`), которое менять в рамках этой фичи ЗАПРЕЩЕНО (NON-NEGOTIABLE).
- **A-007**: Существующий `HealthReport.startRepairAll()` в `ApiController.doCreateFromFolder` (`ApiController.kt:5511`) после этой фичи **сохраняется без изменений**. Он по-прежнему нужен для других аспектов пост-обработки (картинки `pictureAlbum`/`pictureAuthor`, восстановление из хранилища при отсутствии файлов на диске и т.д.), а дедупликация в нём уже корректно отрабатывает ситуацию «задание уже идёт из кортежа».
- **A-008**: Загрузка в локальное (`UPLOAD_TO_LOCAL_STORE`) и удалённое (`UPLOAD_TO_REMOTE_STORE`) хранилища использует один и тот же `bucketName = "karaoke"` — это константа `Song.storageBucketName` (`Song.kt:78`), уже используемая во всём караоке-пайплайне, менять её в этой фиче не требуется.
- **A-009**: Под «после задания на создание mp3» в запросе пользователя понимается **после завершения всех заданий создания mp3** в кортеже, а не после каждого из них (см. Q2 в Clarifications): сначала выполняются оба `FF_MP3_*` (acc + vocals), затем — все четыре `UPLOAD_*` (local+remote × acc+vocals). Это логически чистое разделение фаз «подготовка → загрузка»; порядок в одном lane обеспечивается приоритетами `-1` для `FF_MP3_*` и `-2` для `UPLOAD_*`. Точный порядок зафиксирован в FR-001 (шаги 1–6).
- **A-010**: Фича затрагивает только серверную часть (`karaoke-app`). Фронтенд (`webvue3`, `karaoke-public`) не меняется — оператор видит просто более длинный кортеж в UI списка процессов (`/api/utils/getprocesses`), без изменения текстов кнопок и диалогов.
