---
id: domain-health
title: "Domain: Health (Здоровье данных)"
status: Active
slug: health
related:
  - ../catalog/domain.md
  - ../storage/domain.md
  - ../monitoring/domain.md
---

# Domain: Health (Здоровье данных)

> Bounded context для проверки консистентности файлов песни и её
> восстановления. Прецедент создания — задачи OpenProject #65
> («Ошибка при проверке наличия файла в удаленном хранилище», 2026)
> и #69 («Кеширование информации из хранилища», 2026-09).

## Обзор контекста (Bounded Context)

Health — это **система самодиагностики и авторемонта** песен. Каждая
песня в проекте может существовать в **трёх местах одновременно**:

1. **Локальная файловая система** (FS): путь `~/Karaoke/.../song-123.flac`
   или `~/Karaoke/.../song-123.mp4`. Видна только админ-процессу
   (`karaoke-app`).
2. **Локальное MinIO** (`KaraokeStorageService`): S3-bucket на той же
   admin-машине, через MinIO SDK. Доступен только `karaoke-app`.
3. **Удалённое MinIO** (`StorageApiClient`): S3-bucket на проде.
   Доступен `karaoke-app` (на запись) и `karaoke-web` (на чтение).

Health отвечает за проверку: «эти три представления согласованы? все
ли нужные файлы реально существуют, не повреждены, не устарели?».
Если нет — Health предлагает **actions** (восстановить, пересоздать,
удалить лишнее), которые пользователь (через webvue3) может
запустить одной кнопкой.

**Граница**: контекст НЕ отвечает за:

- хранение самих файлов (→ [storage](../storage/domain.md));
- консистентность Postgres LOCAL↔SERVER (→ two-DB-sync, ещё не описан);
- мониторинг прод-контейнеров и Telegram polling
  (→ [monitoring](../monitoring/domain.md)).

## Ubiquitous Language | Единый язык

| Термин | Определение | Пример в коде |
| --- | --- | --- |
| **`HealthReport`** | Data class с одной строкой «проблема/решение» по файлу песни | `HealthReport.kt:36` |
| **`HealthReportType`** | `FILE_VIOLATION` (файл не на месте) / `CONSISTENCY_VIOLATION` (симлинки) / `STATUS_VIOLATION` (Song.status рассинхрон с реальностью) | `HealthReportType.kt` |
| **`HealthReportStatus`** | `OK` / `WARNING` / `ERROR` / `FATAL_ERROR` / `IN_PROGRESS` | `HealthReportStatus.kt` |
| **`KaraokeFileType`** | Что за файл: `MLT`, `MLT_PREVIEW`, `AUDIO`, `VIDEO`, `COVER`, `CARDS`, `KARAFILE_MP4`, `MARKERS_JSON`, ... | `KaraokeFileType.kt` |
| **`KaraokeFileTypeLocations`** | Где файл должен быть: `LOCAL_FILESYSTEM`, `LOCAL_STORAGE`, `REMOTE_STORAGE` | `KaraokeFileTypeLocations.kt` |
| **`actionsLocalFileSystem`** | Действия для FS-файлов (создание, удаление, симлинки) | `HealthReport.kt:161` |
| **`actionsLocalStorage`** | Действия для локального MinIO (download/upload) | `HealthReport.kt:501` |
| **`actionsRemoteStorage`** | Действия для удалённого MinIO (download/upload) | `HealthReport.kt:789` |
| **`autoRepair`** | Цикл «взять все HealthReports → выполнить solutionActions → пересчитать» | `startRepairAll:2259` |
| **`reconcilePlayerReadinessFlags`** | Перевод Song.status → PlayerReady (готов ли плеер играть эту песню) | `HealthReport.kt:2113` |

Полный каталог — см. [health-report](components/health-report.md).

## Ключевые компоненты

- **`HealthReport`** (`karaoke-app/.../HealthReport.kt`) — data class с
  одним нарушением и набором actions для исправления. **Вся логика
  живёт в companion object** (`HealthReport.kt:61-2302`), что даёт
  ~2240 строк в одном файле (см. Known gaps).
- **`HealthReportDTO`** — сериализация для webvue3 (минимальный набор
  полей).
- **`HealthReportType`** / **`HealthReportStatus`** — перечисления.

## Domain Invariants | Инварианты и правила бизнеса

1. **`HealthReport` НИКОГДА не выполняет действия сам**: actions
   хранятся как `List<() -> Unit>` в `solutionActions` и выполняются
   **только по команде** (`executeSolutionActions()`).
2. **`actionsLocalFileSystem`** — единственное место, где симлинки
   создаются и удаляются (см. `KaraokeFileSymlink.kt`).
3. **`actionsLocalStorage`** и **`actionsRemoteStorage`** —
   единственные места, где MinIO upload/download дёргается из
   health-контекста. Любые другие вызовы `KaraokeStorageService`
   из health-контекста ЗАПРЕЩЕНЫ.
4. **Repair-loop защищён `recomputeAndBroadcast`**: после каждого
   `executeResolvable` сразу пересчитываются PlayerReady-флаги и
   рассылается SSE-уведомление (`KaraokeWebService` / SSE — см. P2).
5. **`getHealthReportList` перебирает ВСЕ `KaraokeFileType`** для
   одной песни → 5+ проверок на песню. **Это и есть источник
   «тяжёлых» запросов к MinIO** (OpenProject #69).

## Архитектурные решения (принятые)

| Решение | Обоснование |
|---|---|
| Один файл `HealthReport.kt` с companion object на 2240 строк | Исторически — все actions тривиально связаны, разделение по типу location (`LOCAL_FILESYSTEM`/`LOCAL_STORAGE`/`REMOTE_STORAGE`) внутри одного объекта. Рефакторинг — P3. |
| `solutionActions: List<() -> Unit>` (лямбды), а не `Command` / `Action` enum | Позволяет execute'у быть любым closure (включая `KaraokeProcess.createProcess`), а не конечным набором enum-значений. |
| `recomputeAndBroadcast` дёргает SSE после каждой repair-операции | UI обновляется без polling — экономит ~95% load на `getHealthReportList`. |
| `actionsRemoteStorage` через `StorageApiClient` (HTTP), а не прямой MinIO SDK | На проде `karaoke-web` имеет read-only через тот же `StorageApiClientWeb`. Health-контекст админа работает через **тот же интерфейс**, что и прод, что гарантирует «запись видна прод-чтению». |

## Связь с OpenProject и задачами

- **#65** «Ошибка при проверке наличия файла в удаленном хранилище»
  (2026, in progress): первопричина — race condition в HTTP-вызовах
  к `StorageApiClient.fileExists`. Детальный анализ — в
  [health-report.md#known-issues](components/health-report.md).
- **#69** «Кеширование информации из хранилища» (2026-09): попытка
  спеки 2026-09-09 провалилась из-за пропуска Knowledge-first
  (см. Pass 340, спека 339 удалена). Перезапуск — после Pass 341.

## Код (физическая реализация)

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReportDTO.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReportStatus.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReportType.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeFileType.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeFileTypeLocations.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeFileSymlink.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (Song.status, поля для HealthReport)

## Known gaps

Эта компонента покрыта **на минимальном уровне** для разблокировки
спеки #69. Следующие TODO (Pass 342+):

- [ ] **Детальный поток вызовов**: кто вызывает `getHealthReportList`
  (UI? cron? SSE?). Сейчас известно только, что webvue3 дёргает
  через `/api/health` (нужно проследить).
- [ ] **`HealthReport.kt:2113` reconcilePlayerReadinessFlags** — не
  описана, занимает 50 строк; критична для player UX.
- [ ] **`executeResolvable`** (`HealthReport.kt:2252`) — точка входа
  repair-loop, не описана.
- [ ] **`LEGACY_MLT_FILE_TYPES`** (строка 1107) — set из 4 типов,
  помечен как legacy; нужно понять, планируется ли удаление.
- [ ] **`StatsCache` / `StatsDebugController`** — связь HealthReport
  с admin-метриками не описана.
- [ ] **webvue3 сторона**: `HealthReportView.vue` (нужно найти
  и описать UI), Vuex-модуль `healthReport/store.js`.
- [ ] **`KaraokeProcess.THREAD_LANE_HEALTH_REPORT`** — отдельная
  thread-lane для repair-процессов; связана с Async Process Queue
  (Pass 342).
- [ ] **Race conditions**: специфика race в `fileExists` для
  remote storage (OpenProject #65) — детальный анализ и
  repro-сценарий.

## Changelog

- **Pass 341** (2026-09-09): Initial domain. Прецедент: задачи #65
  и #69. Автор: agent (Karaoke).