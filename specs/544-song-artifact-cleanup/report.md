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

**T022 — закрыта в суженном объёме, по слову владельца.** При реализации выяснилось, что
FR-009 в исходной редакции конфликтует с живым кодом: `.mlt` читает замороженный, однако
живой melt-конвейер (`MELT_*`-процессы создаются из `MainController.kt:470,489,511,534` и
`KaraokeProcessWorker.applyRenderSideEffect`, аргументы melt ссылаются на
`done_projects/*.mlt`), и прекращение записи поставило бы их в падающее состояние (SC-008).
Владелец решил **сузить FR-009 до `.kdenlive`/`.run`** (2026-10-16); `.mlt` остаётся и
продолжает создаваться, вывод MELT-процессов из эксплуатации — отдельная работа.
Удалено: писатели `*.run` и `Song.createKdenliveFiles` вместе с эндпоинтом
`/song/{id}/createkdenlivefiles` (вызовов во фронтендах нет).

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

$ bash tools/check-knowledge-cross-links.sh
OK: All 677 cross-links valid

$ bash tools/check-feature-doc.sh docs/features/*.md
==> Все документы валидны

$ bash tools/check-knowledge-cross-links.sh
[1/2] Проверка относительных путей в Markdown...
[2/2] Проверка related: slugs (basic)...
Total link checks: 677 (skipped 0 placeholder(s))
OK: All 677 cross-links valid
```

> Гейт `check-knowledge-cross-links.sh` добавлен в список после ревью: его не было в T037,
> и CI падал на сломанной ссылке `../rendering/components/mlt-pipeline.md` (исправлена лидом).

**Юнит-тесты** (`:karaoke-app:test`, фильтр по новым классам; в CI тестов нет —
Constitution § «Рабочий процесс»):

| Класс тестов | Тестов | Результат |
|---|---|---|
| `ArtifactCleanupRegistryTest` | 13 | 0 failures |
| `CleanupJournalTest` | 8 | 0 failures |
| `DemoMp4CleanupTest` | 10 | 0 failures |
| `StemsMp3LocalDeleteTest` | 6 | 0 failures |
| `HealthReportMp3LocationsTest` | 5 | 0 failures |
| `ArtifactCleanupGuardTest` | 16 | 0 failures |
| `HasChordsFromDbTest` | 8 | 0 failures |
| `RenameWithoutSidecarTest` | 6 | 0 failures |

Фронтенды (`webvue3/`, `karaoke-public/`) в этой поставке не менялись — `npm run lint`/`build`
для них не запускались.

## Исправлено после ревью лида (2026-10-16)

**Решение владельца (вариант A)**: FR-009 сужен до `.kdenlive`/`.run`; `.mlt` продолжает
создаваться (его читает живой melt-конвейер), вывод `MELT_*`-процессов из эксплуатации —
отдельная работа.

**Найденный на ревью дефект**: матчер класса `DONE_PROJECTS` включал расширение `.mlt`,
то есть разовая чистка класса 4 снесла бы 2 198 файлов `done_projects/*.mlt` — ровно те, на
которые ссылаются аргументы `MELT_*`-процессов (`KaraokeProcess.kt:1135-1147`). Это нарушало
бы решение владельца и SC-008.

Что сделано:

- из `DONE_PROJECTS` убран `.mlt`; объёмы класса пересчитаны (6 452 файла ≈4,4 ГБ вместо
  8 650 / 8,7 ГБ), `note` исправлен — «читателей нет» верно для `.kdenlive`/`.run`/`.txt`;
- добавлен отдельный класс `MLT_PROJECTS` (`done_projects/*.mlt`, 2 198 файлов / 4,3 ГБ) с
  вердиктом `KEEP` и пояснением, почему `.mlt` остаётся;
- `KEEP` включён в `isProtected`: теперь API чистки отбивает такие классы так же, как
  `PROTECTED`/`NOT_TOUCH` (иначе `MLT_PROJECTS` можно было бы удалить через `plan`/`apply`);
- отказ планировщика переведён с `require` (давал бы 500) на `CleanupException` → `400`;
- правило матчинга вынесено в общий код (`CleanupFileMatcher.matchesPath` /
  `matchesFileName`), им пользуются и планировщик, и тесты;
- тесты: реестр 13 (+4), предохранители 16 (+3) — проверяют, что `.mlt` не попадает ни в один
  удаляемый класс, что `plan`/`apply` по `MLT_PROJECTS` отклоняются (400) и что представители
  всех классов `KEEP`/`NOT_TOUCH` (`song [lyrics].txt`, `song [sheetsage].pdf`,
  `song [key].json`, `song.mlt`, `key_bpm.csv`) не попадают ни в один удаляемый класс.

**Проверка остальных матчеров (пункт 4 ревью)**: пересечений с `KEEP`/`NOT_TOUCH` не найдено.
`HYGIENE_JUNK` не матчит `key_bpm.csv` (точное имя) и `LogoAlbum.png` (требуется имя из
списка или каталог/расширение видео); `SRT` ограничен корнем папки песни и расширениями
`.srt`/`.srt.backup` — в известные подкаталоги (`done_files`, `sheetsage`, `hdemucs_mmi`,
`symlink_*`) не заходит; `SONG_SIDECAR` — только `.song`/`.settings`. Единственное
наблюдение: правило «корень папки песни = родитель, не входящий в известные подкаталоги»
означает, что файл `.srt` во вложенной пользовательской папке внутри альбома тоже попадёт в
класс `SRT`; класс удаляется только по утверждённому отчёту сухого прогона, поэтому риск
ограничен и вынесен на приёмку.

## Инцидент на первом старте и его исправление (P0, 2026-10-16)

**Симптом.** После выката `karaoke-app` не стартовал:
`IllegalStateException: Property APP_WORK_ON_SERVER should be initialized before get`
→ `BeanCreationException: Error creating bean with name 'cleanupController'` → контейнер вышел с кодом 1.

**Причина.** `ArtifactCleanupPlanner` имел значение по умолчанию
`roots: List<String> = PROJECT_ROOT_FOLDERS`, которое Kotlin вычисляет **в конструкторе**.
`CleanupController` — eager-бин Spring, он создаётся раньше, чем `KaraokeAppService`
выставит `APP_WORK_ON_SERVER`; обращение к `PROJECT_ROOT_FOLDERS` тянуло
`Constants.<clinit>` → `WORKING_DATABASE = Connection.local()` → `Connection.<clinit>`,
который читает `APP_WORK_ON_SERVER`. Порядок инициализации не совпал — приложение упало.

**Почему не поймали раньше.** Ни один гейт этого не видит: компиляция, ktlint, doc-гейты и
unit-тесты проходят; в CI **нет job'а с запуском тестов и нет smoke-старта приложения**.
Дефект виден только при фактическом старте контейнера.

**Исправление.** Корни обхода вычисляются лениво — поле `roots` по умолчанию `null`,
обращение к `PROJECT_ROOT_FOLDERS` перенесено в `effectiveRoots()`, вызываемый только из
`plan()`, то есть уже после старта. Явно переданные корни (тесты) по-прежнему работают.

**Регрессионный тест.** `karaoke-app/src/test/.../services/cleanup/ArtifactCleanupStartupWiringTest.kt`
(4 теста): конструктор контроллера и сервиса с параметрами по умолчанию не трогает глобалы
приложения; поле `roots` остаётся пустым до первого `plan()`; явный override не подменяется.
Проверено, что тест **падает** на старом коде: с жадным вычислением воспроизводятся
`ExceptionInInitializerError` и `NoClassDefFoundError: Could not initialize class ConstantsKt`
— то есть страж ловит именно этот класс дефекта.

**Пробел инфраструктуры (вне этой поставки).** Стоит завести проверку старта приложения
(smoke) в деплое или в CI — иначе следующий дефект порядка инициализации снова дойдёт
до боевого контейнера.

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
6. **Рекомендация после массового удаления**: сбросить/прогреть кеш метаданных хранилища
   (`tbl_storage_metadata_cache`, спеки #446/#449) — чистка работает с файловой системой и
   кеш не проверяет (см. `data-model.md` §7).

## Открытые вопросы и факты, найденные при реализации

1. **FR-009 vs живой melt — решено владельцем** (сужение до `.kdenlive`/`.run`, см. выше).
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
