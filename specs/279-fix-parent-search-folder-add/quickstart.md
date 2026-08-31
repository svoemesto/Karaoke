# Quickstart: ручная проверка фикса 279

**Дата**: 2026-08-31
**Спека**: [spec.md](spec.md)
**План**: [plan.md](plan.md)
**Research**: [research.md](research.md)

Этот документ — **руководство по ручной валидации** фикса на стороне пользователя (на admin-машине). Существующие интеграционные тесты в `karaoke-app/src/test` помечены `@Disabled` и не используются как автоматическая проверка (см. Constitution § «Тесты»).

## Предусловия

1. Admin-машина с работающим `karaoke-app` (Docker-контейнер, см. AGENTS.md и `deploy/docker-compose-app.yml`).
2. Доступ к PostgreSQL через `psql` или pgAdmin (`docker exec -it karaoke-postgres psql -U postgres -d karaoke`).
3. Доступ к файловой системе admin-машины (папка для импорта).
4. Тестовая папка с аудиофайлами в формате `YYYY (NN) [Автор] - Песня.flac`.

## Подготовка тестовых данных

### Шаг 1: Убедиться, что в БД есть базовая песня с заполненным текстом

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
SELECT id, song_name, song_author, root_id, id_status, LENGTH(source_text) AS source_text_len
FROM tbl_songs
WHERE song_author = 'Король и Шут'
  AND song_name = 'Камнем по голове'
ORDER BY id;
"
```

**Ожидаемый результат**: есть как минимум одна строка с `id_status >= 1` и `source_text_len > 100` (текст заполнен).

Если такой строки нет — добавить через обычный импорт из папки или вручную (через UI редактирования песни в `webvue3`).

### Шаг 2: Создать тестовую папку с файлами

```bash
TEST_FOLDER="/tmp/karaoke_test_import_$(date +%s)"
mkdir -p "$TEST_FOLDER"
cd "$TEST_FOLDER"

# Базовая папка альбома (формат: YYYY - Album Name)
mkdir -p "1998 - Акустический альбом"

# Базовая песня (для проверки, что её не дублирует)
# Примечание: если уже импортирована — loadListFromDb её пропустит

# Три файла с разными суффиксами (одна базовая + 2 варианта)
# (используем flac, но код поддерживает также mp3/m4a — см. Song.createFromPath:8050)
touch "1998 - Акустический альбом/1 (1) [Король и Шут] - Камнем по голове (Epic Orchestral, Cover-2).flac"
touch "1998 - Акустический альбом/2 (2) [Король и Шут] - Камнем по голове (Instrumental).flac"
touch "1998 - Акустический альбом/3 (3) [Король и Шут] - Камнем по голове.flac"

ls -la "$TEST_FOLDER/1998 - Акустический альбом/"
```

**Ожидаемый результат**: 3 файла с правильным форматом имён.

### Шаг 3: Запомнить baseline состояние БД (для сравнения после импорта)

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
SELECT COUNT(*) AS total_songs, COUNT(*) FILTER (WHERE song_author = 'Король и Шут' AND root_id = 0) AS root_songs,
       COUNT(*) FILTER (WHERE song_author = 'Король и Шут' AND song_name LIKE 'Камнем по голове%') AS kamnem_songs
FROM tbl_songs;
"
```

## Сценарий проверки 1: Основной кейс пользователя (SC-001)

### Шаг 1.1: Применить фикс

Если фикс ещё не применён — внести изменения в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4425-4431` (см. [research.md R1](research.md#r1--корневая-причина-сломанного-поиска-родителя)):

```kotlin
// БЫЛО:
val sql =
    "SELECT id, song_name FROM tbl_songs" +
        " WHERE id <> ?" +
        (if (sameAuthorOnly) " AND LOWER(song_author) = LOWER(?)" else "") +
        " AND TRIM(source_text) <> ''" +
        " ORDER BY id ASC"

// СТАЛО:
val sql =
    "SELECT id, song_name FROM tbl_songs" +
        " WHERE id <> ?" +
        (if (sameAuthorOnly) " AND song_author ILIKE ?" else "") +
        " AND TRIM(source_text) <> ''" +
        " ORDER BY id ASC"
```

### Шаг 1.2: Пересобрать и перезапустить `karaoke-app`

```bash
cd ~/Karaoke
./gradlew :karaoke-app:bootJar --parallel
# Перезапуск контейнера — на стороне пользователя (Constitution: «Категорически запрещено пересобирать karaoke-app локально, исключение: dev-pc/dev»)
```

### Шаг 1.3: Запустить импорт из тестовой папки

Через UI (`webvue3` → главная страница → «Добавить файлы из папки» → ввести `$TEST_FOLDER`):

```
Путь к папке: /tmp/karaoke_test_import_<timestamp>
[Подтвердить]
```

Или через `curl`:

```bash
curl -X POST "http://localhost:8080/api/utils/createfromfolder?folder=$TEST_FOLDER"
```

### Шаг 1.4: Дождаться завершения

SSE-уведомление (в UI или в логах):

```
Добавление файлов из папки «/tmp/karaoke_test_import_<timestamp>»: 3 (пропущено: 0)
```

**Ожидаемый результат**: 3 файла добавлено.

### Шаг 1.5: Проверить БД — `root_id` у новых песен

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
SELECT id, song_name, song_author, root_id, id_status,
       LENGTH(source_text) AS source_text_len,
       LENGTH(source_markers) AS source_markers_len
FROM tbl_songs
WHERE song_author = 'Король и Шут'
  AND song_name LIKE 'Камнем по голове%'
ORDER BY id;
"
```

**Ожидаемый результат** (SC-001, SC-007):

| song_name | root_id | id_status | source_text_len | source_markers_len |
|-----------|---------|-----------|-----------------|---------------------|
| `Камнем по голове` (базовая) | `0` | `>= 1` | `> 100` | `>= 0` |
| `Камнем по голове (Epic Orchestral, Cover-2)` | `=<id базовой>` | `1` | `> 100` (скопирован) | `>= 0` (скопирован) |
| `Камнем по голове (Instrumental)` | `=<id базовой>` | `1` | `> 100` (скопирован) | `>= 0` (скопирован) |

**Если `root_id = 0` у новых песен** — фикс не сработал. Проверить:
- Запущен ли пересобранный контейнер с новым кодом.
- Правильно ли изменён SQL (`song_author ILIKE ?`).
- Регистр автора в БД и в имени файла (для отладки).

## Сценарий проверки 2: Минимальный `id` (SC-001 / FR-002)

### Шаг 2.1: Создать ещё одну запись «Камнем по голове» с другим id

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
INSERT INTO tbl_songs (song_name, song_author, source_text, root_id, id_status)
VALUES ('Камнем по голове (Live)', 'Король и Шут', 'Live текст...', 0, 1);
"
```

Запомнить `id` этой новой записи.

### Шаг 2.2: Импортировать новый файл с тем же нормализованным названием

```bash
touch "1998 - Акустический альбом/99 (99) [Король и Шут] - Камнем по голове (Cover-3).flac"
curl -X POST "http://localhost:8080/api/utils/createfromfolder?folder=$TEST_FOLDER"
```

### Шаг 2.3: Проверить

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
SELECT id, song_name, root_id
FROM tbl_songs
WHERE song_name = 'Камнем по голове (Cover-3)';
"
```

**Ожидаемый результат**: `root_id` = ID **базовой** песни (с минимальным `id` среди всех «Камнем по голове» того же автора с заполненным текстом), а НЕ ID только что вставленной «Live»-версии.

## Сценарий проверки 3: Межавторская защита (SC-002 / FR-004)

### Шаг 3.1: Создать запись другого автора с тем же нормализованным названием

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
INSERT INTO tbl_songs (song_name, song_author, source_text, root_id, id_status)
VALUES ('Камнем по голове', 'Кино', 'Текст от Кино...', 0, 1);
"
```

### Шаг 3.2: Импортировать файл «Король и Шут — Камнем по голове (Variant).flac»

```bash
touch "1998 - Акустический альбом/100 (100) [Король и Шут] - Камнем по голове (Variant).flac"
curl -X POST "http://localhost:8080/api/utils/createfromfolder?folder=$TEST_FOLDER"
```

### Шаг 3.3: Проверить

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
SELECT id, song_name, song_author, root_id
FROM tbl_songs
WHERE song_name = 'Камнем по голове (Variant)';
"
```

**Ожидаемый результат**: `root_id` указывает на запись «Король и Шут» — «Камнем по голове», а НЕ на «Кино» — «Камнем по голове». Привязка к чужому автору НЕ происходит.

Дополнительно (SC-002):

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
SELECT COUNT(*) AS cross_author_bindings
FROM tbl_songs
WHERE song_author <> 'Король и Шут'
  AND root_id IN (SELECT id FROM tbl_songs WHERE song_author = 'Король и Шут' AND song_name = 'Камнем по голове');
"
```

**Ожидаемый результат**: `cross_author_bindings = 0`.

## Сценарий проверки 4: Регистр автора (SC-007 / FR-006)

### Шаг 4.1: Импортировать файл с автором в нижнем регистре

```bash
touch "1998 - Акустический альбом/200 (200) [король и шут] - Камнем по голове (Lower).flac"
curl -X POST "http://localhost:8080/api/utils/createfromfolder?folder=$TEST_FOLDER"
```

### Шаг 4.2: Проверить

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
SELECT id, song_name, song_author, root_id
FROM tbl_songs
WHERE song_name = 'Камнем по голове (Lower)';
"
```

**Ожидаемый результат**: `root_id` указывает на запись «Король и Шут» — «Камнем по голове». `ILIKE` корректно сравнивает регистронезависимо.

## Сценарий проверки 5: Race condition защита (SC-005 / FR-007)

Этот сценарий проверяет, что фикс не сломал защиту от race condition из спеки 278.

### Шаг 5.1: Импортировать песню и проверить, что `song_tone`/`song_bpm` не перезатираются

```bash
# Создать файл
touch "1998 - Акустический альбом/300 (300) [Король и Шут] - Камнем по голове (Race).flac"

# Запустить импорт
curl -X POST "http://localhost:8080/api/utils/createfromfolder?folder=$TEST_FOLDER"
```

Подождать, пока `KEY_BPM_FROM_FILE` отработает (5-30 сек после создания песни). Можно контролировать через:

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
SELECT id, song_name, song_tone, song_bpm, root_id
FROM tbl_songs
WHERE song_name = 'Камнем по голове (Race)';
"
```

**Ожидаемый результат** (SC-005):

- `root_id` указывает на базовую песню.
- `song_tone` либо пустое (если `KEY_BPM_FROM_FILE` ещё не отработал), либо содержит тональность (`Am`, `C`, и т.п.) — НЕ сброшено в пустую строку после `applyDuplicateOriginal`.

## Сценарий проверки 6: «Родитель не найден» (SC-004 / FR-008)

### Шаг 6.1: Импортировать файл с уникальным названием

```bash
mkdir -p "$TEST_FOLDER/2024 - Совсем новый альбом"
touch "$TEST_FOLDER/2024 - Совсем новый альбом/1 (1) [Новый Автор] - Уникальная Песня.flac"
curl -X POST "http://localhost:8080/api/utils/createfromfolder?folder=$TEST_FOLDER"
```

### Шаг 6.2: Проверить

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
SELECT id, song_name, song_author, root_id, id_status
FROM tbl_songs
WHERE song_name = 'Уникальная Песня';
"
```

**Ожидаемый результат**: `root_id = 0`, `id_status = 0` или `1` (если интернет-поиск уже нашёл текст). Дальше работает `findYandexSongLyrics` / `lyricsSearchExecutor`.

## Сценарий проверки 7: `customFunction` (SC-003 / FR-010)

### Шаг 7.1: Запустить повторный поиск родителей через UI

В `webvue3` → главная страница → кнопка «Custom Function» (или эквивалент через `curl POST /api/utils/customfunction`).

### Шаг 7.2: Проверить SSE-уведомление

```
Поиск родителей и аудио-родителей: завершено. Обработано N, родитель назначен M, ...
```

### Шаг 7.3: Проверить БД

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
SELECT COUNT(*) FILTER (WHERE root_id <> 0) AS with_parent,
       COUNT(*) FILTER (WHERE root_id = 0) AS without_parent
FROM tbl_songs
WHERE id_status < 6 AND song_name LIKE 'Камнем по голове%';
"
```

**Ожидаемый результат**: число `with_parent` увеличилось (для песен, у которых раньше `root_id = 0`, но которые должны быть привязаны). Если все песни уже были привязаны в сценариях 1-5 — `with_parent` не изменится (это тоже нормально).

## Очистка тестовых данных

После успешной проверки — очистить созданные тестовые песни:

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
DELETE FROM tbl_songs
WHERE song_author IN ('Король и Шут', 'Кино', 'Новый Автор')
  AND song_name LIKE 'Камнем по голове%';
DELETE FROM tbl_songs
WHERE song_name = 'Уникальная Песня';
"

# Удалить тестовую папку
rm -rf "$TEST_FOLDER"
```

## Ожидаемые результаты по SC

| SC | Критерий | Проверка |
|----|----------|----------|
| SC-001 | 9 из 10 новых песен имеют `root_id <> 0` (для импорта 9 файлов с суффиксами + 1 базовый) | SELECT по `tbl_songs` |
| SC-002 | 0 межавторских привязок (`root_id` указывает на того же автора) | SELECT с фильтром по автору |
| SC-003 | `customFunction` корректно находит родителей | SSE + SELECT |
| SC-004 | Поведение «родитель не найден» не изменяется (`root_id = 0` для уникальных имён) | SELECT |
| SC-005 | Race condition защита (спека 278) сохранена — `song_tone`/`song_bpm`/`audio_*` не перезатираются | SELECT с задержкой |
| SC-006 | Нет регрессий в существующих сценариях импорта | Ручная проверка + UI |
| SC-007 | Регистр автора в имени файла vs БД не блокирует поиск | SELECT по «(Lower)» |

Если хотя бы один SC не пройден — фикс требует доработки. Зафиксировать в `specs/279-fix-parent-search-folder-add/checklists/validation.md` или новой задаче.
