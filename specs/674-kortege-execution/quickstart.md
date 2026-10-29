# Quickstart: проверка кортежного исполнения

**Feature**: `674-kortege-execution` · **Дата**: 2026-10-29
**Назначение**: воспроизводимые сценарии проверки плана/реализации. Все сценарии — **на копиях**;
прод-очередь, прод-БД и контейнеры `karaoke-app` не трогаются.

---

## 0. Подготовка (hard gates)

Все команды — из корня репозитория `/home/nsa/Karaoke`.

```bash
# Переносимые пути от корня репозитория (AGENTS.md Pass 372-375):
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")/.gradle}"
export DOCKER_CONFIG="${DOCKER_CONFIG:-$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")/.docker}"
```

Запрещено на этом этапе: `curl` в прод, `docker restart`, `do.sh start_app` без согласия владельца
и без остановки очереди (Pass 380), любые DDL/DML к прод-БД, постановка заданий в рабочую очередь
без пометки «тест».

## 1. Unit-тесты (без БД, без docker)

```bash
./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.tuple.*"
```

Ожидаемо: зелёные `TupleSchedulerSqlTest`, `TupleScopeTest`, `SingleFlightTest`, `TupleRecoveryTest`,
`TupleFailureTest`, `ArtifactVerificationTest`, `TupleJanitorTest`; существующие
`ProcessExitWiringTest` и `KaraokeProcessCleanupTest` остаются зелёными.

Проверки, которые эти тесты фиксируют:

| Тест | Что доказывает | FR/SC |
|---|---|---|
| `TupleSchedulerSqlTest` | SQL выбирает только голову, один кортеж на лейн, `EXISTS WAITING`, без `process_order` между кортежами; `firstRunnableStep` = `MIN(order)` среди `WAITING` | FR-001/002/003 |
| `TupleScopeTest` | `scopeId = chainId ?: id`; `{scope}` подставлен; каталоги/имена контейнеров двух кортежей не совпадают; финальный шаг чистит только свой каталог | FR-004/005 |
| `SingleFlightTest` | живой кортеж → `SKIP`; `DONE`-строка живого кортежа не удаляется; завершённый кортеж → `CREATE` | FR-010 |
| `TupleRecoveryTest` | resume с первого не-`DONE`; `CREATING→WAITING`; безголовая цепочка получает голову; soft-deleted сосед не подвешивает кортеж | FR-011/FR-018 |
| `TupleFailureTest` | ненулевой код → весь кортеж `ERROR`, причина в `process_description`, оставшиеся шаги не исполняются, второй retry не ставится | FR-007/009/016 |
| `ArtifactVerificationTest` | ±0.5 с; отсутствие/чужой sidecar → key/bpm не применяются; `bpm=0`/пустой `key` → ошибка | FR-008/012/013 |
| `TupleJanitorTest` | старый осиротевший каталог удалён; каталог живого кортежа и корень целы | FR-005 |
| `TupleThreadLifecycleTest` | поток кортежа одноразовый: `closeThreadConnection()` вызван ровно один раз за кортеж (не на шаг), после последнего шага; второй кортеж закрывает своё соединение; `finally` вокруг цикла сохранён (ADR `local-0009`) | FR-018 |
| `TupleWithoutControlTest` | `withoutControl=true` (`SYMLINK`) не отменяет fail-fast, порядок и resume: сбой останавливает весь кортеж, следующий шаг не стартует | FR-003/007/018 |
| `AdminDoneFilterTest` | `status=DONE` и top-level выдача после `purgeFinishedTuples`: `DONE`-голова живого кортежа видна, завершённый кортеж удалён как при `deleteDone`, `tbl_processes_audit` не задет | FR-010/015 |
| `ArtifactIntegrityTest` | все виды находок `verifySong` (`missingStem`, `foreignDuration`, `staleArtifact`, `missingSidecar`, `sidecarMismatch`, `emptyKeyBpm`), правок нет | FR-012/014 |
| `AdminTupleContractTest` | `topLevelOnly` содержит `process_chain_id = id`; DTO содержит `chainState`/`chainStepIndex`/`progress` | FR-015 |
| `SyncTargetContractTest` | `SyncRegistry.all` не содержит `tbl_processes`; `recordhash_processes.sql` не менялся; `tools/check-recordhash-consistency.py` зелёный | FR-017 |

Линтеры и сборка (обязательно перед PR):

```bash
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
./gradlew :karaoke-app:ktlintCheck
./gradlew :karaoke-app:bootJar --parallel
cd webvue3 && npm run lint && npm run build && npm run format:check && cd ..
```

## 2. Integration: изоляция двух кортежей (manualTest, копии)

Основа — сценарий `.scratch/wf308/exp/queue.py` (`01-repro.md` §3.4, §6), портированный на Kotlin:
Kotlin-тест `TupleIsolationIT` в `karaoke-app/src/manualTest`.

```bash
# 1. короткие исходники разных длин (12 с и 9 с) — на копиях, не из прода:
mkdir -p /tmp/kortege-exp/src /tmp/kortege-exp/dest /tmp/kortege-exp/tmp
S="/sm-karaoke/done2/Сектор Газа/2026 - Чёрный вторник (Live Трибьют)"
ffmpeg -v error -y -i "$S/2026 (01) [Сектор Газа] - Лирика (Live Трибьют 2026).flac" -t 12 -c:a flac /tmp/kortege-exp/src/A-Lirika-12s.flac
ffmpeg -v error -y -i "$S/2026 (03) [Сектор Газа] - Бомж (Live Трибьют 2026).flac"    -t 9  -c:a flac /tmp/kortege-exp/src/B-Bomzh-9s.flac

# 2. прогон теста (stub docker пишет стемы длиной входного файла; реальные argv из Song.argsDemucs2)
./gradlew :karaoke-app:manualTest --tests "com.svoemesto.karaokeapp.tuple.TupleIsolationIT"
```

Ожидаемо:

- два кортежа в одном лейне исполняются **последовательно** (второй не начинает шагов, пока жив первый);
- у каждого кортежа свой каталог `.../tmp/<scopeId>/` и своё имя контейнера `demucs-<scopeId>`;
- стемы кортежа A имеют длительность 12 с, кортежа B — 9 с (сверка `ffprobe`);
- в логах нет `cannot stat` / `cannot remove` / `already in use`;
- каталоги обоих кортежей удалены их финальными шагами; корень `tmp` цел.

### 2.1 Спайк SHEETSAGE (обязателен до приёмки P3 п.4)

```bash
# два прогона на копиях с разными scopeId; реальные пути зависят от реализации
./gradlew :karaoke-app:manualTest --tests "com.svoemesto.karaokeapp.tuple.SheetsageScopeSpikeIT"
```

Критерий приёмки: два параллельных прогона не видят файлов друг друга; `rm -f` одного не уносит файлы
другого; результат каждого лежит в своём `scope`-каталоге. Если `~/sheetsage/sheetsage.sh` пишет
cwd-относительно — шаг запускается с `ProcessBuilder.directory(scopeDir)`; это фиксируется в P3 п.4.
Без выполненного спайка пункт считается невыполненным, а не «проверенным по коду».

Примечание: если `docker` недоступен, stub-`docker` кладётся в `PATH` теста; реальные образы
`demucs`/`keybpmfinder` в integration не используются.

## 3. E2E на копиях 8 песен (ручной стенд, не прод)

**Условие**: стенд с рабочей копией БД/каталогов (не прод). Контрольная пачка — `songId
30390–30397` («Сектор Газа / 2026 — Чёрный вторник»), копии файлов в отдельном `rootFolder`.

### 3.1 Подготовка стенда

1. Остановить очередь стенда (`:8898/process/stop`), дождаться пустых `WORKING/CREATING`.
2. Скопировать 8 песен в тестовый `rootFolder`; убедиться, что у копий нет стемов/`[key].json`.
3. Зафиксировать «до»: время обработки пачки (для SC-006), содержимое готовых артефактов.

### 3.2 Прогон

1. Поставить кортежи для 8 песен (импорт папки или HR-repair на стенде) — **только на стенде**.
2. Дождаться конца очереди; снять `tbl_processes` (для каждой песни: цепочки, статусы, причины).
3. Для каждой песни:

```bash
# длительность источника
ffprobe -v error -show_entries format=duration -of csv=p=0 "<song>.flac"
# длительности стемов
for f in "<rootFolder>/hdemucs_mmi/<song>"-{accompaniment,vocals}.flac; do
  ffprobe -v error -show_entries format=duration -of csv=p=0 "$f"
done
# key/bpm и sidecar
cat "<rootFolder>/<song> [key].json"; cat "<rootFolder>/<song> [key].json.meta"
```

**Измерение SC-001 (согласовано с лидом 2026-10-29).** Приёмка:
«каждая песня получает свои стемы и свой key/bpm; допускается один автоматический CPU-retry,
отражённый в отчёте; строка `ERROR` исходного кортежа не считается провалом приёмки, если
retry-кортеж довёл артефакты до проверенного результата». Считать по артефактам и отчёту
(таблица `song → стемы → key/bpm → проверка`), а не по числу строк `ERROR` в `tbl_processes`.

Ожидаемо (SC-001/SC-002/SC-003/SC-007):

- у всех 8 песен есть свои стемы и свой `[key].json`;
- длительность каждого стема = длительность источника ±0.5 с;
- `[key].json` разобран, `bpm > 0`, `.meta` совпадает с источником;
- ни один шаг с ненулевым кодом не показан как «готово»; причина видна в админке.

### 3.3 Повторный прогон (SC-004)

Повторить постановку той же пачки. Ожидаемо: 0 изменений готовых артефактов, 0 новых кортежей для
готовых песен (single-flight), `DONE`-строки живых кортежей не удалены.

### 3.4 Рестарт посреди кортежа (SC-008, FR-011/018)

1. Запустить пачку, дождаться, пока кортеж на шаге `k > 0` (видно в админке по `chainStepIndex`).
2. Остановить очередь (`doStop`), затем `forceStop` (кнопка «Стоп» дважды) — кортеж целиком в `WAITING`.
3. Перезапустить `karaoke-app` стенда (только стенд; на `nsa-i9` — по согласию владельца и Pass 380).
4. Ожидаемо: кортеж продолжается с шага `k` (первый не-`DONE`), а не с нуля; в `tbl_processes` нет
   `WORKING`/`CREATING` без живого потока; прогресс не «застрял».

### 3.5 Ремонт и целостность (SC-005, FR-013/014)

```bash
# 1. Сухой прогон: только находки, без заданий
curl -s -X POST http://localhost:7906/api/admin/artifacts/repair \
  -H 'Content-Type: application/json' \
  -d '{"songIds":[30390,30391,30392,30393,30394,30395,30396,30397],"dryRun":true}'

# 2. Явный ремонт (после подтверждения находок), только на стенде
curl -s -X POST http://localhost:7906/api/admin/artifacts/repair \
  -H 'Content-Type: application/json' \
  -d '{"songIds":[…],"dryRun":false,"includeKeyBpm":true}'

# 3. Отчёт ежедневной проверки (read-only)
curl -s http://localhost:7906/api/admin/artifacts/integrity
```

Ожидаемо: после ремонта отчёт целостности — `findingsTotal = 0`; до ремонта — находки
`foreignDuration`/`sidecarMismatch` для 30390/30391 (чужие стемы/key-bpm, `01-repro.md` §3.1–3.3);
ежедневная проверка ничего не правит.

**Проверка FR-014 (ежедневный отчёт):** дождаться срабатывания `karaoke.integrity.cron` (или
`queue.integrity.enabled=true` + временный cron на стенде) и убедиться, что (а) появился файл
`PATH_TO_LOGS/artifact-integrity/<date>.json`, (б) `GET /api/admin/artifacts/integrity` отдаёт тот же
отчёт, (в) mtime и содержимое артефактов песни **не изменились** (проверка молча не правит),
(г) находки видны оператору (SSE/эндпоинт).

## 4. Админка (FR-015, SC-007)

1. Открыть таблицу «Процессы» (`webvue3`), `topLevelOnly=true`.
2. Ожидаемо: голова кортежа видна как строка с «Шаг k из N» и состоянием кортежа; дети — по
   раскрытию (`topLevelOnly=false&parentId=<headId>`).
3. Во время выполнения: видно песню, текущий шаг, прогресс; после сбоя — причина в строке и в
   `GET /api/admin/processes/{id}`.
4. `POST /api/admin/processes/{id}/retry` на `ERROR`-шаге кортежа: весь кортеж возвращается в
   `WAITING`, приоритет восстановлен, в audit — одна запись на кортеж.
5. **Фильтр `DONE` и история после purge**: `GET /api/admin/processes?status=DONE` возвращает
   ровно те `DONE`-строки, что есть в БД (как до фичи); живой кортеж с `DONE`-головой виден и
   раскрывается; завершённый кортеж удаляется при старте/новой постановке так же, как раньше это
   делал `deleteDone`; `tbl_processes_audit` не затронут. Если оператор ожидает более долгую
   историю `DONE`, это отдельное решение (retention) — в спеке не меняется.

## 5. Откат

```text
1. Остановить очередь стенда/прода (Pass 380), дождаться пустых WORKING/CREATING.
2. Вернуть предыдущий образ karaoke-app (do.sh start_app после stop_app), миграций нет.
3. Строки tbl_processes остаются пригодными для старого кода (головы/шаги размечены chain_id/order).
```

## 6. Чего НЕ делать

- Не запускать сценарии на прод-очереди и прод-БД.
- Не вызывать `docker restart`, не перезапускать `karaoke-app` без согласия владельца и Pass 380.
- Не удалять вручную `/sm-karaoke/system/*-docker/tmp` целиком — уборка только кодом (владелец/janitor).
- Не править `deploy/recordhash_processes.sql` и миграции: схема не меняется.
- Не путать `webvue3` и `karaoke-public` (`cd webvue3 && npm run …`; в задании опечатка «webview3»).
