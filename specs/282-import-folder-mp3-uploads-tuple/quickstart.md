# Quickstart: 282 — Кортеж заданий при «Добавить файлы из папки»

**Дата**: 2026-08-31 | **Спека**: [spec.md](spec.md) | **План**: [plan.md](plan.md) | **Research**: [research.md](research.md) | **Data Model**: [data-model.md](data-model.md) | **Contracts**: [contracts/process-context.md](contracts/process-context.md)

> **Замечание.** Этот документ — сценарий end-to-end проверки фичи **ручным пользователем** на admin-машине. В CI тестов нет (см. `constitution.md` § «Тесты»). Все шаги воспроизводимы; ожидаемые результаты соответствуют Success Criteria (SC-001..SC-007) из спеки.

## Предусловия

### Окружение

- **Машина**: admin-машина (где развёрнут `karaoke-app` локально; см. `AGENTS.md` § «Деплой-окружения»).
- **Доступ**: SSH/локальный доступ к admin-машине; права на запись в `~/Karaoke` и `/sm-karaoke/system`.
- **Состояние БД**: локальная Postgres (`karaoke-local`) — пустая или с известным baseline'ом; НЕ на проде.
- **MinIO**: локальный и удалённый MinIO доступны; бакет `karaoke` существует; объекты для тестовых песен отсутствуют (для чистоты эксперимента).

### Тестовая папка

Подготовить папку:

```bash
TEST_FOLDER="/tmp/karaoke-test-282/2026 - TestAlbum"
mkdir -p "$TEST_FOLDER"
# Положить 2-3 FLAC-файла по шаблону:
# 2026 (01) [TestAuthor] - TestSong1.flac
# 2026 (02) [TestAuthor] - TestSong2.flac
# 2026 (03) [TestAuthor] - TestSong3.flac
```

(Реальные FLAC-файлы можно взять из любого существующего тестового набора; допускается использовать `dd if=/dev/urandom of=... bs=1M count=1` для синтетических, если Demucs готов работать с произвольным аудио — но реальные FLAC предпочтительнее для проверки Demucs.)

### Билд

```bash
cd ~/Karaoke
./gradlew :karaoke-app:bootJar --parallel
```

Ожидаемый результат: BUILD SUCCESSFUL, jar в `karaoke-app/build/libs/`.

### Перезапуск `karaoke-app`

Только если машина — `dev-pc`/`dev` (см. `constitution.md` § «Ограничения и доступы агента», исключение из п. 1). Иначе — пользователь перезапускает контейнер вручную.

```bash
cd ~/Karaoke/deploy && bash do.sh build_start_karaoke-app
```

(Или эквивалентная команда для конкретной машины.)

## Сценарий проверки

### Шаг 1. Создать песню через импорт папки

Действие: на главной странице админки `webvue3` нажать «Добавить файлы из папки», ввести путь `$TEST_FOLDER`, подтвердить.

Ожидаемый результат:
- В UI отображается SSE-уведомление «Добавление файлов из папки ... Добавлено файлов из папки ...: 3 (пропущено: 0)».
- 3 новые записи в `tbl_songs` с правильно заполненными `song_name`/`song_author`/`song_year`/`song_album`.

### Шаг 2. Проверить `tbl_processes` (SC-001)

SQL:
```sql
SELECT
    s.song_name,
    s.song_author,
    p.process_type,
    p.thread_id,
    p.process_priority,
    p.process_status,
    p.process_args
FROM tbl_processes p
JOIN tbl_songs s ON s.id = p.song_id
WHERE s.song_author = 'TestAuthor'
  AND s.song_album = 'TestAlbum'
ORDER BY s.id, p.process_priority, p.id;
```

Ожидаемый результат (для каждой из 3 песен):

| process_type | thread_id | process_priority |
|--------------|-----------|------------------|
| `KEY_BPM_FROM_FILE` | 2 | -1 |
| `DEMUCS2` | 1 | -1 |
| `FF_MP3_ACCOMPANIMENT` | 1 | -1 |
| `FF_MP3_VOCAL` | 1 | -1 |
| `UPLOAD_TO_LOCAL_STORE` | 1 | -2 (×2 для acc+vocal) |
| `UPLOAD_TO_REMOTE_STORE` | 1 | -2 (×2 для acc+vocal) |

**Всего на песню**: 11 строк (1 KEY_BPM + 1 DEMUCS + 2 FF_MP3 + 4 UPLOAD). На 3 песни — 33 строки.

Проверка `process_args` для `UPLOAD_*`:
```sql
SELECT process_args FROM tbl_processes WHERE process_type IN ('UPLOAD_TO_LOCAL_STORE', 'UPLOAD_TO_REMOTE_STORE') AND song_id IN (SELECT id FROM tbl_songs WHERE song_author='TestAuthor');
```

Ожидаемый результат: значения `process_args` либо `karaokeFileType=MP3_ACCOMPANIMENT`, либо `karaokeFileType=MP3_VOCAL` (для каждой песни — по 4 записи, по 2 каждого типа).

### Шаг 3. Дождаться завершения кортежа

В UI или через SQL отслеживать `process_status`:

```sql
SELECT process_type, process_status, COUNT(*) as cnt
FROM tbl_processes
WHERE song_id IN (SELECT id FROM tbl_songs WHERE song_author='TestAuthor')
  AND process_type IN ('DEMUCS2', 'FF_MP3_ACCOMPANIMENT', 'FF_MP3_VOCAL',
                       'UPLOAD_TO_LOCAL_STORE', 'UPLOAD_TO_REMOTE_STORE')
GROUP BY process_type, process_status;
```

Ожидаемый результат после завершения (время зависит от CPU: Demucs ~1-5 мин на песню, FF_MP3 ~5-10 сек, UPLOAD ~2-10 сек):

| process_type | process_status | cnt |
|--------------|----------------|-----|
| DEMUCS2 | DONE | 3 |
| FF_MP3_ACCOMPANIMENT | DONE | 3 |
| FF_MP3_VOCAL | DONE | 3 |
| UPLOAD_TO_LOCAL_STORE | DONE | 6 |
| UPLOAD_TO_REMOTE_STORE | DONE | 6 |

### Шаг 4. Проверить файлы на диске (SC-002, часть 1)

```bash
ls -la "$TEST_FOLDER/TestSong1"*  # ожидаем .flac, -accompaniment.flac, -vocals.flac, -accompaniment.mp3, -vocals.mp3
```

Ожидаемый результат: для каждой песни существуют файлы:
- `<fileName>.flac` (исходник)
- `<fileName>-accompaniment.flac` (результат Demucs)
- `<fileName>-vocals.flac` (результат Demucs)
- `<fileName>-accompaniment.mp3` (результат FF_MP3_ACCOMPANIMENT)
- `<fileName>-vocals.mp3` (результат FF_MP3_VOCAL)

### Шаг 5. Проверить локальный MinIO (SC-002, часть 2)

Использовать `mc` (MinIO Client) или прямой HTTP:

```bash
# Имена в MinIO: $storageFileName = "TestAuthor/2026 - TestAlbum/TestSongN"
# Для acc: TestAuthor/2026 - TestAlbum/TestSongN.accompaniment.mp3
# Для vocal: TestAuthor/2026 - TestAlbum/TestSongN.vocals.mp3
mc ls local/karaoke/TestAuthor/2026\ -\ TestAlbum/
```

Или через API:

```bash
curl -I "http://localhost:9000/karaoke/TestAuthor/2026%20-%20TestAlbum/TestSong1.accompaniment.mp3"
```

Ожидаемый результат: HTTP 200 OK; `Content-Length` > 0.

### Шаг 6. Проверить удалённый MinIO (SC-002, часть 3)

Аналогично шагу 5, но с endpoint удалённого MinIO (см. `deploy/.env` или `Karaoke.properties` для URL):

```bash
curl -I "https://<REMOTE_MINIO_HOST>/karaoke/TestAuthor/2026%20-%20TestAlbum/TestSong1.vocals.mp3"
```

Ожидаемый результат: HTTP 200 OK; `Content-Length` > 0.

### Шаг 7. Проверить UI (SC-002, часть 4)

Открыть карточку одной из песен в админке. Проверить, что:
- `stemAccompanimentReady` / `stemVocalReady` (или эквивалентный признак готовности) = `true`.
- В секции «Аудио» видны ссылки на скачивание/прослушивание mp3 (или хотя бы кнопки активны).

### Шаг 8. Проверить дедупликацию (SC-003)

**Действие**: запустить «Добавить файлы из папки» повторно по той же `$TEST_FOLDER`.

Ожидаемый результат:
- SSE-уведомление: «Добавлено файлов из папки ...: 0 (пропущено: 3)» — все треки уже в БД.
- В `tbl_processes` НЕ появилось новых строк для этих песен (`loadListFromDb` по `file_name`+`root_folder` пропускает уже существующие).
- Кортеж НЕ пересоздан заново.

### Шаг 9. Проверить параллельность (SC-004)

**Действие**: подготовить папку с 10 треками; запустить импорт. Наблюдать через UI список процессов.

Ожидаемый результат:
- В `tbl_processes` появилось 110 новых строк (10 × 11).
- В UI список процессов показывает параллельную работу (несколько DEMUCS2 в `WORKING` одновременно на разных песнях).
- Все 110 строк в итоге переходят в `DONE`.

### Шаг 10. Проверить отсутствие изменений UI (SC-006)

```bash
git diff master..HEAD -- webvue3/ karaoke-public/
```

Ожидаемый результат: пусто (никаких изменений во фронтенде).

### Шаг 11. Проверить отсутствие регрессий (SC-007)

Запустить «Исправить всё» (`HealthReport.startRepairAll`) для одной из импортированных песен через UI карточки.

Ожидаемый результат:
- HealthReport показывает, что mp3 уже в MinIO (статус OK для `MP3_ACCOMPANIMENT` и `MP3_VOCAL`).
- НЕ создаются дублирующие `UPLOAD_*` задачи (`HealthReport.inProgressOwnArgs` уже видит их).
- Картинки автора/альбома (`PICTURE_*`) обрабатываются как обычно.

## Граничные сценарии

### G-01. Повторный запуск при уже идущем кортеже (US2 / SC-003)

**Действие**: запустить «Добавить файлы из папки», затем (пока кортеж ещё не завершился) — запустить ещё раз.

Ожидаемый результат: `loadListFromDb` пропускает треки, которые уже созданы (но ещё обрабатываются) — повторный импорт не добавляет дублей.

### G-02. Ошибка DEMUCS2 (Edge case «Если DEMUCS2 упал»)

**Действие**: подложить в папку битый FLAC, вызывающий падение Demucs. Запустить импорт.

Ожидаемый результат:
- DEMUCS2 падает (`ERROR`).
- FF_MP3_* падают (нет исходных стемов).
- UPLOAD_* падают (нет mp3 на диске).
- Оператор видит в UI проблемы (HealthReport показывает их).

### G-03. Большая папка (Performance / Edge case «1000+ файлов»)

**Действие**: импортировать папку с 100+ треками.

Ожидаемый результат: импорт успешен, без OOM (см. спеку 082), кортежи для всех песен сформированы, в `tbl_processes` +1100 строк (или сколько соответствует), система остаётся отзывчивой (нет деградации UI).

## Что НЕ проверяется этим quickstart'ом

- **Сборка фронтенда** (нет изменений в `webvue3`/`karaoke-public`).
- **Деплой на прод-сервер** (это пользовательская операция, см. AGENTS.md § «Деплой»; quickstart предполагает локальную проверку на admin-машине).
- **Синхронизация LOCAL↔SERVER** (`tbl_processes` — отдельная тема; см. research.md R-005).

## Если что-то пошло не так

| Симптом | Возможная причина | Что делать |
|---------|-------------------|------------|
| Кортеж не формируется (только DEMUCS2) | Не добавлены вызовы после DEMUCS2 в `Song.createFromPath` | Проверить `git diff` — должно быть +6 вызовов |
| `UPLOAD_*` падают с ошибкой пути | `pathToFile` или `storageFileName` сформированы неправильно | Проверить `song.accompanimentNameMp3`/`song.vocalsNameMp3` — должны существовать на диске ПОСЛЕ `FF_MP3_*` |
| Дубликаты в `tbl_processes` после повторного импорта | Дедупликация сломана | Проверить `KaraokeProcess.createProcess` — ключ должен включать `karaokeFileType` для UPLOAD |
| `HealthReport` создаёт дубликаты `UPLOAD_*` | Изменилась логика `inProgressOwnArgs` в `HealthReport.kt` | Сравнить `HealthReport.kt:569-580` и `:857-868` с master — должны быть без изменений |
| UI показывает старый список процессов | Фронтенд не подхватил новые записи (если менялся API `/api/utils/getprocesses`) | Проверить `git diff` — никаких изменений в API не должно быть |
