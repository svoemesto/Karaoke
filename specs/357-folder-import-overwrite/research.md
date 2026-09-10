# Research: 357 — Аудит мест `Song.saveToDb()` для FR-100

**Дата**: 2026-09-10
**Спека**: [spec.md](spec.md)
**Цель**: Полный аудит всех 49 мест `Song.saveToDb()` (НЕ `saveToDbLocked`) в `karaoke-app/src/main/kotlin/`, классификация по категориям A/B/C, обоснование для FR-100..FR-150.

## Команда аудита

```bash
grep -rn "\.saveToDb()" karaoke-app/src/main/kotlin/ --include="*.kt" \
  | grep -v "saveToDbLocked" \
  | grep -v "//.*saveToDb" \
  | grep -v "KDoc" \
  | grep -v "specs/"
```

Результат: **49 мест** в 9 файлах.

## Эвристика классификации

| Категория | Критерий | Решение |
|---|---|---|
| **A** — короткий endpoint | HTTP-handler, объект `song` живёт < 100 мс, между `loadFromDbById` и `ps.executeUpdate` нет IO/ML/ffmpeg | Оставить `saveToDb()` |
| **B** — долгий процесс | Фоновый pipeline (KEY_BPM_FROM_FILE, DEMUCS2, поиск текстов, авто-публикация, ремонт), объект `song` живёт секунды/десятки секунд | Перевести на `saveToDbLocked()` |
| **C** — первичная вставка | `saveToDb()` вызывается на только что созданном `song` (нет `id` или `id == 0`) | Оставить `saveToDb()` |

## Таблица аудита

| # | Файл:строка | Категория | Обоснование | Требуется фикс? |
|---|---|---|---|---|
| 1 | `services/VkAutoPublishService.kt:484` | **B1** | VK API publish — десятки секунд между load и save | Да → `saveToDbLocked()` |
| 2 | `services/VkAutoPublishService.kt:539` | **B1** | VK API publish (другой код-путь) | Да → `saveToDbLocked()` |
| 3 | `services/PremiumAutoPublishScheduler.kt:312` | **B1** | Scheduler — объект `song` может жить минуты (ожидание cron) | Да → `saveToDbLocked()` |
| 4 | `services/SongReleaseAnnouncementService.kt:438` | **B1** | Announce service — отложенная публикация в Telegram/VK | Да → `saveToDbLocked()` |
| 5 | `services/TelegramUpdatesConsumer.kt:140` | **B1** | Updates consumer — обработка апдейтов от Telegram | Да → `saveToDbLocked()` |
| 6 | `HealthReport.kt:2214` | **B1** | `reconcilePlayerReadinessFlags` — фоновый repair loop, объект живёт минуты | Да → `saveToDbLocked()` |
| 7 | `model/Song.kt:3710` (truncateVoicesTo) | **B1** | Вызывается после цикла `setSourceMarkers` (может жить секунды) | Да → `saveToDbLocked()` |
| 8 | `model/Song.kt:6681` (setNewVersion) | **B1** | Цикл по `versions` — может жить секунды при большом кол-ве версий | Да → `saveToDbLocked()` |
| 9 | `controllers/ApiController.kt:889` | **B1** | `applyFoundLyricsIfMissing` HTTP-путь, но `findYandexSongLyrics` идёт ДО — десятки секунд | Да → `saveToDbLocked()` |
| 10 | `controllers/ApiController.kt:910` | **B1** | Тот же путь, fallback | Да → `saveToDbLocked()` |
| 11 | `controllers/ApiController.kt:2977` | **A** | Короткий endpoint (сразу после получения из БД) | Нет |
| 12 | `controllers/ApiController.kt:5370` | **B1** | `findAudioParentByWaveform` HTTP — десятки секунд ffmpeg | Да → `saveToDbLocked()` |
| 13 | `controllers/ApiController.kt:6963` | **A** | Короткий endpoint (`save` после небольшой логики) | Нет |
| 14 | `controllers/ApiController.kt:6992` | **A** | Короткий endpoint | Нет |
| 15 | `controllers/ApiController.kt:7048` | **A** | Короткий endpoint | Нет |
| 16 | `controllers/ApiController.kt:7074` | **A** | Короткий endpoint | Нет |
| 17 | `controllers/ApiController.kt:7856` | **B1** | `applyFamilySongSelection` HTTP — секунды между load и save | Да → `saveToDbLocked()` |
| 18 | `controllers/ApiController.kt:7861` | **B1** | Тот же путь | Да → `saveToDbLocked()` |
| 19 | `controllers/MainController.kt:184` | **B1** | `setNewVersion`-loop — секунды | Да → `saveToDbLocked()` |
| 20 | `controllers/MainController.kt:1640` | **B1** | ML/ffmpeg в той же функции | Да → `saveToDbLocked()` |
| 21 | `controllers/MainController.kt:1813` | **A** | Короткий endpoint (song value update) | Нет |
| 22 | `controllers/MainController.kt:2002` | **A** | Короткий endpoint | Нет |
| 23 | `controllers/SongEditorController.kt:126` | **A** | Короткий endpoint (save после UI-редактирования) | Нет |
| 24 | `controllers/SongEditorController.kt:131` | **A** | Тот же путь | Нет |
| 25 | `controllers/SongEditorController.kt:418` | **A** | Короткий endpoint | Нет |
| 26 | `Utils.kt:173` (findDuplicateOriginal) | **B1** | Цикл по `songList` — может жить минуты (большая author-выборка) | Да → `saveToDbLocked()` |
| 27 | `Utils.kt:341` (autoAssignOriginalByWaveform) | **B1** | ffmpeg-сверка — десятки секунд | Да → `saveToDbLocked()` |
| 28 | `Utils.kt:668` (applyFoundLyricsIfMissing) | **B1** | Yandex.Sync — десятки секунд | Да → `saveToDbLocked()` |
| 29 | `Utils.kt:1596` (applyFamilySongSelection auto) | **B1** | Секунды | Да → `saveToDbLocked()` |
| 30 | `Utils.kt:1626` (applyFamilySongSelection auto, fallback) | **B1** | Секунды | Да → `saveToDbLocked()` |
| 31 | `Utils.kt:1707` (applyAudioParentMarkers) | **B1** | Секунды | Да → `saveToDbLocked()` |
| 32 | `Utils.kt:1736` (applyAudioParentMarkers fallback) | **B1** | Секунды | Да → `saveToDbLocked()` |
| 33 | `Utils.kt:1739` (applyAudioParentMarkers loop) | **B1** | Цикл — секунды/минуты | Да → `saveToDbLocked()` |
| 34 | `Utils.kt:4126` (setNewVersion) | **B1** | Цикл по версиям — секунды | Да → `saveToDbLocked()` |
| 35 | `Utils.kt:4186` (setNewVersion fallback) | **B1** | Секунды | Да → `saveToDbLocked()` |
| 36 | `Utils.kt:4639` (autoAssignOriginalByWaveform, в searchAllByAuthor) | **B1** | Цикл по `songList` — может жить минуты | Да → `saveToDbLocked()` |
| 37 | `model/Song.kt:8380` (createFromPath) | **C** | Первичная вставка, только что созданный song | Нет |
| 38 | `model/Song.kt:8551` (loadListFromDb filter save) | **B1** | Цикл по `result` — может жить секунды | Да → `saveToDbLocked()` |
| 39 | `model/Song.kt:8568` (loadListFromDb filter save fallback) | **B1** | Секунды | Да → `saveToDbLocked()` |
| 40 | `model/Song.kt:8738` (renameFilesIfDiff) | **B1** | IO между load и save (переименование файлов на диске) | Да → `saveToDbLocked()` |

## Сводка по категориям

| Категория | Кол-во | Требуется фикс |
|---|---|---|
| A (короткий endpoint) | 9 | Нет (оставить `saveToDb()`) |
| B1 (долгий процесс → `saveToDbLocked()`) | **30** | **Да** |
| C (первичная вставка) | 1 | Нет (оставить `saveToDb()`) |
| **Итого** | **40** | — |

> **Примечание**: 49 мест в grep включают KDoc/комментарии и ссылки на `saveToDbLocked()`. После очистки — **40 уникальных мест кода**. Из них **30 мест категории B1** требуют перевода на `saveToDbLocked()`.

## Стратегия реализации

### Один PR (решение Q1 — Clarifications 2026-09-10)

Все 30 мест категории B1 переводятся в **одном PR**, чтобы:
- Bug был полностью закрыт в один момент времени
- Нет окна «в одном сервисе исправлено, в другом нет»
- Review показывает единый diff с явной классификацией
- Rollback при проблемах — один revert

### Шаги в PR

1. **WARN-логирование** (FR-160..FR-180): добавить `song.locked_save_diff_overlap` в `Song.saveToDbLocked()`.
2. **KDoc-обновления** (FR-150): для каждого из 30 мест добавить комментарий с `@see specs/357`.
3. **Точечная замена** `saveToDb()` → `saveToDbLocked()` в каждом из 30 мест.
4. **Manual test checklist** (`contracts/manual-test-checklist.md`) — 7 шагов (наследует 5 из спеки 299 + 2 новых).
5. **Knowledge update** — `knowledge/domains/catalog/components/song-entity.md` обновить (раздел «Методы»), упомянуть `saveToDbLocked()` как рекомендуемый путь для долгих процессов.

### Файлы для изменений

| Файл | Кол-во B1 мест |
|---|---|
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` | 4 (3710, 6681, 8551, 8568, 8738 — итого 5) |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` | 11 |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt` | (см. ниже) |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` | 5 |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt` | 2 |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt` | 2 |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PremiumAutoPublishScheduler.kt` | 1 |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt` | 1 |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramUpdatesConsumer.kt` | 1 |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` | 1 |
| **Итого** | **~30** |

> `UtilsAI.kt` — содержит только комментарии про `saveToDb()`, не сами вызовы. Реальных мест в нём нет.

## Альтернативы, которые были отклонены

### Альтернатива 1: Перевести ВСЕ 40 мест на `saveToDbLocked()`

- **Плюс**: максимальная защита.
- **Минус**: для коротких endpoint'ов — overhead на `SELECT FOR NO KEY UPDATE` + транзакцию без пользы (race не воспроизводится). Также 9 мест категории A включают уже открытые внешние транзакции (например, `SongEditorController` внутри `@Transactional`), которые `saveToDbLocked` сломает.
- **Решение**: НЕ применять — избирательный перевод (FR-130 B1/B2).

### Альтернатива 2: Оптимистическая блокировка (version column)

- **Плюс**: нет блокировки на уровне БД, лучше для высоких нагрузок.
- **Минус**: требует миграции БД (новая колонка `version`), обработки `OptimisticLockException` в каждом из 30+ мест, изменения API.
- **Решение**: НЕ применять — спека 299 уже отвергла этот подход (см. Clarifications Q1 в спеке 299, pessimistic `FOR NO KEY UPDATE`).

### Альтернатива 3: Reload-from-db-before-save (Pass 281)

- **Плюс**: проще в реализации, без блокировки.
- **Минус**: **не атомарен** — между `loadFromDbById` и `ps.executeUpdate` другая транзакция успевает закоммитить изменение (это и есть причина бага #73). Спека 299 уже отвергла (R1).
- **Решение**: НЕ применять — спека 299 FR-001 заменила этот подход на `saveToDbLocked`.

## Открытые вопросы исследования

- **Q-Research-1 (Resolved)**: Нужно ли покрывать каждое новое место B1 unit-тестом? → **Нет** (Q2 в спеке). `saveToDbLocked()` уже покрыт тестами спеки 299. Новые места используют тот же API. Smoke-тест на 1-2 представителях (`VkAutoPublishService`, `applyFamilySongSelection`) — достаточно.
- **Q-Research-2 (Resolved)**: WARN-лог `song.locked_save_diff_overlap` (FR-160) — добавлять сейчас или отложить? → **Сейчас** (Q3 в спеке). Логика тривиальная (diff уже вычисляется, добавить 10 строк кода), даёт операционную видимость.
- **Q-Research-3 (Resolved)**: Должно ли место `model/Song.kt:8738` (renameFilesIfDiff) быть `saveToDbLocked`? → **Да**. `renameFilesIfDiff` переименовывает файлы на диске (FFmpeg/MV) между `loadFromDbById` и `saveToDb` — это IO операция, объект живёт десятки секунд. Race возможна.

## Knowledge impact

- [`knowledge/domains/catalog/components/song-entity.md`](../../knowledge/domains/catalog/components/song-entity.md) — раздел «Методы» дополнить описанием `saveToDbLocked()` как рекомендуемого пути для долгих процессов (race protection).
- [`docs/features/process-bulk-actions.md`](../../docs/features/process-bulk-actions.md) — НЕ трогаем (не относится напрямую).
- [`docs/features/idempotent-path-sanitize.md`](../../docs/features/idempotent-path-sanitize.md) — НЕ трогаем.
