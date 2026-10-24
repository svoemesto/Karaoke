# Отчёт по #388 — PKG-S3-03 «TEST/karaoke-web» (BR-511, BR-512, BR-513)

> **Пакет**: PKG-S3-03 · **волна B** · **severity S3** · **модуль**: `karaoke-web`
> **Ветка**: `568-pkg-s3-03-web-tests` (worktree `.worktrees/waveB-web-tests`, от master `6cbaad02`)
> **Аудит**: #336, порция **P-40** (`specs/336-code-audit/findings/P-40.md`, реестр
> `specs/336-code-audit/registry.csv`: BR-511/BR-512/BR-513 → `work_package=388`).
> **Дата**: 2026-10-24.
> **Объём**: 2 файла тестов; код приложения, публичное поведение и `knowledge/` не менялись.

## Что было

Три записи аудита о тестах `karaoke-web`, которые **не могут упасть** — зелёный прогон
не означал, что проверка вообще выполнялась.

| BR | Файл | Что не так |
|---|---|---|
| BR-511 | `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/util/Mp3TrimmerTest.kt:16–42` | Внешние процессы (`ffmpeg`, `ffprobe`) запускались **без таймаута**: `waitFor()` без ограничения и без `destroy()`. Зависший бинарник — бесконечное ожидание (в CI — упор в лимит раннера). Вывод процесса (`redirectErrorStream(true)` → stdout) **вообще не читался**, хотя `waitFor()` выполнялся: при объёме вывода больше pipe-буфера (~64 КБ) процесс блокировался на `write()` — тоже навсегда. |
| BR-512 | `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/util/Mp3TrimmerTest.kt:48–49` | Два «реальных» теста обрезки mp3 начинались с `assumeTrue(toolAvailable("ffmpeg"), "ffmpeg not available — skipping")` (и то же для `ffprobe`). При отсутствии бинарников JUnit **молча помечал тесты skipped**, а не упавшими: проверка обрезки mp3 не выполнялась ни в одном автоматическом окружении, дефект `Mp3Trimmer` (битый фрейм-граница) проходил незамеченным. |
| BR-513 | `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkDateTimeTest.kt:47–58` | Тест был назван «`toMskLocalDateTime is invariant under JVM default timeZone`» и в комментарии заявлял проверку независимости от TZ JVM, но **системную TZ он не менял** (это было признано в самом комментарии). Проверялось лишь то, что при текущей TZ функция вернула московский час, — а TZ процесса в CI = `Europe/Moscow`, то есть регресс с `ZoneId.systemDefault()` внутри функции оставался зелёным. |

Дополнительно (по карточке P-40, п. «Сверки»): остальные тесты модуля
(`ZakromaStreamProgressTest`, `PublicPlayerControllerAccessTest` и др.) дефектов не имеют —
они проверяют поведение по существу и правки не требуют.

## Что стало

Правлены **только файлы тестов**; код приложения (`Mp3Trimmer.kt`,
`SongShareLinkService.kt`) не менялся — обе находки чинятся правкой теста.

### BR-511 — таймауты внешних процессов + вывод в файл

`karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/util/Mp3TrimmerTest.kt`:

- Единый хелпер `runTool(cmd, log, timeoutSeconds)`:
  - `process.waitFor(timeoutSeconds, TimeUnit.SECONDS)` — жёсткий предел (60 с для рабочих
    вызовов, 15 с для проверки доступности бинарника);
  - при таймауте — `process.destroyForcibly()` + `fail(...)` с внятным сообщением
    («не завершился за N с — процесс убит») и хвостом вывода процесса;
  - `redirectErrorStream(true)` (ADR-0006) **и** `redirectOutput(log)` — вывод пишется в файл,
    а не остаётся в pipe: переполнение pipe-буфера больше не может заблокировать процесс,
    а текст доступен в диагностике падения;
  - `readLog()` + `assertTrue(exit == 0, …)` — ненулевой код возврата больше не остаётся
    «незамеченным» (раньше код возврата `ffmpeg` вообще не проверялся).
- Генерация фикстуры (`generateVbrFixture`) и замер длительности (`probeDurationSeconds`)
  переведены на `runTool`; длительность парсится из вывода `ffprobe` по первой строке,
  которую удаётся разобрать как число.

### BR-512 — «инструмента нет» больше не «пропуск», а падение

- `assumeTrue(...)` удалён из обоих тестов.
- `assertToolAvailable(name, tmp)`: если бинарника нет (или он не отвечает на `-version`),
  тест **падает** с сообщением: «Внешний инструмент 'ffmpeg' обязателен для этого теста: без
  него проверка обрезки mp3 не выполняется (BR-512: молчаливый skip прятал регрессии
  Mp3Trimmer). Установите ffmpeg/ffprobe (apt install ffmpeg) …».
- Смысл: **зелёный прогон означает, что проверка обрезки mp3 действительно выполнилась**.
  Это первый вариант правки из карточки P-40-02 («установить ffmpeg в CI-образ и убрать
  `assumeTrue` — тогда отсутствие бинарника есть падение»). Второй вариант карточки (джоб CI,
  который ставит ffmpeg и падает на skipped) требует правки `.github/workflows/lint.yml` —
  это **BR-507, отдельный пакет #369** (см. «Что осталось за владельцем»).

### BR-513 — TZ-инвариант теперь действительно проверяется

`karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkDateTimeTest.kt`:

- Тест принудительно меняет TZ процесса: `TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))`,
  восстановление — в `finally` (`TimeZone.setDefault(original)`), поэтому состояние JVM не
  протекает в другие тесты.
- Самопроверка: `assertEquals("America/New_York", ZoneId.systemDefault().id, …)` — если бы тест
  не смог сменить TZ, он упал бы, а не проверял бы пустоту.
- Основное утверждение: `toMskLocalDateTime(1786431456000L)` обязан вернуть
  `2026-08-11T09:57:36` (московское стенное время) при любой TZ JVM.
- Контроль-эталон: тот же epoch ms в `America/New_York` — это `02:57:36`
  (`assertEquals(2, systemDefaultHour, …)`) — если это перестанет выполняться, тест теряет смысл
  и должен падать, а не молчать.

## Проверки «до/после» (факты с диска)

Демонстрации выполнены в этом же worktree; артефакты (логи, шимы-фейки ffmpeg/ffprobe,
init-скрипт gradle) лежат в `.scratch/audit388/` (в git не попадают, каталог в `.gitignore`).
Фейковые бинарники подставлялись в `PATH` тест-процесса через init-скрипт gradle
(`environment("PATH", …)`), продакшн-код не трогался.

### BR-512 — «ffmpeg недоступен»

**ДО** (тест из `git HEAD`, фейковый `ffmpeg`/`ffprobe` возвращают `exit 1`):

```
$ GRADLE_USER_HOME=… ./gradlew :karaoke-web:test --tests '…Mp3TrimmerTest' \
      --no-build-cache -I .scratch/audit388/shim-init-missing.gradle
BUILD SUCCESSFUL in 24s
```

отчёт JUnit: `Mp3TrimmerTest tests= 4 failures= 0 errors= 0 skipped= 2`
```
- trims a real VBR mp3 to the requested end at a frame boundary  => SKIPPED  Assumption failed: ffmpeg not available — skipping
- trims a real VBR mp3 to a mid-track range at a frame boundary    => SKIPPED  Assumption failed: ffmpeg not available — skipping
```
→ **проверка обрезки mp3 не выполнилась, прогон зелёный.**

**ПОСЛЕ** (тот же фейк, исправленный тест):

```
> Task :karaoke-web:test FAILED
Mp3TrimmerTest > trims a real VBR mp3 to the requested end at a frame boundary FAILED
Mp3TrimmerTest > trims a real VBR mp3 to a mid-track range at a frame boundary FAILED
4 tests completed, 2 failed
BUILD FAILED in 23s
```
сообщение падения:
```
org.opentest4j.AssertionFailedError: Внешний инструмент 'ffmpeg' обязателен для этого теста:
без него проверка обрезки mp3 не выполняется (BR-512: молчаливый skip прятал регрессии
Mp3Trimmer). Установите ffmpeg/ffprobe (apt install ffmpeg) или запустите тест в окружении,
где они есть. Вывод проверки: fake ffmpeg: not installed
```
→ `skipped= 0`, `failures= 2`.

### BR-511 — переполнение pipe-буфера (фейковый ffmpeg пишет 1 МБ в stdout)

**ДО** (тест из `git HEAD`):

```
$ timeout 100 env GRADLE_USER_HOME=… ./gradlew :karaoke-web:test --tests '…Mp3TrimmerTest' … -I shim-init-flood.gradle
EXIT=124 (124 = timeout: процесс ffmpeg завис на переполнении pipe, тест не отработал)
> Task :karaoke-web:test          ← строка последняя: тест так и не завершился
```
→ **тест висел бесконечно** (процесс заблокирован на `write()` в непрочитанный pipe).

**ПОСЛЕ** (исправленный тест):

```
4 tests completed, 2 failed
BUILD FAILED in 25s
org.opentest4j.AssertionFailedError: ffmpeg не создал фикстуру (код возврата 0). Вывод: xxxx…
```
время класса тестов — `time= 4.491` с (вместо бесконечности), весь вывод процесса (1 МБ)
попал в диагностику.
→ **взаимной блокировки больше нет**: процесс отработал за секунды, тест упал по существу
(фикстура не создана), а не завис.

### BR-511 — зависший ffmpeg (`sleep 600`)

**ДО** (тест из `git HEAD`):

```
$ timeout 100 env GRADLE_USER_HOME=… ./gradlew :karaoke-web:test --tests '…Mp3TrimmerTest' … -I shim-init-hang.gradle
EXIT=124 (124 = тест висит бесконечно на waitFor() без таймаута)
> Task :karaoke-web:test          ← последняя строка: тест не завершён
```

**ПОСЛЕ** (исправленный тест):

```
4 tests completed, 2 failed
BUILD FAILED in 2m 36s
org.opentest4j.AssertionFailedError: Внешний инструмент 'ffmpeg' не завершился за 60 с — процесс убит. Вывод:
```
время класса — `time= 120.135` с (2 теста × 60 с таймаут).
→ **зависания больше нет**: процесс принудительно убит, тест упал с диагностикой.

### BR-513 — регресс `ZoneId.systemDefault()` в `toMskLocalDateTime`

Временно (на время демонстрации) в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt`
явная зона МСК заменена на `ZoneId.systemDefault()`; после демонстрации файл восстановлен
(`git diff` по нему пуст).

**ДО** (тест из `git HEAD` + сломанная реализация):

```
BUILD SUCCESSFUL in 53s
SongShareLinkDateTimeTest tests= 4 failures= 0 errors= 0 skipped= 0
- toMskLocalDateTime is invariant under JVM default timeZone() => PASSED
```
→ **регресс FR-014 не пойман** (TZ процесса = Europe/Moscow, `systemDefault()` совпадает с МСК).

**ПОСЛЕ** (исправленный тест + та же сломанная реализация):

```
> Task :karaoke-web:test FAILED
SongShareLinkDateTimeTest > toMskLocalDateTime is invariant under JVM default timeZone() FAILED
4 tests completed, 1 failed
BUILD FAILED in 14s
org.opentest4j.AssertionFailedError: epoch ms must be converted to the Moscow wall clock
regardless of the JVM default TZ ==> expected: <2026-08-11T09:57:36> but was: <2026-08-11T02:57:36>
```
→ **регресс пойман**.

### Сводка «до/после»

| BR | Сценарий | ДО | ПОСЛЕ |
|---|---|---|---|
| BR-512 | ffmpeg/ffprobe недоступны | `skipped= 2`, `BUILD SUCCESSFUL` | `failures= 2`, `BUILD FAILED`, `skipped= 0` |
| BR-511 | переполнение pipe-буфера | тест висит (exit 124) | тест падает за 4.5 с с диагностикой |
| BR-511 | зависший ffmpeg | тест висит (exit 124) | процесс убит через 60 с, тест падает с диагностикой |
| BR-513 | регресс `ZoneId.systemDefault()` | `BUILD SUCCESSFUL` (регресс не пойман) | `BUILD FAILED`: `expected 09:57:36 but was 02:57:36` |

## Проверки проекта (до коммита)

| Проверка | Результат |
|---|---|
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test` | **PASS** — 75 тестов, 0 failures, 0 errors, **0 skipped** (до правки было те же 75, 0 skipped) |
| То же под `TZ=Asia/Vladivostok` (сценарий из старого комментария BR-513) | **PASS** — `Mp3TrimmerTest` 4/4, `SongShareLinkDateTimeTest` 4/4 |
| `GRADLE_USER_HOME=… ./gradlew ktlintCheck` (все модули) | **PASS** — `BUILD SUCCESSFUL` |
| `GRADLE_USER_HOME=… ./gradlew :karaoke-web:ktlintCheck` | **PASS** (после одной правки пустой строки после `companion object`) |
| `bash tools/check-knowledge-structure.sh` | **OK** — 9/9 проверок |
| `python3 tools/check-doc-references.py` | **OK** — 4102 ссылки, 2236 путей, 306 эндпоинтов |
| `bash tools/check-subagent-isolation.sh` | **OK** (до коммита: «ветка не содержит новых commits») |
| `pre-commit run --files <2 изменённых файла>` | **PASS** — 9 хуков (все always-run guard'ы + ktlint), остальные — «no files to check» |
| `python3 tools/check-ssot-impact.py` | **INFO: no significant changes (only docs/tests/tooling)** — тесты исключены правилами `.ssot-map.yml` (`.*/src/test/.*`, `.*/test/.*Test\.kt$`), `knowledge/` править не требуется |

Замечания по среде (не по правке):

- `pre-commit` **не установлен** как git-hook в общем `.git/hooks` репозитория (лежат только
  `*.sample`), поэтому хуки прогонялись явно — как предписывает `knowledge/guidelines/code-style.md`
  («Pre-commit (все 7 проверок): `pre-commit run --all-files`»).
- Фронтовые хуки (eslint/prettier для `webvue3`, `karaoke-public`) не выполнялись: в worktree
  нет `node_modules`, а правка фронтовых файлов не затрагивает. В CI эти джобы отработают.
- Две сборки в ходе проверок упали с «Gradle build daemon disappeared unexpectedly» — это
  не тесты: причина — параллельные gradle-дамоны нескольких волновых пакетов на одной машине.
  Повторные прогоны (в т.ч. `--no-daemon`) — зелёные.

## Knowledge-first (что прочитано до правки)

| Шаг | Что | Релевантное |
|---|---|---|
| 1 | `AGENTS.md` (v3.4.1), `knowledge/README.md`, `knowledge/domains/README.md` | Домен `karaoke-web`; hard-gates (gradle c `GRADLE_USER_HOME`, git-CI-gate, OpenProject-workflow) |
| 2 | grep по `knowledge/`: `test` (104 совпадения), `JUnit\|assertThrows\|assertEquals` (3), `mock\|Mockito\|mockk\|assumeTrue\|skipped` (5), `toMskLocalDateTime\|FR-014` (8) | `guidelines/code-style.md:34–38` — «Юнит-тесты: JUnit 5», «Интеграционные тесты: помечены `@Disabled`»; `adr/0006-processbuilder-redirect-errorstream.md` — «каждый новый `ProcessBuilder()` ОБЯЗАН иметь `.redirectErrorStream(true)`» + регрессионный тест-пример; `domains/karaoke-web/components/song-share-link-service.md:108–110,185–186` — инвариант «все временные метки — naive-МСК», `setObject(…, Types.TIMESTAMP)` вместо `setTimestamp` |
| 3 | `knowledge/domains/karaoke-web/domain.md` + компоненты `song-share-link-service.md`, `services-overview.md`, `public-controllers-4.md` | `toMskLocalDateTime` — «алгоритмический, а не конфигурационный выбор (FR-002, FR-014): тесты на машинах в любом TZ должны давать одинаковую строку» — ровно тот инвариант, который BR-513 и не проверял |
| 4 | `knowledge/adr/local-0001…local-0008.md` | Ни один не про тестовую гигиену; правок не требует |
| 5 | `.ssot-map.yml`, `tools/check-ssot-impact.py` | Тесты исключены из SSoT-impact → `knowledge/` не меняется (проверено гейтом) |

Связь BR-511 с ADR-0006: находка и ADR описывают один и тот же класс дефекта — **pipe, который
никто не читает**. ADR требует `redirectErrorStream(true)`, но не запрещает `redirectOutput(файл)`;
правка теста убирает сам pipe, сохраняя требование ADR.

## Что осталось за владельцем

1. **CI-джоб с тестами и ffmpeg — вне этого пакета.** BR-512 закрыт вариантом «нет бинарника —
   падение», но джоба `:karaoke-web:test` в CI сейчас нет вообще — это **BR-507**
   (`ci-no-build-no-test:ci`, порция P-78, `work_package=369`, ветка `565-pkg-s2-05-guards-ci`).
   Когда он появится, в образ раннера **обязательно должен стоять `ffmpeg`** (иначе тесты
   обрезки mp3 будут падать по новому правилу — это ожидаемое поведение, не регресс).
   Второй вариант из карточки P-40-02 (джоб, который ставит ffmpeg и явно падает на `skipped`)
   остаётся как страховка для BR-507.
2. **Обновить карточку аудита**: `specs/336-code-audit/findings/P-40.md` (статусы находок
   P-40-01/02/03 → «исправлено») и `specs/336-code-audit/registry.csv` (BR-511/512/513 →
   `work_package=388`, статус). Не сделано здесь намеренно: правки карточек аудита ведутся в
   ветке аудита (`553-336-code-audit`), а волна B идёт несколькими параллельными пакетами —
   общий `registry.csv` собирает лид (прецедент — отчёт по #357, `specs/554-.../report.md`).
3. **Локальные окружения без ffmpeg**: теперь `:karaoke-web:test` на такой машине будет
   падать (раньше — молча пропускать). Это намеренное поведение по карточке; установка
   `ffmpeg` описана в тексте падения.

## Ветка и коммиты

- Ветка: `568-pkg-s3-03-web-tests` (worktree `/home/nsa/Karaoke/.worktrees/waveB-web-tests`,
  от master `6cbaad02`). Коммит в master не делался, push/PR — не выполнялись.
- Изменены только файлы тестов:
  - `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/util/Mp3TrimmerTest.kt`
  - `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkDateTimeTest.kt`
- Код приложения не менялся (проверено `git diff` — изменения только в `src/test`).
