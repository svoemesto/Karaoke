# Implementation Plan: устранение спама PROCESS_COUNT_WAITING в SSE-канале

**Branch**: `177-fix-process-count-waiting-spam`
**Date**: 2026-08-12
**Spec**: [spec.md](./spec.md)

**Input**: Feature specification from
[`/specs/177-fix-process-count-waiting-spam/spec.md`](./spec.md).

## Summary

Устранить шум в SSE-канале `/api/subscribe` админки: одинаковые
сообщения `PROCESS_COUNT_WAITING` с `countWaiting == 0` (и другими
повторяющимися значениями) приходят тысячи раз в минуту при простое.
Источник — функция
`KaraokeProcessWorker.sendCountWaitingMessage(count: Long)` и её
5 call-sites (см. [research.md](./research.md)). Фикс — подавление
дублей на стороне продьюсера: in-memory volatile-переменная с
последним отправленным значением, сравнение перед `SNS.send` во всех
5 call-sites, сброс состояния при старте/рестарте воркера. Изменений
в DTO, БД, UI не требуется.

## Technical Context

- **Language/Version**: Kotlin 1.x, JDK 17 (как в `karaoke-app`/`karaoke-web`)
- **Primary Dependencies**: Spring Boot 3.x, кастомный `SNS`-сервис
  (Notification fan-out), `SseNotification.processCountWaiting(...)`
- **Storage**: N/A — фикс чисто in-memory; БД не трогаем
- **Testing**: интеграционных тестов нет; проверка делается вручную
  на admin-машине через DevTools → Network → EventStream
- **Target Platform**: Linux server, admin-машина (karaoke-app
  разворачивается только на ней)
- **Project Type**: web-service (Spring Boot + SSE)
- **Performance Goals**: ≤1 сообщение `PROCESS_COUNT_WAITING` за 5 минут
  простоя при пустой очереди (сейчас — десятки в секунду)
- **Constraints**: отзывчивость UI — бейдж обновляется в течение 1–2
  секунд после реального изменения счётчика; обратной несовместимости
  с фронтом нет (формат payload не меняется)
- **Scale/Scope**: 1 воркер очереди, ~10 SSE-подписчиков одновременно,
  1 volatile-переменная на бэкенде

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн | ✅ PASS | Никаких новых внешних зависимостей; фикс локальный, в `karaoke-app` |
| II. Сырой JDBC + дифф по хэшам | ✅ PASS | БД не трогаем |
| III. Двух-БД синхронизация | ✅ PASS | Не относится — фикс в SSE-рассылке, не в `KaraokeDbTable` |
| IV. Async-очередь задач с парсингом stdout | ✅ PASS | Тронем только *рассылку* SSE, не контракт `ProcessBuilder`/`redirectErrorStream(true)` |
| V. Двух-фронтенд | ✅ PASS | Меняем только серверную часть; webvue3 и karaoke-public не меняются |
| VI. Code Standards | ⚠️ WATCH | FR-006 (конституция): KDoc на публичных API с `@see docs/features/<slug>.md`. У `sendCountWaitingMessage` уже есть KDoc; для нового поля/метода дедупликации нужно добавить `@see docs/features/async-process-queue.md` (per-feature документ уже существует — см. `docs/features/README.md`) |
| VII. Cross-Machine Setup | ✅ PASS | Не относится |
| VIII. Секреты и git-гигиена | ✅ PASS | Секретов нет, .env/.gitignore не трогаем |

**Итог**: 7/8 PASS, 1 WATCH (FR-006 — добавить `@see`-ссылку на
`docs/features/async-process-queue.md` для нового кода дедупликации).

## Project Structure

### Documentation (this feature)

```text
specs/177-fix-process-count-waiting-spam/
├── plan.md              # Этот файл (/speckit.plan output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── sse-payload.md
├── checklists/
│   └── requirements.md  # Уже создан на /speckit.specify
├── spec.md              # Уже создан на /speckit.specify
└── tasks.md             # Phase 2 output (/speckit.tasks — НЕ создаётся на /speckit.plan)
```

### Source Code (repository root)

Фичи касается только `karaoke-app` — модуль Spring Boot, который
разворачивается на admin-машине и отвечает за рассылку SSE.

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── KaraokeProcessWorker.kt          # МЕНЯЕТСЯ: добавление поля дедупликации,
│                                    # переписывание sendCountWaitingMessage
│                                    # с проверкой перед SNS.send,
│                                    # сброс состояния в start()/stop().
├── KaraokeProcess.kt                # МЕНЯЕТСЯ: 3 call-sites (createDbInstance,
│                                    # run, forceStop) — передавать через
│                                    # один helper (см. research.md §3)
└── services/
    └── SNS.kt                       # НЕ МЕНЯЕТСЯ (Notification fan-out —
                                     # корректно работает с уже отфильтрованными
                                     # сообщениями)
```

**Structure Decision**: Option 2 (Web application). Меняется только
backend (`karaoke-app`). Frontend (`webvue3`, `karaoke-public`) и
БД не трогаем.

## Complexity Tracking

> **Не заполнено** — Constitution Check не содержит нарушений, требующих
> обоснования.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| (нет) | — | — |
