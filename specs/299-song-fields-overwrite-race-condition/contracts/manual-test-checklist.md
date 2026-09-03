# Manual Test Checklist — Перезатирание полей песни при фоне (spec 299)

> **Когда выполнять**: перед merge PR #299 в `master`. Каждый шаг должен пройти успешно; при провале любого — PR **не мержится**, инцидент-репорт в `livedocs/SESSION-SUMMARY.md`.
>
> **Контекст**: спека [spec.md](../spec.md) описывает сценарий US1 — импорт папки → параллельная правка `songName` через SongEdit → завершение поиска текстов → проверка, что ручная правка сохранена. Этот чек-лист формализует acceptance check, т.к. в проекте нет автотестов на `Song.saveToDb()` (см. Constitution §II + AGENTS.md «CI 7/7»).

## Предусловия

- [ ] Ветка `299-song-fields-overwrite-race-condition` собрана: `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` → BUILD SUCCESSFUL.
- [ ] KDoc coverage ≥ 50%: `bash tools/check-kdoc-coverage.sh --strict` → pass.
- [ ] ktlint: `./gradlew ktlintCheck` → pass.
- [ ] На dev-машине поднят `karaoke-app` + `karaoke-web` с новым кодом (см. AGENTS.md «Машинно-специфичные исключения (Pass 282)» — на `nsa-i9` под `nsa` разрешено без явного согласия).
- [ ] В БД есть тестовая песня (создать через `/song/createfrompath` или взять существующую со `songName='ПММЛ'`, `author='Test'`, `source_text=''`, `id_status=0`).
- [ ] `KaraokeProperties.songSaveLockedTimeoutMs` = 5000 (default), `lyricsSearchEngine` = FOURGET (дефолт в спеке 015).

## Шаг 1 — Компиляция и линтеры

```bash
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
./gradlew :karaoke-web:ktlintCheck
```

- [ ] BUILD SUCCESSFUL на обоих шагах.
- [ ] Нет новых ktlint-нарушений (baseline не растёт).

## Шаг 2 — Unit-проверка `applyFoundLyricsIfMissing` через мок (опционально, если есть dev-настройка)

> В проекте нет автотестов для `Song.saveToDb()`; этот шаг — для разработчиков, которые хотят дополнительно проверить логику без полного импорта папки.

Создать временный скрипт (НЕ коммитить в git):
```kotlin
// /tmp/test_song_save_locked.kt
fun main() {
    val song = Song.loadFromDbById(id = 12345L, database = WORKING_DATABASE, ...)!!
    val lyrics = "Найденный текст песни для теста lock'а"
    // Эмулировать фоновый поток: одновременно изменить songName через прямой UPDATE
    Thread {
        Thread.sleep(100)
        val conn = WORKING_DATABASE.getConnection()!!
        conn.autoCommit = false
        val ps = conn.prepareStatement("UPDATE tbl_songs SET song_name = ? WHERE id = ?")
        ps.setString(1, "ИзмененоИзДругогоПотока")
        ps.setLong(2, 12345L)
        ps.executeUpdate()
        conn.commit()
        conn.autoCommit = true
        ps.close()
    }.start()

    // Запустить applyFoundLyricsIfMissing через 200мс (после UPDATE параллельного потока)
    Thread.sleep(200)
    applyFoundLyricsIfMissing(song, listOf(lyrics))
    // Проверить: songName остался "ИзмененоИзДругогоПотока", sourceText = lyrics
    val finalSong = Song.loadFromDbById(id = 12345L, database = WORKING_DATABASE, ...)!!
    assert(finalSong.songName == "ИзмененоИзДругогоПотока") { "songName потерян!" }
    assert(finalSong.sourceText == lyrics) { "sourceText не применён!" }
}
```

- [ ] Параллельный UPDATE НЕ блокирует `applyFoundLyricsIfMissing` дольше 100мс (типично < 5мс с `FOR NO KEY UPDATE`).
- [ ] songName сохранён как "ИзмененоИзДругогоПотока", sourceText = lyrics (без перезатирания).

## Шаг 3 — End-to-end через импорт папки (ОСНОВНОЙ ТЕСТ, обязательный)

1. Подготовить папку с 3 аудиофайлами (например, `/tmp/test-import-299/` с `song1.mp3`, `song2.mp3`, `song3.mp3`).
2. Записать начальные значения:
   ```sql
   SELECT id, song_name, source_text, id_status FROM tbl_songs WHERE song_name IN ('ПММЛ-1', 'ПММЛ-2', 'ПММЛ-3');
   ```
   - [ ] 3 песни с пустым `source_text`, `id_status=0`.
3. Открыть админку в браузере, начать импорт папки `/tmp/test-import-299/` (стандартный сценарий `doCreateFromFolder`).
4. СРАЗУ после старта импорта (в течение 5 секунд, пока фоновая очередь не дошла до поиска текстов):
   - [ ] Открыть карточку **любой** из импортированных песен (например, `ПММЛ-2`).
   - [ ] Изменить `songName` = `П.М.М.Л.-2`, `author` = `ТестовыйАвтор`, нажать «Сохранить».
   - [ ] UI возвращает success, таблица песен обновлена (через SSE).
5. Дождаться завершения поиска текстов для всех 3 песен (1-3 минуты на 4get/SearXNG).
6. Проверить финальное состояние:
   ```sql
   SELECT id, song_name, author, source_text, id_status
   FROM tbl_songs
   WHERE song_name IN ('ПММЛ-1', 'П.М.М.Л.-1', 'ПММЛ-2', 'П.М.М.Л.-2', 'ПММЛ-3', 'П.М.М.Л.-3');
   ```
   - [ ] Песня с `song_name='П.М.М.Л.-2'`: `author='ТестовыйАвтор'`, `source_text` НЕ пустой (найденный), `id_status >= 1`.
   - [ ] `song_name` НЕ вернулся к `'ПММЛ-2'`.
   - [ ] `author` НЕ потерян (остался `'ТестовыйАвтор'`).
   - [ ] Другие 2 песни (без ручной правки) имеют непустой `source_text` и `id_status >= 1`.

## Шаг 4 — SQL-проверка и метрики

1. **Проверить логи `infra.prod.ping` на наличие WARN/ERROR** (см. `docs/ops/log-correlation.md`):
   ```bash
   docker logs karaoke-app 2>&1 | grep -E 'WARN.*song\.(locked_save_fallback|locked_save_failed|lock_timeout)|ERROR' | tail -50
   ```
   - [ ] Нет WARN `song.locked_save_fallback` или `song.locked_save_failed` (за исключением случаев удаления песни во время фоновой обработки — это ожидаемое поведение).
   - [ ] Нет ERROR с trace от `Song.saveToDbLocked`.
   - [ ] Нет deadlock-ов (`deadlock detected`).

2. **Проверить, что Pass 281 acceptance scenarios не сломаны** (быстрая регрессия):
   - Импорт папки из 3 файлов без ручных правок → все 3 имеют `key`/`bpm` (если есть в файлах), `source_text`, `id_status >= 1`.
   - Открыть модалку «Похожие версии песни» → кликнуть по строке → `applyFamilySongSelection` срабатывает без ошибок.

3. **Проверить через SSE, что обновления таблицы песен приходят без задержек > 1 сек** (lock не должен блокировать SSE-нотификации):
   ```bash
   # В отдельной консоли: открыть http://localhost:8897/admin/songs в браузере, смотреть DevTools → Network → EventStream
   ```
   - [ ] Изменение `songName` через SongEdit → SSE-уведомление приходит за < 1 сек.

## Шаг 5 — Откат (если шаги 3-4 провалились)

> **Сценарий**: после merge обнаружилось, что `FOR NO KEY UPDATE` ломает сценарий X (например, `applyAudioParentMarkers` падает с `PSQLException`). Действия:

1. **Немедленно** откатить PR (см. AGENTS.md «CI-gate для master», revert через `git revert`):
   ```bash
   N=$(./tools/reserve-branch-number.sh revert-299-overwrite-race)
   git checkout master
   git pull
   git checkout -b "${N}-revert-299-overwrite-race"
   git revert -m 1 <merge-commit-of-299>
   git push -u origin "${N}-revert-299-overwrite-race"
   gh pr create --base master
   gh pr merge --merge
   ```
2. **Заполнить incident-report** в `docs/architecture-notes.md` (Pass 8 + Constitution §VIII.4): что сломалось, какая часть US1 воспроизвелась, stack trace.
3. **Завести follow-up** в OpenProject (через `tools/tracker.sh create-issue`): `bug: 299 race-condition fix regression in <function>`.
4. **Опционально** — переключиться на optimistic-подход (FR-030-bis) как fallback, если pessimistic окажется фундаментально несовместим.

## Sign-off

| Шаг | Результат | Кто проверил | Дата |
|-----|-----------|--------------|------|
| 1. Компиляция + линтеры | ⬜ pass / fail | | |
| 2. Unit-проверка (опц.) | ⬜ pass / fail / skip | | |
| 3. End-to-end импорт + правка | ⬜ pass / fail | | |
| 4. SQL + логи + SSE | ⬜ pass / fail | | |
| 5. Откат | ⬜ n/a / выполнен | | |

- [ ] Все шаги 1, 3, 4 прошли — PR готов к merge.
- [ ] Если какой-то шаг провалился — см. шаг 5.

## См. также

- [`../spec.md`](../spec.md) — основная спецификация (US1-US4, FR-001..FR-060, SC-001..SC-008).
- [`../checklists/requirements.md`](../checklists/requirements.md) — quality checklist спецификации.
- [`docs/ops/log-correlation.md`](../../../docs/ops/log-correlation.md) — карта логов прода, grep-маркеры `infra.prod.ping`.
- [`specs/281-find-lyrics-overwrites-key-bpm/spec.md`](../../281-find-lyrics-overwrites-key-bpm/spec.md) — Pass 281 (предыдущая итерация фикса, на которой основан этот).
- [`AGENTS.md`](../../../AGENTS.md) — governance, lint-gates, machine-specific rules.
