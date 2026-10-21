# Contract: политика записи при подстановке данных

**Feature**: `546-lyrics-search-preserve-song-fields`
**Роль**: белый список колонок и правила решений для четырёх операций. Наборы полей —
в [data-model.md](../data-model.md) §2; механика — в [field-scoped-save.md](field-scoped-save.md).

## 1. Подстановка найденного текста

Вход: `songId`, `foundText` (результат поиска), `engine` (для лога).

| Условие (по состоянию в БД под блокировкой) | Решение | В `UPDATE` |
|---|---|---|
| строки нет | `NotFound` | — |
| `foundText` пустой/пробельный | `Skipped(EMPTY_INPUT)` | — |
| `source_text` непустой | `Skipped(EXISTING_TEXT)` | — |
| `source_text` пустой, `id_status = 0` | `Write` | `source_text`, `id_status = 1` |
| `source_text` пустой, `id_status > 0` | `Write` | только `source_text` |

Допустимый набор колонок: `{source_text, id_status}` — других быть не может.

## 2. Копирование от родителя (`applyDuplicateOriginal`)

Допустимый набор: `{root_id, source_text, result_text, source_markers,
formatted_text_song, formatted_text_tabs, formatted_text_chords, id_status}`.

| Условие | Решение |
|---|---|
| строки нет | `NotFound` |
| `id_status = 0` | `Write` полного набора, `id_status = 1` |
| `id_status ≥ 1` | `Write` полного набора **без** `id_status` |

## 3. Маркеры от аудио-родителя (`applyAudioParentMarkers`)

Допустимый набор: `{source_text, result_text, source_markers,
formatted_text_song, formatted_text_tabs, formatted_text_chords, id_status}`.

| Условие | Решение |
|---|---|
| строки нет | `NotFound` |
| `id_status < 5` | `Write` набора, `id_status = 5` |
| `id_status ≥ 5` | `Write` набора **без** `id_status` |

## 4. Выбор похожей версии (`applyFamilySongSelection`)

Допустимый набор: `{root_id, source_text, result_text, source_markers,
formatted_text_song, formatted_text_tabs, formatted_text_chords,
audio_parent_id, audio_similarity_percent, audio_delta_ms, id_status}`.

| Условие | Решение |
|---|---|
| строки нет | `NotFound` |
| `id_status = 0` | `Write`, `id_status = 1` |
| `id_status ≥ 1` | `Write` **без** `id_status` |
| аудиопара не задана | поля `audio_*` в набор не входят |

## 5. Запрещённые колонки (для всех четырёх операций)

`song_name`, `song_author`, `song_album`, `song_year`, `song_track`, `song_tone`, `song_bpm`,
`audio_song`, `audio_music`, `audio_vocals`, `audio_drums`, `audio_bass`,
`publish_date`, `publish_time`, `tags`, `song_type`, любые `id_*`/`version_*` площадок,
`recordhash`.

Проверка: unit-тест `SongFieldUpdateSqlTest` падает, если SQL содержит колонку вне
белого списка операции; ручной шаг — [manual-test-checklist.md](manual-test-checklist.md) §4.

## 6. Примеры (приёмочные)

1. **Сценарий #324.** Импорт папки; песня `id=1`, `source_text=''`, `id_status=0`. Пока идёт
   поиск, админ ставит `song_name='П.М.М.Л.'`, `song_tone='Am'`. Поиск нашёл текст.
   → `UPDATE tbl_songs SET source_text = ?, id_status = 1 WHERE id = 1`.
   → Итог: название и тональность — администратора, текст — найденный, статус `1`.
2. **Текст уже есть.** Пока шёл поиск, админ вставил текст вручную.
   → `Skipped(EXISTING_TEXT)`, в БД ничего не меняется, INFO-лог.
3. **Статус ушёл вперёд.** Редактор перевёл песню в `2`. Поиск нашёл текст.
   → `UPDATE … SET source_text = ?` (без `id_status`), статус остаётся `2`.
4. **Песня удалена.** → `NotFound`, никакой вставки, WARN, импорт продолжается.
5. **Два поиска одновременно.** Оба читают под блокировкой: первый пишет текст, второй видит
   непустой `source_text` → `Skipped(EXISTING_TEXT)`.
