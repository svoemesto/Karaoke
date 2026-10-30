# Data Model: спека 676 — устойчивость karaoke-app к исчерпанию памяти

> **Ветка**: `676-oom-karaoke-app` | **Дата**: 2026-10-30
> **Спека**: [spec.md](spec.md) · **План**: [plan.md](plan.md) · **Research**: [research.md](research.md)

Сущности новой предметной области — превентивные меры против OOM. Существующие
сущности (`Publication`, `Publication`-сетка, `MonitorAlert`, `MonitorSeverity`)
описываются здесь только в части, которой касается фича.

---

## 1. `HeapUsageCheck` — проверка загрузки heap

| Поле | Тип | Значение по умолчанию | Правило |
|---|---|---|---|
| `warnPercent` | `Int` | `75` | env `MONITOR_HEAP_WARN_PERCENT`; `?.toIntOrNull() ?: 75`; вне 1..100 → дефолт |
| `criticalPercent` | `Int` | `90` | env `MONITOR_HEAP_CRITICAL_PERCENT`; то же |
| `alerting` | `Boolean` (`@Volatile`) | `false` | состояние в памяти, **не персистится** (FR-019) |

**Свойства (вычисляемые, без состояния)**

| Свойство | Формула | Комментарий |
|---|---|---|
| `maxBytes` | `Runtime.getRuntime().maxMemory()` | потолок heap ≈ `-Xmx` минус survivor-space |
| `usedBytes` | `totalMemory() - freeMemory()` | занятая часть heap |
| `usedPercent` | `usedBytes * 100.0 / maxBytes` | `0.0`, если `maxBytes <= 0` (защита от деления на ноль) |

**Инварианты**

1. `1 <= warnPercent < criticalPercent <= 100` — иначе дефолт (75/90).
2. Порог читается на каждом тике (env может измениться только при рестарте
   контейнера — значит фактически константа; читаем каждый раз, чтобы не
   заводить кэш и не расходиться с конфигурацией).
3. Проверка **не имеет побочных эффектов** (FR-016): только чтение `Runtime`
   и формирование алерта; ничего не пишет в БД, файлы, сеть.
4. Время выполнения — микросекунды; бюджет тика (< 5 с) не расходуется.

**Правила severity**

| Условие | Severity | Ключ алерта |
|---|---|---|
| `usedPercent >= criticalPercent` | `CRITICAL` | `monitor.heap.usage` |
| `warnPercent <= usedPercent < criticalPercent` | `WARNING` | `monitor.heap.usage` |
| `usedPercent < warnPercent` | алерта нет (пустой список) | — |

**Переходы состояния (`alerting`)**

```text
        usedPercent >= warnPercent
  OK ───────────────────────────────► ALERTING (WARNING | CRITICAL)
  ▲                                        │
  └────────────────────────────────────────┘
        usedPercent < warnPercent
        + лог INFO «heap:recovered»
```

`alerting` нужно, чтобы recovery-сообщение писалось **только** на переходе
(«не мигать» в логе на каждом тике) — тот же приём, что в `ProdContainerCheck`
(`firstFailureAt`, `ProdContainerCheck.kt:83-93`).

---

## 2. `HeapSample` — снимок загрузки (только для логирования)

| Поле | Тип | Источник |
|---|---|---|
| `usedBytes` | `Long` | `Runtime.totalMemory() - Runtime.freeMemory()` |
| `maxBytes` | `Long` | `Runtime.maxMemory()` |
| `usedPercent` | `Double` | `usedBytes * 100.0 / maxBytes` |
| `severity` | `MonitorSeverity?` | `null`, если ниже порога |

Не хранится — используется только для формирования строки лога и `detail`
алерта. `detail` намеренно **не входит** в `MonitorAlert.contentHash()`
(`monitor-core.md:85-92`) — иначе сообщение «мигало» бы read/unread на каждом
тике.

---

## 3. `ContainerMemoryPolicy` — пределы контейнера `karaoke-app`

Живёт в **отслеживаемой** конфигурации `deploy/docker-compose-app.yml`.

| Параметр | Значение | Где | Связь |
|---|---|---|---|
| `memLimit` | `8g` | `mem_limit:` | предел памяти cgroup (FR-006) |
| `heapMax` | `6g` | `-Xmx6g` в `command:` | максимум heap JVM (FR-007) |
| запас | ~2 ГБ | — | метапространство, native-память, потоки |
| `heapDumpOnOOM` | `true` | `-XX:+HeapDumpOnOutOfMemoryError` | FR-009 |
| `heapDumpPath` | `/dumps` | `-XX:HeapDumpPath=/dumps` | FR-009, том |
| `dumpsVolume` | `./karaoke-db/dumps:/dumps` | `volumes:` | FR-010 |
| `warnPercent` | `75` | env `MONITOR_HEAP_WARN_PERCENT` | FR-015 |
| `criticalPercent` | `90` | env `MONITOR_HEAP_CRITICAL_PERCENT` | FR-015 |

**Инвариант**: `heapMax < memLimit` (6 ГБ < 8 ГБ; 75 % ≤ 80 % — SC-003).
Нарушение = красный гейт `tools/check-deploy-hardening.sh` (BR-629).

**Порядок флагов**: `-Xmx6g` стоит **после** `${APP_JAVA_OPTS}`, потому что
последний `-Xmx` побеждает (проверено: `java -Xmx8g -Xmx6g` → `MaxHeapSize`
= 6 GiB) — так перекрывается устаревшее `-Xmx8g` из неотслеживаемого
`deploy/.env`.

---

## 4. `HeapDumpRetentionPolicy` — политика хранения дампов

Живёт в `deploy/heap-dump-retention.sh` (host-side) + systemd-юнит/таймер.

| Параметр | Значение | Обоснование |
|---|---|---|
| `KEEP_RECENT` | `2` | решение владельца: «последние 2 дампа» (FR-011) |
| `MAX_TOTAL_BYTES` | `20 GiB` | «лимит тома 20 ГБ» (FR-011) |
| `PATTERN` | `*.hprof` | **только** heap-дампы; SQL-дампы БД в этом же каталоге не трогаются |
| стратегия | вытеснение по возрасту (старые — первыми) | FR-011 |
| возрастная защита | файл моложе `MIN_AGE_MINUTES` не удаляется | не удалить дамп, который пишется прямо сейчас |
| периодичность | systemd-таймер | политика работает и тогда, когда JVM мёртв |

**Алгоритм (одна итерация прогона)**

1. Собрать `*.hprof` в каталоге дампов, отсортировать по mtime (новые → старые).
2. Кандидаты на удаление = все, кроме двух самых новых **и** кроме файлов
   моложе `MIN_AGE_MINUTES`.
3. Пока суммарный размер `*.hprof` > 20 GiB и остались кандидаты — удалить
   самый старый кандидат.
4. Записать в лог: `heap:retention kept=… removed=… freedBytes=… totalBytes=…`.

---

## 5. `PublicationGrid` — раскладка неопубликованных песен

Существующая сущность; здесь зафиксированы инварианты, которые закрывает FR-004.

| Поле | Тип | Правило |
|---|---|---|
| `rows` | `List<List<Publication>>` | число строк = **максимальное** число песен у одного автора (FR-002) |
| `columns` | число авторов с непустой стопкой | `rows[i].size == columns` для всех `i` |
| `totalSongs` | `sumPublish10` | число песен, попавших в раскладку |

**Инварианты**

1. **Конечность** (FR-001): каждая итерация уменьшает суммарное число песен в
   стопках хотя бы на одну → число итераций ≤ числа песен.
2. **Форма** (FR-002): строки = максимум песен у автора, столбцы = авторы.
3. **Пустой вход** → 0 строк, 0 итераций.
4. **Порядок**: песни автора выкладываются в порядке публикации (стопка LIFO),
   столбцы соответствуют авторам, отсортированным по размеру стопки.

**Границы теста (FR-004, SC-002)**: 500 неопубликованных песен, 10 авторов по
50 песен → 50 строк × 10 столбцов = 500 публикаций; вызов
`Publication.getUnPublicationList(...)` обязан завершиться за конечное время.

---

## 6. Связи между сущностями

```text
deploy/docker-compose-app.yml
  ├── ContainerMemoryPolicy ──(heapMax < memLimit)──▶ BR-629 guard
  ├── heapDumpPath=/dumps ──(том)──▶ HeapDumpRetentionPolicy ──▶ deploy/karaoke-db/dumps/*.hprof
  └── env MONITOR_HEAP_*_PERCENT ──▶ HeapUsageCheck.warnPercent / criticalPercent

HeapUsageCheck.run(ctx) ──▶ MonitorAlert(key="monitor.heap.usage") ──▶ SSE + GET /api/monitor/alerts
HeapUsageCheck ──▶ лог «infra.monitor.heap» (event:key=value)
```
