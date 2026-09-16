# Quickstart: 126 — Поиск тональности из существующего `[key].json`

> Сгенерировано Stage 3 (`/speckit-plan`) — Phase 1.
> Запускаемая валидационная инструкция.

## Предусловия

- Karaoke backend собран (`./gradlew :karaoke-app:bootJar`).
- Минимум 1 песня в БД с пустым `song.key` и существующим файлом `<songFile> [key].json`.
- Доступ к PostgreSQL через `WORKING_DATABASE` (см. `karaoke-app/.../Constants.kt:208`).
- Доступ к `deploy/do.sh` (с Tier-1-согласия для restart).

## Сценарий 1: HealthReport пропускает docker, если файл есть

### Подготовка

1. Выберите песню с пустым `song.key` и существующим `[key].json`:
   ```sql
   SELECT s.id, s.song_file_name, s.song_tone
   FROM tbl_songs s
   WHERE s.song_tone IS NULL OR s.song_tone = ''
   LIMIT 5;
   ```
2. Проверьте, что файл существует:
   ```bash
   ls -la "<rootFolder>/<songFileName> [key].json"
   ```
3. Проверьте содержимое (должно быть `key` и `bpm`):
   ```bash
   cat "<rootFolder>/<songFileName> [key].json"
   # Ожидаемый вывод: {"key": "Am", "bpm": 120, ...}
   ```

### Шаги валидации

1. Откройте страницу HealthReport для этой песни в UI (`/healthreport/`).
2. До фикса: видим ERROR «У песни отсутствует тональность» (если раньше не был).
3. **После фикса**: ERROR не появляется, `song.key` уже заполнен.
4. Проверьте в БД:
   ```sql
   SELECT song_tone FROM tbl_songs WHERE id = <songId>;
   -- Ожидаемый вывод: 'Am' (значение из файла)
   ```

### Ожидаемый результат

- Время от нажатия «Исправить всё» до появления `song.key` в UI: **< 1 сек** (вместо 5-10 сек с docker).
- В логе бэка (`docker logs karaoke-app`):
  ```
  keybpm: applied from file for songId=<N> (key=Am, bpm=120)
  ```
- **Никакой** `docker run svoemestodev/keybpmfinder:latest` не вызывается.

## Сценарий 2: HealthReport запускает docker, если файла нет

### Подготовка

1. Выберите песню с пустым `song.key` и **отсутствующим** файлом:
   ```bash
   rm -f "<rootFolder>/<songFileName> [key].json"
   ```

### Шаги валидации

1. Откройте HealthReport, нажмите «Исправить всё».
2. Создаётся `KaraokeProcess` типа `KEY_BPM_FROM_FILE` (проверьте в `tbl_karaoke_processes`).
3. Worker запускает docker `svoemestodev/keybpmfinder:latest`.
4. После завершения: файл `[key].json` создан, `song.key` заполнен.

### Ожидаемый результат

- Время: ~5-10 сек (как обычно).
- В логе: `keybpm_docker_run ...` или подобное (НЕ `applied from file`).

## Сценарий 3: Невалидный файл

### Подготовка

```bash
echo '{"key": null, "bpm": null}' > "<rootFolder>/<songFileName> [key].json"
```

### Шаги валидации

1. Откройте HealthReport, нажмите «Исправить всё».
2. **Ожидается fallback на docker** (т.к. `data.key == null` → `applyKeyBpmFromFileIfExists` возвращает `false`).
3. Файл будет перезаписан с валидным `data.key` после docker-прогона.

## Unit-тесты

Запуск:
```bash
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test \
  --tests "com.svoemesto.karaokeapp.KeyBpmFromFileCacheTest" --no-daemon
```

Ожидаемый результат: **4/4 PASS**.

## Hard gates

Перед merge:
```bash
# Backend
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck

# Guards
tools/check-no-jpa-imports.sh      # PASS
tools/check-no-mp4-mentions.sh     # PASS (без новых violations)
```

## Rollback plan

Если после деплоя что-то пошло не так:

1. **git revert** merge-коммит (один коммит):
   ```bash
   git revert -m 1 <merge-commit-sha>
   git push origin 126-key-from-file
   ```
2. **Пересобрать и задеплоить** через `deploy/do.sh build_karaoke-app && restart_karaoke-app` (с согласия).
3. **Никаких миграций** откатывать не нужно.

## OpenProject workflow

После merge:
```bash
./tools/tracker.sh add-comment 126 --file specs/126-key-from-file/report.md
./tools/tracker.sh mark-review 126
```

После ревью владельца:
```bash
./tools/tracker.sh close-issue 126
```
