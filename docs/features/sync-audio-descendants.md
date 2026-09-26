# Синхронизация аудио-потомков

> **Status**: active
> **Feature Key**: sync-audio-descendants
> **Last Updated**: 2026-09-19

## Что делает

При сохранении песни-родителя (статус ≥ 5) с изменённым контентом — или при переходе
её статуса из `<5` в `≥5` — фоново переносит актуальный текст, маркеры (со сдвигом),
форматирование в её прямых аудио-потомков (`audio_parent_id` указывает на родителя),
обновляет аудио-метрики, переводит потомка в статус 5 и перегенерирует `.srt`. Плюс
кнопка массового прохода на главной странице админки.

## Зачем

Песня-потомок — по звучанию копия родителя, но с собственным аудио-таймлайном. Когда
родитель правится (сдвинули секцию, поправили слово), потомки остаются с устаревшей
разметкой. Раньше синхронизации не было: правку родителя приходилось руками повторять
на каждом потомке. Фича автоматизирует это для владельца/редактора и разово догоняет
весь каталог через админ-функцию.

## Как работает

1. `Song.saveToDb()` / `Song.saveToDbLocked()` после успешного UPDATE вызывают
   `SyncAudioDescendants.onSongSaved`. `saveToDbLocked()` подавляет pre-commit вызов
   и повторяет хук после `commit()` (чтобы воркер видел зафиксированный статус).
2. Гейт `shouldEnqueue`: родитель `idStatus ≥ 5`; изменены content-поля **или** был
   переход `<5→≥5`; для типа «Песня» маркеры непусты; запись не порождена самой
   синхронизацией. Это отсекает фоновые сохранения без правок контента.
3. Родитель кладётся в `LinkedHashSet<Long>` (дедупликация), один воркер-поток
   разгребает очередь последовательно (single-flight; общий и для авто-хука, и для
   admin-функции).
4. Для каждого прямого потомка (`audio_parent_id = parent`, `id_status < 6`):
   - активный `KaraokeProcess` → пропуск;
   - `WaveformCompare.compareWaveforms(child, parent)`; схожесть < 95 % → пропуск;
   - иначе перенос контента (`shiftMarkersAndFixEnd`), метрики/history, статус 5,
     один `saveToDbLocked()`, запись `.voice{N}.srt`.
5. `POST /api/utils/syncaudioparents` (`SyncAudioDescendants.syncAll`) — массовый
   проход: `SELECT DISTINCT audio_parent_id … id_status < 6`, каждый родитель один раз,
   SSE-сводка по завершении.

Диаграмма потока:

```text
Song.saveToDb (UPDATE)
   └─ onSongSaved ─ gate ─► pendingParents ─► worker ─► syncOneParent
                                                       └─ per child: compare ─► shift/copy ─► saveToDbLocked ─► .srt
admin: POST /utils/syncaudioparents ─► syncAll ─► (та же очередь) ─► SSE summary
```

## Инварианты / правила

- **MUST**: запись синхронизации — только LOCAL-БД, без push на прод (см.
  [constitution.md](/.specify/memory/constitution.md), Principle III о двух-БД sync;
  выравнивание прода — штатным sync).
- **MUST**: атомарность записи потомка — один `saveToDbLocked()`
  ([specs/299](/specs/299-song-fields-overwrite-race-condition/spec.md)).
- **MUST**: не глотать ошибки БД молча, логировать и продолжать цикл
  ([local-0002](/knowledge/adr/local-0002-save-exception-handling.md)).
- **MUST**: `ProcessBuilder.redirectErrorStream(true)` (используется в `WaveformCompare`)
  ([ADR-0006](/knowledge/adr/0006-processbuilder-redirect-errorstream.md)).
- **MUST**: Knowledge-SSoT обновляется в том же PR
  ([AGENTS.md § Knowledge SSoT](/AGENTS.md)).
- **SHOULD**: порог схожести — `AUDIO_PARENT_THRESHOLD` (95 %), единый с поиском
  аудио-родителя ([knowledge/system/utilities.md](/knowledge/system/utilities.md)).

## Известные ловушки

- **Точка перехвата в `saveToDb`, а не в HTTP-эндпоинтах**: маркеры сохраняются
  множеством путей (`/song/update`, `savesourcetextmarkers`, `setSourceMarkers` в апруве,
  импорт), поэтому хук стоит в общем методе. Гейт по diff обязателен — `saveToDb()`
  вызывается ~80 раз, в т.ч. фоновыми циклами без правок контента.
- **Pre-commit запись внутри `saveToDbLocked`**: без подавления (`suppressTriggers`)
  и повторного хука после `commit()` синхронизация могла запуститься на ещё не
  зафиксированном статусе родителя; для потоков, читающих через другое соединение,
  это дало бы ложный пропуск (race «transition»).
- **Каскад**: без флага `inSync` запись потомка (а он сам может быть родителем)
  породила бы новый круг. Флаг подавляет хук в потоке синхронизации.
- **Порог 95 % не гарантирует идентичный таймлайн**: поэтому целевой статус — 5
  (предфинальная вычитка), а не 6 (specs/022-song-status-lifecycle).
- **Разное число голосов**: контент родителя копируется целиком, поэтому число голосов
  потомка приводится к родителю (перезаписью JSON-полей).
- **Активный `KaraokeProcess`**: перезапись маркеров под работающим рендером/анализом
  пропускается — процесс читал бы старые данные/сломался.

## Ссылки на ключевые классы/файлы

- [`karaoke-app/.../SyncAudioDescendants.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SyncAudioDescendants.kt) — гейт, очередь, воркер, перенос, `syncAll`.
- [`karaoke-app/.../model/Song.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt) — хук в `saveToDb()`/`saveToDbLocked()`, `shiftMarkersAndFixEnd`.
- [`karaoke-app/.../WaveformCompare.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/WaveformCompare.kt) — аудио-сверка пары.
- [`karaoke-app/.../controllers/ApiController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt) — `POST /utils/syncaudioparents`.
- [`karaoke-app/src/test/.../SyncAudioDescendantsTest.kt`](../../karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SyncAudioDescendantsTest.kt) — unit-тесты гейта/очереди.
- [`webvue3/src/views/HomeView.vue`](../../webvue3/src/views/HomeView.vue) — кнопка и модалка запуска.
- [`webvue3/src/components/Songs/store.js`](../../webvue3/src/components/Songs/store.js) — `syncAudioParentsPromise`.
- [spec `413-sync-audio-descendants`](/specs/413-sync-audio-descendants/spec.md) — полная спека.
- [knowledge компонент](/knowledge/domains/catalog/components/audio-descendant-sync.md) — SSoT-описание.
