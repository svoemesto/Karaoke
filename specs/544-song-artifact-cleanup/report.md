# Отчёт по issue #321 — автоуборка артефактов песни

**Спека**: `specs/544-song-artifact-cleanup/spec.md` · **Ветка**: `544-song-artifact-cleanup`
**Issue**: OpenProject #321 (карта решений — #306, инвентаризация — #305)
**Статус документа**: черновик (T039); дополняется владельцем после T030/T040
**Дата**: 2026-10-16

> **REQUIRED-артефакт трекера.** Источник для
> `bash tools/tracker.sh add-comment 321 --file specs/544-song-artifact-cleanup/report.md`.

## Что сделано

Реализованы фазы 1–7 задач (`tasks.md`), кроме шагов владельца:

| Фаза | Задачи | Итог |
|---|---|---|
| 1 Setup | T001–T002 | зафиксирован зелёный baseline сборки, ktlint и doc-гейтов |
| 2 Foundational | T003–T007 | реестр классов, журнал удалений, 4 флага уборки, 17 тестов |
| 3 US1 | T008–T012 | демо-ролик удаляется в конце публикации; публикационные картинки — при успехе обеих площадок |
| 4 US2 | T013–T017 | `MP3_*` без `LOCAL_FILESYSTEM`; локальный mp3 удаляется только при успехе в оба хранилища; 11 тестов |
| 5 US3 | T018–T025 | сняты писатели `.song`/`.settings`, `.srt`, `.run`, `.kdenlive`; `hasChords` по маркерам БД; 14 тестов |
| 6 US4 | T026–T029 | сухой прогон, применение по отпечатку, API `/api/cleanup`, журнал; 13 тестов |
| 7 US5 | T031–T035 | аудит каталогов `symlink_*` целиком; Knowledge приведён к факту; per-feature документ |
| 8 Polish | T036–T039 | T036/T037 прогнаны (факты ниже), запись в `architecture-notes.md`, этот отчёт |

**Не сделано (шаги владельца)**: T030 (разовый массовый прогон чистки) и T040 (приёмка по
`quickstart.md` с замером `df`). У агента нет прав записи в `/sm-karaoke`, поэтому чистка и
перезапуск контейнера — только владелец.

**Остановлено с докладом (T022, частично)**: FR-009 требует не создавать `.mlt`, но файл
читает замороженный, однако живой melt-конвейер (типы процессов `MELT_*` создаются из
`MainController.kt:470,489,511,534` и `KaraokeProcessWorker.applyRenderSideEffect`, аргументы
melt ссылаются на `done_projects/*.mlt`). Прекратить запись `.mlt`, не выведя MELT-процессы
из эксплуатации, — значит поставить их в заведомо падающее состояние (SC-008). Удалено
безопасное подмножество: писатели `*.run` и `Song.createKdenliveFiles` с эндпоинтом
`/song/{id}/createkdenlivefiles`. Решение по `.mlt` — за владельцем.

## Факты с диска (до чистки)

```
Файл.система   Размер Использовано  Дост Использовано% Cмонтировано в
/dev/sdc1        1,8T         1,2T  600G           66% /sm-karaoke/done1
/dev/sda          15T         9,0T  4,9T           65% /sm-karaoke/done2
/dev/sdb1        916G         575G  295G           67% /sm-karaoke/system
```

Ожидаемый эффект (инвентаризация #305): ≈650 ГБ по классам с низким риском
(демо-ролики 176,7 ГБ + локальные mp3-стемы 445 ГБ + публикационные картинки ≈20,6 ГБ),
всего ≥600 ГБ (SC-003). Прирост от этих классов после поставки — нулевой (SC-001/SC-002).

## Фактические результаты проверок (T036/T037)

**T036 — обязательные проверки после изменения** (AGENTS.md Pass 239+245,
`GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`):

```
$ ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel \
    :karaoke-web:ktlintCheck :karaoke-app:bootJar :karaoke-web:bootJar
> Task :karaoke-app:bootJar
> Task :karaoke-web:bootJar
BUILD SUCCESSFUL in 34s
16 actionable tasks: 7 executed, 9 up-to-date
```

`./gradlew :karaoke-app:ktlintCheck` — BUILD SUCCESSFUL (в том же прогоне).

**T037 — гейты документации и SSoT**:

```
$ bash tools/check-no-mp4-mentions.sh --quiet
exit=0

$ python3 tools/check-ssot-impact.py
[SSoT] Checking impact surface against base 'origin/master'
[1/3] Found 42 changed files (base=origin/master)
[2/3] Filtered to 19 significant files (excluded 23)
[3/3] Checked 1 rules; 0 violation(s)
OK: all matched SSoT-impact rules satisfied.

$ python3 tools/lint-knowledge.py
All documentation passes architectural style and structural checks.

$ bash tools/check-knowledge-structure.sh
Files with frontmatter: 16/16
OK: knowledge/ structure valid (9/9 checks passed)

$ bash tools/check-feature-doc.sh docs/features/*.md
==> Все документы валидны
```

**Юнит-тесты** (`:karaoke-app:test`, фильтр по новым классам; в CI тестов нет —
Constitution § «Рабочий процесс»):

| Класс тестов | Тестов | Результат |
|---|---|---|
| `ArtifactCleanupRegistryTest` | 9 | 0 failures |
| `CleanupJournalTest` | 8 | 0 failures |
| `DemoMp4CleanupTest` | 10 | 0 failures |
| `StemsMp3LocalDeleteTest` | 6 | 0 failures |
| `HealthReportMp3LocationsTest` | 5 | 0 failures |
| `ArtifactCleanupGuardTest` | 13 | 0 failures |
| `HasChordsFromDbTest` | 8 | 0 failures |
| `RenameWithoutSidecarTest` | 6 | 0 failures |

Фронтенды (`webvue3/`, `karaoke-public/`) в этой поставке не менялись — `npm run lint`/`build`
для них не запускались.

## Что осталось за владельцем

1. **T030 — разовый массовый прогон чистки** (у агента нет прав записи в `/sm-karaoke`).
   Порядок обязателен (`quickstart.md` сценарий 4):
   ```bash
   BASE=http://localhost:8898/api/cleanup
   curl -s $BASE/classes | python3 -m json.tool | head -60
   curl -s -X POST $BASE/plan -H 'Content-Type: application/json' -d '{"classId":"DEMO_MP4"}' > /tmp/plan-demo.json
   # предохранитель: защищённый класс должен быть отклонён
   curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/plan -H 'Content-Type: application/json' -d '{"classId":"STEMS_FLAC"}'   # ожидаемо 400
   PLAN=$(python3 -c "import json;print(json.load(open('/tmp/plan-demo.json'))['planId'])")
   FP=$(python3 -c "import json;print(json.load(open('/tmp/plan-demo.json'))['fingerprint'])")
   curl -s -X POST $BASE/apply -H 'Content-Type: application/json' \
     -d "{\"planId\":\"$PLAN\",\"fingerprint\":\"$FP\",\"approvedBy\":\"Сергей Новиков\",\"confirm\":true}"
   curl -s "$BASE/status?runId=<runId>" | python3 -m json.tool
   head -3 /sm-karaoke/system/cleanup-logs/2026-10-16-DEMO_MP4.tsv
   ```
   Ожидаемо: `DEMO_MP4` ≈17 885 файлов / ≈176,7 ГБ, затем классы 2→7 по порядку реестра;
   класс `STEMS_MP3_LOCAL` — после проверки сценария 2.
2. **Перезапуск `karaoke-app`** — только владелец и только после остановки очереди заданий
   (AGENTS.md Pass 380/381: проверить `POST http://localhost:7906/api/processesdigests` на
   активные `WORKING`/`CREATING`, затем «Стоп» в шапке админки; эндпоинты очереди живут в
   `MainController` без префикса `/api` и доступны напрямую на `:8898`).
3. **Включение уборки для проверки факта удаления** (SC-001/SC-002):
   ```bash
   for kv in "cleanupEnabled=true" "cleanupDryRun=false"; do
     curl -s -X POST http://localhost:8898/api/properties/setproperty --data-urlencode "${kv%%=*}=${kv#*=}"; echo
   done
   ```
   Без этого демо-ролик и локальные mp3 остаются на месте **по замыслу** (FR-011).
4. **T040 — приёмка** по `quickstart.md` (сценарии 1–5) и замер `df` до/после;
   дописать результат в этот отчёт.
5. **Решение по `.mlt`** (см. выше): сузить FR-009 до `.kdenlive`/`.run` либо вывести
   MELT-процессы из эксплуатации.
6. **Рекомендация после массового удаления**: сбросить/прогреть кеш метаданных хранилища
   (`tbl_storage_metadata_cache`, спеки #446/#449) — чистка работает с файловой системой и
   кеш не проверяет (см. `data-model.md` §7).

## Открытые вопросы и факты, найденные при реализации

1. **FR-009 vs живой melt** — см. выше (T022).
2. **`SongRenderContext` стал недостижим**: после вырезания файлового ввода-вывода
   (T019) класс никто не конструирует — он конструировался только ради побочного эффекта
   записи `.srt` (вызов удалён из `ApiController.getSongTextVkKaraokeHeader`). Кандидат на
   удаление отдельным решением.
3. **`nameFileRenderMp4` сохранён** вопреки формулировке задачи: у имени есть читатель —
   ручное воспроизведение файла через smplayer (`Song.kt:3451-3460`). «Мёртвым» был сам
   файл (1 экземпляр на дисках), а не код.
4. **`HealthReport`-правка из T014 не потребовалась**: ветки `MP3_*` с созданием
   `FF_MP3_*`-задач стали недостижимы уже после снятия `LOCAL_FILESYSTEM` — обход идёт по
   объявленным `locations`, а `actionsLocalFileSystem` начинается с
   `if (!willBeInLocation) return emptyList()`. Подтверждено тестом
   `HealthReportMp3LocationsTest`.
5. **Три теста написаны иначе, чем в задаче**, по фактам кода (конструктор `Song` требует
   БД и в юнит-тесте недоступен): `HasChordsFromDbTest` проверяет чистую функцию
   `hasChordsInMarkers` (вынесена как единственный источник правды), `RenameWithoutSidecarTest`
   проверяет состав членов `Song` структурно, а `StemsMp3LocalDeleteTest` — правило FR-003
   на реальных файлах. Runtime-пути, требующие БД и обоих хранилищ (`closeIfBothChannelsDone`,
   `renameCascadeExtraArtifacts`, `getHealthReportList`), юнит-тестами не покрыты — их
   проверяет приёмка владельца (T040).
6. **`PUBLICATION_PNG`-матчер уточнён по коду**: набор суффиксов взят из `UtilsPictures.kt`
   (`[karaoke]`, `[lyrics]`, `[chords]`, `[tabs]`, `[sponsr]`, `[VKlink]`, `[VK]`,
   `[boosty]`, `[files]`), а не из сводки инвентаризации (`[vklink]`, `[vkgroup]`,
   `song_banner` — таких имён в коде нет). Сухой прогон покажет фактическое число файлов.
