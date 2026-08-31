# Contract: applyDuplicateOriginal — применение найденного «родителя»

**Дата**: 2026-08-31
**Спека**: [../spec.md](../spec.md)
**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4528`

## Сигнатура

```kotlin
fun applyDuplicateOriginal(
    newSong: Song,
    original: Song,
)
```

## Назначение

Применяет данные «родителя» (`original: Song`, найденного через `findDuplicateOriginal`) к новой песне (`newSong: Song`):

- `root_id = original.id`
- `source_text = original.sourceText`
- `result_text = original.resultText`
- `source_markers = original.sourceMarkers`
- `formatted_text_song = original.formattedTextSong`
- `formatted_text_tabs = original.formattedTextTabs`
- `formatted_text_chords = original.formattedTextChords`
- `id_status = 1` (TEXT_CREATE)

И сохраняет изменения через `Song.saveToDb()`.

## Входные параметры

| Параметр | Тип | Описание |
|----------|-----|----------|
| `newSong` | `Song` | Новая песня (только что импортированная). Должна иметь `id > 0` (быть уже в БД). Используется `id` и `database`, `storageService`, `storageApiClient`. |
| `original` | `Song` | Родительская песня, найденная через `findDuplicateOriginal`. Должна иметь заполненные `sourceText`, `sourceMarkers`, `resultText`, `formattedText*`. |

## Возвращаемое значение

`Unit`. Результат — побочный эффект: `UPDATE tbl_songs SET ...` через `saveToDb()`.

## Поведение

```kotlin
fun applyDuplicateOriginal(
    newSong: Song,
    original: Song,
) {
    // specs/278-fix-key-loss-on-lyrics-search: между Song.createFromPath() и этим saveToDb() может
    // пройти достаточно времени (поиск дубликата через сравнение имён), чтобы параллельный процесс
    // (KEY_BPM_FROM_FILE, DEMUCS2) успел обновить song_tone/song_bpm/url'ы стемов в БД через свой
    // экземпляр Song.saveToDb(). Перезагружаем объект из БД, чтобы getDiff() не включил эти поля
    // в UPDATE (иначе — перезатирание пустыми значениями из stale in-memory объекта).
    val songToSave =
        Song.loadFromDbById(
            id = newSong.id,
            database = newSong.database,
            storageService = newSong.storageService,
            storageApiClient = newSong.storageApiClient,
        ) ?: newSong
    songToSave.rootId = original.id
    songToSave.sourceText = original.sourceText
    songToSave.resultText = original.resultText
    songToSave.sourceMarkers = original.sourceMarkers
    songToSave.formattedTextSong = original.formattedTextSong
    songToSave.formattedTextTabs = original.formattedTextTabs
    songToSave.formattedTextChords = original.formattedTextChords
    songToSave.fields[SongField.ID_STATUS] = "1"
    songToSave.saveToDb()
}
```

## Инварианты

1. **`songToSave.id == newSong.id`** — после reload или fallback на `newSong`.
2. **`songToSave.rootId == original.id`** после присваивания. В `getDiff` (`Song.kt:6831`):
   ```kotlin
   if (settA.rootId != settB.rootId) result.add(RecordDiff("root_id", settA.rootId, settB.rootId))
   ```
   `settA.rootId = original.id` (например, 50), `settB.rootId = 0` (из БД). Diff включает `root_id=50`. UPDATE выполняется.
3. **`songToSave.sourceText == original.sourceText`** — копируется строка.
4. **`songToSave.idStatus == 1L`** после `saveToDb()` — поле `id_status` в БД становится `1`.

## Race condition защита (спека 278)

`Song.loadFromDbById(id = newSong.id, ...)` перед `saveToDb()` гарантирует:

- Если параллельный процесс `KEY_BPM_FROM_FILE` уже записал `song_tone = "Am"` в БД, после reload `songToSave.key = "Am"`.
- Если параллельный процесс `DEMUCS2` уже записал `audio_song = "http://..."` в БД, после reload `songToSave.audioSong = "http://..."`.
- В `getDiff` после присваивания `songToSave.rootId = 50`, `songToSave.sourceText = "..."`, `songToSave.idStatus = 1`:
  - `songToSave.key` НЕ присваивалось (остаётся `"Am"` из reload).
  - `getDiff` сравнивает `this.key = "Am"` и `savedSong.key = "Am"` → НЕ включает `song_tone` в diff → НЕ перезатирает.
  - `getDiff` сравнивает `this.audioSong = "http://..."` и `savedSong.audioSong = "http://..."` → НЕ включает `audio_song` в diff → НЕ перезатирает.
  - `getDiff` сравнивает `this.rootId = 50` и `savedSong.rootId = 0` → ВКЛЮЧАЕТ `root_id` в diff → `root_id = 50` в UPDATE.

Это та же защита, что была введена в спеке 278 — фикс её не нарушает.

## Изменения в спеке 279

**Никаких.** `applyDuplicateOriginal` остаётся без изменений. Фикс локализован только в `findDuplicateOriginal` (см. [find-duplicate-original.md](find-duplicate-original.md)).

## Вызывающие места

| Где | Контекст |
|-----|----------|
| `ApiController.doCreateFromFolder:5415` | После успешного `findDuplicateOriginal` в цикле по `createdList`. Если `original != null` → `applyDuplicateOriginal(newSong, original)`. |
| `Utils.customFunction:159` | В повторном поиске родителей/аудио-родителей. Вызывается из той же фазы 1, что и `findParentCandidateId`. |

## Связь с FR спеки

| FR | Реализация |
|----|------------|
| FR-003 | Копирование `source_text`, `source_markers`, `result_text`, `formatted_text_*` от родителя, `id_status = 1`. |
| FR-007 | Race condition защита через reload-from-db-before-save. |
| FR-009 | Не затрагивается напрямую (аудио-родитель идёт через `applyAudioParentMarkers`). |
| FR-010 | `customFunction` тоже использует — фикс автоматически покрывает. |

## Тестирование

См. [../quickstart.md](../quickstart.md) — ручная проверка сценариев SC-001..SC-007.

## Обратная совместимость

- Сигнатура функции не меняется.
- Поведение для случая «родитель найден и есть текст/маркеры» сохраняется.
- Поведение для случая «родитель без текста» не применяется (вызывающий код не вызывает `applyDuplicateOriginal`, если `findDuplicateOriginal` вернул `null`).
- Поведение race condition защиты (спека 278) сохраняется.
