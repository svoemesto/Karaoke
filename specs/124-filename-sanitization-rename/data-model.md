# Data Model: Санитайзинг имён файлов при импорте и переименование при редактировании

**Feature**: [spec.md](./spec.md) | **Date**: 2026-08-03

Фича **не добавляет и не меняет** схему БД (см. `research.md` §7). Ниже —
концептуальная модель сущностей и их полей/производных путей, релевантных для
реализации, построенная на основе уже существующих `Song` (`tbl_songs`) и
файловых конвенций проекта.

## Song (существующая сущность, без изменений схемы)

| Поле | Тип | Значение для этой фичи |
|---|---|---|
| `fileName` | `String` (`tbl_songs.file_name`) | Санитайзированное имя (без `!`,`?`,`:`,`*`,`"`,`/`,`\`,`'`→`''`) — основа для всех производных путей и ключей хранилища. Уникально в пределах `rootFolder`. |
| `rootFolder` | `String` (`tbl_songs.root_folder`) | Папка на диске (альбомная папка импорта). Область проверки уникальности `fileName` (клэрификация Q3). |
| `fields[SongField.NAME]` | `String` (JSON-поле `tbl_songs.fields`) | Отображаемое название — **не** санитайзируется, сохраняет оригинальные символы источника (FR-002). |

**Инвариант (новый, вводимый этой фичей)**: `fileName` всегда результат
`rawFileName.rightFileName()`; никакой код-путь не должен присваивать
`song.fileName` не пропущенное через `rightFileName()` значение (ни при
импорте, ни при `songs2Update`).

**Уникальность**: `(rootFolder, fileName)` — логически уникальная пара (не
enforced на уровне БД constraint в рамках этой фичи — проверяется в коде перед
сохранением, см. `contracts/song-rename-contract.md`).

## Производные файлы песни (шаблонные пути, не отдельные таблицы)

Все — функции от `(rootFolder, fileName)`, физически на диске
admin-машины и/или в бакетах MinIO. При переименовании (User Story 2) каждый
существующий элемент этого списка — кандидат на cascade-rename:

| Артефакт | Путь/ключ (шаблон) | Существует всегда? |
|---|---|---|
| Основной аудиофайл (FLAC) | `$rootFolder/$fileName.flac` | Да, после импорта |
| Аудиостемы (по одному на каждый выделяемый инструмент/голос — vocals/music/drums/bass/etc.) | `$rootFolder/<stem-подпапка>/$fileName-<stem>.<ext>` (точный шаблон — см. `StemJobProcessing.kt`/существующий код стемов) | Нет — только после успешного Demucs-джоба |
| Файл проекта видеоредактора (`.kdenlive`) | `$rootFolder/$fileName.kdenlive` (шаблон `SongOutputFile.PROJECT`) | Нет — только после генерации проекта |
| Файл субтитров | `$rootFolder/$fileName.kdenlive.srt` (шаблон `SongOutputFile.SUBTITLE`) | Нет — только после генерации |
| Файл метаданных (sidecar) | `$rootFolder/$fileName.song`/`.settings` (существующий формат, уже переписывается при сохранении в SongEdit) | Да |
| Объект в локальном хранилище | ключ = `storageFileName` (сегодня — не всегда санитайзирован, известный пред-существующий гэп, см. project-memory) | Нет — только после загрузки |
| Объект в удалённом (MinIO) хранилище | тот же ключ, отдельный бакет/клиент (`StorageApiClient`) | Нет — только после загрузки |

**Обработка отсутствующих артефактов (FR-009)**: перед переименованием каждого
элемента — проверка существования (`File.exists()` / `storageService.fileExists()`
/ `storageApiClient.fileExists()`); отсутствующий артефакт молча пропускается,
не считается ошибкой.

## RenameAttempt (эфемерное понятие, не персистится)

Не отдельная сущность/таблица — представляет собой последовательность
шагов, выполняемых синхронно в теле обработчика `songs2Update` при изменении
`fileName`:

```
RenameAttempt(oldFileName, newFileNameSanitized, rootFolder):
  1. validate: newFileNameSanitized != "" AND
               no other Song with (rootFolder, newFileNameSanitized) exists
     → если нарушено: reject, HTTP-ответ с понятным сообщением (FR-011), STOP
  2. guard: no KaraokeProcess for this song with status in (WAITING, WORKING)
     → если нарушено: reject, сообщение "дождитесь завершения обработки" (FR-013), STOP
  3. for each artifact in [audio, stems..., kdenlive, subtitle, sidecar]:
       if exists(artifact under oldFileName): rename to newFileNameSanitized
       (ошибка на этом шаге → STOP, не откатывать уже переименованное — FR-010)
  4. for each storage in [local, remote]:
       if object exists under oldFileName key: upload under new key, delete old key
       (ошибка на этом шаге → STOP, не откатывать уже переименованное — FR-010)
  5. song.fileName = newFileNameSanitized; song.saveToDb(); song.saveToFile()
```

Результат незавершённого `RenameAttempt` **не хранится явно** — обнаруживается
постфактум существующим механизмом `HealthReport` (ожидаемый артефакт под
`song.fileName` отсутствует → ERROR/FATAL_ERROR с `solutionActions`), что и
служит «явной пометкой ошибки» из FR-010 без новой схемы БД.

## HealthReport (существующая сущность, переиспользуется как есть)

| Поле | Роль в этой фиче |
|---|---|
| `healthReportStatus` (`HealthReportStatus`: OK/WARNING/IN_PROGRESS/ERROR/FATAL_ERROR) | ERROR/FATAL_ERROR после неполного переименования — видно пользователю в `HealthReportTable.vue` внутри SongEdit. |
| `solutionActions: List<() -> Unit>` | Уже существующий механизм ре-запуска недостающего шага (используется как «повтор операции» из FR-010, без новой кнопки "retry rename"). |
| `problemText` / `solutionText` | Свободный текст, отображаемый пользователю — переиспользуется существующая инфраструктура сообщений, отдельного UI для rename-ошибок не требуется. |
