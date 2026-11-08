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
| `:karaoke-app:test --tests CauseChainConverterTest` | 8/8 успешно (в т.ч. регресс-тест на «битую» конфигурацию) |
| `bash tools/check-log-hygiene.sh --report` | видит реальные источники (до правки — 0); после перевода горячих путей — 34 файла |
| `bash tools/check-log-hygiene.sh` | без регресса (baseline пересобран по факту: 34 файла) |
| `:karaoke-app:test` (весь модуль) | 1000 тестов, зелёные (обновлён контрактный тест BR-151 под новый формат `AppLog`) |
| `:karaoke-app:ktlintCheck` | успешно |
| `python3 tools/lint-knowledge.py` | OK |
| `bash tools/check-knowledge-structure.sh` | OK (9/9) |

## Прод: выкатка и регресс, найденный на ней

### Выкатка (PR #79, образ `77461d181314`)

- Образ `karaoke-app` пересобран, контейнер пересоздан (Pass 380: очередь
  остановлена → дренаж → старт → старт очереди). Очередь после старта — работает
  (`ProcessWorker: Стартуем`).
- Проверка после выката: в логе контейнера нет ни строки `at com.svoemesto…`, ни
  `Caused by:`.

### Регресс: из консоли пропали ВСЕ сообщения logback

На той же выкатке обнаружено: в `docker logs karaoke-app` остались только строки
`println`/`printStackTrace` вида `[2026-11-08 18:22:57.40714527] Текст…`, а
сообщений logback (формата `дата уровень PID --- [поток] логгер : …`) не было
вообще. В начале вывода — предупреждения logback:

```
Appender named [CONSOLE] not referenced. Skipping further processing.
No appenders present in context [default] for logger [com.svoemesto.karaokeapp.KaraokeAppApplicationKt].
```

Причина: в `logback-spring.xml` был подключён
`org/springframework/boot/logging/logback/console-appender.xml` — он даёт
appender, но **не** даёт `<root>` с `appender-ref`. Spring Boot подставляет
конфигурацию целиком (`base.xml`: appenders CONSOLE и FILE + `root` level INFO),
а частичная подмена оставила root-логгер без appender'а — и все сообщения
logback исчезли из вывода.

Итог: «стектрейсов в логе нет» оказалось правдой по не той причине — логов
не было вообще. Это и есть цена решения «свой `logback-spring.xml` вместо
неизменённого дефолта Spring Boot».

### Исправленная выкатка (PR #80)

- Образ `sm-karaoke.ru:5443/karaoke/karaoke-app:1` = `5fbebb6c5a56` (собран из
  ветки `732-no-stacktraces-in-logs`, коммит `1e78bd59`; merge в master — `26eaa888`).
- Порядок Pass 380: очередь остановлена (`/process/stop`) → дренаж (активных
  заданий нет) → `do.sh start_app` → старт очереди (`/process/start`).
- Проверки после выката:
  * в логе контейнера **0** строк `at com.svoemesto…` и `Caused by:`;
  * сообщения logback **вернулись**: строки формата
    `дата уровень PID --- [поток] логгер : сообщение` (раньше в выводе были только
    `println`/`printStackTrace`);
  * предупреждений logback («No appenders present», «not referenced») нет;
  * причина — в той же строке, одной строкой. Примеры из боевого лога:
    `event=db.connect.failed code=DB-CONNECT-001 target=SERVER … cause=PSQLException: Закончилось время ожидания`
    и `event=check.failed code=MON-CHECK-001 check=UnreadChatMessagesCheck reason=IllegalStateException: chat.unread.count.unavailable: … cause=IllegalStateException: chat.unread.count.unavailable: …`;
  * файловый appender (`/tmp/spring.log` в контейнере) — тот же однострочный формат;
  * очередь работает (`isworking=true`), после прогрева — ни одного WARN/ERROR.

### Исправление

- `karaoke-app/src/main/resources/logback-spring.xml` — подключается
  `base.xml` (appenders CONSOLE и FILE, `root` level INFO) — ровно то, что
  Spring Boot подставляет сам; `<conversionRule causeChain>` остаётся.
- `application.yml` — добавлен `logging.pattern.file` с тем же однострочным
  шаблоном: файловый appender иначе писал бы полные стектрейсы (`%wEx` из
  шаблона по умолчанию).
- Регресс-тест `CauseChainConverterTest.реальный конфиг вешает CONSOLE на root и
  печатает исключение без кадров` — разбирает **реальный** `logback-spring.xml`
  через Joran, проверяет, что у root есть appender `CONSOLE`, что в шаблоне есть
  `%causeChain` и `%nopex`, что logback не оставил предупреждений о потере
  appender'а, и что по реальному шаблону исключение печатается без кадров.
  Проверено: на «битой» конфигурации (`console-appender.xml` без `root`) тест
  падает, на исправленной — зелёный.
- База знаний: `knowledge/system/error-handling.md` (Правило 4 — требование
  вешать appender на root и симптом), `knowledge/guidelines/runbooks/log-correlation.md`
  (§1c «В логе нет ни одной записи logback» — симптом и команда поиска).


## Что осталось на владельца

- Полный перевод остальных мест на `AppLog` (свои коды событий) — отдельный проход;
  на стектрейсы в логе это уже не влияет («пол» работает глобально), но диагностика
  по кодам событий станет точнее.
- `LOG_STACKTRACES=true` включается на время разбора, если нужен точный кадр.
