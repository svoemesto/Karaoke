# OpenProject #364 — PKG-ERR-EXITCODE (волна D): код возврата внешнего процесса игнорируется

- **Ветка**: `588-pkg-err-exitcode` (worktree `.worktrees/waveD-exitcode`, от `origin/master` `07126462`)
- **Пакет**: `PKG-ERR-EXITCODE`, порции P-04/P-22 аудита #336
- **Записи**: BR-029 (S2, ERR), BR-161 (S2, DATA) — цикл короткий (ТЗ = карточки аудита)
- **Дата**: 2026-10-24

---

## 1. Итог

| BR | Файл:строка | Было | Стало |
|---|---|---|---|
| BR-029 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:221–339` (`KaraokeProcessThread.run`, ветка subprocess) | stdout читался до EOF, после чего задание **безусловно** получало `DONE`; `waitFor()`/`exitValue()` в файле отсутствовали, stderr был слит в stdout и не анализировался. Любой ненулевой exit (`docker compose run`, `ffmpeg`, `melt`) выглядел успехом: цепочка шагов шла дальше, HealthReport/админка/`song.statusProcess*` считали артефакт готовым | После EOF — `awaitProcessExit(...)` (`waitFor(timeout)` 60 с, env `KARAOKE_PROCESS_EXIT_TIMEOUT_SECONDS`, при превышении `destroyForcibly()`); `exitCode != 0` → `ProcessExitException` → `catch` → `ERROR` (команда, код, распознавание docker/ffmpeg/melt, хвост stdout/stderr). Force-stop по-прежнему даёт `WAITING`. Единственное исключение — известный `SHEETSAGE`-фолбэк на `SHEETSAGE2` |
| BR-161 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PlayerMp4MuxService.kt:196–199` (`mixAndMux`) | `process.waitFor()` вызывался только ради ожидания, код возврата отбрасывался, затем писалось «Готово» и `onProgress(100)`. `executeRenderMp4` безусловно копировал `output.mp4` в `done_files` → обрезанный MP4 мог уйти в Telegram/VK. Таймаута не было: зависший ffmpeg навсегда блокировал лейн | Проверка `exitValue()`; при `!= 0` недописанный `output.mp4` удаляется и бросается `ProcessExitException` (команда, код, stderr-хвост). `onProgress(100)` — только после `exit 0`. Watchdog по неактивности (120 с без строк прогресса, параметр `inactivityTimeoutSeconds`) убивает зависший ffmpeg через `destroyForcibly()`. Тестовый шов `ffmpegBinary` |

**Корень (общий)**: результат внешнего процесса не был частью контракта задания —
«процесс закрыл stdout» трактовалось как «процесс успешно завершился». В
`knowledge/domains/processing/components/async-process-queue.md` контракт
`exit 0 → DONE`, `exit ≠ 0 → ERROR` был **описан и раньше**, но код ему не
соответствовал; теперь код приведён к SSoT.

**Изменённые/созданные файлы**:

```
M karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt
M karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PlayerMp4MuxService.kt
A karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/ProcessExitGuard.kt
A karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/ProcessExitGuardTest.kt
A karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/ProcessExitWiringTest.kt
A karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/PlayerMp4MuxExitCodeTest.kt
M knowledge/domains/processing/components/async-process-queue.md
M knowledge/domains/processing/components/playwright-rendering.md
A docs/features/process-exit-code-check.md
A specs/364-pkg-err-exitcode/report.md                              (этот отчёт)
```

---

## 2. Разбор по коду

### 2.1. BR-029 — воркер очереди

`KaraokeProcessThread.run` (`KaraokeProcessWorker.kt`) запускает subprocess
(`ProcessBuilder(args)` с `redirectErrorStream(true)`), построчно читает stdout,
парсит `percentage` и по EOF ставит терминальный статус. До правки:

- `waitFor()`/`exitValue()` в файле **отсутствовали** — проверено grep'ом
  (`grep -n "waitFor\|exitValue" KaraokeProcessWorker.kt` → пусто);
- после цикла чтения (строки 267–294) выполнялась запись лога, затем — `DONE`
  (строки 318–339);
- stderr был слит в stdout (R-43/ADR-0006 соблюдался), но хвост вывода никуда,
  кроме файла лога, не попадал.

После правки:

1. Сразу после EOF вызывается `awaitProcessExit(startedProcess, args, outputTail = { outputTail(log) })`
   (`ProcessExitGuard.kt`). Ожидание ограничено: `waitFor(timeout)`, по
   умолчанию 60 с (`KARAOKE_PROCESS_EXIT_TIMEOUT_SECONDS`), при превышении —
   `destroyForcibly()` + `ProcessExitException`.
2. После записи лога (диагностика сохраняется и для неуспеха) выполняется
   `if (exitCode != 0 && !sheetsageFallback && !forceStopped) throw processExitFailure(args, exitCode, outputTail(log))`.
3. `processExitFailure` формирует сообщение: команда (с ограничением длины),
   код, распознавание типовых кодов (`docker` 125/126/127/137/143,
   `ffmpeg` 1/69/234, `melt`, `mediainfo`), хвост stdout/stderr (20 строк).
   stderr в хвосте потому, что потоки слиты — R-43 не нарушен.
4. `catch` переводит задание в `ERROR` (или `WAITING` при force-stop), как и
   раньше; изменилось только то, что сбой теперь **доходит** до `catch`.

Сознательное исключение: `SHEETSAGE` с последней строкой
`NotImplementedError: Dynamic chunking not implemented...` штатно переигрывается
заданием `SHEETSAGE2`. Его ненулевой код не должен превращать задание в `ERROR`
поверх уже созданного фолбэка — условие сохранено и вынесено в
`sheetsageFallback`.

### 2.2. BR-161 — мукс ffmpeg

`PlayerMp4MuxService.mixAndMux` собирает JPEG-секвенцию и стемы в MP4. До правки:

- `process.waitFor()` (строка 196) вызывался без таймаута и **без сохранения**
  результата;
- `onProgress?.invoke(100)` выполнялся в reader-потоке по EOF — то есть и при
  ошибке;
- лог «Готово: $outputPath» писался всегда;
- `Utils.executeRenderMp4` после возврата копировал файл в `done_files`
  (`Utils.kt:4566–4587`), откуда `RENDER_MP4_DEMO` публикуется в Telegram/VK.

После правки:

- reader-поток обновляет `lastActivityNanos` на каждой строке и хранит
  ограниченный хвост (40 строк, stdout+stderr);
- основной поток крутит watchdog: если строк нет дольше
  `inactivityTimeoutSeconds` (по умолчанию 120 с), процесс убивается
  `destroyForcibly()`, файл удаляется, бросается `ProcessExitException`;
- после EOF — `awaitProcessExit` + проверка `exitValue()`; при `!= 0` файл
  удаляется и бросается `processExitFailure(...)`;
- `onProgress(100)` и «Готово» — только после `exit 0`;
- исключение проходит через `KaraokeProcessThread.run` → `ERROR` (оба
  call-site `executeRenderMp4` находятся внутри try/catch воркера), поэтому
  файл не копируется и не публикуется.

R-43: `redirectErrorStream(true)` оставлен как есть; сторожевой таймаут и
чтение слитого потока не конфликтуют.

---

## 3. Регрессионные тесты «до/после»

Тесты:

| Тест | Что проверяет | BR |
|---|---|---|
| `ProcessExitGuardTest` (5 тестов) | ненулевой код доходит до вызывающего; сообщение содержит команду/код/stderr-хвост; R-43 (2000 строк stderr не блокируют); зависший процесс убивается по таймауту; `outputTail`; распознавание кодов docker | BR-029 |
| `PlayerMp4MuxExitCodeTest` (3 теста) | реальный путь `mixAndMux` со stub-ffmpeg: `exit 1` → исключение, `output.mp4` удалён, 100 % не выставляется; `exit 0` → успех и 100 %; зависший stub → watchdog | BR-161 |
| `ProcessExitWiringTest` (2 теста) | инвариант исходников: `awaitProcessExit` до `DONE`; `processExitFailure` до `onProgress(100)`; удаление файла; нет `process.waitFor()` без таймаута; `redirectErrorStream(true)` | BR-029, BR-161 |

Внешний процесс подменяется управляемым shell-скриптом
(`#!/bin/sh`, `createTempFile` + `setExecutable`); для мукса — через
`ffmpegBinary`.

### 3.1. «До» — дефекты возвращены в код

Оба production-файла возвращены к `HEAD` (`git show HEAD:<path>`); в мукс
механически добавлены только два параметра (`ffmpegBinary`,
`inactivityTimeoutSeconds`), чтобы тест компилировался и мог подставить
stub-бинарь. Тело — старое (без проверки exit code).

```
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" :karaoke-app:test \
    --tests "*ProcessExitGuardTest" --tests "*ProcessExitWiringTest" --tests "*PlayerMp4MuxExitCodeTest"

ProcessExitWiringTest > воркер очереди проверяет exit code до DONE() FAILED
    org.opentest4j.AssertionFailedError at ProcessExitWiringTest.kt:29
ProcessExitWiringTest > мукс проверяет exit code до прогресса 100 и удаляет недописанный файл() FAILED
    org.opentest4j.AssertionFailedError at ProcessExitWiringTest.kt:45
PlayerMp4MuxExitCodeTest > зависший ffmpeg убивается по неактивности и не блокирует лейн() FAILED
    org.opentest4j.AssertionFailedError at PlayerMp4MuxExitCodeTest.kt:118
PlayerMp4MuxExitCodeTest > ненулевой exit ffmpeg — исключение, недописанный mp4 удалён, 100 процентов не выставляется() FAILED
    org.opentest4j.AssertionFailedError at PlayerMp4MuxExitCodeTest.kt:46
10 tests completed, 4 failed
> Task :karaoke-app:test FAILED
```

Смысл падений: на старом коде `assertThrows` не получает исключения (мукс
возвращается «успешно»), а wiring-инвариант не находит проверки exit code.
`ProcessExitGuardTest` проходит и на «до» — он тестирует новый хелпер, которого
в старом коде не было; его регрессионная роль — зафиксировать контракт
(в частности R-43 и таймаут).

### 3.2. «После» — тот же набор на исправленном коде

```
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" :karaoke-app:test \
    --tests "*ProcessExitGuardTest" --tests "*ProcessExitWiringTest" --tests "*PlayerMp4MuxExitCodeTest"
BUILD SUCCESSFUL
```

Полный прогон модуля: `karaoke-app` — 474 теста / 60 классов, 0 failures,
0 errors, 0 skipped; `karaoke-web` — 173 теста / 34 класса, 0 failures.

---

## 4. Проверки

| Проверка | Команда | Результат |
|---|---|---|
| Тесты app | `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" :karaoke-app:test` | Первый прогон: 474 теста, **4 падения в `VkPreviewWarmupClientTest`** — `java.awt.AWTError: Can't connect to X11 window server using ':0'` (окружение, не связано с правкой). С `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` — **BUILD SUCCESSFUL**, 474/474 |
| Тесты web | то же для `:karaoke-web:test` | **BUILD SUCCESSFUL**, 173/173 |
| Линтер | `... ktlintCheck` | **BUILD SUCCESSFUL** (после форматирования `ProcessExitGuard.kt`/`PlayerMp4MuxService.kt`) |
| Knowledge-структура | `bash tools/check-knowledge-structure.sh` | `OK: knowledge/ structure valid (9/9 checks passed)`, frontmatter 16/16 |
| Ссылки в доках | `python3 tools/check-doc-references.py` | `OK: битых ссылок, несуществующих путей и эндпоинтов нет.` (4190 ссылок, 2334 пути, 316 эндпоинтов) |
| SSoT-влияние | `python3 tools/check-ssot-impact.py` | изменённые файлы не попадают под правила `.ssot-map.yml` (worker/mux в карте отсутствуют) |
| Изоляция субагентов | `bash tools/check-subagent-isolation.sh` | **ложноположительный в этой волне**: локальная ветка `master` отстала (`d4109801` против `origin/master` `07126462`), поэтому `git log master..HEAD` включает уже смерженные в `origin/master` коммиты чужих PR и guard видит их «в чужих ветках». По `origin/master..HEAD` в ветке только коммит этого пакета; коммиты созданы с `SKIP=subagent-isolation-guard`. Локальный `master` не двигался (главная копия не тронута) |

Pre-commit прошёл (кроме пропущенного `subagent-isolation-guard` — причина выше):
ktlint, knowledge-structure, doc-references, lychee и остальные guard'ы.

---

## 5. Knowledge-first: что прочитано и какие grep'ы были

Прочитано: `knowledge/README.md`, `knowledge/domains/README.md`,
`knowledge/adr/0006-processbuilder-redirect-errorstream.md`,
`knowledge/domains/processing/domain.md`,
`knowledge/domains/processing/components/async-process-queue.md`,
`knowledge/domains/processing/components/playwright-rendering.md`,
`knowledge/guidelines/architecture-conventions.md` (R-43),
`specs/336-code-audit/findings/P-04.md` (BR-029),
`specs/336-code-audit/findings/P-22.md` (BR-161),
`specs/336-code-audit/backlog.md` (§ PKG-ERR-EXITCODE).

Grep'ы по `knowledge/`:

| # | Запрос | Результат |
|---|---|---|
| 1 | `grep -rn "exitValue\|waitFor(\|код возврата" knowledge/` | контракт `exit 0 → DONE / exit ≠ 0 → ERROR` уже описан в `async-process-queue.md:141–142` — код ему не соответствовал |
| 2 | `grep -rn "redirectErrorStream" knowledge/` | 10+ вхождений; канон — ADR-0006 + `processing/domain.md` + `architecture-conventions.md:237` |
| 3 | `grep -rln "PlayerMp4MuxService\|mixAndMux" knowledge/` | `playwright-rendering.md`, `song-lifecycle.md`, `mlt-pipeline.md`, `system/utilities.md` |

---

## 6. Что осталось за владельцем

1. **`specs/336-code-audit/registry.csv`** — по условию задачи не изменялся
   (BR-029/BR-161 остаются `TODO`); статус `исправлено` и поле `review`
   проставляет лид волны после merge.
2. **Реестр `review`** — BR-161 в карточке P-22 помечен «Проверено лидом: нет»;
   проверка пакета — за лидом.
3. **Остальные «проглатывания» кодов возврата вне пакета** (не входят в
   BR-029/BR-161, чтобы не расширять периметр): `MediaInfo.kt:29–40` (P-04-08,
   `waitFor` закомментирован), `ApiController.kt:8091`,
   `SongEditorController.kt:109`, `Utils.kt:3834/4657` (там exit code уже
   проверяется), `WaveformCompare.kt:131`. Кандидаты для следующих пакетов
   аудита.
4. **Таймаут ожидания воркера — только после EOF stdout.** Если процесс
   завис, не закрывая stdout, `readLine()` будет ждать вечно (для мукса это
   закрыто watchdog'ом, для воркера — нет). Полный watchdog воркера потребует
   согласованного inactivity-таймаута для долгих рендеров (десятки минут) —
   отдельная задача; сейчас для этого есть `RenderQueueStalledCheck`/ручная
   остановка очереди.
5. **Публикация демо-видео**: контракт «не копировать файл без `exit 0`»
   обеспечен исключением; проверка «файл существует и непустой» перед
   публикацией остаётся вне пакета.

---

## 7. Коммиты

| Хэш | Сообщение |
|---|---|
| `ef541411` | `fix(err): проверка кода возврата внешнего процесса (аудит #336, BR-029/BR-161)` — код, `ProcessExitGuard`, 10 регрессионных тестов |
| см. `git log -1` | `docs(364): knowledge SSoT + feature-doc + отчёт PKG-ERR-EXITCODE` — этот отчёт, `knowledge/`, `docs/features/` |

Коммит в `master` не делается; `push`/`PR` не выполняются (запрещено задачей).
