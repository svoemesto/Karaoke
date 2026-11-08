# Tasks: В логах — нормальные сообщения, а не стектрейсы

**Feature Branch**: `732-no-stacktraces-in-logs`
**Spec**: `specs/732-no-stacktraces-in-logs/spec.md`
**Issue ID**: `none`

## Phase 1 — Диагностика

- [x] T001 Разобрать лог прода: найти источники четырёх типов исключений из жалобы владельца
- [x] T002 Проверить, что guard `tools/check-log-hygiene.sh` показывает 0 источников (baseline пуст) — и почему
- [x] T003 Посчитать реальные источники (разбор вызовов по скобкам, любой логгер, многострочные вызовы)

## Phase 2 — Глобальный «пол» (FR-001…FR-003)

- [x] T101 `karaoke-app/.../logging/CauseChainConverter.kt` — конвертер: исключение одной строкой (цепочка причин, без кадров); полный стек — при `LOG_STACKTRACES=true`
- [x] T102 `karaoke-app/src/main/resources/logback-spring.xml` — `<conversionRule conversionWord="causeChain">` + стандартные включения Spring Boot
- [x] T103 `application.yml` — `logging.pattern.console` с `%causeChain`, формат строки прежний
- [x] T104 `CauseChainConverterTest` — контракт: одна строка, нет кадров, цепочка причин, полный стек по флагу

## Phase 3 — Горячие пути на AppLog (FR-006)

- [x] T301 `monitor/MonitoringService.kt` — `check.failed` (`MON-CHECK-001`), `broadcast.failed` (`MON-BROADCAST-001`)
- [x] T302 `monitor/checks/UnreadChatMessagesCheck.kt` — `chat.unread.count.query.failed` (`CHAT-UNREAD-002`)
- [x] T303 `services/TelegramUpdatesConsumer.kt` — `poll.failed` (`TG-POLL-001`), `webhook.delete.failed` (`TG-POLL-002`)
- [x] T304 Прочие места с передачей исключения в логгер (schedulers, cleanup, контроллеры, утилиты) — переведены на `AppLog`

## Phase 4 — Guard (FR-004, FR-005)

- [x] T401 `tools/log_hygiene_scan.py` — сканер: разбор вызова по скобкам, любое имя логгера, снятие комментариев и строковых литералов
- [x] T402 `tools/check-log-hygiene.sh` — переход на сканер (один проход по каталогу)
- [x] T403 `config/log-hygiene-baseline.txt` — пересобрать по факту

## Phase 5 — База знаний

- [x] T501 `knowledge/system/error-handling.md` — Правило 3 и Правило 4 приведены в соответствие (причина в логе — тип и сообщение, не кадры)
- [x] T502 `knowledge/domains/monitoring/components/log-categories.md` — раздел про исключения без кадров
- [x] T503 `knowledge/guidelines/runbooks/log-correlation.md` — §1a (глобальный «пол»), §1b (поправка про слепую зону guard'а), новые коды в реестре

## Phase 6 — Выкат

- [x] T601 Коммит + PR (CI зелёный) + merge через `tools/merge-pr.sh`
- [x] T602 Пересборка образа `karaoke-app` и пересоздание контейнера (Pass 380: очередь → стоп → дренаж → старт → старт очереди)
- [x] T603 Проверка на проде: в логе нет `at com.svoemesto…` и `Caused by:`, причина в строке есть
- [x] T604 Отчёт владельцу
