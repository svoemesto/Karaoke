# Data Model: 282 — Кортеж заданий при «Добавить файлы из папки»

**Дата**: 2026-08-31 | **Спека**: [spec.md](spec.md) | **План**: [plan.md](plan.md) | **Research**: [research.md](research.md)

> **Замечание.** Эта фича НЕ вводит новых таблиц, колонок, индексов или ограничений. Все изменения — только в данных (новые записи в существующей `tbl_processes`). Сущности ниже — это переиспользуемые доменные модели проекта; их контракты уже зафиксированы в `archive/docs/features/async-process-queue.md` (см. `@see` в KDoc `KaraokeProcess` и `Song`).

## Сущности

### 1. `Song` (без изменений схемы)

**Таблица**: `tbl_songs` (существующая).

**Изменяемые поля**: нет.

**Используемые геттеры** (переиспользуются в новых вызовах `KaraokeProcess.createProcess`):

| Геттер | Тип | Описание | Источник |
|--------|-----|----------|----------|
| `accompanimentNameMp3` | `String` | Путь к mp3-минусовке: `"$pathToResultedModel/$fileName-accompaniment.mp3"` | `Song.kt:1743` |
| `vocalsNameMp3` | `String` | Путь к mp3-голосу: `"$pathToResultedModel/$fileName-vocals.mp3"` | `Song.kt:1746` |
| `storageBucketName` | `String` | Имя бакета MinIO: `"karaoke"` (константа) | `Song.kt:78` |
| `storageFileName` | `String` | Префикс имени файла в MinIO: `"$author/$year - $album/$fileName"` | `Song.kt:79` |

**Lifecycle**: создаётся в `Song.createFromPath()` (строка 8157), `song.saveToDb()` сохраняет запись в `tbl_songs`. После этого начинается формирование кортежа заданий.

**Отношения**:
- `1 Song → N KaraokeProcess` (через `song_id` в `tbl_processes`).
- `1 Song → 1 Album` (через `album_id`, устанавливается через `Album.findOrCreateForSongImportWithAutoCover` — см. спеку 238).
- `1 Song → 1 Author` (через поле `song_author`).

### 2. `KaraokeProcess` (без изменений схемы)

**Таблица**: `tbl_processes` (существующая).

**Изменяемые поля**: нет (только INSERT новых строк).

**Используемые поля в новых записях**:

| Поле | Тип | Значение для новых заданий | Источник |
|------|-----|---------------------------|----------|
| `song_id` | `Long` | `song.id` | `Song.createFromPath` |
| `process_type` | `String` (enum name) | `DEMUCS2`, `FF_MP3_ACCOMPANIMENT`, `FF_MP3_VOCAL`, `UPLOAD_TO_LOCAL_STORE`, `UPLOAD_TO_REMOTE_STORE` | `KaraokeProcessTypes` |
| `thread_id` | `Int` | `1` (для DEMUCS2, FF_MP3_*, UPLOAD_*); `2` (для KEY_BPM_FROM_FILE — без изменений) | `KaraokeProcess.THREAD_LANE_HEALTH_REPORT`, `THREAD_LANE_STEM_JOBS` |
| `process_priority` | `Int` | `-1` (для DEMUCS2, FF_MP3_*); `-2` (для UPLOAD_*); `-1` (для KEY_BPM_FROM_FILE — без изменений) | `KaraokeProcess.createProcess.prior` |
| `process_status` | `String` (enum name) | `WAITING` (т.к. `doWait = true`) | `KaraokeProcessStatuses.WAITING` |
| `process_args` | `String` | Для UPLOAD_*: `"karaokeFileType=MP3_ACCOMPANIMENT\|MP3_VOCAL"`; для остальных — пусто | см. `KaraokeProcess.kt:1004-1006` |
| `process_order` | `Int` | `-1` (default; воркер пересортирует при WAITING-выборке) | `KaraokeProcess.kt:1024` |

**Lifecycle**:
- `CREATE` — через `KaraokeProcess.createProcess(...)` в `Song.createFromPath()` (синхронно).
- `WAITING → WORKING → DONE` (или `ERROR`) — через `KaraokeProcessWorker.run()`.
- Дубликаты (повторный вызов `createProcess` для того же `song_id`+`process_type`+`thread_id`[+`process_args`]) — удаляются и пересоздаются (см. `KaraokeProcess.kt:1011-1017`); если процесс в `WORKING` — возвращается `0` без создания.

**Отношения**:
- `N KaraokeProcess → 1 Song` (через `song_id`).

### 3. `KaraokeFileType` (без изменений enum)

**Enum**: `com.svoemesto.karaokeapp.KaraokeFileType` (существующий).

**Используемые значения** (передаются в `context["karaokeFileType"]` для `UPLOAD_*`):

| Значение | extention | suffix | locations |
|----------|-----------|--------|-----------|
| `MP3_ACCOMPANIMENT` | `mp3` | `.accompaniment` | `LOCAL_FILESYSTEM`, `LOCAL_STORAGE`, `REMOTE_STORAGE` |
| `MP3_VOCAL` | `mp3` | `.vocals` | `LOCAL_FILESYSTEM`, `LOCAL_STORAGE`, `REMOTE_STORAGE` |

Источник: `KaraokeFileType.kt:59-95`.

**Lifecycle**: N/A (enum).

### 4. `KaraokeProcessTypes` (без изменений enum)

**Enum**: `com.svoemesto.karaokeapp.KaraokeProcessTypes` (существующий).

**Используемые значения** (передаются в `action` параметр `KaraokeProcess.createProcess`):

| Значение | Описание | Источник |
|----------|----------|----------|
| `FF_MP3_ACCOMPANIMENT` | Задание на создание mp3-минусовки через ffmpeg | `KaraokeProcessTypes.kt:27` |
| `FF_MP3_VOCAL` | Задание на создание mp3-голоса через ffmpeg | `KaraokeProcessTypes.kt:28` |
| `UPLOAD_TO_LOCAL_STORE` | Задание на загрузку файла в локальный MinIO | существующий |
| `UPLOAD_TO_REMOTE_STORE` | Задание на загрузку файла в удалённый MinIO | существующий |

**Lifecycle**: N/A (enum).

## Отношения и инварианты

### Кортеж заданий на одну новую песню (после фикса)

```text
Song.createFromPath(song) {
  song.saveToDb()                     ← создаёт запись в tbl_songs
  KaraokeProcess.createProcess(       ← уже было
    song, KEY_BPM_FROM_FILE,
    prior=-1, threadId=2              ← отдельный lane, не в кортеже
  )
  KaraokeProcess.createProcess(       ← уже было (первый шаг кортежа)
    song, DEMUCS2,
    prior=-1, threadId=1
  )
  // ==== НАЧАЛО НОВОГО БЛОКА (эта фича) ====
  KaraokeProcess.createProcess(       ← НОВОЕ
    song, FF_MP3_ACCOMPANIMENT,
    prior=-1, threadId=1
  )
  KaraokeProcess.createProcess(       ← НОВОЕ
    song, FF_MP3_VOCAL,
    prior=-1, threadId=1
  )
  KaraokeProcess.createProcess(       ← НОВОЕ
    song, UPLOAD_TO_LOCAL_STORE,
    prior=-2, threadId=1,
    context={
      pathToFile=song.accompanimentNameMp3,
      karaokeFileType="MP3_ACCOMPANIMENT",
      storageFileName="${song.storageFileName}.accompaniment.mp3",
      bucketName=song.storageBucketName
    }
  )
  KaraokeProcess.createProcess(       ← НОВОЕ
    song, UPLOAD_TO_LOCAL_STORE,
    prior=-2, threadId=1,
    context={
      pathToFile=song.vocalsNameMp3,
      karaokeFileType="MP3_VOCAL",
      storageFileName="${song.storageFileName}.vocals.mp3",
      bucketName=song.storageBucketName
    }
  )
  KaraokeProcess.createProcess(       ← НОВОЕ
    song, UPLOAD_TO_REMOTE_STORE,
    prior=-2, threadId=1,
    context={ ... то же что для LOCAL, но для remote store ... }
  )
  KaraokeProcess.createProcess(       ← НОВОЕ
    song, UPLOAD_TO_REMOTE_STORE,
    prior=-2, threadId=1,
    context={ ... то же что для LOCAL, но для remote store ... }
  )
  // ==== КОНЕЦ НОВОГО БЛОКА ====
}
```

### Гарантии порядка

- В пределах одного `thread_id` KaraokeProcessWorker выбирает задания по `ORDER BY process_priority, process_order, id` (`KaraokeProcessWorker.kt:806`).
- `prior=-1` (`DEMUCS2`, `FF_MP3_*`) — выполняются раньше, чем `prior=-2` (`UPLOAD_*`).
- В пределах одного `prior` порядок между `DEMUCS2`/`FF_MP3_ACCOMPANIMENT`/`FF_MP3_VOCAL` **не гарантирован строго** — но т.к. `FF_MP3_*` требуют результатов `DEMUCS2` (создают mp3 из flac-стемов, полученных Demucs), они **упадут**, если Demucs ещё не завершился. Это естественная деградация (HealthReport покажет проблему), не требует специальной обработки.
- После завершения всех `FF_MP3_*` воркер начнёт обрабатывать `UPLOAD_*` (т.к. у них `prior=-2 < -1`).

### Дедупликация

| Тип процесса | Ключ дедупликации | Поведение при дублировании |
|--------------|-------------------|----------------------------|
| `DEMUCS2` | `(song_id, process_type, thread_id)` | Удалить старый (если не WORKING) + создать новый; если WORKING — пропустить |
| `FF_MP3_ACCOMPANIMENT` | `(song_id, process_type, thread_id)` | То же |
| `FF_MP3_VOCAL` | `(song_id, process_type, thread_id)` | То же |
| `UPLOAD_TO_LOCAL_STORE` | `(song_id, process_type, thread_id, process_args)` | То же; `process_args = "karaokeFileType=MP3_ACCOMPANIMENT"` или `"karaokeFileType=MP3_VOCAL"` разделяет задания для разных типов |
| `UPLOAD_TO_REMOTE_STORE` | То же | То же |

Источник: `KaraokeProcess.kt:995-1018`.

## Объём данных

- На одну новую песню: +6 строк в `tbl_processes` (5 уже было: 1 KEY_BPM + 1 DEMUCS; теперь 11: 1 KEY_BPM + 1 DEMUCS + 2 FF_MP3 + 4 UPLOAD).
- На 10 новых песен (типичная папка): +60 строк.
- На 1000 песен (edge case из LiveDoc 082): +6000 строк.
- Размер строки `tbl_processes` оценивается в ~1 KB (с `process_args`, `name`, и т.д.) — итого ~6 MB на 1000 песен, что пренебрежимо мало по сравнению с уже существующим объёмом (тысячи записей).

## Миграции БД

**Не требуются**. Никаких изменений схемы `tbl_songs`, `tbl_processes`, `tbl_albums`, `tbl_authors` и связанных таблиц.

## Валидация (для реализатора)

- [ ] `git diff --stat` показывает изменения только в `Song.kt` (+ строки).
- [ ] Никаких новых файлов в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/` (или минимальный cleanup — см. R-004).
- [ ] `./gradlew :karaoke-app:compileKotlin --parallel` (см. AGENTS.md § «Обязательная проверка после ЛЮБОГО изменения кода») — успешно.
- [ ] `./gradlew :karaoke-web:ktlintCheck` — без новых нарушений.
- [ ] Никаких изменений в `webvue3/`, `karaoke-public/`, миграциях SQL, `Karaoke.properties`.
