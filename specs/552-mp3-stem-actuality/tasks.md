# Tasks: Ложная неактуальность mp3-стемов

**Feature**: `552-mp3-stem-actuality` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

## Phase 1: Setup

- [ ] T001 Зафиксировать базовую линию: прогнать `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests '*HealthReportMp3LocationsTest*'` и убедиться, что тест зелёный до правок.

## Phase 2: Foundational

- [ ] T002 [P] Добавить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` внутренний helper `diskCopyIsAuthoritative(karaokeFileType: KaraokeFileType): Boolean` = `karaokeFileType.willBeInFileSystem` с KDoc, объясняющим решение #330.
- [ ] T003 Добавить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` внутренний helper `storageActuality(local: StorageFileInfo?, remote: StorageFileInfo?): StorageActuality` (enum `OK`/`MISMATCH`): `null` в любом аргументе → `OK`, равные `size` → `OK`, иначе `MISMATCH`. KDoc: сравнение только по размеру (#333).

## Phase 3: User Story 1 — После рендера демо нет ложных записей (P1)

**Goal**: при наличии объектов в обоих хранилищах дисковая копия не влияет на вердикт; при равных размерах — OK.
**Independent Test**: unit-тест helper'а `diskCopyIsAuthoritative` для mp3-типов = false; `storageActuality` с равными размерами = OK.

- [ ] T004 [US1] В `HealthReport.actionsLocalStorage` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt`, ветка «объект в LOCAL есть и файл на диске есть») выбрать сравнение по `diskCopyIsAuthoritative`: для storage-only типов сравнивать `size` LOCAL и REMOTE через `storageActuality`, не читая диск.
- [ ] T005 [US1] Симметрично поправить `HealthReport.actionsRemoteStorage` (ветка «объект в REMOTE есть и файл на диске есть»): для storage-only типов сравнивать REMOTE и LOCAL через `storageActuality`.
- [ ] T006 [P] [US1] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/HealthReportMp3ActualityTest.kt`: `diskCopyIsAuthoritative` = false для пяти mp3-типов; `storageActuality` = OK при равных размерах, OK при `null`, MISMATCH при разных.

## Phase 4: User Story 2 — Настоящая рассинхронизация видна (P2)

**Goal**: при разных размерах LOCAL↔REMOTE — WARNING с корректным текстом и без автоматических действий.
**Independent Test**: `storageActuality(local(size=100), remote(size=200))` = MISMATCH; в ветках выставляется WARNING, `canResolve=false`, `solutionActions` пуст.

- [ ] T007 [US2] В обеих ветках (T004/T005) для `MISMATCH` выставить `healthReportStatus = WARNING`, `canBeResolved = false`, `problemText = "Размеры файлов в локальном и удалённом хранилищах различаются"`, `solutionText = "Приведите хранилища к согласию вручную"`, без actions; для `OK` — статус OK и текст «Проблем нет».
- [ ] T008 [P] [US2] Добавить в `HealthReportMp3ActualityTest.kt` проверку MISMATCH и констант текста (если текст вынесен в `internal const val`).

## Phase 5: User Story 3 — Единое правило для всех mp3-типов (P3)

**Goal**: правило действует для всех пяти storage-only типов.
**Independent Test**: параметризованный тест по пяти типам.

- [ ] T009 [P] [US3] Добавить в `HealthReportMp3ActualityTest.kt` параметризованную проверку всех пяти mp3-типов и контрольных типов с `LOCAL_FILESYSTEM` (`AUDIO_SONG`, `PICTURE_ALBUM`) — у последних helper возвращает true.

## Phase 6: Polish

- [ ] T010 Обновить `knowledge/domains/health/components/health-report.md`: правило «для storage-only типов дисковая копия не эталон; сравнение LOCAL↔REMOTE по size; WARNING без действий».
- [ ] T011 Прогнать проверки: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`, `:karaoke-web:ktlintCheck`, целевые тесты; `python3 tools/lint-knowledge.py`.
- [ ] T012 Написать `specs/552-mp3-stem-actuality/report.md` с фактами и командами проверки.
- [ ] T013 Commit в ветку `552-mp3-stem-actuality` с маркером `[tracker-claim-335]`, push, PR в master.

## Dependencies

```text
T001 → T002 → T003 → T004 → T005 → T007 → T008
                     ↘ T006 [P]
                              T009 [P]
T004…T009 → T010 → T011 → T012 → T013
```

## Parallel Execution

- T006 [P] и T009 [P] — тесты в одном новом файле, но независимы от T004/T005 по написанию; запускать после T003.
- T002 [P] — отдельный helper, не конфликтует с T003 по смыслу, но оба в `HealthReport.kt`, поэтому выполнять последовательно.

## Implementation Strategy

MVP — US1 (T001–T006): после него ложные ERROR исчезают, реальные расхождения продолжают показываться по прежней ветке. US2 и US3 добавляют точность текста и покрытие типов; Polish — knowledge и CI.
