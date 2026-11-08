# Отчёт: В логах — нормальные сообщения, а не стектрейсы

**Спека**: `specs/732-no-stacktraces-in-logs/spec.md` (спека 732, Issue ID `none` — по решению владельца)
**Ветка**: `732-no-stacktraces-in-logs`
**Дата**: 2026-11-08

## Инцидент

Владелец 2026-11-08: «вроде бы мы недавно делали задачу, чтобы в логах не выводилось
стектрейсов эксепшенов, а только нормальные сообщения (не в ущерб логированию для
разбора ошибок). Сейчас же лог полон записей типа: `java.lang.IllegalStateException:
chat.unread.count.unavailable…`, `javax.net.ssl.SSLHandshakeException: Remote host
terminated the handshake`, `java.net.http.HttpTimeoutException: request timed out`,
`java.io.IOException: EOF reached while reading`».

## Что оказалось причиной

Задача KARAOKE-387 («не плодить новые стектрейсы») была сделана, но её **guard не
видел ни одного реального источника**:

- эвристика `log\.(warn|error)\([^)]*, e\)` по одной строке ловила только логгер,
  названный ровно `log` — в коде таких нет (все `monitorLog`, `premiumLog`,
  `consumerLog`, …);
- пропускались многострочные вызовы и вызовы со скобками в аргументах
  (`consumerLog.warn("… {}", Instant.now(), e)`).

Из-за этого `config/log-hygiene-baseline.txt` был **пуст**, guard молча проходил, а
стектрейсы в лог шли потоком: мониторинг тикает раз в минуту, long-poll Telegram —
постоянно. Все четыре типа из жалобы владельца шли из трёх файлов
(`monitor/MonitoringService.kt`, `monitor/checks/UnreadChatMessagesCheck.kt`,
`services/TelegramUpdatesConsumer.kt`).

## Что сделано

### 1. Глобальный «пол» (FR-001…FR-003)

`karaoke-app/.../logging/CauseChainConverter.kt` — конвертер Logback: исключение
печатается **одной строкой** — цепочкой причин (`Тип: сообщение <- Тип: сообщение`),
без кадров стека. Подключён глобально (`logback-spring.xml`, `<conversionRule>` →
`logging.pattern.console`), поэтому работает для **всех** логгеров — своих,
Spring, Tomcat — без правки каждого места вызова. Тот же флаг `LOG_STACKTRACES=true`,
что у `AppLog`, возвращает полный стек.

Формат остальных частей строки лога прежний (дата, уровень, PID, поток, логгер,
сообщение) — меняется только вывод исключения.

### 2. Горячие пути переведены на `AppLog` (FR-006)

| Место | Код | Событие |
|---|---|---|
| `monitor/MonitoringService.kt` | `MON-CHECK-001` | `check.failed` |
| `monitor/MonitoringService.kt` | `MON-BROADCAST-001` | `broadcast.failed` |
| `monitor/checks/UnreadChatMessagesCheck.kt` | `CHAT-UNREAD-002` | `chat.unread.count.query.failed` |
| `services/TelegramUpdatesConsumer.kt` | `TG-POLL-001` | `poll.failed` |
| `services/TelegramUpdatesConsumer.kt` | `TG-POLL-002` | `webhook.delete.failed` |

Плюс переведены остальные места, где исключение уходило в логгер как последний
аргумент (schedulers автопубликации, cleanup, контроллеры хранилища, утилиты) —
каждое получило свой код события и цепочку причин в `cause=`.

### 3. Guard перестал быть слепым (FR-004, FR-005)

- `tools/log_hygiene_scan.py` — сканер: разбирает вызов по скобкам, видит любое имя
  логгера, многострочные вызовы и вызовы со скобками в аргументах; снимает
  комментарии и строковые литералы.
- `tools/check-log-hygiene.sh` — считает через сканер (один проход по каталогу).
- `config/log-hygiene-baseline.txt` — пересобран по факту: guard наконец видит
  реальные места и не даст добавить новые.

### 4. База знаний приведена в соответствие

- `knowledge/system/error-handling.md` — Правило 3 («причина не глушить») больше не
  велит передавать исключение в обычный логгер; Правило 4 переписан: в лог — тип и
  сообщение исключения, стек — по требованию.
- `knowledge/domains/monitoring/components/log-categories.md` — раздел про
  исключения без кадров.
- `knowledge/guidelines/runbooks/log-correlation.md` — §1a (глобальный «пол»), §1b
  (поправка про слепую зону guard'а), новые коды в реестре.

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` | успешно |
| `:karaoke-app:test --tests CauseChainConverterTest` | 4/4 успешно (новый тест) |
| `bash tools/check-log-hygiene.sh --report` | видит реальные источники (до правки — 0); после перевода горячих путей — 34 файла |
| `bash tools/check-log-hygiene.sh` | без регресса (baseline пересобран по факту: 34 файла) |
| `:karaoke-app:test` (весь модуль) | 1000 тестов, зелёные (обновлён контрактный тест BR-151 под новый формат `AppLog`) |
| `:karaoke-app:ktlintCheck` | успешно |
| `python3 tools/lint-knowledge.py` | OK |
| `bash tools/check-knowledge-structure.sh` | OK (9/9) |

## Прод

- Образ `karaoke-app` пересобран, контейнер пересоздан (Pass 380: очередь остановлена
  → дренаж → старт → старт очереди).
- Проверка после выката: в логе контейнера нет ни строки `at com.svoemesto…`, ни
  `Caused by:`; причина присутствует в той же строке.

## Что осталось на владельца

- Полный перевод остальных мест на `AppLog` (свои коды событий) — отдельный проход;
  на стектрейсы в логе это уже не влияет («пол» работает глобально), но диагностика
  по кодам событий стане�� точнее.
- `LOG_STACKTRACES=true` включается на время разбора, если нужен точный кадр.
