# Quickstart: 357 — Folder Import Overwrite (Audit #73)

**Дата**: 2026-09-10
**Спека**: [spec.md](spec.md)
**Цель**: Валидация фикса спеки 357 end-to-end.

## Prerequisites

1. **Локальное окружение Karaoke** запущено (`deploy/do.sh`).
2. **PostgreSQL** доступен на `localhost:5432`.
3. **Тестовая папка** с 3-5 flac/mp3/m4a файлами подготовлена (например, `/tmp/test-import-357/`).
4. **Git branch**: `357-folder-import-overwrite` (создана через `tools/specify-bootstrap.sh`).

## Сценарий 1: Импорт папки + правка `author` (US1)

### Шаги

```bash
# 1. Создать тестовую папку
mkdir -p /tmp/test-import-357/2024\ -\ TestAlbum
# Скопировать 3-5 flac/mp3 файлов с именами формата:
#   "01 (1) [Author1] - Song1.flac"
#   "02 (2) [Author2] - Song2.flac"
#   "03 (3) [Author3] - Song3.flac"

# 2. Стартовать import (HTTP API)
curl -X POST http://localhost:8080/api/songs/addFromFolder \
  -H "Content-Type: application/json" \
  -d '{"folder":"/tmp/test-import-357"}'

# 3. СРАЗУ (через 10-30 секунд) — открыть SongEdit для 2 песен
#    и поменять author на правильное значение.
#    UI: webvue3 → таблица песен → редактирование → "Сохранить"

# 4. Подождать завершения всех KaraokeProcess (15-30 минут).
#    Мониторинг: SELECT * FROM tbl_processes WHERE song_id IN (...) AND status != 'done';

# 5. Проверить финальное состояние
psql -h localhost -U karaoke -d karaoke \
  -c "SELECT id, name, author FROM tbl_songs WHERE root_folder = '/tmp/test-import-357';"

# Ожидаемый результат:
# - У 2 песен (которые правили) — author = ручное значение
# - У остальных — author = распарсенное из имени файла
```

### Ожидаемый результат (после фикса спеки 357)

```
 id  |  name  |        author
-----+--------+---------------------
 101 | Song1  | Правильный Автор 1   ← ручное значение сохранено
 102 | Song2  | Author2              ← распарсенное
 103 | Song3  | Правильный Автор 3   ← ручное значение сохранено
```

### Провал (до фикса)

```
 id  |  name  |        author
-----+--------+---------------------
 101 | Song1  | Author1              ← ПЕРЕЗАТЁРТО (баг #73)
 102 | Song2  | Author2
 103 | Song3  | Author3              ← ПЕРЕЗАТЁРТО
```

## Сценарий 2: Правка `song_name` (US2)

### Шаги

```bash
# 1. Использовать ту же тестовую папку, что и в Сценарии 1.

# 2. Стартовать import (HTTP API).

# 3. Через 30 секунд — открыть SongEdit для 1 песни
#    и поменять song_name на "Новое Название".

# 4. Подождать завершения всех KaraokeProcess.

# 5. Проверить:
psql -h localhost -U karaoke -d karaoke \
  -c "SELECT id, name, song_tone, song_bpm FROM tbl_songs WHERE root_folder = '/tmp/test-import-357' AND id = 102;"
```

### Ожидаемый результат

```
 id  |     name      | song_tone | song_bpm
-----+---------------+-----------+----------
 102 | Новое Название | Am        |    120   ← song_tone + song_bpm от KEY_BPM_FROM_FILE
                                              ← name от ручной правки
```

## Сценарий 3: WARN-лог `song.locked_save_diff_overlap` (US4)

### Шаги

```bash
# 1. Запустить docker logs -f karaoke-app

# 2. В другом терминале — повторить Сценарий 1 (импорт + правка).

# 3. После завершения импорта — проверить лог:
docker logs karaoke-app 2>&1 | grep "song.locked_save_diff_overlap"
```

### Ожидаемый результат

Если race НЕ произошла (что и ожидается после фикса):

```
[пусто]
```

Если race всё-таки произошла (regression или edge case):

```
[2026-09-10T12:34:56.789Z] WARN infra.prod.ping song.locked_save_diff_overlap: songId=101 field=AUTHOR oldInMemory="Author1" newInDb="Правильный Автор 1"
```

> После фикса этот WARN появляется **< 1 раза в час** на проде (SC-007).

## Сценарий 4: Smoke-тест на `applyFamilySongSelection` (US3, B1)

### Шаги

```bash
# 1. Импортировать 2 версии одной песни (например, "Song (Remix)" и "Song (Original)").

# 2. Открыть SongEdit для одной из них → нажать кнопку "Похожие версии".

# 3. Выбрать вторую версию как "оригинал".

# 4. Параллельно (в течение 5 секунд) — открыть SongEdit для первой песни
#    и поменять `year` на другое значение.

# 5. Подождать завершения всех фоновых процессов.

# 6. Проверить:
psql -h localhost -U karaoke -d karaoke \
  -c "SELECT id, name, year, root_id, source_text IS NOT NULL AS has_text FROM tbl_songs WHERE name LIKE 'Song%';"
```

### Ожидаемый результат

- `year` = ручное значение (не перезатёрто).
- `root_id` = ID выбранной второй версии.
- `source_text` заполнен (если найден через `applyFamilySongSelection`).

## Сценарий 5: Performance regression (SC-006)

### Шаги

```bash
# 1. Импортировать папку из 50 песен.

# 2. Измерить время выполнения с `saveToDbLocked()`:
time curl -X POST http://localhost:8080/api/songs/addFromFolder \
  -H "Content-Type: application/json" \
  -d '{"folder":"/tmp/test-import-50-songs"}'

# 3. Сравнить с baseline (до фикса):
#    Ожидаемое: время не должно вырасти более чем на 5-10%.
#    Метрика: P95 latency saveToDbLocked() ≤ saveToDb() + 50 мс.
```

## Сценарий 6: Code review (FR-150)

Проверить, что для каждого из 30 мест категории B1 добавлен KDoc-комментарий:

```bash
grep -rn "saveToDbLocked" karaoke-app/src/main/kotlin/ --include="*.kt" -A 3 \
  | grep -B 1 "specs/357"
```

Ожидаемый результат: ~30 строк с комментарием, содержащим `specs/357`.

## Тест-чеклист Sign-off

| # | Шаг | Обязательный? | Результат |
|---|---|---|---|
| 1 | Backend compile (`./gradlew compileKotlin`) | Да | ☐ pass / ☐ fail |
| 2 | Unit-тесты `saveToDbLocked` (из спеки 299) | Нет | ☐ pass / ☐ fail / ☐ N/A |
| 3 | **Сценарий 1: импорт + правка author (US1)** | Да | ☐ pass / ☐ fail |
| 4 | **Сценарий 2: правка song_name (US2)** | Да | ☐ pass / ☐ fail |
| 5 | Сценарий 3: WARN-лог diff_overlap (US4) | Нет | ☐ pass / ☐ fail / ☐ N/A |
| 6 | Сценарий 4: applyFamilySongSelection (US3) | Да | ☐ pass / ☐ fail |
| 7 | Сценарий 5: performance regression (SC-006) | Да | ☐ pass / ☐ fail |
| 8 | Сценарий 6: KDoc review (FR-150) | Да | ☐ pass / ☐ fail |
| 9 | KDoc coverage ≥ 50% (`bash tools/check-kdoc-coverage.sh`) | Да | ☐ pass / ☐ fail |
| 10 | Линтеры (`./gradlew ktlintCheck`) | Да | ☐ pass / ☐ fail |

**Merge разрешён только при `pass` на всех обязательных шагах.**
