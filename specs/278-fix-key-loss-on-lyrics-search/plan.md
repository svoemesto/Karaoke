# Implementation Plan: Fix Key/Tone Loss During Lyrics Search in Add-Files-From-Folder

**Branch**: `278-fix-key-loss-on-lyrics-search` | **Date**: 2026-08-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/278-fix-key-loss-on-lyrics-search/spec.md`

## Summary

Устранить race condition в `ApiController.doCreateFromFolder` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:5390`), при которой параллельный процесс `KEY_BPM_FROM_FILE` (и потенциально `DEMUCS2`) записывает тональность (key/bpm) и URL'ы стемов в БД, но последующий `newSong.saveToDb()` после синхронного `findYandexSongLyrics` (Playwright, десятки секунд) использует **stale in-memory объект** и через `getDiff()` включает эти поля в UPDATE — перезатирая их пустыми значениями.

**Технический подход**: перед каждой записью в `doCreateFromFolder` (3 точки: `applyDuplicateOriginal`, `applyAudioParentMarkers`, блок после `findYandexSongLyrics`) перезагрузить объект `Song` из БД через `Song.loadFromDbById(id, WORKING_DATABASE, storageService, storageApiClient)`. Подход локальный, не трогает низкоуровневый `Song.saveToDb()` (используется в 46+ других местах, проверенных на production).

## Technical Context

**Language/Version**: Kotlin 1.x, Spring Boot 2.x/3.x, JDK 17 (см. Constitution §"Технологический стек"). Никаких новых зависимостей не требуется.

**Primary Dependencies**: 
- `KaraokeProcess` / `KaraokeProcessWorker` (async-очередь задач, Principle IV)
- `Song` / `Song.loadFromDbById` / `Song.saveToDb` (сырой JDBC через `KaraokeConnection`, Principle II)
- `HealthReport.startRepairAll` (вызывается в конце `doCreateFromFolder`, не модифицируется)

**Storage**: PostgreSQL через сырой JDBC (`WORKING_DATABASE`). Поведение: SELECT → обновить in-memory `fields` → `saveToDb()` (UPDATE только diff-полей). Никаких изменений схемы БД не требуется.

**Testing**: Существующие тесты в `karaoke-app/src/test` — интеграционные, большинство `@Disabled` (Constitution §"Рабочий процесс"). Проверка — пользователем вручную на production-like окружении через UI «Добавить файлы из папки».

**Target Platform**: Linux-сервер (admin-машина для `karaoke-app`, prod-сервер для `karaoke-web`). Изменения только в `karaoke-app` (бэкенд).

**Project Type**: Backend (Kotlin Spring Boot monolith). Никаких frontend-изменений.

**Performance Goals**: 
- Дополнительная перезагрузка через `Song.loadFromDbById` — это один SELECT по `id` (индексированный PK), занимает миллисекунды.
- `findYandexSongLyrics` уже синхронно занимает 10-60 сек — добавление одной перезагрузки не влияет на воспринимаемую скорость.
- Для 10 песен: +10 SELECT'ов по PK = пренебрежимо по сравнению с общим временем импорта.

**Constraints**: 
- Должна сохраниться совместимость с 46+ существующими точками вызова `Song.saveToDb()` — это запрещает модификацию самого `saveToDb()`.
- Не должно появиться новых `KaraokeProcess*` вызовов (только фикс race в существующем коде).
- Никаких изменений API-контракта (`/api/utils/createfromfolder` остаётся с тем же поведением на success/error).

**Scale/Scope**: 
- Scope: ~10 строк изменений в 1 файле (`ApiController.kt`), ~20 строк изменений в `Utils.kt` (для 3 функций: `applyDuplicateOriginal`, `applyAudioParentMarkers`, и опционально — `setSourceMarkers` если он действительно вызывается из doCreateFromFolder).
- Total LOC: <50.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Принцип I (Self-contained автопайплайн) — ✅ PASS
Изменения только в локальной логике `doCreateFromFolder`, никаких новых внешних API или зависимостей. Playwright (Yandex) уже используется локально.

### Принцип II (Сырой JDBC + дифф по хэшам) — ✅ PASS
Подход полностью построен на существующем механизме `Song.loadFromDbById` (сырой JDBC через `KaraokeConnection`) и `Song.saveToDb()` (UPDATE только diff-полей через recordhash). Никакого JPA/Hibernate, никаких изменений схемы.

### Принцип III (Двух-БД синхронизация через SyncRegistry) — ✅ PASS
Модифицируемые сущности (Song) уже зарегистрированы в `SyncRegistry.all` через `SongSyncTarget`. Никаких изменений в sync-флагах.

### Принцип IV (Async-очередь задач с парсингом stdout) — ✅ PASS
Существующие `KaraokeProcess.createProcess(KEY_BPM_FROM_FILE)` и `KaraokeProcess.createProcess(DEMUCS2)` НЕ модифицируются. Фикс только в синхронной части `doCreateFromFolder`, гарантирующей что sync-`saveToDb()` после длительной операции использует актуальное состояние БД.

### Принцип V (Двух-фронтенд) — ✅ PASS
Изменения только в бэкенде (`karaoke-app`), фронтенд (`webvue3`/`karaoke-public`) не затрагивается.

### Принцип VI (Code Standards) — ⚠️ ТРЕБУЕТ ВНИМАНИЯ
- **FR-006** (Code Standards из Constitution): публичные API MUST сопровождаться KDoc с `@see` на per-feature документ. Изменяемые функции — публичные (`applyDuplicateOriginal`, `applyAudioParentMarkers`) — должны иметь/получить KDoc.
- **FR-009**: при правке кода одной из 9 ключевых подсистем — обновить per-feature документ в том же PR. Импорт файлов — это часть admin-import функционала. Нужно найти соответствующий per-feature документ (см. Plan §"Documentation Update").
- **FR-007**: после правок запустить `./gradlew :karaoke-app:ktlintCheck` — никаких НОВЫХ нарушений baseline.

### Принцип VII (Cross-Machine Setup) — ✅ PASS
Никаких изменений в git-конфигах, `.gitattributes`, `.git-blame-ignore-revs`. Фикс — обычный коммит с понятным message.

### Принцип VIII (Секреты и git-гигиена) — ✅ PASS
Никаких секрет-файлов, никаких изменений в env/deploy-конфигах.

**Constitution Check verdict**: ✅ PASS. Все принципы соблюдены, требуется только KDoc-обновление (Принцип VI/FR-006) и проверка ktlint baseline (Принцип VI/FR-007).

## Project Structure

### Documentation (this feature)

```text
specs/278-fix-key-loss-on-lyrics-search/
├── spec.md              # ✅ Готово (см. /speckit.specify)
├── plan.md              # ✅ Этот файл (/speckit.plan output)
├── checklists/
│   └── requirements.md  # ✅ Готово (Quality checklist)
└── tasks.md             # ⏳ Будет создан в /speckit.tasks
```

### Source Code (repository root)

Изменяемые файлы:
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (1 место: блок после `findYandexSongLyrics` в `doCreateFromFolder`)
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (2 места: `applyDuplicateOriginal`, `applyAudioParentMarkers`)

Документация (FR-009):
- Найти соответствующий per-feature документ в `docs/features/` или `archive/docs/features/`. Кандидаты: `admin-import.md`, `llm-lyrics-search.md`, `async-process-queue.md`. Обновить описание race condition и фикс.

**Structure Decision**: Сохраняем существующую структуру проекта (monorepo: `karaoke-app/`, `karaoke-web/`, `webvue3/`, `karaoke-public/`). Изменения только в `karaoke-app` модуле.

## Implementation Approach

### Шаг 1: Reload перед saveToDb после findYandexSongLyrics

В `ApiController.kt:5461-5466`:
```kotlin
if (yandexLyricsResult is YandexLyricsSearchOutcome.Found && yandexLyricsResult.text.isNotBlank()) {
    val reloadedSong = Song.loadFromDbById(
        id = newSong.id,
        database = WORKING_DATABASE,
        storageService = storageService,
        storageApiClient = storageApiClient,
    ) ?: newSong  // fallback если БД недоступна — старое поведение
    reloadedSong.sourceText = yandexLyricsResult.text
    if (reloadedSong.idStatus == 0L) reloadedSong.fields[SongField.ID_STATUS] = "1"
    reloadedSong.saveToDb()
    textResolved = true
}
```

`reloadedSong` содержит актуальное состояние БД (включая key/bpm от KEY_BPM_FROM_FILE, URL'ы стемов от DEMUCS2). `getDiff(this=reloadedSong, savedSong=loadFromDbById в saveToDb)` увидит только реальные изменения от текущего состояния БД — перезаписи не будет.

**Fallback**: если `loadFromDbById` вернёт null (БД недоступна), используем исходный `newSong` — это безопасный fallback (старое поведение сохраняется, без регрессии).

### Шаг 2: Reload перед saveToDb в applyDuplicateOriginal

В `Utils.kt:4528-4541`:
```kotlin
fun applyDuplicateOriginal(newSong: Song, original: Song) {
    val reloaded = Song.loadFromDbById(
        id = newSong.id,
        database = newSong.database,
        storageService = newSong.storageService,
        storageApiClient = newSong.storageApiClient,
    ) ?: newSong
    reloaded.rootId = original.id
    reloaded.sourceText = original.sourceText
    reloaded.resultText = original.resultText
    reloaded.sourceMarkers = original.sourceMarkers
    reloaded.formattedTextSong = original.formattedTextSong
    reloaded.formattedTextTabs = original.formattedTextTabs
    reloaded.formattedTextChords = original.formattedTextChords
    reloaded.fields[SongField.ID_STATUS] = "1"
    reloaded.saveToDb()
}
```

Это та же защита — `applyDuplicateOriginal` вызывается ДО `findYandexSongLyrics`, но всё равно между `createFromPath` (запускает KEY_BPM_FROM_FILE) и `applyDuplicateOriginal` может пройти время (поиск дубликата через сравнение имён), и KEY_BPM_FROM_FILE мог отработать.

### Шаг 3: Reload перед saveToDb в applyAudioParentMarkers

В `Utils.kt:4556-4569`:
```kotlin
fun applyAudioParentMarkers(song: Song, audioParent: Song, deltaMs: Long) {
    val reloaded = Song.loadFromDbById(
        id = song.id,
        database = song.database,
        storageService = song.storageService,
        storageApiClient = song.storageApiClient,
    ) ?: song
    reloaded.sourceText = audioParent.sourceText
    reloaded.resultText = audioParent.resultText
    reloaded.sourceMarkers = shiftMarkersAndFixEnd(audioParent.sourceMarkers, deltaMs, reloaded.ms)
    reloaded.formattedTextSong = audioParent.formattedTextSong
    reloaded.formattedTextTabs = audioParent.formattedTextTabs
    reloaded.formattedTextChords = audioParent.formattedTextChords
    reloaded.fields[SongField.ID_STATUS] = "5"
    reloaded.saveToDb()
}
```

То же самое. `applyAudioParentMarkers` — самый долгий шаг в цепочке (поиск по waveform через акустическое сходство), KEY_BPM_FROM_FILE почти наверняка уже отработал к этому моменту.

### Шаг 4: KDoc + per-feature doc

- Обновить KDoc у `applyDuplicateOriginal` и `applyAudioParentMarkers` с упоминанием race condition и подхода «reload-from-db-before-save».
- Найти/обновить per-feature документ (вероятно `archive/docs/features/async-process-queue.md` или аналогичный в `docs/features/`).

### Шаг 5: Проверка

- `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
- `./gradlew :karaoke-web:ktlintCheck`
- Ручная проверка на dev-pc (по соглашению AGENTS.md): импортировать 3-5 файлов, проверить что key/bpm не теряются.

## Complexity Tracking

Нет нарушений Constitution. Все подходы — extension существующих паттернов проекта (`Song.loadFromDbById` уже используется в `Song.saveToDb()` строки 5180, 5411 — тот же паттерн). Никаких новых абстракций, новых зависимостей или новых БД-таблиц.

## Risk Assessment

| Риск | Вероятность | Митигация |
|------|-------------|-----------|
| `Song.loadFromDbById` сам подвержен race condition (если параллельный saveToDb идёт одновременно с load) | Низкая | Загрузка — простой SELECT, не транзакционный. Окно race — миллисекунды. Если проблема — обернуть в `SELECT ... FOR UPDATE`. |
| Fallback на `newSong` при null возвращает старое поведение | Очень низкая | Только если БД полностью недоступна (что уже сломано). |
| Регрессия в других 46 точках `saveToDb()` | Нулевая | Сам `saveToDb()` НЕ модифицируется, изменения только в 3 вызывающих местах. |
| Ktlint нарушения | Низкая | Запустить ktlintCheck в обязательном порядке (Pass 245 AGENTS.md). |

## Verification Strategy

1. **Compile**: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` (AGENTS.md обязательно)
2. **Lint**: `./gradlew :karaoke-web:ktlintCheck` (AGENTS.md обязательно)
3. **Backend bootJar**: `./gradlew :karaoke-web:bootJar --parallel` (AGENTS.md обязательно)
4. **Ручная проверка** (на dev-pc, по соглашению AGENTS.md):
   - Добавить 3-5 файлов в папку через UI «Добавить файлы из папки».
   - Дождаться завершения KEY_BPM_FROM_FILE для всех песен.
   - Проверить `tbl_songs` через SELECT: `song_tone`, `song_bpm` заполнены, `source_text` заполнен (если Яндекс нашёл).
   - Убедиться что НЕ появились новые процессы KEY_BPM_FROM_FILE для этих песен.
