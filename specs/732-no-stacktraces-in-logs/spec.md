# Feature Specification: В логах — нормальные сообщения, а не стектрейсы

**Feature Branch**: `732-no-stacktraces-in-logs`
**Created**: 2026-11-08
**Status**: Draft

**Input**: User description: «вроде бы мы недавно делали задачу, чтобы в логах не выводилось стектрейсов эксепшенов, а только нормальные сообщения (не в ущерб логированию для разбора ошибок). Сейчас же лог полон записей типа: `java.lang.IllegalStateException: chat.unread.count.unavailable…`, `javax.net.ssl.SSLHandshakeException: Remote host terminated the handshake`, `java.net.http.HttpTimeoutException: request timed out`, `java.io.IOException: EOF reached while reading`» (2026-11-08).

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Plane Workflow)*

> Трекер проекта — Plane (`knowledge/guidelines/runbooks/tracker-setup.md`, ADR `local-0012`).
> Спека заведена по решению владельца без отдельной задачи в Plane.

### Идентификация

- **Issue ID**: `none`
- **Title**: Логи `karaoke-app` без стектрейсов: глобальный «пол» + рабочий guard (KARAOKE-387)
- **Created in Plane**: `none` (спека заведена по решению владельца в чате 2026-11-08)

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | — | Issue ID отсутствует, workflow tracking не применяется | — |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | Согласно Constitution Principle IX | Agent |
| 3. **Work** | код, тесты, обновление базы знаний | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | — | Issue ID отсутствует; отчёт — в `specs/732-no-stacktraces-in-logs/report.md` | Agent |
| 5. **Mark review** | — | Issue ID отсутствует | — |
| 6. **Close** | — | Issue ID отсутствует | — |

### Проверки (validation)

- `tools/check-spec-issue-link.py` (CI) — наличие секции `## OpenProject Tracking` и полей `Issue ID` / `Title` / `Workflow`.
- `tools/spec-knowledge-preflight.sh` — наличие заполненной секции `## Knowledge References`.

## Knowledge References

- `knowledge/system/error-handling.md` — «Правило 3. Причину не глушить» (устаревшая формулировка: передавать исключение в лог считалось правильным — после KARAOKE-387 это не так).
- `knowledge/domains/monitoring/components/log-categories.md` — категории логов и правило «стек — только при `LOG_STACKTRACES=true`» (KARAOKE-387).
- `knowledge/guidelines/runbooks/log-correlation.md` — как читать логи, разбор по кодам событий.
- `knowledge/adr/local-0006-logging-and-error-handling-karaoke-web.md` — базовые договорённости по логированию.
- `knowledge/adr/local-0005-structured-logging-karaoke-app.md` — структурированная строка события.
- `tools/check-log-hygiene.sh` + `config/log-hygiene-baseline.txt` — guard KARAOKE-387 (стоп роста источников стектрейсов).
- `knowledge/domains/monitoring/components/error-semantics.md` — семантика ошибок мониторинга.

**Searched**: `grep -rn 'стектрейс\|stacktrace' knowledge/` → 6 файлов; `grep -rn 'printStackTrace' karaoke-app/src/main/kotlin` → 10 вхождений, все в комментариях; `grep -rn 'LOG_STACKTRACES' knowledge/ karaoke-app/` → правило в `log-categories.md` и `AppLog`.

## Диагностика (факты, 2026-11-08)

**Факт 1 — стектрейсы идут из трёх мест, которые горят постоянно.** Все четыре типа, названные владельцем, нашлись в трёх файлах:

| Тип в логе | Источник |
|---|---|
| `java.lang.IllegalStateException: chat.unread.count.unavailable…` | `monitor/MonitoringService.kt` (`checkFailureAlert`) + `monitor/checks/UnreadChatMessagesCheck.kt` |
| `javax.net.ssl.SSLHandshakeException: Remote host terminated the handshake` | `services/TelegramUpdatesConsumer.kt` (long-poll) |
| `java.net.http.HttpTimeoutException: request timed out` | там же (long-poll) + `ProdContainerCheck.pingSite` |
| `java.io.IOException: EOF reached while reading` | там же (long-poll) |

Мониторинг тикает раз в минуту, long-poll Telegram — постоянно: за час в лог падают десятки стектрейсов, и полезного (код события, хост, операция) в них нет.

**Факт 2 — guard KARAOKE-387 не видел ни одного источника.** `config/log-hygiene-baseline.txt` был пуст, `--report` показывал 0 файлов. Причина — эвристика гварда: регулярка `log\.(warn|error)\([^)]*, e\)` по одной строке:
  * ловит только логгер, названный ровно `log` (в коде таких нет — все `monitorLog`, `premiumLog`, `consumerLog`, …);
  * пропускает многострочные вызовы и вызовы со скобками в аргументах (`consumerLog.warn("… {}", Instant.now(), e)`).

То есть задача KARAOKE-387 («не плодить новые стектрейсы») остановила рост только на бумаге: реальных источников она не видела.

**Факт 3 — реальных источников много.** Разбор вызовов по скобкам (без эвристики) даёт **43 файла** и ~110 вызовов, где последний аргумент — переменная исключения. Часть из них — старые вызовы вне `AppLog`.

**Факт 4 — `AppLog` уже даёт нужный формат.** `AppLog.error/warn` печатает одну строку `event=… code=… key=value cause=Тип: сообщение <- Тип: сообщение`; стек — только при `LOG_STACKTRACES=true`. То есть «правильный» путь есть, но он не распространён и не защищён guard'ом.

## Пользовательские истории

### US1 — как администратор (P1)

**Как** администратор, **я хочу** читать лог `karaoke-app` без простыней `at com.svoemesto…`, **чтобы** по логу было видно, что случилось, а не как это случилось.

### US2 — как администратор (P1)

**Как** администратор, **я хочу** видеть в логе причину сбоя (тип и сообщение, цепочку причин), **чтобы** разбор ошибок не пострадал от отказа от стектрейсов.

### US3 — как разработчик (P1)

**Как** разработчик, **я хочу**, чтобы guard ловил реальные места с передачей исключения в лог, **чтобы** новые стектрейсы не появлялись.

## Функциональные требования

**FR-001 (US1, US2)**: исключение в логе печатается **одной строкой** — цепочкой причин (`Тип: сообщение <- Тип: сообщение`), без кадров стека. Реализуется глобально: конвертер `causeChain` (`karaoke-app/.../logging/CauseChainConverter.kt`), подключённый в `logback-spring.xml` через `<conversionRule>` и используемый в `logging.pattern.console`.

**FR-002 (US2)**: полный стек печатается **только** при `LOG_STACKTRACES=true` — тот же флаг, что у `AppLog` (KARAOKE-387).

**FR-003 (US1)**: формат строки лога не меняется (дата, уровень, PID, поток, логгер, сообщение) — меняется только вывод исключения.

**FR-004 (US3)**: `tools/check-log-hygiene.sh` считает источники **по-настоящему**: разбор вызова по скобкам, любое имя логгера (`*Log`, `*Logger`, `log`, `logger`), многострочные вызовы и вызовы со скобками в аргументах; комментарии и строковые литералы не считаются.

**FR-005 (US3)**: baseline `config/log-hygiene-baseline.txt` пересобран по факту; guard падает при появлении нового файла с источниками или росте счётчика.

**FR-006 (US1)**: горячие пути (мониторинг, long-poll Telegram) переведены на `AppLog` — одна строка с кодом события и цепочкой причин.

**FR-007 (US1)**: контракт конвертера закреплён тестом (`CauseChainConverterTest`): одна строка, нет кадров, цепочка причин, полный стек по флагу.

## Нефункциональные требования

- **NFR-001**: guard и сканер не ломают сборку: сканер — `tools/log_hygiene_scan.py` (Python 3, без внешних зависимостей).
- **NFR-002**: изменение логирования не меняет формат остальных строк лога (совместимость с разбором по grep и с дашбордами).
- **NFR-003**: `knowledge/` обновляется синхронно с кодом (Knowledge SSoT).

## Критерии успеха

- **SC-001**: в логе `karaoke-app` (прод, обычный режим) нет ни строки `at com.svoemesto…`, ни `Caused by:` от наших логгеров.
- **SC-002**: причина сбоя в логе присутствует (тип и сообщение исключения в одной строке).
- **SC-003**: `bash tools/check-log-hygiene.sh` видит реальные источники (до правки — 0).
- **SC-004**: `CauseChainConverterTest` — зелёный; `LOG_STACKTRACES=true` печатает полный стек.
- **SC-005**: `tools/lint-knowledge.py` и `tools/check-knowledge-structure.sh` — без новых нарушений.

## Допущения

- `LOG_STACKTRACES` — единственный переключатель режима; в проде по умолчанию выключен.
- Spring Boot 3.2+ и Logback 1.3 (в проекте — 1.3.14) поддерживают `<conversionRule>` в `logback-spring.xml`.

## За границами спеки

- Полный перевод всех ~110 мест на `AppLog` (кодифицированные события) — отдельный проход; здесь сделан «пол» и переведены горячие пути.
- Изменение уровней логирования и фильтрация шумных событий.
- Логи `karaoke-web` (другой модуль) — там действует ADR `local-0006`; общий объём правок не входит в эту спеку.
