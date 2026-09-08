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
| **infra.prod.ping** | SLF4J-категория для HTTP-пинга сайта | фича 288 |
| **infra.prod.db** | SLF4J-категория для JDBC-пинга прод-БД | фича 288 |
| **infra.cache.statbysong** | SLF4J-категория для `StatBySong.refreshCache()` | фича 289 |

Полный словарь магических кодов алертов — см.
[monitor-checks](components/monitor-checks.md). SLF4J-категории — см.
[log-categories](components/log-categories.md).

## Aggregate Roots

- **MonitorCheck**: интерфейс проверки. Identity = `name`.
  Каждая проверка возвращает `MonitorAlert?` (`null` = OK, не-null = алерт).
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
2. **WARNING → CRITICAL** по нарастающей: если проверка WARNING > 5
   минут без recovery, эскалация в CRITICAL (TODO).
3. **SLF4J-категории обязательны**: каждая проверка логирует через
   категорию `infra.prod.*` или `infra.cache.*` для grep'аемости.
4. **Recovered-сообщения тоже логируются** (INFO), чтобы видеть
   возвращение нормы.
5. **Никаких side-эффектов** в MonitorCheck — только чтение и алерт.

## Публичные контракты (API)

### Internal API

- `MonitoringService.tick()` — тик раз в минуту (Spring `@Scheduled`).
- `MonitorRegistry.register(check)` — регистрация новой проверки.

### Внешний артефакт

- Логи в stdout (через SLF4J) с категориями `infra.prod.*`, `infra.cache.*`.
- Метрики — TODO (Prometheus/StatsD экспорт).

## Структура компонентов (C4 L3)

- [monitor-checks](components/monitor-checks.md) — все 7 проверок:
  таблица с описанием, файл, частоты, типичные алерты.
- [log-categories](components/log-categories.md) — SLF4J-категории
  `infra.prod.*` / `infra.cache.*` с примерами логов.

## Runbook

[`docs/ops/log-correlation.md`](../../../docs/ops/log-correlation.md)
— карта источников логов, grep-маркеры, типичные сценарии диагностики.

## Код (физическая реализация)

- `karaoke-app/src/main/kotlin/.../monitor/MonitoringService.kt`
- `karaoke-app/src/main/kotlin/.../monitor/MonitorCheck.kt`
- `karaoke-app/src/main/kotlin/.../monitor/MonitorRegistry.kt`
- `karaoke-app/src/main/kotlin/.../monitor/MonitorAlert.kt`
- `karaoke-app/src/main/kotlin/.../monitor/checks/*.kt` (7 проверок)
