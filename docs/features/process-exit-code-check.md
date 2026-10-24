# Код возврата внешнего процесса проверяется (аудит #336, BR-029/BR-161)

> **Status**: active
> **Feature Key**: process-exit-code-check
> **Last Updated**: 2026-10-24
> **Spec**: [specs/364-pkg-err-exitcode/report.md](../../specs/364-pkg-err-exitcode/report.md)
> **Issue**: OpenProject #364 (пакет `PKG-ERR-EXITCODE`, волна D)
> **Branch**: `588-pkg-err-exitcode`
> Per-feature документ (Constitution § VI FR-009).

## Что делает

Внешний процесс (`docker compose run`, `ffmpeg`, `melt`) считается успешным
только при коде возврата `0`. Ненулевой код завершает задание очереди со
статусом `ERROR` и внятной причиной (команда, код, распознавание типовой
ошибки, хвост stdout/stderr), а не `DONE`. Рендер MP4 не копирует и не
публикует недописанный файл.

## Зачем

Аудит #336 (порции P-04 и P-22, записи BR-029 и BR-161, severity S2):

- `KaraokeProcessThread.run` читал stdout до EOF и **безусловно** ставил
  `DONE`; `waitFor()`/`exitValue()` в файле отсутствовали. Сбой любого шага
  (`docker compose run`, `ffmpeg`, `melt`) выглядел успехом: цепочка шла
  дальше, HealthReport и админка считали артефакт готовым.
- `PlayerMp4MuxService.mixAndMux` вызывал `process.waitFor()` ради ожидания,
  код возврата отбрасывал и писал «Готово». `executeRenderMp4` затем копировал
  `output.mp4` в `done_files`, и обрезанный/неиграбельный файл мог уйти в
  Telegram/VK (`RENDER_MP4_DEMO`). Таймаута не было: зависший ffmpeg навсегда
  блокировал лейн рендера.

Документация (`async-process-queue.md`) уже описывала контракт
`exit 0 → DONE`, `exit ≠ 0 → ERROR` — код ему не соответствовал.

## Как работает

1. **Единый хелпер.** `karaoke-app/.../ProcessExitGuard.kt`:
   `awaitProcessExit(process, command, timeoutSeconds, outputTail)` —
   `waitFor(timeout)`, при превышении `destroyForcibly()` и
   `ProcessExitException`; `processExitFailure(command, exitCode, tail)` —
   исключение с командой, кодом, распознаванием docker/ffmpeg/melt и хвостом
   вывода; `outputTail(text, maxLines)` — последние строки.
2. **Очередь.** В `KaraokeProcessThread.run` после EOF stdout вызывается
   `awaitProcessExit`; ненулевой код → `processExitFailure` → `catch` →
   `ERROR`. Таймаут по умолчанию 60 с (env
   `KARAOKE_PROCESS_EXIT_TIMEOUT_SECONDS`). Force-stop по-прежнему даёт
   `WAITING` (переигровка).
3. **R-43.** `redirectErrorStream(true)` сохраняется (ADR-0006): stderr слит в
   stdout, поэтому хвост в сообщении об ошибке содержит и stderr, и процесс не
   блокируется на переполнении stderr.
4. **Исключение SHEETSAGE.** Известный `NotImplementedError` в последней
   строке штатно переигрывается заданием `SHEETSAGE2`; только этот случай не
   переводит задание в `ERROR`.
5. **Мукс.** `mixAndMux` проверяет `exitValue()`; при `!= 0` удаляет
   `output.mp4` и бросает исключение. Прогресс 100 % выдаётся только после
   `exit 0`. Watchdog по неактивности (по умолчанию 120 с без строк прогресса)
   убивает зависший ffmpeg.
6. **Тестовый шов.** У `mixAndMux` есть параметр `ffmpegBinary` (по умолчанию
   `ffmpeg`): регрессионные тесты подменяют бинарь управляемым скриптом.

## Инварианты

- Ненулевой код возврата не может привести к `DONE` (кроме `SHEETSAGE`-
  фолбэка) и не может выдать 100 % прогресса.
- Сообщение об ошибке содержит команду, код и хвост вывода.
- `output.mp4` после неуспешного ffmpeg не существует — копировать в
  `done_files` нечего.
- Ожидание завершения процесса всегда ограничено по времени.

## Ссылки

- [Отчёт пакета PKG-ERR-EXITCODE](../../specs/364-pkg-err-exitcode/report.md)
- [Карточка P-04 (BR-029)](../../specs/336-code-audit/findings/P-04.md)
- [Карточка P-22 (BR-161)](../../specs/336-code-audit/findings/P-22.md)
- [ProcessExitGuard.kt](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/ProcessExitGuard.kt)
- [KaraokeProcessWorker.kt](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt)
- [PlayerMp4MuxService.kt](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PlayerMp4MuxService.kt)
- [async-process-queue.md](../../knowledge/domains/processing/components/async-process-queue.md)
- [playwright-rendering.md](../../knowledge/domains/processing/components/playwright-rendering.md)
- [ADR-0006](../../knowledge/adr/0006-processbuilder-redirect-errorstream.md)
- [ProcessExitGuardTest.kt](../../karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/ProcessExitGuardTest.kt)
- [PlayerMp4MuxExitCodeTest.kt](../../karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/PlayerMp4MuxExitCodeTest.kt)
- [ProcessExitWiringTest.kt](../../karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/ProcessExitWiringTest.kt)

## Changelog

- **2026-10-24** (BR-029/BR-161, OpenProject #364): код возврата внешнего
  процесса проверяется, задание уходит в `ERROR` с командой и stderr-хвостом,
  недописанный MP4 удаляется, зависший ffmpeg снимается по watchdog.
