---
id: domain-monitoring
title: "Domain: Monitoring (Мониторинг)"
status: Active
slug: monitoring
related:
  - ../processing/domain.md
  - ../rendering/domain.md
  - ../editorial/domain.md
  - ../caching/domain.md
---

# Domain: Monitoring (Мониторинг)

> Bounded context для мониторинга инфраструктуры и продуктовых метрик.
>

## Обзор контекста (Bounded Context)

Monitoring — контекст для **регулярных проверок здоровья** компонентов
Karaoke (PostgreSQL, `karaoke-web`, `karaoke-app`, nginx) и **алертов**
при деградации. Также — сбор логов для post-hoc анализа инцидентов.

**Граница**: контекст НЕ отвечает за:

- бизнес-логику конкретных доменов (только проверяет их состояние);
- сбор событий пользователей (→ stats domain, TODO);
- алертинг в Telegram/Email (заглушка, TODO).

## Ubiquitous Language | Единый язык

| Термин | Определение | Пример в коде |
| --- | --- | --- |
| **MonitorCheck** | Интерфейс одной проверки | `MonitorCheck.kt` |
| **MonitorRegistry** | Реестр всех проверок | `MonitorRegistry.kt` |
| **MonitorAlert** | Структура алерта (severity + message) | `MonitorAlert.kt` |
| **MonitorSeverity** | WARNING, CRITICAL | `MonitorSeverity.kt` |
| **Stalled** | Задача/лейн не двигается > N минут | `RenderQueueStalledCheck` |
| **HeapUsageCheck** | Проверка загрузки heap JVM (пороги 75 % / 90 %) | `HeapUsageCheck` |
| **HeapSample** | Снимок загрузки heap: занято / потолок | `HeapSample` |
| **infra.prod.ping** | SLF4J-категория для HTTP-пинга сайта | фича 288 |
| **infra.prod.db** | SLF4J-категория для JDBC-пинга прод-БД | фича 288 |
| **infra.cache.statbysong** | SLF4J-категория для `StatBySong.refreshCache()` | фича 289 |

Полный словарь магических кодов алертов — см.
[monitor-checks](components/monitor-checks.md). SLF4J-категории — см.
[log-categories](components/log-categories.md).

## Aggregate Roots

- **MonitorCheck**: `fun interface` проверки. Единственный метод —
  `run(ctx: MonitorContext): List<MonitorAlert>` (пустой список = OK).
  Identity проверки задаёт реализация, а не интерфейс.
  Прежняя версия описывала `val name` + `check(): MonitorAlert?` — такого
  интерфейса в коде нет (поправка Pass 472, `monitor/MonitorCheck.kt:24-26`).
- **MonitorAlert**: данные алерта. Identity = `(checkName, timestamp)`.

## Entities

- **MonitorRegistry**: singleton со списком всех активных проверок.

## Value Objects

- **MonitorSeverity** (WARNING | CRITICAL).
- **MonitorAlert.message** — текст алерта.

## Domain Events

- **CheckPassed**: проверка прошла (INFO).
- **CheckFailed**: проверка вернула алерт (WARNING/CRITICAL).
- **CheckRecovered**: предыдущая failed проверка прошла (INFO после WARNING).

## Domain Invariants | Инварианты и правила бизнеса

1. **Каждая проверка тикает раз в минуту** через `MonitoringService`.
   - **Правки BR-134, BR-135, BR-136 (аудит #336, `PKG-S3-23`)**:
     - ошибка проверки, читающей прод-БД, НЕ подменяется нулём «проблем нет»:
       исключение пробрасывается и превращается в WARNING-алерт
       `check.<Name>.failure` (BR-134);
     - тик мониторинга **не пересекается с самим собой**: `fixedDelay` +
       single-flight guard, пересекающийся тик пропускается с записью в лог
       (BR-135);
     - реестр фоновых admin-задач ограничен по TTL (1 час), пул потоков
       закрывается при остановке приложения (BR-136);
     - общий инвариант «сбой ≠ штатный результат» —
       [error-semantics](components/error-semantics.md).

2. **WARNING → CRITICAL** по нарастающей: если проверка WARNING > 5
   минут без recovery, эскалация в CRITICAL (TODO).
3. **SLF4J-категории обязательны**: каждая проверка логирует через
   категорию `infra.prod.*` или `infra.cache.*` для grep'аемости.
4. **Recovered-сообщения тоже логируются** (INFO), чтобы видеть
   возвращение нормы.
5. **Никаких side-эффектов** в MonitorCheck — только чтение и алерт.
6. **Ошибка логируется СО СТЕКОМ** (аудит #336, пакет `PKG-S3-25`):
   - `log.warn(..., e)` — throwable последним аргументом (SLF4J сам добавит
     стек и cause-цепочку); `println` — только текстом, без стека (BR-275);
   - `printStackTrace()` в затронутом коде заменён на SLF4J-категорию
     `infra.*` (BR-249, BR-255, BR-256);
   - «сбой БД» и «нет данных» — разные состояния и разные коды ответа
     (BR-236, BR-240, BR-264) — см.
     [boundary-validation](components/boundary-validation.md).
7. **Пакетная загрузка вместо N+1** (BR-230, BR-273): структурные данные
   читаются ОДНИМ запросом и раздаются из кеша/индекса; точечный
   `SELECT` на каждый элемент цикла — нарушение ADR-0001.
8. **Загрузка heap видна ДО падения** (BR-630, спека 676): 8-я проверка
   `HeapUsageCheck` публикует загрузку heap **на каждом тике** (не только
   при превышении порога) — пороги 75 % (WARNING) и 90 % (CRITICAL) из
   конфигурации. Состояние проверки — только в памяти, новых персистентных
   ключей нет. Severity алерта и уровень журнала — разные шкалы:
   CRITICAL логируется как ERROR (ADR `local-0014`).

## Публичные контракты (API)

### Internal API

- `MonitoringService.tick()` — тик раз в минуту (Spring `@Scheduled`).
- `MonitorRegistry.register(check)` — регистрация новой проверки.

### Внешний артефакт

- Логи в stdout (через SLF4J) с категориями `infra.prod.*`, `infra.cache.*`.
- Метрики — TODO (Prometheus/StatsD экспорт).

## Структура компонентов (C4 L3)

- [error-semantics](components/error-semantics.md) — сквозной инвариант «сбой ≠ штатный результат»: как отличать «нет данных» от «хранилище/БД/сеть не ответили» (аудит #336, пакет `PKG-S3-23`, 39 записей).
- [monitor-checks](components/monitor-checks.md) — 8-я проверка `HeapUsageCheck` (загрузка heap JVM, BR-630): источник данных `Runtime`, пороги из конфигурации, маппинг severity → уровень журнала.
- [boundary-validation](components/boundary-validation.md) — сквозной инвариант «вход проверяется ДО использования»: HTTP-параметры, страницы из интернета, значения из админских свойств и ссылки внутри `.mlt` (аудит #336, пакет `PKG-S3-25`).
- [log-categories](components/log-categories.md) — В Karaoke есть **соглашение о SLF4J-категориях**: компоненты логируют через `infra.*` категории для удобного grep'а в…
- [monitor-checks-detailed](components/monitor-checks-detailed.md) — детальный каталог 7 MonitorCheck'ов. Дополняет [monitor-checks.md](components/monitor-checks.md).
- [monitor-checks](components/monitor-checks.md) — Эта компонента — единая точка регистрации всех проверок мониторинга. Каждая проверка реализует `MonitorCheck` интерфейс…
- [monitor-core](components/monitor-core.md) — базовые классы `MonitorContext`, `MonitorCheck`, `MonitorAlert`, `MonitorRegistry`.

## Runbook

[`knowledge/guidelines/runbooks/log-correlation.md`](../../guidelines/runbooks/log-correlation.md)
— карта источников логов, grep-маркеры, типичные сценарии диагностики.

## Код (физическая реализация)

- `karaoke-app/src/main/kotlin/.../monitor/MonitoringService.kt`
- `karaoke-app/src/main/kotlin/.../monitor/MonitorCheck.kt`
- `karaoke-app/src/main/kotlin/.../monitor/MonitorRegistry.kt`
- `karaoke-app/src/main/kotlin/.../monitor/MonitorAlert.kt`
- `karaoke-app/src/main/kotlin/.../monitor/checks/*.kt` (8 проверок)

## Changelog

- **Спека 676** (2026-10-30, KARAOKE-245, BR-630): 8-я проверка `HeapUsageCheck` (загрузка heap JVM) — Linking Protocol L3→L2 соблюдён, проверка перечислена в структуре компонентов; добавлен инвариант о публикации загрузки до падения. Автор: agent (Karaoke).
