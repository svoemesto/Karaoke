# Phase 0 — Research: подстановка найденного текста не перезатирает песню

**Feature**: `546-lyrics-search-preserve-song-fields` | **Date**: 2026-10-18 |
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Все факты ниже сняты с диска 2026-10-18 (номера строк — на момент чтения).

---

## R1. Почему reload-и-lock не устранили дефект (и что из этого следует)

**Decision**: не повторять ни паттерн «перечитать перед `saveToDb()`» (спеки 278, 281), ни
паттерн «блокировка строки + `saveToDb()`» (спеки 299, 357). Оба лежат в основе прежних
исправлений и оба не устраняют механизм.

**Evidence**:

- `Song.saveToDb()` (`Song.kt:5258`) в UPDATE-ветке: `savedSong = loadFromDbById(id)` →
  `diff = getDiff(this, savedSong)` (`Song.kt:6843`) → `UPDATE tbl_songs SET <колонки из diff>`
  с bind-значениями `RecordDiff.recordDiffValueNew`, то есть **значениями из `this`**
  (`Song.kt:5462-5520`).
- `Song.saveToDbLocked()` (`Song.kt:5641`): `SELECT id ... FOR NO KEY UPDATE` + загрузка строки
  (`loadFromDbByIdForUpdate`, `Song.kt:8274`), затем проверка «поля, где `this` ≠ БД» с WARN
  `song.locked_save_diff_overlap` — и вызов `saveToDb()` под блокировкой. Блокировка делает
  транзакцию атомарной, но **не меняет источник значений**: `UPDATE` по-прежнему пишет `this`.
- Сценарий #324: `ApiController.doCreateFromFolder` (`ApiController.kt:5324`) ставит в
  `lyricsSearchExecutor` замыкание с `newSong` (`ApiController.kt:5445`), снятым в начале
  импорта; через десятки секунд `applyFoundLyricsIfMissing` (`UtilsAI.kt:144`) меняет
  `sourceText` этого снимка и вызывает `saveToDbLocked()` → в БД уезжают все поля, где снимок
  расходится с текущей строкой: название, `song_tone`, `song_bpm`, URL'ы стемов.

**Rationale**: у вызывающего кода нет способа отличить «я хочу записать это поле» от
«в моём объекте это поле просто устарело». Пока запись строится на diff «объект против БД»,
любой долгоживущий объект будет откатывать чужие изменения — сколько бы раз его ни
перечитывали и как бы ни блокировали строку.

**Alternatives considered**:

- *Ещё раз перечитать перед сохранением* — окно между `SELECT` и `UPDATE` остаётся (именно это
  и было заменено в 299 на блокировку); главное — не решает проблему разных намерений.
- *Обновлять `this` из заблокированной строки перед `saveToDb()`* — затрёт намеренные
  изменения вызывающего (он пришёл записать текст).
- *Оптимистичная блокировка (колонка `version`)* — требует миграции (нарушает FR-013) и
  превращает конфликт в ошибку сохранения: текст будет теряться вместо перезаписи.

---

## R2. Примитив записи ограниченного набора полей

**Decision**: добавить `SongFieldUpdate` — запись **только перечисленных колонок** одной
транзакцией с блокировкой строки:

```
BEGIN                                        (connection.autoCommit = false)
SET LOCAL lock_timeout = '<songSaveLockedTimeoutMs>ms'
SELECT * FROM tbl_songs WHERE id = ? FOR NO KEY UPDATE    → previous | null
<guard по previous: пустой текст, статус 0>  → SKIPPED / NOT_FOUND — commit/rollback
UPDATE tbl_songs SET <только колонки операции> WHERE id = ?
SELECT * FROM tbl_songs WHERE id = ?                       → current (для фактического diff)
COMMIT
<побочные эффекты: SSE, SyncAudioDescendants, remote push>
```

**Rationale**:

- Набор записываемых колонок — часть контракта операции, а не следствие состояния объекта.
  Устаревший объект может влиять только на то, *что* мы решили записать, но не на то, *какие
  поля* попадут в `UPDATE`.
- Блокировка строки сохраняется: guard-решения (пуст ли текст, равен ли статус нулю)
  принимаются по актуальным данным, поэтому «прочитал → решил → записал» атомарно
  (FR-006).
- Повторный `SELECT` после `UPDATE` даёт точный diff для SSE и хука: не нужно угадывать,
  что реально изменилось (например, статус не менялся, потому что guard не сработал).

**Alternatives considered**:

- *Один `UPDATE` с предикатами в `WHERE` (`AND id_status = 0 AND (source_text = '' ...)`)* —
  короче, но не даёт старых значений для SSE/хука и плохо расширяется на операции с 7-8
  полями и условным статусом. Оставлено как возможная оптимизация, не как основа.
- *`UPDATE ... RETURNING *`* — возвращает только новые значения; старые всё равно нужны для
  diff/хука → лишний `SELECT` неизбежен.

---

## R3. Переход `id_status 0 → 1` без понижения

**Decision**: решение о смене статуса принимается **в Kotlin по значению, прочитанному под
блокировкой**: если `previous.idStatus == 0`, в набор записи добавляется `id_status = 1`;
если статус уже больше `0` — колонка `id_status` в `UPDATE` не попадает вовсе.

**Rationale**: FR-003 требует «не понижать статус»; при чтении под блокировкой это обычное
условие, а не SQL-выражение. Так же проще тестировать (чистая функция).

**Alternatives considered**: `CASE WHEN id_status = 0 THEN 1 ELSE id_status END` внутри
`UPDATE` — работает, но скрывает решение в SQL и требует отдельного чтения после записи,
чтобы понять, менялся ли статус.

---

## R4. Guard «не перезаписывать уже существующий текст»

**Decision**: под блокировкой проверяется `previous.sourceText`: пусто (`""`, `"[\"\"]"`,
`NULL`) → пишем; непусто → `SKIPPED_EXISTING_TEXT`, в БД ничего не пишется, событие в лог.

**Rationale**: FR-004; это же поведение заложено в текущем `!song.haveSourceText`
(`Song.kt:383` — `sourceText != "" && sourceText != "[\"\"]"`), но теперь проверка идёт по
актуальному состоянию, а не по снимку.

**Alternatives considered**: предикат в `WHERE` — см. R2 (нет старых значений для лога/SSE).

---

## R5. Побочные эффекты полной записи: что переносится, что нет

**Decision**: примитив после `commit` выполняет:

| Побочный эффект `saveToDb()` | Переносим? | Почему |
|---|---|---|
| SSE `recordChange` (`SNS.send`) | **Да**, по фактическому diff `previous → current` | UI должен увидеть текст и статус без перезагрузки |
| `SyncAudioDescendants.onSongSaved(song, previous, changedContentFields)` | **Да**, с фактическим набором изменённых content-полей | спека 413: изменение `source_text` запускает синхронизацию аудио-потомков |
| `HealthReport.recomputeAndBroadcast` + удаление `SearchResult`/`SearchAsync` при переходе через `idStatus = 6` | **Да**, если `previous.idStatus < 6 && current.idStatus >= 6` | в сценарии подстановки текста переход в 6 не происходит, но условие дешёвое и сохраняет контракт |
| `updateRemoteSongFromLocalDatabase` (при `Karaoke.autoUpdateRemoteSettings`) | **Да** | иначе изменение уедет на прод только следующим «1 клик»/автосинком |
| `renameFilesIfDiff` | Нет | набор полей не содержит имени/автора — переименование файлов невозможно |
| version-bump блок (`idBoosty`/`idSponsr`/`version*`) | Нет | относится к публикационным идентификаторам, которых в наборе нет |
| `markNewsAvailableIfReady()` | Нет | срабатывает только при `idStatus = 6` и готовых флагах; в сценарии недостижимо |

**Rationale**: переносим всё, что наблюдается снаружи (UI, sync, аудио-потомки); не переносим
то, что привязано к полной записи и в новом наборе полей недостижимо.

**Alternatives considered**: вызывать `saveToDb()` и «гасить» лишние поля — невозможно без
изменения самого `saveToDb()` (запрещено FR-009/контрактом 31 вызывающего места).

---

## R6. Тестируемость без БД

**Decision**: логика делится на чистые функции и исполнение:

- `planLyricsWrite(previous: SongSnapshot, foundText: String): LyricsWritePlan` — guard-политика
  (R3, R4) и набор колонок;
- `buildFieldScopedUpdateSql(columns: List<String>): String` — SQL, в котором могут оказаться
  только разрешённые колонки;
- исполнение (`applyFieldUpdate`) — тонкая обвязка JDBC, проверяется ручным чек-листом.

**Rationale**: в `karaoke-app` нет тестовой БД; существующие тесты (`SyncAudioDescendantsTest`,
`KaraokeProcessCleanupTest`) проверяют чистые функции. Тот же стиль даёт быстрые тесты без
инфраструктуры и прямо кодирует FR-002 («ни одного лишнего поля»).

**Alternatives considered**: Testcontainers/Postgres в CI — дорого для проекта и не
используется; mocked `Connection` — хрупко и не проверяет SQL.

---

## R7. Три помощника копирования данных

**Decision**: перевести на примитив `applyDuplicateOriginal` (`Utils.kt:4994`),
`applyAudioParentMarkers` (`Utils.kt:5026`), `applyFamilySongSelection` (`Utils.kt:5047`) —
с наборами полей из [data-model.md](data-model.md). Guard для них — только «статус не
понижается» (текст они копируют осознанно, в т.ч. поверх пустого).

**Rationale**: FR-008; они вызываются в том же импорте и с тем же долгоживущим объектом; после
перевода в проекте не остаётся места, где копирование контента идёт через diff снимка.

**Alternatives considered**: оставить их на `saveToDbLocked()` — тогда механизм дефекта
сохраняется в соседней операции того же сценария, и регрессия вернётся при первом же
совпадении правок.

---

## R8. Что остаётся за рамками

**Decision**: 50 вызовов `saveToDbLocked()` в 14 файлах (в основном
`VkAutoPublishService`, `TelegramAutoPublishService`, `PremiumAutoPublishScheduler`,
`SongReleaseAnnouncementService`) остаются как есть; их перевод — отдельная задача.

**Rationale**: спека #324 (FR-008) называет операции подстановки данных; публикационные
сервисы меняют узкие наборы полей (`id_vk*`, `version_*`, `date_publish`) и требуют
отдельной приёмки (внешние API, откаты). Смешивать это с #324 — риск для публикаций.

**Alternatives considered**: перевести всё сразу — объём и риск не покрываются приёмкой
#324; зафиксировано как технический долг в `docs/features/…` и Knowledge.

---

## R9. Наблюдаемость

**Decision**: события:

- `song.field_update_skipped` — guard не пропустил (текст уже есть / статус уже больше 0);
- `song.field_update_not_found` — строка удалена между поиском и записью;
- `song.field_update_failed` — SQL-ошибка/lock timeout (rollback);
- `song.stale_snapshot_ignored` — в объекте вызывающего были значения, отличные от БД, но они
  не попали в запись (диагностика: сколько полей «устарело»), с прежней категорией
  `infra.prod.ping` для совместимости с `docs/ops/log-correlation.md`.

**Rationale**: FR-010; формат — продолжение `song.locked_save_diff_overlap` из спеки 357,
поэтому существующие grep-и по логам продолжают работать.

**Alternatives considered**: только WARN из `saveToDbLocked` — он теперь не будет вызываться
в этих путях, событие пропадёт.
