# Feature Specification: 126 — Поиск тональности из существующего `[key].json`

**Feature Branch**: `401-key-from-file`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "Работа над задачей #126 в трекере OpenProject"

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

### Идентификация

- **Issue ID**: `#126`.
- **Title**: «Поиск тональности».
- **Created in OpenProject**: 2026-09-15.

### Workflow (NON-NEGOTIABLE)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `tracker.sh claim-issue 126` | ПЕРЕД первой строкой кода спеки | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | Согласно Constitution Principle IX | Agent |
| 3. **Work** | код, тесты, knowledge updates | `/speckit-implement` | Agent |
| 4. **Add comment с отчётом** | `tracker.sh add-comment 126 --file specs/126-key-from-file/report.md` | После merge | Agent |
| 5. **Mark review** | `tracker.sh mark-review 126` | После публикации комментария | Agent |
| 6. **Close** | `tracker.sh close-issue 126` | После ревью владельцем | Owner |

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-15.
- **Grep-запросы**:
  1. `grep -riE 'тональност|key/.*json|KEY_BPM|keyFromFile|KEY_BPM_FROM_FILE' knowledge/` →
     найдено 2 упоминания в `knowledge/domains/integration/components/song-public-dto.md`
     и `knowledge/domains/catalog/components/song-entity.md`. Подробного описания
     логики «файл есть → применить, нет → запустить» **не нашлось** — это закрытый
     пробел в Living Docs (закроем в рамках этой задачи).
  2. `grep -rn 'KEY_BPM_FROM_FILE|keybpmfinder|argsKeyBpmFinder|getKeyBpmFromFile|\[key\]\.json' karaoke-app/src/main/kotlin/` →
     10+ hits в `HealthReport.kt`, `Song.kt`, `Utils.kt`, `KaraokeProcess.kt`.
  3. `grep -riE 'KEY_BPM_FROM_FILE|keybpmfinder' .specify/memory/constitution.md knowledge/adr/` →
     **no relevant docs** (нет ADR про key/bpm detection — это новая фича).

### Knowledge files consulted

- [`knowledge/domains/catalog/components/song-entity.md`](../../knowledge/domains/catalog/components/song-entity.md)
  — зачем прочитан: `Song` — главная entity, поле `key` хранится в `fields[SongField.KEY]`,
  запись в `tbl_songs`, `saveToDbLocked()` для долгих процессов.
- [`knowledge/domains/integration/components/song-public-dto.md`](../../knowledge/domains/integration/components/song-public-dto.md)
  — зачем прочитан: `key` — публичное поле, отдаётся в SongPublicDto как «Тональность».

### Если ничего не нашлось (явный no-op для ADR)

> Searched: KEY_BPM_FROM_FILE, keybpmfinder, тональность в `knowledge/adr/` →
> **no relevant docs**. Это PASS — feature новая, ADR не требуется.

### Что НЕ нашлось (явные пробелы — будут закрыты в этой спеце)

> Searched: `KEY_BPM_FROM_FILE` в `knowledge/domains/processing/components/` →
> **no relevant component doc**. Существует `async-process-queue.md`, но
> специфической логики `argsKeyBpmFinder` там нет.

## Clarifications

### Session 2026-09-15

- Q: Где ставить проверку файла `[key].json`? → A: В обоих — `HealthReport.solutionActions` (около строки 1418) **и** `KaraokeProcess.prepareContext` для типа `KEY_BPM_FROM_FILE` (около строки 1861). Общий алгоритм выносится в helper `KeyBpmFromFileCache.applyIfExists(song, database)` в `Song.kt` (рядом с `getKeyBpmFromFile`). Это покрывает все call-sites и защищает от race-condition на уровне HealthReport.

## User Scenarios & Testing

### Primary User Story

**Как** администратор караоке-системы,
**я хочу** при первом HealthReport-чеке песни без тональности, у которой уже
есть файл `[key].json` (с результатом прошлого keybpmfinder-прогона), чтобы
тональность была **применена** к песне, а не переcчитывалась через docker,
**чтобы** экономить ~5-10 секунд docker-прогона и CPU-ресурсы на каждый
такой случай.

### Acceptance Scenarios

1. **Given** песня с `song.key == ""` (нет тональности в БД), у которой на
   диске существует валидный `<songFile> [key].json` с заполненными
   `key` и `bpm`,
   **When** пользователь открывает карточку песни в админке или нажимает
   «Исправить всё» в HealthReportView,
   **Then** `song.key` и `song.bpm` **сразу** получают значения из файла,
   HealthReport-запись CONSISTENCY_VIOLATION «У песни отсутствует тональность»
   **не появляется** (т.к. проблема уже решена), и **никакой** `KaraokeProcess`
   типа `KEY_BPM_FROM_FILE` **не создаётся**.

2. **Given** песня с `song.key == ""`, у которой файла `<songFile> [key].json`
   нет (или он невалиден),
   **When** пользователь нажимает «Исправить всё»,
   **Then** создаётся `KaraokeProcess` типа `KEY_BPM_FROM_FILE`, worker
   запускает docker `svoemestodev/keybpmfinder:latest`, после успешного
   завершения `song.key` и `song.bpm` обновляются через `runFunctionWithArgs
   getKeyBpmFromFile` (как сейчас).

3. **Given** песня с `song.key == ""`, у которой **повреждённый**
   `<songFile> [key].json` (файл есть, но `data.key == null` или `data.bpm == null`),
   **When** пользователь нажимает «Исправить всё»,
   **Then** создаётся `KaraokeProcess` типа `KEY_BPM_FROM_FILE` (файл
   пересчитывается).

### Edge Cases

- **Race condition**: два пользователя одновременно нажали «Исправить всё» →
  должен быть **ровно один** `KaraokeProcess` (текущий код это уже
  обеспечивает через `KaraokeProcess.loadList(args = processArgs).isNotEmpty()`).
- **Файл `[key].json` создался вручную** (не через docker) — должен быть
  распознан как валидный (формат JSON проверяется в `getKeyBpmFromFile`).
- **docker недоступен**, файл есть — файл должен примениться без ошибки
  (т.к. docker даже не вызывается).

## Functional Requirements

### FR-001. Чтение тональности из существующего файла `[key].json`

При попытке автоматически исправить проблему «У песни отсутствует
тональность» (`HealthReport` `CONSISTENCY_VIOLATION` с пустым `song.key`)
**перед** созданием `KaraokeProcess` типа `KEY_BPM_FROM_FILE` проверить
наличие файла `<songFile> [key].json` (см. `Song.nameFileKeyBpmFinder` /
`pathToFileKeyBpmFinder`).

**If file exists AND valid** (т.е. `Json.decodeFromString(AudioAnalysisResult)`
возвращает `data.key != null` И `data.bpm != null`):
- Применить `data.key` к `song.fields[SongField.KEY]`.
- Применить `data.bpm` к `song.fields[SongField.BPM]` (с `toString()`).
- Сохранить через `song.saveToDbLocked()` (для защиты от race с SongEdit).
- **НЕ создавать** `KaraokeProcess` типа `KEY_BPM_FROM_FILE`.
- **НЕ вызывать** `docker run svoemestodev/keybpmfinder:latest`.

**If file missing OR invalid** (текущее поведение):
- Создать `KaraokeProcess` как сейчас (через `KaraokeProcess.createProcess`).

### FR-001.a. Точки проверки (clarification #126-1, 2026-09-15)

Владелец подтвердил: проверка файла нужна **в обоих** местах
(защита от всех call-sites):

1. **`HealthReport.kt`** (около строки 1418, в `solutionActions` для
   `CONSISTENCY_VIOLATION` с пустым `song.key`) — **первый** уровень защиты.
   Перед `KaraokeProcess.createProcess(...)` сделать inline-проверку через
   `song.getKeyBpmFromFile(reFind = false)`. Если файл есть — применить,
   иначе — создать процесс.

2. **`KaraokeProcess.prepareContext`** (около строки 1861, для типа
   `KEY_BPM_FROM_FILE`) — **второй** уровень защиты. Перед
   `song.argsKeyBpmFinder()` проверить файл. Если есть — вызвать
   `executeGetKeyBpmFromFile` и пометить процесс как `DONE` без запуска
   docker. Это покрывает ручные запуски через `/api/process/...` и любые
   другие call-sites.

Алгоритм проверки выносится в общий приватный helper
`KeyBpmFromFileCache.applyIfExists(song, database): Boolean` (расположен в
`Song.kt` рядом с `getKeyBpmFromFile`), который возвращает `true` если
файл был найден и применён, `false` если нужно запускать docker.

### FR-002. Логирование пропуска docker-прогона

Когда docker-прогон пропускается (файл уже есть), добавить INFO-запись в лог
категории `infra.cache.keybpm` с полями:
- `songId`, `songFileName`,
- `source = "file-cache"`,
- `key`, `bpm`.

Это поможет диагностировать: «почему тональность применилась без docker'а?».

### FR-003. Knowledge SSoT update

Добавить документ `knowledge/domains/processing/components/key-bpm-from-file.md`
с описанием:
- Точка вызова (HealthReport / ApiController).
- Алгоритм: проверка файла → если есть, применить; иначе docker.
- Формат файла `[key].json` (ссылка на `AudioAnalysisResult.serializer()`).
- Race-safety через `saveToDbLocked()`.

### FR-004. Юнит-тесты

Добавить 3-5 юнит-тестов:
- Файл есть, валиден → `song.key`/`song.bpm` обновляются, процесс НЕ создаётся.
- Файл есть, невалиден (`null` поля) → процесс создаётся.
- Файла нет → процесс создаётся.
- Race: два параллельных вызова → один процесс (через `KaraokeProcess.loadList`).

### FR-005. Hard gates

- gradle compile + ktlint PASS.
- webvue3 lint + Vite build PASS (если задеваем фронт).
- Юнит-тесты PASS.
- `tools/check-no-jpa-imports.sh` PASS.
- `tools/check-no-mp4-mentions.sh` PASS (без новых violations).

## Success Criteria

1. **SC-001**: На странице HealthReport у песни с пустым `song.key`, но
   существующим `[key].json` файлом, **не появляется** запись «У песни
   отсутствует тональность» (т.к. проблема уже решена).
2. **SC-002**: В логе бэка появляется запись `keybpm: applied from file for songId=X (key=Y, bpm=Z)`
   — т.е. оператор явно видит, что тональность применилась без docker'а.
3. **SC-003**: 4+ юнит-теста PASS.
4. **SC-004**: Все hard gates (ktlint, Vite build, JPA, MP4) PASS.
5. **SC-005**: Ручная проверка на проде: 5 случайных песен с пустым
   `song.key` и валидным `[key].json` файлом открываются за **< 1 сек**
   (вместо ~5-10 сек с docker-прогоном).

## Key Entities

- **`Song`** — главная entity (`knowledge/domains/catalog/components/song-entity.md`).
  Поля `key` и `bpm` хранятся в `fields[SongField.KEY]` и `fields[SongField.BPM]`.
- **`AudioAnalysisResult`** — формат файла `[key].json`. Уже сериализуется
  через `Json.decodeFromString(AudioAnalysisResult.serializer(), text)` в
  `Song.getKeyBpmFromFile`.
- **`KaraokeProcess`** — сущность очереди заданий. Тип `KEY_BPM_FROM_FILE`
  используется для docker-прогона.

## Assumptions

1. **Файл `[key].json` уже валиден** (т.е. JSON-парсинг проходит и поля
   `key`/`bpm` заполнены). Если парсинг падает → трактуем как «файл
   отсутствует» (запускаем docker).
2. **Файловая система доступна** — `File(pathToFileKeyBpmFinder).exists()` —
   это та же FS, где работает docker-volumes (passportStorage).
3. **Никакого миграционного скрипта** не требуется: фича только меняет
   логику создания `KaraokeProcess`, не данные.
4. **Legacy процессы** (созданные до merge этой ветки и сейчас в очереди с
   типом `KEY_BPM_FROM_FILE`) — **доработают** как обычно. Внутри worker'а
   `getKeyBpmFromFile(reFind=false)` уже корректно работает: если файл
   есть — docker не запускает.

## Out of Scope

- **Обратная задача**: «если docker-прогон вернул ту же тональность, что в
  файле — не сохранять файл». Сейчас docker **перезаписывает** файл каждый
  раз, даже если результат тот же. Это нормально (метаданные timestamp и т.п.).
- **Распознавание формата `data.minor == null`** (мажор vs минор) — сейчас
  берётся `data.key` целиком. Это поведение сохраняем.
- **Удаление `[key].json` при удалении песни** — отдельная задача.
- **UI-индикатор «key/bpm from cache»** в карточке песни — отдельная задача.

## Changelog

- **Pass 401** (2026-09-15): Initial. Автор: agent (Karaoke).
