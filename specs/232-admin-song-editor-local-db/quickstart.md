# Quickstart: 232-admin-song-editor-local-db

**Feature**: 232-admin-song-editor-local-db
**Date**: 2026-08-15

## Цель

Валидация end-to-end: после фикса облегчённый редактор песен в админке
(webvue3, `SongKaraokeEditorModal`, `mode='song'`) сохраняет правки в
локальную БД admin-машины, а не в серверную. Это устраняет расхождение
«что вижу — что сохраняю».

Фича runtime-only, без миграций. Проверка делается пользователем
вручную на admin-машине (см. `Constitution § Рабочий процесс → Тесты`).

---

## Предусловия (Prerequisites)

1. **Admin-машина** с развёрнутым стеком Karaoke (см. `DEVELOPMENT.md` и
   `deploy/do.sh`):
   - `karaoke-db` (PostgreSQL, localhost:8832 / `karaoke-db:5432` в docker).
   - `karaoke-app` (admin-бэкенд, контейнер с портом 8899 → хост через webvue3 proxy).
   - `karaoke-webvue3` (админка, порт 7906).
   - Внешний доступ к прод-серверной БД (`188.119.64.111:8832` или иной,
     заданный в `DB_REMOTE_HOST`/`DB_REMOTE_PORT`).

2. **Postgres-клиент** для прямых SELECT в обе БД:
   ```bash
   psql -h localhost -p 8832 -U <DB_LOCAL_POSTGRES_USER> -d karaoke
   psql -h 188.119.64.111 -p 8832 -U <DB_SERVER_POSTGRES_USER> -d karaoke
   ```
   Имена пользователей/пароли — из `deploy/do.env` (НЕ коммитить,
   см. `Constitution § VIII`).

3. **Тестовая песня** в tbl_songs обеих БД (для сценария US1, US2).
   - Например: `SELECT id, song_name FROM tbl_songs WHERE id_status >= 5 LIMIT 1;` — выбрать песню, у которой есть размеченный текст/маркеры.
   - Если в LOCAL-БД нет ни одной песни с разметкой — выполните
     синхронизацию LOCAL ← SERVER (через админку: раздел «Синхронизация»),
     либо возьмите любую песню, которая уже есть локально.

4. **Браузер** с открытой админкой `http://localhost:7906` (или
   `<admin-machine-ip>:7906`). Залогинен как админ.

5. **Состояние `assignmentsTarget`** в webvue3 store:
   - Если в настройках KaraokeProperties `editorAssignmentDefaultTarget = 'remote'`
     (дефолт), то при открытии раздела «Задание редактора» —
     `assignmentsTarget = 'remote'`.
   - Это нормальное состояние для проверки US1/Acceptance Scenarios 1.

---

## Сценарий 1 (P1, US1 Acceptance Scenarios 1–3): «правки → локальная БД»

### Setup

1. Открыть админку `http://localhost:7906`.
2. Перейти в раздел «Песни» → выбрать тестовую песню (или SongsTable).
3. Открыть облегчённый редактор: кнопка «…» / контекстное меню → «Открыть в редакторе»
   (точное название зависит от UI; важно, что открывается `SongKaraokeEditorModal` с `mode='song'`).
4. Альтернативно: в `SongEdit.vue` нажать кнопку редактора караоке-разметки (вызывает `showKaraokeEditor`).

### Действия

1. **Запомнить исходное состояние**:
   - В редакторе: первый голос, последняя строка текста и последние 2–3 маркера.
   - В БД:
     ```sql
     -- LOCAL
     SELECT id, song_name, source_text[1] AS voice1_text
       FROM tbl_songs WHERE id = <testSongId>;
     -- SERVER
     SELECT id, song_name, source_text[1] AS voice1_text
       FROM tbl_songs WHERE id = <testSongId>;
     ```

2. **Изменить текст и/или маркеры** в редакторе (например, добавить
   спецтег `~Припев~` в начало одного из голосов, или сдвинуть 1–2 маркера).

3. **Дождаться автосохранения** (индикатор «Сохранение…» → «Сохранено ✓»),
   или нажать «Сохранить» вручную.

4. **Повторно открыть редактор той же песни** (закрыть модалку → открыть заново,
   или обновить страницу и снова открыть).

### Ожидаемый результат

✅ Редактор показывает **только что сохранённый** текст и маркеры.

✅ В БД:
```sql
-- LOCAL: source_text[1] изменился соответственно правке
SELECT id, song_name, source_text[1] FROM tbl_songs WHERE id = <testSongId>;
-- SERVER: source_text[1] НЕ изменился (если только sync не качал с LOCAL)
SELECT id, song_name, source_text[1] FROM tbl_songs WHERE id = <testSongId>;
```

Если в SERVER-БД тоже изменилось — это регресс (фича не работает). Возможные причины:
- Старая версия `karaoke-app` (без фикса) — пересобрать jar (`./gradlew clean karaoke-app:bootJar --parallel`) и перезапустить контейнер.
- Sync LOCAL → SERVER был запущен между шагами 3 и 4 (не должен быть автоматическим, но возможен случайный запуск мониторингом) — повторить сценарий, исключив sync в момент проверки.

---

## Сценарий 2 (P2, US2 Acceptance Scenarios 1–2): «логи/диагностика»

### Setup

Тот же, что в Сценарии 1.

### Действия

1. Открыть логи `karaoke-app` (stdout контейнера или `journalctl` /
   `docker logs karaoke-app` — зависит от развёртывания).

2. Открыть редактор тестовой песни.

3. Найти в логах запись, соответствующую запросу `/api/songeditor/edit/byId`.

4. Проверить, что в логах фигурирует LOCAL-БД (например, по `database.name == "LOCAL"`
   в структурированных логах или по характерному SQL-запросу к
   `karaoke-db` контейнеру).

### Ожидаемый результат

✅ В логах видно подключение к LOCAL-БД при чтении Song в `mode='song'`.
✅ После сохранения — подключение к LOCAL-БД при записи Song.

(Опционально: если фича расширит логирование именем `KaraokeConnection`,
то в логе будет явно `name="LOCAL"`. До фикса — может фигурировать как
`name="LOCAL"` через `WORKING_DATABASE`, так и `name="SERVER"` если
`target='remote'`. После фикса — всегда `name="LOCAL"` для mode='song'.)

---

## Сценарий 3 (US1 Acceptance Scenario 4): «assignment не сломался»

### Setup

Тот же.

### Действия

1. Создать задание редактору (через UI или прямой INSERT в tbl_song_assignments).
2. Открыть это задание в редакторе (`mode='assignment'`) — из раздела «Задание редактора».
3. Изменить текст черновика, нажать «Сохранить».
4. Переоткрыть редактор задания — правки должны быть на месте.
5. Переключить `assignmentsTarget` на `'remote'` (если есть задания на SERVER),
   открыть другое задание (живущее на SERVER), изменить черновик, сохранить.
6. Проверить в SERVER-БД:
   ```sql
   SELECT * FROM tbl_song_assignment_drafts WHERE assignment_id = <remoteAssignmentId>;
   ```

### Ожидаемый результат

✅ `mode='assignment'` работает по-прежнему target-aware: задания на LOCAL — в LOCAL-БД, задания на SERVER — в SERVER-БД.

✅ Нет регрессии: фикс не затронул ветку `mode='assignment'` в `editById`/`editSave`.

---

## Сценарий 4 (Edge Case): «песня есть только в SERVER-БД»

### Setup

1. Выбрать песню, которая есть в SERVER-БД, но **отсутствует** в LOCAL-БД
   (например, недавно добавлена на проде, sync LOCAL ← SERVER не запускался).

### Действия

1. Открыть эту песню в облегчённом редакторе (`mode='song'`).

### Ожидаемый результат

✅ Редактор показывает ошибку «Не удалось загрузить данные для редактирования»
(текущее поведение `found=false`).

✅ Опционально (FR-005 спеки): если реализован отличимый код `song_not_found_in_local_db`,
то в UI выводится более понятное сообщение вида «Песня не найдена в локальной БД —
выполните синхронизацию LOCAL ← SERVER, чтобы подтянуть запись».

✅ Решение пользователя: запустить sync LOCAL ← SERVER (через раздел «Синхронизация»),
дождаться появления песни в LOCAL-БД, открыть редактор заново — теперь работает.

---

## Команды для проверки состояния

### До и после сохранения (Сценарий 1)

```bash
# Текущее значение source_text и recordhash для тестовой песни
LOCAL_HASH=$(psql -h localhost -p 8832 -U $DB_LOCAL_POSTGRES_USER -d karaoke -tAc \
  "SELECT recordhash FROM tbl_songs WHERE id = $TEST_ID")
SERVER_HASH=$(psql -h 188.119.64.111 -p 8832 -U $DB_SERVER_POSTGRES_USER -d karaoke -tAc \
  "SELECT recordhash FROM tbl_songs WHERE id = $TEST_ID")
echo "LOCAL  hash: $LOCAL_HASH"
echo "SERVER hash: $SERVER_HASH"
```

После сохранения в редакторе:
- `LOCAL_HASH` должен измениться (новая запись → новый md5).
- `SERVER_HASH` не должен измениться (если sync не запускался).

### Проверка, что `karaoke-app` действительно использует новый код

```bash
# На admin-машине — проверить, что jar свежий
docker exec karaoke-app ls -la /app.jar
# Дата должна быть после фикса.

# Проверить, что код содержит изменение (если jar не обфусцирован):
docker exec karaoke-app sh -c 'unzip -p /app.jar BOOT-INF/classes/com/svoemesto/karaokeapp/controllers/SongEditorController.class | strings | grep -i "Connection.local"'
# Должно быть упоминание "Connection.local" в методах editById/editSave.
```

---

## Чек-лист прохождения

| Сценарий | Acceptance | Пройден? | Замечания |
|----------|------------|----------|-----------|
| 1 | US1 AC1–AC3 | ☐ | Правки видны после переоткрытия; LOCAL изменился, SERVER — нет |
| 2 | US2 AC1–AC2 | ☐ | Логи показывают LOCAL-БД для mode='song' |
| 3 | US1 AC4 | ☐ | mode='assignment' работает target-aware как раньше |
| 4 | Edge Case | ☐ | Песня только в SERVER → понятная ошибка |

Когда все 4 сценария пройдены — фича валидирована, можно выкатывать.

---

## Что делать, если сценарий не прошёл

1. **Проверить сборку**: `karaoke-app` действительно использует новый jar?
   Пересобрать: `./gradlew clean karaoke-app:bootJar --parallel` (см.
   `Constitution § Рабочий процесс → Сборка бэка`).

2. **Проверить, что контейнер перезапущен**: `docker compose -f /<path>/deploy/docker-compose-app.yml up -d --force-recreate karaoke-app` (или эквивалент через `deploy/do.sh`).

3. **Проверить логи**: нет ли исключений при чтении/записи Song в LOCAL-БД?

4. **Проверить, что `Connection.local()` указывает на ожидаемую БД**:
   ```bash
   docker exec karaoke-app sh -c 'env | grep -E "DB_LOCAL|DB_SERVER|DB_REMOTE|WORK_IN|WORK_ON"'
   ```
   На admin-машине ожидается `WORK_ON_SERVER=0`, `WORK_IN_CONTAINER=1`,
   `DB_LOCAL_*` указывают на localhost/karaoke-db, `DB_REMOTE_HOST` = прод-сервер.

5. Если ничего не помогает — откатить jar (`git revert` коммита фикса),
   пересобрать, и поднять issue с логами и шагами воспроизведения.
