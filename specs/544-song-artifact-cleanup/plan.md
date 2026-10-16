# Implementation Plan: Автоуборка артефактов песни

**Branch**: `544-song-artifact-cleanup` | **Date**: 2026-10-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/544-song-artifact-cleanup/spec.md`
(исполнение решений wayfinder-карты [#306](http://localhost:8080/work_packages/306);
issue исполнения — [#321](http://localhost:8080/work_packages/321))

## Summary

Жизненный цикл песни оставляет на дисках admin-машины 2,4 ТБ артефактов, из которых
≈650 ГБ — заведомо лишние (демо-ролики, локальные копии mp3-стемов, мёртвые
sidecar-файлы, субтитры, проекты замороженного melt). Технический подход:

1. **Прекратить накопление** — удалять демо-ролик в конце шага публикации, удалять
   локальные mp3-стемы после успешной загрузки в оба MinIO, перестать писать
   `.song`/`.settings`, `.srt`/`.backup`, файлы проектов рендера, удалять
   публикационные картинки после публикации.
2. **Снять ожидание удалённого** — привести модель ожидаемых файлов
   (`KaraokeFileType.locations` + ветки `HealthReport`) в соответствие с новым
   жизненным циклом, иначе авторемонт будет воссоздавать удалённое.
3. **Вернуть место по истории** — разовая чистка классами: отчёт сухого прогона →
   утверждение владельцем → удаление с журналом; классы с дорогим/невозможным
   восстановлением (исходники, flac-стемы) явно защищены.
4. **Закрыть гигиену и документацию** — мусор, покрытие проверки здоровья, политика
   MinIO, правка Knowledge о несуществующем melt-конвейере.

## Technical Context

**Language/Version**: Kotlin (JDK 17), Spring Boot; фронтенды не затрагиваются.

**Primary Dependencies**: `karaoke-app` (ядро: пайплайн, HealthReport, публикация),
MinIO (локальный и удалённый) через `KaraokeStorageService` / `StorageApiClient`.
Внешние SaaS не добавляются (Constitution I).

**Storage**: PostgreSQL — только через сырой JDBC (`KaraokeConnection`), схема **не
меняется**; файловая система admin-машины (`/sm-karaoke/{work,done1,done2,store}`,
`/sm-karaoke/system`); MinIO (бакет `karaoke`); журнал чистки — файл в
`/sm-karaoke/system/cleanup-logs/` (не таблица БД: избегаем JPA/Exposed-пути и
sync-обязательств).

**Testing**: `karaoke-app/src/test` (Kotlin/JUnit); большинство тестов исторически
`@Disabled` (см. Constitution § «Рабочий процесс»: тестов в CI нет). План опирается
на (а) новые unit-тесты на чистые части — реестр классов, построение отчёта,
журнал, правило «локальная копия mp3 не ожидается»; (б) проверку владельцем по
`quickstart.md` в production-like окружении.

**Target Platform**: Linux (admin-машина `nsa-i9`, контейнер `karaoke-app`).
Прод-сервер не затрагивается (там нет `karaoke-app`; уборка прода — отдельный
прогон после дисков admin-машины, вне этой поставки).

**Project Type**: web-service/backend (monorepo Gradle multi-module).

**Performance Goals**: шаг публикации не должен заметно замедляться (удаление файла —
одна операция); проверка здоровья после правки модели — не медленнее текущей
(уменьшение числа проверяемых типов только ускоряет); сухой прогон разовой чистки —
полный обход 362 482 файлов без блокировки HTTP-потоков.

**Constraints**:
- Никакая автоматическая уборка не удаляет `<song>.flac` и `hdemucs_mmi/*.flac`
  (FR-010) — 1,7 ТБ защищённого объёма.
- Уборка выключена по умолчанию; обязателен режим сухого прогона (FR-011).
- Длительные операции не выполняются в HTTP-потоке (Constitution IV,
  `storage/domain.md` инвариант 4).
- Перезапуск `karaoke-app` — только владелец (Constitution § «Ограничения и доступы»).
- Прод: read-only, изменения только по отдельному согласию.

**Scale/Scope**: 2 139 папок-альбомов, 362 482 файла, 2,4 ТБ на дисках; 103 637
объектов / 467,4 ГБ в локальном MinIO; 17 885 демо-роликов; 53 618 локальных
mp3-стемов; 20 288 субтитров; 9 787 `.song`; 2 198 `.mlt`. Ожидаемый эффект —
≈650 ГБ.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Вердикт | Обоснование / обязательство |
|---|---|---|
| I. Self-contained автопайплайн | PASS | Уборка — локальные операции с файлами; новых внешних SaaS нет. |
| II. Сырой JDBC, без JPA | PASS | Схема БД не меняется; журнал — файл, а не таблица. Новых сущностей нет. |
| III. Двух-БД синхронизация | PASS | Новых синхронизируемых сущностей и полей `sync_*` не появляется; `SyncRegistry` не трогаем. |
| IV. Async-очередь, `redirectErrorStream(true)` | PASS + обязательство | Разовая чистка длинная → выполняется вне HTTP-потока (фоновый исполнитель/процесс) с прогрессом; если понадобится subprocess — только с `redirectErrorStream(true)`. |
| V. Два фронтенда раздельно | PASS | UI не меняется (кнопка в админке — вне объёма этой поставки). |
| VI. Code standards (KDoc/ktlint/FR-009) | PASS + обязательство | Новые публичные API — с KDoc; в том же PR обязателен `docs/features/song-artifact-cleanup.md` (FR-009) и запись в `docs/architecture-notes.md`. |
| VII. Cross-machine setup | PASS | Локальные AI-конфиги не трогаем. |
| VIII. Секреты и git-гигиена | PASS | Секретов нет; в журнал пишутся только пути/размеры/классы. |
| IX. Knowledge-first | PASS | Pre-flight выполнен (8 ссылок, маяк `.specify/.preflight-song-artifact-cleanup.ok`); мутация Knowledge включена в объём (FR-017). |
| IX.3. Subagent workspace isolation | PASS | Работа идёт в одной ветке `544-song-artifact-cleanup`; параллельные субагенты не запускаются, `git worktree` не требуется. Если появятся параллельные ветки — правило обязательно. |
| Ограничения доступа (раздел «Ограничения и доступы агента») | PASS + обязательство | Агент не перезапускает `karaoke-app` (это делает владелец), не деплоит на прод, не правит файлы на сервере. Массовая чистка запускается **внутри приложения** (у агента нет прав записи на `/sm-karaoke`). |

**Гейт пройден.** Нарушений, требующих обоснования в Complexity Tracking, нет.

## Project Structure

### Documentation (this feature)

```text
specs/544-song-artifact-cleanup/
├── spec.md              # спека (issue #321)
├── plan.md              # этот файл
├── research.md          # Phase 0: решения и альтернативы
├── data-model.md        # Phase 1: реестр классов, отчёт, журнал, модель ожиданий
├── quickstart.md        # Phase 1: сценарии проверки
├── checklists/
│   └── requirements.md  # чек-лист качества спеки
├── contracts/
│   └── cleanup-api.md   # контракт эндпоинтов разовой чистки
└── tasks.md             # Phase 2 (/speckit.tasks)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── KaraokeFileType.kt                     # локации типов (модель ожидаемых файлов)
├── KaraokeProperties.kt                   # флаги уборки и режима сухого прогона
├── HealthReport.kt                        # ветки MP3_*, PICTURE_*, покрытие symlink_*
├── KaraokeProcess.kt                      # FF_MP3_* (удаление после загрузки), SYMLINK
├── Utils.kt                               # executeRenderMp4, upload-задачи, удаление путей
├── UtilsPictures.kt                       # создание картинок публикации
├── Functions.kt                           # createKaraoke (проекты рендера)
├── model/
│   ├── Song.kt                            # saveToFile/loadFromFile, convertMarkersToSrt, пути
│   ├── SongOutputFile.kt                  # имена/расширения (мёртвое имя [render].mp4)
│   └── SongRenderContext.kt               # чтение .srt, запись .backup (вырезается)
├── controllers/
│   ├── ApiController.kt                   # запись .song/.srt (снимается)
│   ├── MainController.kt                  # hasChords, createKdenliveFiles (снимается)
│   └── SongEditorController.kt            # запись .srt (снимается)
├── services/
│   ├── PlayerMp4RenderService.kt           # рендер демо
│   ├── PremiumAutoPublishScheduler.kt      # точка удаления демо (конец шага публикации)
│   ├── TelegramAutoPublishService.kt       # публикация TG
│   ├── VkAutoPublishService.kt             # публикация VK
│   └── cleanup/                            # НОВОЕ: реестр классов, план, журнал
│       ├── ArtifactCleanupClass.kt         # перечисление классов и их вердиктов
│       ├── ArtifactCleanupPlanner.kt       # построение отчёта (сухой прогон)
│       ├── ArtifactCleanupService.kt       # применение по классу + журнал
│       └── CleanupJournal.kt               # запись журнала удалений
└── SyncAudioDescendants.kt, KaraokeProcessWorker.kt   # запись .srt (снимается)

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/   # новые unit-тесты (см. Testing)

docs/features/song-artifact-cleanup.md     # per-feature документ (FR-009)
knowledge/domains/{catalog,rendering,health,storage}/   # мутация SSoT (FR-017)
```

**Structure Decision**: всё — в существующем модуле `karaoke-app`; новый подпакет
`services/cleanup/` для разовой чистки, чтобы не раздувать и без того крупные
`Utils.kt`/`HealthReport.kt`. Новая таблица БД не заводится: классы артефактов —
код (enum + реестр), журнал — файл на диске. `karaoke-web` не затрагивается
(публичный сайт продолжает читать mp3-стемы и картинки из MinIO).

## Complexity Tracking

> Нарушений Constitution Check нет — таблица пуста по существу.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

---

## Phase 0: Outline & Research

**Output**: [research.md](research.md) — 8 решений (R1…R8), каждое с обоснованием и
отвергнутыми альтернативами. Открытых `NEEDS CLARIFICATION` нет: все развилки
закрыты wayfinder-картой [#306](http://localhost:8080/work_packages/306) и её
тикетами #307-#320.

Ключевые решения:

- **R1** — точка удаления демо-ролика: `PremiumAutoPublishScheduler.closeIfBothChannelsDone`
  (конец шага публикации, после обеих площадок), а не внутри `TelegramAutoPublishService`.
- **R2** — способ снять ожидание локальной копии mp3: убрать `LOCAL_FILESYSTEM` из
  локаций `MP3_*` + снять проверку локального существования в ветках `HealthReport`,
  сохранив `canCreate` (перекодирование из flac остаётся возможным вручную).
- **R3** — удаление локального mp3: только после успешной загрузки в **оба**
  хранилища; использовать существующий `deleteAfterUpload`, но с условием
  «все целевые загрузки успешны».
- **R4** — `hasChords` считается по данным разметки (БД), чтение `.srt` и запись
  `.backup` из `SongRenderContext` вырезаются.
- **R5** — мёртвые писатели (`saveToFile`, `createKdenliveFiles`, `.srt`-запись,
  проекты рендера) удаляются вместе с осиротевшими путями и легаси-эндпоинтами.
- **R6** — разовая чистка: новый сервис + контракт `contracts/cleanup-api.md`;
  `plan` (сухой прогон) → утверждение → `apply` с подтверждением по отпечатку
  отчёта; журнал — файл `/sm-karaoke/system/cleanup-logs/<дата>-<класс>.tsv`.
- **R7** — гигиена симлинков: расширить покрытие `HealthReport` на каталоги
  `symlink_*` целиком, включая посторонние файлы и ссылки без цели.
- **R8** — мутация Knowledge: точный список файлов и правок (melt→MinIO, покрытие
  здоровья, политика хранилищ, мини-словарь buckets).

## Phase 1: Design & Contracts

**Output**: [data-model.md](data-model.md), [contracts/cleanup-api.md](contracts/cleanup-api.md),
[quickstart.md](quickstart.md).

- **data-model.md** — реестр классов артефактов (13 классов с вердиктом, объёмом,
  риском, порядком чистки), модель ожидаемых файлов до/после, отчёт сухого прогона,
  запись журнала, флаги конфигурации.
- **contracts/cleanup-api.md** — HTTP-контракт разовой чистки: `GET /api/cleanup/classes`,
  `POST /api/cleanup/plan` (класс → отчёт + отпечаток), `POST /api/cleanup/apply`
  (класс + отпечаток + подтверждение → запуск, ответ 202), `GET /api/cleanup/status`.
  Правила: apply без отпечатка отклоняется; классы из защищённого списка отклоняются
  всегда; работа идёт вне HTTP-потока.
- **quickstart.md** — сценарии проверки по пользовательским историям US1…US5,
  включая проверку «система не воссоздаёт удалённое» (SC-006) и замер освобождённого
  места (SC-003).

### Post-design Constitution re-check

| Проверка | Вердикт |
|---|---|
| Никаких новых внешних зависимостей/сервисов | PASS |
| Схема БД не меняется, JPA/Exposed не появляются | PASS |
| Длительная чистка вне HTTP-потока | PASS (контракт: 202 + опрос статуса) |
| Публичные API с KDoc, `docs/features/` в том же PR | PASS (обязательство в задачах) |
| Уборка не удаляет защищённые классы | PASS (защита в реестре классов + отказ API) |
