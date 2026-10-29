# Contract: argv, временные ресурсы и лейны кортежа

**Feature**: `674-kortege-execution` · **Тип**: контракт внешних процессов (argv/каталоги/контейнеры)
**Владелец**: `model/Song.kt` (построители), `Constants.kt` (корни), `tuple/TupleScope.kt` (подстановка),
`Utils.kt` (финализаторы)
**Связанные FR**: FR-004, FR-005, FR-006, FR-009

---

## 1. Токен scope

| Правило | Значение |
|---|---|
| Токен | `{scope}` — буквально, без экранирования |
| Где допустим | в любом элементе `process_args` (включая `runFunctionWithArgs` параметры) |
| Когда разворачивается | непосредственно перед созданием `ProcessBuilder`, после пересчёта CPU-лимитов (`refreshArgvCpuLimit`) |
| Во что | `scopeId = process_chain_id ?: id` (десятичное число) |
| В БД | хранится **шаблон** с `{scope}`; админка показывает шаблон |
| В логах | развёрнутый argv (реальный путь), как сейчас пишется в `PATH_TO_LOGS` |

Пример (DEMUCS2):

```text
mkdir -p /sm-karaoke/done2/<...>/hdemucs_mmi
chmod 777 /sm-karaoke/done2/<...>/hdemucs_mmi
mkdir -p /sm-karaoke/system/demucs-docker/tmp/{scope}
chmod 777 /sm-karaoke/system/demucs-docker/tmp/{scope}
cp <song>.flac /sm-karaoke/system/demucs-docker/tmp/{scope}/file.flac
docker run --rm -i --name=demucs-{scope} --gpus all ... \
    -v /sm-karaoke/system/demucs-docker/tmp/{scope}:/data/input \
    -v /sm-karaoke/system/demucs-docker/tmp/{scope}:/data/output \
    svoemestodev/demucs:latest ''./demucs2 -file /sm-karaoke/system/demucs-docker/tmp/{scope}/file.flac ...''
mv /sm-karaoke/system/demucs-docker/tmp/{scope}/file-accompaniment.flac <song>-accompaniment.flac
...
runFunctionWithArgs finalizeDemucs songId=<id> demucsType=DEMUCS2 threadId=<lane> scopeId={scope}
```

## 2. Каталоги и имена

| Ресурс | Шаблон | Кто создаёт | Кто удаляет |
|---|---|---|---|
| demucs tmp | `/sm-karaoke/system/demucs-docker/tmp/{scope}/` | первый шаг кортежа (`mkdir -p`) | финальный шаг `finalizeDemucs` (только свой), janitor |
| demucs вход | `.../{scope}/file.flac` | `cp` шаг кортежа | тот же финализатор |
| demucs выход | `.../{scope}/file-{accompaniment,vocals,drums,bass,other}.flac` | `docker run` | тот же финализатор |
| keybpm tmp | `/sm-karaoke/system/keybpmfinder-docker/tmp/{scope}/` | первый шаг кортежа | шаг `rm -rf .../{scope}` (только свой) |
| keybpm вход | `.../{scope}/file.flac` | `cp` | шаг `rm -rf` |
| keybpm выход | `.../{scope}/result_key_bpm_finder.json` | `docker run` | шаг `rm -rf` |
| sheetsage tmp | `<PATH_TO_TEMP_SHEETSAGE_FOLDER>/{scope}/` (новый корень) | первый шаг кортежа | финальный шаг кортежа |
| sheetsage вход | `.../{scope}/source.wav` | `ffmpeg` | финальный шаг |
| sheetsage выход | `.../{scope}/output/` (`output.pdf`, `output.midi`, `output.ly`, `beattimes`) | `sheetsage.sh -o output` | финальный шаг |
| stemjob tmp | `/sm-karaoke/system/stemjob-docker/tmp/{jobId}/` (без изменений) | кортеж StemJob | финальный шаг StemJob |

**Запрещено:** любой argv, удаляющий корень (`.../demucs-docker/tmp` целиком, `.../keybpmfinder-docker/tmp`,
`.../sheetsage`), и любой argv, читающий/удаляющий каталог чужого `scopeId`.

## 3. Имена контейнеров

| Тип | Имя | Примечание |
|---|---|---|
| `DEMUCS2`, `DEMUCS5`, retry-варианты | `demucs-{scope}` | было `demucs` (`Song.kt:2320,2356,2394,2428`) |
| `KEY_BPM_FROM_FILE` | `keybpmfinder-{scope}` | раньше без имени |
| `STEM_JOB_DEMUCS2/5` | `stemjob-{jobId}` | без изменений |
| `MELT_*` | генерируемое `docker compose run` | без изменений |

`killRunningDockerContainers` (`Utils.kt:4236-4297`) обязан убивать по префиксу
(`name=^demucs-`, `name=^keybpmfinder-`) и (переходно) по старым точным именам (`name=^demucs$`).

## 4. Коды возврата и «успех»

| Код | Значение | Действие |
|---|---|---|
| `0` | шаг выполнен | следующий шаг кортежа |
| `≠ 0` | шаг провален | шаг `ERROR`, весь кортеж `ERROR`, причина = хвост вывода (stderr слит в stdout, `redirectErrorStream(true)`) |
| исключение `ProcessBuilder`/таймаут | шаг провален | то же |
| `runFunctionWithArgs` вернул `false` | шаг провален (данные не подтверждены) | то же |
| `runFunctionWithArgs` вернул `true` | шаг выполнен | следующий шаг |
| особый случай SHEETSAGE `NotImplementedError` | не ошибка кортежа | постановка `SHEETSAGE2` (сохраняется, `KaraokeProcessWorker.kt:308-336`) |

**Запрещено** ставить `DONE` по факту «процесс завершился» без проверки кода возврата
(`ProcessExitWiringTest` — статический инвариант).

## 5. Уборка (владелец + janitor)

**Владелец.** Финальный шаг кортежа удаляет `deleteRecursively(<scopeDir>)` (или эквивалент для
keybpm/sheetsage) и **никогда** — корень. Уборка не является отдельным шагом «на всякий случай»:
если кортеж упал раньше, каталог остаётся и подбирается janitor'ом.

**Janitor** (`TupleJanitor.sweep()`):

| Параметр | Значение |
|---|---|
| Корни | `PATH_TO_TEMP_DEMUCS_FOLDER`, `PATH_TO_TEMP_KEYBPMFINDER_FOLDER`, `PATH_TO_TEMP_SHEETSAGE_FOLDER` |
| Кандидат | подкаталог, имя которого — целое число `scope` |
| «Живой» | есть строка `(id = scope OR process_chain_id = scope)` со статусом `WAITING/WORKING/CREATING` и `process_deleted_at IS NULL` |
| Удаляется | кандидат, не являющийся живым, и `now − mtime > queue.tuple.temp_ttl_hours` (default 24) |
| Не трогается | корень, файлы в корне, каталоги нечисловых имён, любые каталоги живых кортежей |
| Логи | `TupleJanitor: удалён <path> (возраст <h> ч)`, `TupleJanitor: пропущен живой <path>` |
| Расписание | при старте воркера + каждые `queue.tuple.janitor_interval_minutes` (default 60) |
| Ошибка | лог и продолжение (fail-open, как `cleanupOldLogs` — `KaraokeProcessWorker.kt:1478-1530`) |

## 6. Лейны

| Лейн | Константа | Кто ставит |
|---|---|---|
| `0` | `THREAD_LANE_HEAVY_RENDER` | MELT_*, RENDER_MP4_*, SHEETSAGE*, DEMUCS* (ручной запуск), FF_720, SYMLINK, SMARTCOPY |
| `1` | `THREAD_LANE_HEALTH_REPORT` | DEMUCS2/5 (импорт, HR), FF_MP3_*, UPLOAD_*, KEY_BPM (HR/кнопка) |
| `2` | `THREAD_LANE_STEM_JOBS` | StemJob, KEY_BPM (импорт) |
| `-1` | `THREAD_LANE_LIGHT_BACKGROUND` | smartcopyperodbyday |
| `-2` | `THREAD_LANE_REMOTE_STORE_UPLOAD` | (создателей нет) |

Контракт кортежа не меняет распределение по лейнам: `threadId` копируется всем шагам при создании
(`separate`, `KaraokeProcess.kt:2324`), а `scopeId` не зависит от лейна.
