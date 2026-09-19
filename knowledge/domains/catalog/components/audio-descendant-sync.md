# Спецификация компонента: sync-audio-descendants

> **Домен**: [catalog](../domain.md)
> **Компонент**: механизм «Синхронизация аудио-потомков» (OpenProject #141,
> specs/413-sync-audio-descendants) — точка входа `object SyncAudioDescendants`.

## Ответственность | Responsibility

Песня-потомок (`audio_parent_id` указывает на родителя) — по сути копия родителя
по звучанию. Компонент обеспечивает **отражение правок контента родителя в его
прямых аудио-потомках** с учётом аудио-сдвига: при сохранении родителя
(статус ≥ 5) с изменённым контентом или при переходе статуса `<5 → ≥5`
синхронизация асинхронно переносит в потомков актуальный текст/маркеры/форматирование,
обновляет аудио-метрики, переводит потомка в статус 5 и перегенерирует `.srt`.

**Граница**: компонент НЕ:
- ищет аудио-родителей (это [`findAudioParentByWaveform`](../../../system/utilities.md),
  specs/283-admin-find-parent);
- синхронизирует двух-БД (это `SyncTarget`/`SyncRegistry`; запись — только LOCAL);
- рендерит MP4 (это [rendering](../../rendering/domain.md)).

## Интерфейсы и Контракты | Interfaces and Contracts

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SyncAudioDescendants.kt`.

- **`CONTENT_FIELDS`**: `Set<String>` — имена content-полей `Song.getDiff`
  (`source_text`, `result_text`, `source_markers`, `formatted_text_song`,
  `formatted_text_tabs`, `formatted_text_chords`), изменение которых запускает синхронизацию.
- **`shouldEnqueue(newStatus, oldStatus, changedContentFields, songType, markersEmpty, suppressTriggeredBySync): Boolean`**
  — чистая функция гейта (см. «Логика»).
- **`onSongSaved(song, previous, changedContentFields)`** — хук из `Song.saveToDb()`;
  гейт + постановка родителя в очередь. Не блокирует сохранение.
- **`suppressTriggers(block)`** — выполняет блок, подавляя триггеры в текущем потоке
  (используется `saveToDbLocked()` для pre-commit `saveToDb()`).
- **`syncOneParent(parentId): SyncAudioResult`** — синхронизация всех прямых потомков
  одного родителя.
- **`syncAll(): String`** — массовый проход (admin); `"OK"` / `"ALREADY_RUNNING"`.
- **HTTP**: `POST /api/utils/syncaudioparents` (см.
  `specs/413-sync-audio-descendants/contracts/http-endpoint-syncaudioparents.md`).

## Логика и Алгоритмы | Logic and Algorithms

### Гейт запуска

Синхронизация запускается, если **все** условия выполнены:

1. `newStatus >= 5` (родитель готов). Изменения при статусе < 5 потомков не трогают.
2. Изменены content-поля **ИЛИ** статус перешёл из `<5` в `>=5` (например 2→5, 2→6).
   Переход 5→6 повторно синхронизацию не запускает.
3. Для типа «Песня» (`song`) маркеры непусты; для `instrumental`/`poetry` пустые допустимы.
4. Запись не порождена самой синхронизацией (флаг `inSync` в ThreadLocal).

Это отсекает ~80 фоновых вызовов `Song.saveToDb()` (sync/autopublish/workers/HealthReport),
не меняющих контент.

### Очередь и воркер

- `pendingParents: LinkedHashSet<Long>` — дедупликация по id + порядок постановки.
- Один фоновый воркер (single-flight): авто-хук и admin-функция используют одну очередь,
  одновременно два прохода невозможны.
- Админ-проход (`syncAll`) ставит всех уникальных родителей с потомками `id_status < 6`
  и по завершении рассылает SSE-сводку.

### Перенос контента (`syncAudioDescendantFromParent`)

1. `child.sourceText = parent.sourceText` — число голосов потомка становится равным родителю.
2. `child.resultText = parent.resultText`.
3. `child.sourceMarkers = shiftMarkersAndFixEnd(parent.sourceMarkers, deltaMs, child.ms)`
   (END-маркер — по реальной длительности потомка).
4. `child.formattedText*` = из родителя.
5. `audioSimilarityPercent` / `audioDeltaMs` — свежая сверка;
   `audioCompareHistory` — запись пары заменяется/добавляется.
6. `idStatus = 5`, один `saveToDbLocked()` (атомарно, specs/299).
7. `.voice{N}.srt` перегенерируются (`chmod 666`).

### Сверка и пропуски

- `WaveformCompare.compareWaveforms(child, parent)`, порог `AUDIO_PARENT_THRESHOLD` (95 %).
- Схожесть < 95 % → пропуск `below_threshold`, данные/статус не меняются.
- Активный `KaraokeProcess` у потомка → пропуск `active_process`.
- Тип потомка ≠ тип родителя — синхронизируется.
- Только прямые дети, без рекурсии (подавление через `inSync`).

### Краевые случаи

- Родитель/потомок не найден → пропуск + лог.
- Исключение на песне не прерывает проход (логируется).
- Запись только в LOCAL-БД, без push на прод.

## Зависимости | Dependencies

- → [`Song`](song-entity.md) — поля `audioParentId`, `idStatus`, `songType`,
  `sourceText`/`sourceMarkers`/`formattedText*`, `ms`.
- → [`song-lifecycle`](song-lifecycle.md) — статус 5 как предфинальная вычитка.
- → [`dictionaries`](dictionaries.md) — коды `IdStatus`, `SongType`.
- → [processing/async-process-queue](../../processing/components/async-process-queue.md) —
  `KaraokeProcess.hasActiveProcess` (пропуск активного потомка).
- → `WaveformCompare` (`karaoke-app/.../WaveformCompare.kt`) — аудио-сверка.
- → `shiftMarkersAndFixEnd` (`Utils.kt`) — сдвиг маркеров.
- → [storage](../../storage/domain.md) — `KSS_APP`/`SAC_APP` при загрузке `Song`.
- → SSE (`SNS.send`) — сводка admin-прохода.

## Связанные ADR | Related ADRs

- [local-0002](../../../adr/local-0002-save-exception-handling.md) — не глотать ошибки
  БД молча, логировать и продолжать цикл.
- [0006](../../../adr/0006-processbuilder-redirect-errorstream.md) — `redirectErrorStream`
  (используется в `WaveformCompare`, не переписывается).
- specs/299 — `saveToDbLocked()` (атомарность записи потомка).
- specs/283-admin-find-parent — источник `audio_parent_id`.

## Связанные фичи

- `specs/413-sync-audio-descendants` (#141) — сам механизм.

## Код (физическая реализация)

- `karaoke-app/.../SyncAudioDescendants.kt` (new).
- `karaoke-app/.../model/Song.kt` — хук в `saveToDb()`/`saveToDbLocked()`.
- `karaoke-app/.../controllers/ApiController.kt` — `POST /utils/syncaudioparents`.
- `karaoke-app/src/test/.../SyncAudioDescendantsTest.kt` — unit-тесты гейта/очереди.
- `webvue3/src/views/HomeView.vue`, `webvue3/src/components/Songs/store.js` — admin UI.
