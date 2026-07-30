# Contract: точки вызова `SongReleaseAnnouncementService.checkAndAnnounce` + формат текста

Эта фича не добавляет новых публичных HTTP-эндпоинтов. Контракт — внутренний: сигнатура уже
существующей функции не меняется, но у неё появляются два новых вызывающих места, и меняется
содержимое (не формат) генерируемого текста новости.

## Существующая функция (без изменений сигнатуры)

```kotlin
object SongReleaseAnnouncementService {
    /**
     * Находит песни, которые стали публично доступны, но ещё не были анонсированы,
     * и создаёт по каждой новость. Идемпотентно, безопасно вызывать многократно и параллельно
     * из разных вызывающих мест (см. вызывающие точки ниже) — конкурентная защита через
     * ON CONFLICT (song_id) DO NOTHING в SongNewsAnnounced.markAnnounced.
     */
    fun checkAndAnnounce(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): List<Long>
}
```

## Вызывающие точки (три, было — одна)

| # | Место | Модуль | Триггер | Допустимая задержка |
|---|---|---|---|---|
| 1 | `MainController.doChangeRecords` (`POST /changerecords`) | `karaoke-web` | Синхронизация таблиц (без изменений, specs/089) | — (по факту синхронизации) |
| 2 | `SongReleaseAnnouncementScheduler` (новый) | `karaoke-web` | Периодическая проверка, независимая от синхронизации | ~5 минут (см. Clarifications spec.md) |
| 3 | `SongEditorController.approve()` | `karaoke-app` | Апрув задания редактора администратором, после подтверждённого push на сервер | практически мгновенно (секунды) |

Все три вызывают одну и ту же функцию с соответствующим для своего модуля `database`
(`WORKING_DATABASE` в `karaoke-web`, `Connection.remote()` в `karaoke-app`) — без параллельной копии
логики детекции/идемпотентности.

### Контракт вызывающей точки #2 — `SongReleaseAnnouncementScheduler`

```kotlin
@Component
class SongReleaseAnnouncementScheduler(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {
    @Scheduled(fixedDelay = 5 * 60_000L, initialDelay = ...)
    fun checkOnAir() {
        try {
            SongReleaseAnnouncementService.checkAndAnnounce(WORKING_DATABASE, storageService, storageApiClient)
        } catch (e: Exception) {
            println("[SongReleaseAnnouncementScheduler] checkOnAir error: ${e.message}")
        }
    }
}
```

Ошибка не должна прерывать следующий тик планировщика (тот же принцип, что и в `StemJobPollScheduler`/
`StatsCacheScheduler` — `runCatching`/`try-catch` вокруг тела метода).

### Контракт вызывающей точки #3 — `SongEditorController.approve()`

Точка вставки — сразу после существующего блока best-effort push:

```kotlin
if (Karaoke.allowUpdateRemote) {
    try {
        val pushResult = updateRemoteSongFromLocalDatabase(settings.id)
        if (pushResult.created.isNotEmpty() || pushResult.updated.isNotEmpty()) {
            try {
                SongReleaseAnnouncementService.checkAndAnnounce(Connection.remote(), KSS_APP, SAC_APP)
            } catch (e: Exception) {
                println("[SongEditorController.approve] checkAndAnnounce error: ${e.message}")
            }
        }
    } catch (_: Exception) {
    }
}
```

Условие `pushResult.created.isNotEmpty() || pushResult.updated.isNotEmpty()` — обязательное: без него
`checkAndAnnounce` сверялся бы с потенциально устаревшей копией песни на сервере (если push молча не
применился — см. research.md, п.3), что могло бы дать ложноотрицательный результат (не страшно само
по себе — событие поймает либо триггер #1, либо #2 позже — просто не даёт ожидаемой Clarifications
мгновенности) либо ненужную лишнюю проверку без необходимости.

## Формат текста новости (изменение содержимого, не сигнатуры)

`title`/`body`, передаваемые в `News.createAutoAnnouncement(...)` из `checkAndAnnounce`, теперь
включают альбом и год, когда заполнены:

| `song.album` | `song.year` | Текст (пример) |
|---|---|---|
| заполнен | заполнен | `Новая песня: Автор — Название (альбом «Альбом», 2024)` |
| заполнен | не заполнен (`0`) | `Новая песня: Автор — Название (альбом «Альбом»)` |
| не заполнен | заполнен | `Новая песня: Автор — Название (2024)` |
| не заполнен | не заполнен | `Новая песня: Автор — Название` (без изменений относительно specs/089) |

`body` расширяется аналогично. Точная пунктуация уточняется в `tasks.md`/на этапе реализации — важен
инвариант «никаких пустых плейсхолдеров или висящей пунктуации», не конкретный набор скобок/запятых.

## Обратная совместимость

- `NewsDto`/`POST /api/news/list`/`POST /changerecords` — контракты не меняются (см.
  `specs/089-auto-news-song-release/contracts/news-api.md`, актуален без изменений).
- Единственное видимое пользователю изменение — более длинный текст title/body у **новых**
  авто-новостей и более быстрое/независимое от синхронизации появление новости. Уже созданные ранее
  авто-новости (короткий формат без альбома/года) не переформатируются задним числом — это
  единовременная миграция данных не входит в scope этой фичи (не запрашивалась в spec.md).
