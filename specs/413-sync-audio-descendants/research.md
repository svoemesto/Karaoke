# Research: 413 — Синхронизация аудио-потомков (#141)

**Дата**: 2026-09-19
**Спека**: [spec.md](spec.md)
**Цель**: снять технические неизвестные плана: точку перехвата, переиспользование
аудио-сверки, helper переноса, реентерабельность, очерёдность, admin-перебор.

> Все продуктовые решения уже сняты wayfinder-грилем (см.
> [wayfinder-decisions.md](wayfinder-decisions.md), 28 узлов). Здесь —
> только технические решения реализации.

## R1. Точка перехвата сохранения родителя

**Decision**: хук в `Song.saveToDb()` (UPDATE-ветка), после успешного
`ps.executeUpdate()`; для пути `saveToDbLocked()` вызов хука — **после
`connection.commit()`**, а внутренний `saveToDb()` под блокировкой подавляется
ThreadLocal-флагом.

**Rationale**:
- `saveToDb()` — единственная общая точка сохранения; в ней уже есть `diff` и
  `savedSong` (предыдущее состояние) — идеальные входные данные для гейта.
- `saveToDbLocked()` (specs/299) оборачивает транзакцию и вызывает `saveToDb()`
  внутри до `commit()`. Если хук сработает до коммита, фоновый воркер может
  прочитать ещё не зафиксированный статус родителя и ложно пропустить
  синхронизацию (race «transition»). Поэтому для locked-пути хук вызывается
  после commit, а pre-commit вызов подавляется.
- Хук сам по себе не делает тяжёлой работы — только гейт + постановка в очередь
  (не блокирует сохранение).

**Альтернативы**:
- Хук в HTTP-эндпоинтах (`/song/update`, `savesourcetextmarkers`, approve) —
  отклонено: легко забыть новую точку, а `setSourceMarkers` вызывается
  множеством путей.
- Хук только по смене статуса — отклонено: не покрывает правку маркеров
  родителя, который уже в статусе ≥5 (основной кейс #141).

**Гейт (реализация R1)**:
```
oldStatus = savedSong?.idStatus ?: 0
newStatus = this.idStatus
if (newStatus < 5) return                                  // FR-002
contentChanged = diff.any { it.recordDiffName in CONTENT_FIELDS }
transitionIntoReady = oldStatus < 5 && newStatus >= 5       // FR-001/Q13
if (!contentChanged && !transitionIntoReady) return         // FR-003, FR-004
if (this.songType == SongType.SONG && markersEmpty()) return // Q19/Q23
if (SyncAudioDescendants.inSync.get()) return               // FR-020
SyncAudioDescendants.enqueue(this.id)
```
`CONTENT_FIELDS = {source_text, result_text, source_markers,
formatted_text_song, formatted_text_tabs, formatted_text_chords}`
(имена — из `Song.getDiff`, строки 7056-7103).

## R2. Перехват поля — точные имена diff

**Decision**: использовать `RecordDiff.recordDiffName` из `Song.getDiff()`:
`source_text`, `result_text`, `source_markers`, `formatted_text_song`,
`formatted_text_tabs`, `formatted_text_chords`; статус — `id_status`.

**Rationale**: это уже стабильные строковые контракты diff (используются
двух-БД sync и SSE), менять их нельзя; отдельная проверка полей вручную
продублировала бы логику.

## R3. Аудио-сверка пары (родитель ↔ потомок)

**Decision**: переиспользовать `WaveformCompare.compareWaveforms(child, parent)`
(первый аргумент — текущая песня = потомок, второй — кандидат = родитель). Fresh
сверка каждый раз (Q6); результат `deltaMs` затем сдвигает маркеры родителя под
таймлайн потомка через `shiftMarkersAndFixEnd`.

**Rationale**: та же семантика, что в `findAudioParentByWaveform`
(`compareWaveforms(song, candidate)`), и тот же `deltaMs`, который уже
применяет `applyAudioParentMarkers`. Порог — `AUDIO_PARENT_THRESHOLD` (95).

**Альтернативы**: доверять сохранённым `audio_similarity_percent`/`audio_delta_ms`
— отклонено владельцем (Q6): задача явно требует «ещё раз аудио-сверка».

## R4. Перенос контента и выравнивание голосов

**Decision**: новый helper `syncAudioDescendantFromParent(child, parent,
similarityPercent, deltaMs)` в `SyncAudioDescendants.kt`, который:

1. `child.sourceText = parent.sourceText` (весь JSON-список голосов родителя →
   число голосов потомка автоматически становится равным родителю, Q26/Q27);
2. `child.resultText = parent.resultText`;
3. `child.sourceMarkers = shiftMarkersAndFixEnd(parent.sourceMarkers, deltaMs, child.ms)`
   (END-маркер — по реальной длительности потомка, FR-010);
4. `child.formattedTextSong/Tabs/Chords = parent.*`;
5. `child.audioSimilarityPercent = similarityPercent`,
   `child.audioDeltaMs = deltaMs`;
6. `child.audioCompareHistory` — обновить/добавить запись для `parent.id`
   (merge по id, как `historyById` в `findAudioParentByWaveform`);
7. `child.fields[ID_STATUS] = "5"` (FR-017);
8. один `saveToDbLocked()` (атомарно, spec 299);
9. перегенерировать `.voice{N}.srt` + `chmod 666` для каждого голоса
   (FR-012, как в approve/autoAssignOriginalByWaveform).

**Rationale**: `applyAudioParentMarkers` уже делает шаги 1-4 и ставит статус 5, но
не обновляет метрики/history и не пишет `.srt`. Расширять `applyAudioParentMarkers`
рискованно (его вызывает import-путь `ApiController.kt:5350`, где метрики уже
записаны `findAudioParentByWaveform`, а `.srt` для только что созданной песни не
нужны). Поэтому — отдельный helper, переиспользующий `shiftMarkersAndFixEnd`.

**Выравнивание голосов**: присваивание `sourceText`/`sourceMarkers`/`formatted*`
целиком из родителя даёт ровно число голосов родителя; явный `truncateVoicesTo`
не нужен (перезапись JSON-строк). Это и есть «выравнивание до родителя» (Q27).

## R5. Реентерабельность и single-flight

**Decision**: единый `object SyncAudioDescendants` с:
- `ThreadLocal<Boolean> inSync` — подавляет хук в потоке, который сам пишет
  потомка (FR-020, защита от каскада);
- очередь `pendingParents` (synchronized `LinkedHashSet<Long>` — дедупликация по
  id, FR-019);
- один фоновый воркер-поток, который последовательно разгребает очередь;
- счётчики admin-режима для итоговой SSE-сводки.

**Rationale**: паттерн `thread + @Volatile флаг` уже используется в
`customFunction`, `findParentForAuthor`, `findAudioParentForAuthor`
(прецедент). Общий воркер на очередь естественно даёт «общий лок» (FR-021) —
второй проход одновременно невозможен.

**Admin и auto**: `syncAll()` добавляет в ту же очередь всех уникальных
родителей и ставит флаг `adminPending=true`; воркер, разобрав очередь (auto+admin),
при `adminPending` отправляет SSE-сводку и сбрасывает флаг. Повторный клик при
активном admin-проходе возвращает `"ALREADY_RUNNING"`.

**Альтернативы**: `KaraokeProcess` — отклонено владельцем (Q4), прецедент
`customFunction` покрывает этот класс операций.

## R6. Перебор в admin-функции

**Decision**: `SELECT DISTINCT audio_parent_id FROM tbl_songs
WHERE audio_parent_id <> 0 AND id_status < 6` → множество id родителей; каждого
прогнать `syncOneParent(id)` один раз; для каждого потомка — лог-строка.

**Rationale**: ровно «по потомкам, группировка по родителю» (Q10), без N+1 по
всему каталогу.

## R7. Пропуск активного процесса и не найденных

**Decision**: перед записью потомка — `KaraokeProcess.hasActiveProcess(child.id,
WORKING_DATABASE)`; при `true` — skip + лог (Q25). Родитель/потомок не найден при
загрузке — skip + лог. Исключение на песне не прерывает проход (FR-023,
`local-0002` — не глотать молча, логировать).

## R8. Запись `.srt`

**Decision**: после успешного save — для каждого голоса
`val srt = child.convertMarkersToSrt(voice)`; записать в
`"${child.rootFolder}/${child.fileName}.voice${voice + 1}.srt"`; `chmod 666`.
Ошибка записи одного файла — лог, не откат БД (как в approve/autoAssign).

## R9. Обновление `audio_compare_history`

**Decision**: распарсить `child.audioCompareHistoryList`, заменить/добавить запись
с `id = parent.id` (`AudioCompareHistoryEntry(parent.id, similarityPercent,
deltaMs, ok=true, now)`), сериализовать через
`Json.encodeToString(ListSerializer(AudioCompareHistoryEntry.serializer()), …)`.

## R10. Область записи — только LOCAL

**Decision**: синхронизация работает с `WORKING_DATABASE` (= `Connection.local()`),
без `updateRemoteSongFromLocalDatabase` (FR-022). Выравнивание прода — штатным
two-DB sync.

## R11. SSoT-обновления (Constitution IX + `.ssot-map.yml`)

**Decision**:
- новый компонент `knowledge/domains/catalog/components/audio-descendant-sync.md`
  + ссылка в `knowledge/domains/catalog/domain.md` (Linking Protocol);
- обновить `knowledge/domains/catalog/components/song-lifecycle.md`
  (статус 5 как цель синхронизации) и `song-entity.md`;
- `.ssot-map.yml` правило `Song.kt → dictionaries.md` требует изменения
  `dictionaries.md` — добавить там заметку про gate/target статус;
- per-feature документ `docs/features/sync-audio-descendants.md` (FR-009) с
  @see из KDoc.

## Открытые вопросы

Нет. Все NEEDS CLARIFICATION спеки сняты; технические решения выше приняты.
