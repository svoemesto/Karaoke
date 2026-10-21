# Quickstart: как проверить, что подстановка текста не перезатирает песню

**Feature**: `546-lyrics-search-preserve-song-fields`
**Смысл проверки**: после завершения поиска текста в песне меняются только текст и статус,
а любые правки, сделанные за время поиска (ручные и фоновые), остаются.

## 1. Предусловия

- Хост `nsa-i9`, репозиторий `/home/nsa/Karaoke`, ветка `546-lyrics-search-preserve-song-fields`.
- Собранный контейнер `karaoke-app` на dev (не прод).
- Тестовая папка с 2–3 `.flac`, у песен нет родителя в базе.
- Очередь заданий **остановлена** перед перезапуском (Pass 380):
  `curl -s http://localhost:8898/process/stop`, затем убедиться, что нет `WORKING`/`CREATING`.

## 2. Сборка и unit-тесты

```bash
cd /home/nsa/Karaoke
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests '*SongWritePlan*' --tests '*SongFieldUpdateSql*'
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck
```

Ожидаемо: компиляция без ошибок; тесты зелёные (в т.ч. кейс «в SQL только разрешённые
колонки»); ktlint без новых нарушений.

## 3. Проверка основного сценария (#324)

1. Импортировать тестовую папку: админка → главная → «Добавить файлы из папки».
2. Сразу открыть песню в `SongEdit.vue`, поменять название и тональность, сохранить.
3. Дождаться, пока поиск текста завершится (в логе — `song.field_update_applied`).

Проверка в БД:

```sql
SELECT id, song_name, song_tone, id_status, left(source_text, 40) AS text_head
FROM tbl_songs WHERE song_name LIKE '%П.М.М.Л.%' OR song_name LIKE '%ПММЛ%';
```

Ожидаемо: `song_name` и `song_tone` — значения из шага 2; `source_text` — найденный текст;
`id_status = 1`.

## 4. Проверка, что лишние поля не менялись

Снять `COPY ... TO '/tmp/songs_before.csv'` до подстановки и `songs_after.csv` после, сравнить.
В диффе допустимы только `source_text`, `id_status`, `recordhash` (и служебный `last_update`,
если его обновляет триггер). Подробности — [contracts/manual-test-checklist.md](contracts/manual-test-checklist.md) §4.

## 5. Проверка регрессий

| Что | Как | Ожидаемо |
|---|---|---|
| Тональность/BPM | `KEY_BPM_FROM_FILE` завершился до поиска текста | `song_tone`/`song_bpm` не обнулились |
| Стемы | `DEMUCS2` завершился | `audio_*` не пусты |
| Идемпотентность | повторить поиск по песне с текстом | текст не изменился, `reason=existing_text` |
| Удаление | удалить песню во время поиска | нет 500, `song.field_update_not_found` |

## 6. Логи

```bash
docker logs karaoke-app 2>&1 | grep -E 'song\.(field_update|stale_snapshot)' | tail -30
```

Формат и ожидаемые строки — [contracts/log-format.md](contracts/log-format.md).

## 7. Где смотреть детали

- Политика записи (что можно писать): [contracts/lyrics-write-policy.md](contracts/lyrics-write-policy.md).
- Контракт примитива (транзакция, гарантии): [contracts/field-scoped-save.md](contracts/field-scoped-save.md).
- Наборы полей и состояния: [data-model.md](data-model.md).
- Почему прошлые исправления не помогли: [research.md](research.md) R1.
- Полная приёмка: [contracts/manual-test-checklist.md](contracts/manual-test-checklist.md).
