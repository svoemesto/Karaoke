# Contract: process-context для новых вызовов `KaraokeProcess.createProcess`

**Дата**: 2026-08-31 | **Спека**: [spec.md](spec.md) | **План**: [plan.md](plan.md) | **Research**: [research.md](research.md)

> **Замечание.** Эта фича не вводит новых публичных API (HTTP-эндпоинтов, RPC, message-форматов). Все изменения — внутренние вызовы `KaraokeProcess.createProcess(...)` в `Song.createFromPath()`. Тем не менее, для снижения риска рассинхронизации с существующими вызывающими `KaraokeProcess.createProcess` (например, `HealthReport.actions*`), формализуем контракт `context`-параметра, передаваемого в новые вызовы.

## Область контракта

**Метод**: `com.svoemesto.karaokeapp.KaraokeProcess.createProcess(...)` — `KaraokeProcess.kt:985`.

**Сигнатура**:
```kotlin
fun createProcess(
    song: Song,
    action: KaraokeProcessTypes,
    doWait: Boolean = false,
    prior: Int = 1,
    threadId: Int,
    context: Map<String, Any> = emptyMap(),
): Long
```

**Изменения в сигнатуре**: нет (метод не меняется).

**Изменения в `context`-формате** для новых вызовов: нет (формат уже определён, см. ниже).

## Контракт `context` для `UPLOAD_TO_LOCAL_STORE` и `UPLOAD_TO_REMOTE_STORE`

Источник: `KaraokeProcess.kt:1797-1833` + `HealthReport.kt:617-630` (для local) и `911-921` (для remote).

### Схема `context`

| Ключ | Тип | Обязательность | Описание |
|------|-----|----------------|----------|
| `pathToFile` | `String` | **Обязателен** | Абсолютный путь к файлу на диске, который нужно загрузить |
| `karaokeFileType` | `String` | **Обязателен** | Имя enum-значения `KaraokeFileType` (например, `"MP3_ACCOMPANIMENT"`, `"MP3_VOCAL"`). Используется как часть ключа дедупликации (`process_args`) и для определения способа загрузки |
| `storageFileName` | `String` | Опционален | Имя файла в MinIO (если отличается от дефолтного `${storageBucketName}/${basename(pathToFile)}`) |
| `bucketName` | `String` | Опционален | Имя бакета в MinIO (если отличается от дефолтного; для текущей архитектуры всегда `"karaoke"`) |
| `deleteAfterUpload` | `String` | Опционален | `"true"`, если нужно удалить файл с диска после успешной загрузки |

### Значения для новых вызовов в этой фиче

**`UPLOAD_TO_LOCAL_STORE` для `MP3_ACCOMPANIMENT`**:
```kotlin
context = mapOf(
    "pathToFile" to song.accompanimentNameMp3,        // "$pathToResultedModel/$fileName-accompaniment.mp3"
    "karaokeFileType" to "MP3_ACCOMPANIMENT",
    "storageFileName" to "${song.storageFileName}.accompaniment.mp3",  // "$author/$year - $album/$fileName.accompaniment.mp3"
    "bucketName" to song.storageBucketName,            // "karaoke"
)
```

**`UPLOAD_TO_LOCAL_STORE` для `MP3_VOCAL`**:
```kotlin
context = mapOf(
    "pathToFile" to song.vocalsNameMp3,                // "$pathToResultedModel/$fileName-vocals.mp3"
    "karaokeFileType" to "MP3_VOCAL",
    "storageFileName" to "${song.storageFileName}.vocals.mp3",
    "bucketName" to song.storageBucketName,
)
```

**`UPLOAD_TO_REMOTE_STORE` для `MP3_ACCOMPANIMENT`**: тот же `context`, что и для local (но передаётся в вызов `UPLOAD_TO_REMOTE_STORE`, который внутри дёргает `storageApiClient`).

**`UPLOAD_TO_REMOTE_STORE` для `MP3_VOCAL`**: то же.

## Контракт для `FF_MP3_ACCOMPANIMENT` и `FF_MP3_VOCAL`

### Сигнатура вызова

```kotlin
KaraokeProcess.createProcess(
    song = song,
    action = KaraokeProcessTypes.FF_MP3_ACCOMPANIMENT,  // или FF_MP3_VOCAL
    doWait = true,
    prior = -1,
    threadId = 1,
    // context НЕ передаётся (или пустой) — для FF_MP3_* ffmpeg-команда строится внутри KaraokeProcess.kt:1692-1732
)
```

### Схема `context` (для будущих расширений)

| Ключ | Тип | Описание |
|------|-----|----------|
| (отсутствуют в текущей реализации) | — | FF_MP3_* не используют `context`; пути к файлам берутся из свойств `song.accompanimentNameFlac`/`song.vocalsNameFlac`/`song.accompanimentNameMp3`/`song.vocalsNameMp3` напрямую (`KaraokeProcess.kt:1696, 1717`) |

**Изменения в `context` для `FF_MP3_*`**: нет. Передаём пустой `context` (или опускаем параметр — default `emptyMap()`).

## Контракт дедупликации

Источник: `KaraokeProcess.kt:995-1018`.

### Ключ дедупликации

```kotlin
mutableMapOf(
    "song_id" to song.id.toString(),
    "process_type" to action.name,
    "thread_id" to threadId.toString(),
)
// + для UPLOAD_*:
(context["karaokeFileType"] as? String)?.let {
    "process_args" to "karaokeFileType=$it"
}
```

### Поведение при дублировании

```text
existedProcesses = loadList(key, song.database)
for each existedProcess:
    if status != WORKING:
        delete(existedProcess.id)        ← удалить старый
    else:
        wasWorking = true
if wasWorking:
    return 0                              ← не создавать (уже работает)
else:
    create new KaraokeProcess(...)
```

**Инвариант**: для одной песни не существует более одного `WORKING`-процесса с одним и тем же ключом `(song_id, process_type, thread_id[, process_args])`. Это обеспечивает идемпотентность повторных вызовов `createProcess`.

## Совместимость

### Прямая совместимость (forward-compatibility)

| Контракт | Статус | Комментарий |
|----------|--------|-------------|
| Сигнатура `createProcess(...)` | ✅ Сохранена | Никаких новых параметров; `context` остаётся `Map<String, Any>` |
| `KaraokeProcessTypes` | ✅ Сохранена | Все нужные enum-значения уже существуют |
| Схема `tbl_processes` | ✅ Сохранена | Никаких миграций |
| Формат `process_args` | ✅ Сохранена | Новые вызовы передают `karaokeFileType=MP3_*` — существующий HealthReport уже умеет парсить |

### Обратная совместимость (backward-compatibility)

| Потребитель | Статус | Комментарий |
|-------------|--------|-------------|
| `HealthReport.startRepairAll` | ✅ Совместим | Дедуплицирует через `inProgress`-проверку — уже идущие задания кортежа не дублируются |
| `HealthReport.onRepairProcessFinished` | ✅ Совместим | Новые записи в `tbl_processes` обрабатываются так же, как и старые (тип процесса в `HR_REPAIR_PROCESS_TYPES` уже включает `UPLOAD_*` и `FF_MP3_*`) |
| `KaraokeProcessWorker` | ✅ Совместим | Новые записи в `tbl_processes` обрабатываются тем же воркером |
| SSE-канал (`/api/utils/getprocesses`) | ✅ Совместим | Никаких изменений в API — клиент видит просто больше записей в `tbl_processes` для песни |
| UI `webvue3` | ✅ Совместим | Без изменений в коде; новые записи видны в существующем списке процессов |

### Что НЕ входит в контракт (non-goals)

- **НЕ** меняется порядок/набор полей в `Song`.
- **НЕ** меняется `bucketName` — остаётся `"karaoke"`.
- **НЕ** вводится новый `KaraokeFileType` или `KaraokeProcessTypes`.
- **НЕ** вводится новый `threadId` lane (всё в `threadId = 1`).
- **НЕ** меняется `KaraokeProcessWorker` или `KaraokeProcessThread`.

## Что должен проверить реализатор

- [ ] Контекст для каждого из 6 новых вызовов соответствует схеме выше (проверка grep'ом: должно быть ровно 4 ключа для `UPLOAD_*` и 0 ключей для `FF_MP3_*`).
- [ ] Все 6 новых вызовов используют `threadId = 1` (для UPLOAD и FF_MP3).
- [ ] `prior = -1` для `FF_MP3_*`, `prior = -2` для `UPLOAD_*` (порядок важен).
- [ ] Никаких других изменений в `Song.createFromPath` — только добавление новых `KaraokeProcess.createProcess` вызовов после `DEMUCS2` и удаление закомментированных блоков `FF_MP3_KAR`/`FF_MP3_LYR` (см. research.md R-004).
