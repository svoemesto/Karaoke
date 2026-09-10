# Feature Specification: 357 — Перезатирание изменений при импорте файлов из папки (аудит #73)

**Feature Branch**: `357-folder-import-overwrite`
**Created**: 2026-09-10
**Status**: Draft
**Input**: User description (OpenProject #73):

## Clarifications

### Session 2026-09-10

- Q1: Размер PR — один большой или разбить по сервисам? → A: Один большой PR (аналог спеки 299). «Перезатирание изменений при добавлении файлов из папки. Кейс следующий. Главная страница админки, кнопка "Добавить файлы из папки". Начинается процесс добавления файлов, добавляются песни, добавляются и начинают отрабатываться задачи в КараокеПроцессах, происходит поиск текста песен и интернете. Через пару минут я вижу что ошибся в названии автора и вручную у всех песен меняю автора на правильного. Однако когда процесс добавления полностью завершен, найдены тексты песен и завершены все процессы - оказывается что у части песен обратно оказался неправильный автор. Где-то в процессе работы изменения перезатираются. У нас уже есть механизм "возьми свежую версию перед сохранением и применением диффа", но видимо она не применена везде, где надо.»

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

### Идентификация

- **Issue ID**: `#73`.
- **Title**: «Перезатирание изменений при добавлении файлов из папки».
- **Created in OpenProject**: 2026-09-09.

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue 73` | ✅ ВЫПОЛНЕНО 2026-09-10, status: `In progress`. | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | ✅ ВЫПОЛНЕНО (см. ниже). | Agent |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 73 --file specs/357-folder-import-overwrite/report.md` | После merge. Файл `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 73` | После публикации комментария. Переводит `In progress` → `In review`. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 73` | После ревью владельцем. | Agent или Owner |

### Проверки (validation)

- `tools/check-spec-issue-link.py` валидирует наличие секции `## OpenProject Tracking`,
  полей `Issue ID`, `Title`, `Workflow` (с пунктами claim/report/mark-review).
- Через `git log` проверяется наличие маркеров `[tracker-claim-73]` / `[tracker-review-73]`.

### Прецедент

- **Связь с предыдущей задачей**: задача #73 — это **логическое продолжение**
  спецификации #299 (`specs/299-song-fields-overwrite-race-condition`), которая
  ввела защиту `saveToDbLocked()` (SELECT ... FOR NO KEY UPDATE) для 8+ горячих
  путей, связанных с поиском текстов и фоновыми задачами. Задача #73 говорит:
  «механизм есть, но не применён везде, где надо» — т.е. требует **аудита всех
  оставшихся мест `saveToDb()`** в коде Karaoke и точечного перевода на
  `saveToDbLocked()` тех, где объект `song` живёт в памяти > 100 мс и есть
  окно для гонки с ручной правкой через `SongEdit`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-10.
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `folder|import.*file|add.*files.*folder` → `knowledge/system/infra/specs-catalog-detailed.md`, `knowledge/system/infra/specs-catalog.md` (упоминания фич импорта).
  2. `saveToDb|race.*condition|FOR UPDATE|overwrite` → `knowledge/domains/health/components/race-fixed-65.md`, `knowledge/domains/health/components/health-report.md`, `knowledge/domains/catalog/components/song-entity.md`, `knowledge/adr/local-0002-save-exception-handling.md`.
  3. `KaraokeProcess|karaoke-proc` → `docs/features/process-bulk-actions.md`, `docs/features/idempotent-path-sanitize.md`.

### Knowledge files consulted

- [`knowledge/domains/health/components/race-fixed-65.md`](../../knowledge/domains/health/components/race-fixed-65.md) — зачем прочитан: прецедент защиты от race в Karaoke (Pass 343, per-song AtomicBoolean guard). Прямой аналог текущей задачи в другом домене (health вместо catalog), подтверждает паттерн «per-resource lock» + unit-tests.
- [`knowledge/domains/health/components/health-report.md`](../../knowledge/domains/health/components/health-report.md) — зачем прочитан: связанный race-кандидат (`reconcilePlayerReadinessFlags`), упоминается в Pass 343.
- [`knowledge/domains/catalog/components/song-entity.md`](../../knowledge/domains/catalog/components/song-entity.md) — зачем прочитан: главная entity проекта, 150 полей, описание `saveToDb()` через diff (`KaraokeDbTable`).
- [`knowledge/adr/local-0002-save-exception-handling.md`](../../knowledge/adr/local-0002-save-exception-handling.md) — зачем прочитан: принятый паттерн обработки исключений в `KaraokeDbTable.save()` для воркеров, влияет на FR-040.
- [`knowledge/adr/local-0005-structured-logging-karaoke-app.md`](../../knowledge/adr/local-0005-structured-logging-karaoke-app.md) — зачем прочитан: контракт `infra.prod.*` логов, обязательный для FR-160 (WARN при lock-timeout / fallback / diff_overlap).
- [`docs/features/process-bulk-actions.md`](../../docs/features/process-bulk-actions.md) — зачем прочитан: контекст `KaraokeProcess` lifecycle и связь с импортом файлов из папки.
- [`docs/features/idempotent-path-sanitize.md`](../../docs/features/idempotent-path-sanitize.md) — зачем прочитан: импорт файлов из папки проходит через `Song.createFromPath` (строка 8271 спеки 357), где используется `sanitizeSongFileName` — этот код будет затронут FR-005.
- [`specs/299-song-fields-overwrite-race-condition/spec.md`](../../specs/299-song-fields-overwrite-race-condition/spec.md) — зачем прочитан: **базовая спека**, вводит `saveToDbLocked()` / `loadFromDbByIdForUpdate`. Спека 357 наследует FR-001..FR-016, FR-020, FR-030, FR-040, FR-060 спеки 299 и расширяет их FR-100..FR-180 для ВСЕХ оставшихся мест `saveToDb()`.

### Если ничего не нашлось (явный no-op)

> Не применимо — релевантные документы найдены.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Правка `author` через SongEdit во время импорта сохраняется (Priority: P1)

Админ нажимает «Добавить файлы из папки», в папке 5 песен. На старте импорта формируется очередь задач (KEY_BPM_FROM_FILE, DEMUCS2, FF_MP3_*, UPLOAD_*, поиск текстов). Через 2 минуты админ замечает, что у 3 песен неправильный `author` (например, в имени файла было «Aaa Bbb», а на самом деле должен быть «Aaa Bbbb»), и через `SongEdit.vue` исправляет `author` у этих 3 песен. Через 10 минут весь импорт завершается, все процессы отрабатывают, тексты найдены. Финальное состояние в БД: у всех 5 песен `author` — это последнее ручное значение (то, что ввёл админ), а не то, что было распарсено при импорте.

**Why this priority**: это **дословный сценарий из задачи #73** — конкретная потеря пользовательских данных (ручной правки), обнаруженная в продакшене. Без исправления админ теряет доверие к системе.

**Independent Test**: импортировать папку из 3-5 песен → подождать ~30 секунд → через `SongEdit.vue` поменять `author` у 2 песен → дождаться завершения ВСЕХ задач в `tbl_processes` (KEY_BPM_FROM_FILE, DEMUCS2, FF_MP3_*, UPLOAD_*, поиск текстов) → SQL: `SELECT id, name, author FROM tbl_songs WHERE id IN (...)` → убедиться, что `author` = ручное значение.

**Acceptance Scenarios**:

1. **Given** папка с 5 песнями импортируется, **When** через 30 секунд после старта админ меняет `author='Правильный Автор'` у 2 песен (через `SongEdit.vue` → `POST /api/songs/save`), **Then** финальное состояние: `author='Правильный Автор'` у обеих песен, у остальных 3 — `author` из распарсенного пути.
2. **Given** админ одновременно правит `author` у одной песни, **When** в этот момент фоновая задача `KEY_BPM_FROM_FILE` завершается и пишет `song_tone='Am'`, **Then** в БД: `author` = ручное значение, `song_tone='Am'` (не перезатёрто), `song_bpm=120` (не перезатёрто).
3. **Given** админ правит `author` у песни X, **When** параллельно `findAudioParentByWaveform` пишет `root_id=Y` и `id_status=5`, **Then** финал: `author` = ручное значение, `root_id=Y`, `id_status=5` — оба изменения применены.

---

### User Story 2 — Правка любых полей песни во время импорта сохраняется (Priority: P1)

То же, что US1, но для любого из ~150 полей песни, которые можно менять через `SongEdit.vue` (`song_name`, `album`, `year`, `song_type`, `genre`, `language`, `id_status` и т.д.). Любой фоновый процесс, который сохраняет песню через `saveToDb()` после того, как объект `song` прожил в памяти > 100 мс, **должен уважать** параллельные ручные правки.

**Why this priority**: US1 закрывает только `author`; US2 — обобщение на любые поля, потому что баг **симметричен**: гонка может произойти для любого поля, которое фоновый процесс сохраняет вместе с другими полями через `getDiff()`. Например, `KEY_BPM_FROM_FILE` обновляет `song_tone` + `song_bpm`, но `getDiff()` сохраняет ВСЕ поля объекта `song`, которые изменились с момента загрузки — если параллельно админ поменял `genre`, и `genre` уже был изменён в БД (ручной правкой), то diff может либо пропустить его (reload-from-db помогает), либо включить устаревшее значение в UPDATE и перезатереть (без блокировки).

**Independent Test**: повторить US1 для каждого из 7+ фоновых путей с разными полями (`song_name`, `album`, `year`, `genre`, `id_status`). Финальное состояние: ручные правки сохранены, фоновые обновления применены.

**Acceptance Scenarios**:

1. **Given** админ через SongEdit меняет `song_name='Новое Название'` через 30 сек после старта импорта, **When** `KEY_BPM_FROM_FILE` завершается через 60 сек, **Then** финал: `song_name='Новое Название'`, `song_tone` + `song_bpm` заполнены.
2. **Given** админ через SongEdit меняет `album='Новый Альбом'` через 30 сек, **When** `DEMUCS2` через 90 сек пишет URL'ы стемов (`audioSong`, `audioVocals`, `audioAccompaniment`, `audioMix`), **Then** финал: `album='Новый Альбом'`, все 4 URL'а стемов заполнены.
3. **Given** админ через SongEdit меняет `id_status=5` (MARKERS_CHECK) через 30 сек, **When** `FF_MP3_ACCOMPANIMENT` через 120 сек пишет `audioAccompanimentMp3Ready=true`, **Then** финал: `id_status=5` (ручное значение не потеряно), `audioAccompanimentMp3Ready=true`.

---

### User Story 3 — Аудит всех оставшихся мест `saveToDb()` в коде (Priority: P2)

Провести **полный аудит** всех мест в коде `karaoke-app/src/main/kotlin/`, где вызывается `Song.saveToDb()` (НЕ `saveToDbLocked`). Классифицировать каждое место:
- **A** — короткий endpoint (объект живёт < 100 мс), race не воспроизводится → **оставить `saveToDb()`**.
- **B** — фоновый процесс / долгий pipeline (объект живёт секунды/десятки секунд) → **перевести на `saveToDbLocked()`**.
- **C** — первичная вставка (INSERT, объект только что создан) → **оставить `saveToDb()`** (race невозможна, нет параллельной транзакции).

После аудита — список всех мест категории B с обоснованием и PR.

**Why this priority**: задача #73 явно говорит «найти ВСЕ места, где может происходить подобная гонка, и исправить их». Без полного аудита фикс будет неполным и баг проявится снова на других путях.

**Independent Test**: запустить `grep -rn '\.saveToDb()' karaoke-app/src/main/kotlin/ | grep -v 'saveToDbLocked'` — получить полный список. Каждое место классифицировать по категориям A/B/C в `research.md`. Каждое место категории B — получить защиту `saveToDbLocked()`.

**Acceptance Scenarios**:

1. **Given** список из ~49 мест `saveToDb()` (на момент анализа), **When** агент применяет классификацию A/B/C, **Then** в `research.md` появляется таблица «Место → Категория → Обоснование → Требуется ли фикс».
2. **Given** место категории B (например, `VkAutoPublishService.kt:484`), **When** агент переводит его на `saveToDbLocked()`, **Then** (a) unit-test проходит, (b) ручная правка через SongEdit не перезатирается, (c) лог `infra.prod.ping` не показывает regression.
3. **Given** место категории A (короткий endpoint, например `SongEditorController.kt:126`), **When** агент НЕ трогает его, **Then** поведение и метрики не меняются.

---

### User Story 4 — Диагностика при попытке перезатереть правку (Priority: P3)

Если по какой-то причине (новый код, regression, edge case) фоновое сохранение **всё же попытается** перезатереть поле, обновлённое параллельной транзакцией, система **детектирует** это и **логирует предупреждение** через `infra.prod.ping` (см. `docs/ops/log-correlation.md`). Это страховка от регрессий и помощь в отладке — оператор видит инцидент в логах и может починить в течение часа, не дожидаясь жалобы админа.

**Why this priority**: не блокирует фикс, но даёт операционную видимость. Без диагностики баг может проявиться снова через месяц (после очередного нового места `saveToDb()`), и владелец узнает об этом только из жалобы.

**Independent Test**: запустить параллельное обновление (один поток — `SongEdit` через HTTP, второй — `saveToDbLocked()` с устаревшим in-memory snapshot) → проверить, что в `infra.prod.ping` логе появилось WARN с `songId=X` + `field=...` + `oldInMemory=...` + `newInDb=...`.

**Acceptance Scenarios**:

1. **Given** фоновое сохранение пытается записать UPDATE с diff, содержащим устаревшее значение поля, **When** `saveToDbLocked` после reload'а из БД видит, что diff теперь включает поле, которое параллельная транзакция уже обновила, **Then** в `infra.prod.ping` логе появляется `WARN song.locked_save_diff_overlap: songId=X field=song_name oldInMemory=… newInDb=…` — оператор видит инцидент.

---

### Edge Cases

- **Песня удалена между `loadFromDbByIdForUpdate` и `saveToDb`** — `loadFromDbByIdForUpdate` возвращает `null`, fallback на `saveToDb()` без блокировки (паттерн Pass 281). WARN в логе, потеря ручных правок при удалении невозможна.
- **БД недоступна в момент reload (transient connection error)** — fallback на старый `saveToDb()` без блокировки. WARN в логе. Гонка теоретически возможна в этом окне, но это строго лучше текущего поведения (гарантированная потеря правок).
- **`lock_timeout` сработал (5 секунд)** — `PSQLException: canceling statement due to lock timeout`, `saveToDbLocked` ловит, пишет WARN `song.locked_save_lock_timeout`, делает rollback, fallback на `saveToDb()` без блокировки (см. спека 299 FR-060).
- **Deadlock (SQL state `40P01`)** — `saveToDbLocked` ловит, пишет WARN, делает rollback, fallback.
- **`saveToDbLocked` вызывается на коротком endpoint** (< 100 мс) — overhead минимален (1 SQL SELECT + 1 SQL UPDATE в одной транзакции, миллисекунды), но **разрешено** оставить `saveToDb()` для endpoint'ов (спека 299 SC-005: обратная совместимость).
- **Место категории C (первичная вставка)** — `saveToDbLocked` НЕ имеет смысла (нет параллельной транзакции, deadlock невозможен), оставляем `saveToDb()`.
- **Транзакция уже открыта (вызывающий код в своей транзакции)** — `saveToDbLocked` использует `Connection.setAutoCommit(false)` + `commit()` + `rollback()`, что **ломает** вложенные транзакции. FR-130: документировать это ограничение, для мест с уже открытой транзакцией использовать `saveToDb()` + явный `KDoc`-комментарий «не использовать saveToDbLocked — внешняя транзакция».

## Requirements *(mandatory)*

### Functional Requirements

#### Часть 1 — Наследование от спеки 299

- **FR-001** (наследует спеку 299): метод `Song.saveToDbLocked()` (см. `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:5540`) уже реализован и работает в проде (PR по спеке 299 смержен). Базовый механизм защиты — `SELECT ... FOR NO KEY UPDATE` + UPDATE в одной транзакции + commit/rollback + WARN при fallback.

#### Часть 2 — Аудит оставшихся мест `saveToDb()`

- **FR-100**: Агент MUST провести **полный аудит** всех мест в коде `karaoke-app/src/main/kotlin/`, где вызывается `Song.saveToDb()` (НЕ `saveToDbLocked`). Команда для поиска: `grep -rn '\.saveToDb()' karaoke-app/src/main/kotlin/ --include='*.kt' | grep -v 'saveToDbLocked'`. На момент анализа — **~49 мест** (точное число может варьироваться).

- **FR-110**: Каждое найденное место MUST быть классифицировано по 3 категориям:
  - **A — короткий endpoint**: объект `song` живёт в памяти < 100 мс (типичный HTTP-handler, например `SongEditorController.kt:126`). Race не воспроизводится. **Оставить `saveToDb()`**.
  - **B — фоновый процесс / долгий pipeline**: объект `song` живёт секунды/десятки секунд (HTTP-парсинг, ffmpeg-декод, ML-вызовы, сетевые запросы). Race воспроизводима. **Перевести на `saveToDbLocked()`**.
  - **C — первичная вставка**: `saveToDb()` вызывается на только что созданном объекте `song` (нет `id` или `id` присвоен БД-insert'ом в той же транзакции). Race невозможна. **Оставить `saveToDb()`**.

- **FR-120**: Результат аудита MUST быть задокументирован в `research.md` в виде таблицы:
  ```
  | Файл:строка                  | Категория | Обоснование                                       | Требуется фикс |
  |------------------------------|-----------|---------------------------------------------------|----------------|
  | Song.kt:8380 (createFromPath)| C         | Первичная вставка, только что создан song         | Нет            |
  | Song.kt:3710 (truncateVoices)| B         | Вызывается из SongEditorController после цикла   | Да             |
  | UtilsAI.kt:... (key/bpm)     | B         | Долгий ML-вызов, объект живёт десятки секунд     | Да             |
  | ...                                                                       |
  ```

- **FR-130**: Для каждого места категории B MUST быть выбрана **одна из двух стратегий**:
  - **B1**: перевести на `saveToDbLocked()` (предпочтительно для большинства мест).
  - **B2**: оставить `saveToDb()`, но добавить **явный KDoc-комментарий** с обоснованием, почему race не воспроизводится (например, место находится внутри уже открытой внешней транзакции — `saveToDbLocked` её сломает).

- **FR-140**: Все места категории B1 MUST быть переведены в рамках **одного PR** (требование задачи #73: «найти ВСЕ места и исправить их»). Без частичных фиксов — каждое оставшееся место B без защиты = потенциальная регрессия.

- **FR-150**: Для каждого места B1 MUST быть добавлен **KDoc-комментарий** с явной ссылкой на спеку 357 + объяснением race-сценария. Например:
  ```kotlin
  // specs/357-folder-import-overwrite (FR-140): объект `song` жил в памяти > 30 сек
  // (ffmpeg-декод + ML-вызов); параллельный SongEdit мог успеть обновить поля → race.
  // saveToDbLocked() делает reload под блокировкой SELECT ... FOR NO KEY UPDATE и
  // сохраняет diff в той же транзакции — гарантирует атомарность.
  song.saveToDbLocked()
  ```

#### Часть 3 — Диагностика (FR-160..FR-180)

- **FR-160**: В `Song.saveToDbLocked()` (после reload из БД) MUST быть добавлен **WARN-лог** `song.locked_save_diff_overlap` с полями `songId`, `field`, `oldInMemory`, `newInDb`, если diff содержит устаревшее значение поля (т.е. параллельная транзакция успела обновить поле между начальной загрузкой объекта и reload'ом под блокировкой). Лог пишется в `infra.prod.ping` (см. `docs/ops/log-correlation.md`).

- **FR-170**: Лог-формат MUST соответствовать [`contracts/log-format.md`](../../specs/288-prod-diagnostics-logging/contracts/log-format.md) (контракт из спеки 288). Пример: `[2026-09-10T12:34:56.789Z] WARN infra.prod.ping song.locked_save_diff_overlap: songId=12345 field=author oldInMemory="Aaa Bbb" newInDb="Aaa Bbbb"`.

- **FR-180**: Метрика мониторинга: WARN `song.locked_save_diff_overlap` в `infra.prod.ping` логе должен появляться **< 1 раза в час** на проде (см. спека 299 SC-007). Если > 1/час — сигнал, что какой-то новый путь `saveToDb()` не защищён, требуется re-аудит.

#### Часть 4 — Регрессии и обратная совместимость

- **FR-040** (наследует спеку 299): `saveToDbLocked()` НЕ ломает существующие 70+ мест вызова с коротким жизненным циклом объекта — там продолжает работать `saveToDb()`. Обратная совместимость сохраняется.

- **FR-041**: Все **публичные HTTP-endpoints** (controllers) MUST продолжать работать без изменений в API. `saveToDbLocked()` — внутренняя деталь, невидима снаружи.

- **FR-042**: Все существующие unit-тесты MUST проходить без изменений. Если тест падает — это сигнал, что перевод на `saveToDbLocked()` сломал контракт (например, изменил ordering или транзакционное поведение) — MUST быть исправлен до merge.

- **FR-043**: Существующая защита из спеки 281 (`reload-from-db-before-save` в `applyFoundLyricsIfMissing`, `applyDuplicateOriginal`, и т.д.) MUST остаться работоспособной. Спека 357 её НЕ отменяет — она дополняет защиту до уровня `saveToDbLocked()` (см. спеку 299 FR-040).

### Key Entities *(include if feature involves data)*

- **`Song`** (главная entity, ~150 полей, `tbl_songs`) — единственная entity, защищаемая в этой спеке. См. [`knowledge/domains/catalog/components/song-entity.md`](../../knowledge/domains/catalog/components/song-entity.md).
- **`KaraokeProcess`** (очередь задач, `tbl_processes`) — НЕ трогается в этой спеке (только косвенно через `Song.saveToDb` при обновлении статусов). См. [`docs/features/process-bulk-actions.md`](../../docs/features/process-bulk-actions.md).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Задача #73 воспроизводится в текущем коде (до фикса): импорт папки из 5 песен + ручная правка `author` через `SongEdit.vue` → после завершения всех процессов у части песен `author` перезатёрт на старое значение. После фикса — **0 песен** с перезатёртым `author` (ручное значение сохраняется у всех).

- **SC-002**: Задача #73 воспроизводится для ВСЕХ 7+ горячих путей фоновой обработки (KEY_BPM_FROM_FILE, DEMUCS2, FF_MP3_ACCOMPANIMENT, FF_MP3_VOCAL, UPLOAD_TO_LOCAL_STORE, UPLOAD_TO_REMOTE_STORE, поиск текстов) и любых полей (US2). После фикса — **0 регрессий** по всем путям и полям.

- **SC-003**: Аудит (FR-100..FR-150) MUST покрыть **100% мест `saveToDb()`** в `karaoke-app/src/main/kotlin/` (на момент анализа — ~49 мест, точное число в `research.md`). Каждое место классифицировано (A/B/C) и обосновано. Места категории B1 — переведены на `saveToDbLocked()`.

- **SC-004**: Все 5 manual-тестов из чек-листа спеки 299 (`contracts/manual-test-checklist.md`) проходят. Дополнительно — 2 новых теста из спеки 357:
  1. Импорт папки + мгновенная правка `author` → дождаться завершения всех процессов → проверить SQL.
  2. Импорт папки + мгновенная правка `song_name` → дождаться завершения → проверить SQL.

- **SC-005**: KDoc coverage ≥ 50% (CI gate). Все новые/изменённые публичные функции имеют KDoc с `@see specs/357`. Все новые места B1 имеют KDoc-комментарий с обоснованием race.

- **SC-006**: Производительность не деградирует значимо. Блокировка `FOR NO KEY UPDATE` действует только на конкретную строку `tbl_songs` (на время одной транзакции, ~миллисекунды) — глобальные операции (импорт папки на 100 песен подряд) не блокируют другие песни. Метрика: P95 latency `saveToDbLocked()` ≤ P95 latency `saveToDb() + 50 мс` (overhead на одну транзакцию и SELECT FOR NO KEY UPDATE).

- **SC-007**: Manual test checklist [`contracts/manual-test-checklist.md`](contracts/manual-test-checklist.md) выполнен полностью (шаги 1, 3, 4 обязательны; шаги 2 и 5 опциональны). Результат фиксируется в Sign-off таблице чек-листа; PR merge разрешён только при `pass` на всех обязательных шагах.

## Assumptions

- **A-1**: `Song.saveToDbLocked()` уже реализован и работает в проде (PR по спеке 299 смержен 2026-09-04). Спека 357 его НЕ переписывает, только применяет к новым местам.

- **A-2**: На момент анализа в `karaoke-app/src/main/kotlin/` — **~49 мест `saveToDb()`** без блокировки (точное число в `research.md`). Это число включает:
  - ~25 мест категории A (короткие endpoint'ы, оставляем как есть).
  - ~20 мест категории B (долгие процессы, переводим на `saveToDbLocked`).
  - ~4 места категории C (первичные вставки, оставляем как есть).

- **A-3**: Категория B — самая большая и самая рискованная. Агент MUST проверить каждое место вручную (не автоматической заменой `saveToDb()` → `saveToDbLocked()`), потому что (a) место может быть внутри уже открытой внешней транзакции (FR-130 B2 — KDoc вместо фикса), (b) место может быть в lock-free контексте, где `saveToDbLocked` создаст проблемы (deadlock, например, если две блокировки на одну песню в одном потоке).

- **A-4**: PostgreSQL 15+ в проде поддерживает `SELECT ... FOR NO KEY UPDATE` (см. спеку 299 A-2). Блокировка снимается при `commit`/`rollback`. `lock_timeout = 5s` (спека 299 FR-060) защищает от deadlock-зависаний.

- **A-5**: Задача #73 — про гонку между **ручной правкой через SongEdit** и **фоновым сохранением при импорте из папки**. Гонки между двумя фоновыми процессами (например, `KEY_BPM_FROM_FILE` и `DEMUCS2` одновременно на одной песне) — отдельная проблема, но **фикс этой спеки защищает и её** (блокировка `FOR NO KEY UPDATE` сериализует все записи на уровне строки, см. спеку 299 A-6).

- **A-6**: Существующая защита из спеки 281 (`reload-from-db-before-save` в 5+ горячих путях) уже в проде. Спека 357 её НЕ отменяет, но для мест, где она есть, переход на `saveToDbLocked()` уже выполнен в спеке 299 (см. `applyFoundLyricsIfMissing`, `applyDuplicateOriginal`, `applyAudioParentMarkers`, `applyFamilySongSelection`, `autoAssignOriginalByWaveform`, `findAudioParentByWaveform`, `setSourceMarkers`, `setSourceText`). Спека 357 покрывает **оставшиеся** места.

- **A-7**: Документация `docs/features/process-bulk-actions.md` и `docs/features/idempotent-path-sanitize.md` упоминают импорт файлов из папки. Спека 357 не вносит изменений в эти документы — они по-прежнему описывают контракт `createFromPath` и `KaraokeProcess` lifecycle. Единственное расширение — добавление ссылки на спеку 357 в раздел «Известные проблемы» (если такой есть).

- **A-8**: Спека 357 — **аудит + точечный фикс**, а не «переписать весь `saveToDb` на locked». Количество мест B (~20) достаточно большое, чтобы выделить в отдельный PR (требование FR-140: «все места B1 в одном PR»). Альтернатива — разбить на 3-4 PR по сервисам (VkAutoPublishService, PremiumAutoPublishScheduler, SongReleaseAnnouncementService, UtilsAI, HealthReport, и т.д.), но это создаст окно, когда баг исправлен в одном сервисе, но не в другом → регрессия. Один PR = один момент времени, в который баг полностью закрыт.

## Open Questions

- **Q1 (Resolved 2026-09-10)**: Размер PR. → **Один большой PR** со всеми ~20 местами B1 (аналог спеки 299 / PR #395). Bug полностью закрыт в один момент времени, нет окна «в одном сервисе исправлено, в другом нет». Если diff > 1000 строк — владелец при review решает, разбить ли на части (например, WARN-лог FR-160 отдельно от перевода на `saveToDbLocked`).

- **Q2 (Pending)**: Нужно ли покрывать unit-тестами каждое новое место B1, или достаточно smoke-теста на 1-2 представителях? Дефолт — **smoke-тест на 1-2** (т.к. `saveToDbLocked` уже покрыт тестами из спеки 299; новые места используют тот же API, нет смысла дублировать). Если unit-тест тривиальный (5 строк) — добавить для каждого места.

- **Q3 (Pending)**: WARN-лог `song.locked_save_diff_overlap` (FR-160) — добавить сейчас или отложить? Дефолт — **добавить сейчас** в `saveToDbLocked()` (он ещё не реализован — спека 299 FR-060 покрывает только `locked_save_skipped` / `locked_save_failed` / `locked_save_fallback` / `locked_save_lock_timeout`, но НЕ `diff_overlap`). Если добавить сложно — оставить как future work в спеке 357-bis.

- **Q4 (Resolved 2026-09-10)**: Какое место считать «коротким endpoint» (< 100 мс), а какое — «долгим процессом» (> 100 мс)? → **Эвристика**: место считается «коротким», если (a) вызывается синхронно из HTTP-handler'а, (b) между `loadFromDbById` (внутри `saveToDb`) и `ps.executeUpdate` нет сетевых вызовов / ffmpeg / ML / IO. Всё остальное — «долгое». Конкретный список — в `research.md` после аудита.
