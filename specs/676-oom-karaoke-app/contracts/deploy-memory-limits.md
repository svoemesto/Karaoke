# Contract: пределы памяти контейнера `karaoke-app` и дампы heap

> **Ветка**: `676-oom-karaoke-app` | **Дата**: 2026-10-30
> **Спека**: FR-006…FR-013, SC-003, SC-004 | **План**: [plan.md](../plan.md)

Контракт конфигурации развёртывания admin-машины (`nsa-i9`).
**На прод-хосте `karaoke-app` не развёрнут** — контракт касается только
`deploy/docker-compose-app.yml`.

---

## 1. Файл и сервис

| Что | Значение |
|---|---|
| Файл | `deploy/docker-compose-app.yml` |
| Сервис | `karaoke-app` (первый сервис файла) |
| Образец для прод-хоста | `deploy/prod-single-host/docker-compose-web.yml:77`, `:86-87`, `:108-109` (BR-547) |

---

## 2. Обязательные поля сервиса

```yaml
  karaoke-app:
    # … существующие поля без изменений …

    environment:
      # … существующие переменные …
      # Пороги алерта загрузки heap (FR-015) — конфигурация, а не код.
      - MONITOR_HEAP_WARN_PERCENT=75
      - MONITOR_HEAP_CRITICAL_PERCENT=90

    volumes:
      # … существующие монтирования …
      # FR-010: том дампов heap — тот же каталог, что у karaoke-db (BR-046).
      - ./karaoke-db/dumps:/dumps

    # FR-006/FR-007: предел памяти контейнера 8 ГБ.
    #   heap JVM — 6 ГБ (-Xmx6g, команда ниже): 6 ГБ < 8 ГБ,
    #   запас ~2 ГБ на метапространство, native-память и потоки.
    mem_limit: 8g

    # FR-009: дамп состояния heap при OOM — на том, переживает рестарт контейнера.
    #   Порядок флагов ВАЖЕН: -Xmx6g стоит ПОСЛЕ ${APP_JAVA_OPTS}, потому что
    #   последний -Xmx побеждает (проверено: java -Xmx8g -Xmx6g → MaxHeapSize=6 GiB).
    #   Так перекрывается устаревшее -Xmx8g из неотслеживаемого deploy/.env.
    command: ${APP_JAVA_OPTS} -Xmx6g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps -jar /app.jar
```

### Инварианты (проверяются guard'ом BR-629)

| # | Инвариант | Как проверяется |
|---|---|---|
| 1 | `mem_limit` задан | `file_has 'mem_limit:'` |
| 2 | `mem_limit: 8g` | точное значение |
| 3 | `-Xmx6g` присутствует | `file_has '\-Xmx6g'` |
| 4 | heap < предел | `-Xmx6g` < `mem_limit: 8g` |
| 5 | `-XX:+HeapDumpOnOutOfMemoryError` присутствует | `file_has` |
| 6 | `-XX:HeapDumpPath=/dumps` присутствует | `file_has` |
| 7 | том `./karaoke-db/dumps:/dumps` смонтирован | `file_has '\./karaoke-db/dumps:/dumps'` |
| 8 | `-Xmx6g` идёт **после** `${APP_JAVA_OPTS}` | порядок в строке `command` |

---

## 3. Политика хранения дампов

| Что | Где |
|---|---|
| Скрипт | `deploy/heap-dump-retention.sh` |
| systemd-юнит | `deploy/heap-dump-retention.service` (oneshot) |
| systemd-таймер | `deploy/heap-dump-retention.timer` |

Параметры (решение владельца 2026-10-30, FR-011):

| Параметр | Значение |
|---|---|
| сколько хранить | последние **2** дампа |
| лимит тома | **20 ГБ** суммарно |
| вытеснение | по возрасту (старые — первыми) |
| что считается дампом | только `*.hprof` (SQL-дампы БД в этом же каталоге не трогаются) |
| защита от гонки | файл, изменённый менее `MIN_AGE_MINUTES` назад, не удаляется |

Скрипт обязан:
- работать с каталогом дампов (`./karaoke-db/dumps` рядом с ним; переопределяется
  первым аргументом или `HEAP_DUMP_DIR`);
- завершаться кодом `0` при успехе и ненулевым при ошибке;
- печатать в stdout сводку (`kept=… removed=… freedBytes=… totalBytes=…`);
- иметь `--self-test` (проверка на временном каталоге) — по конвенции
  `tools/check-deploy-hardening.sh --self-test` и `tools/gradle-heap-dump.sh`.

**Операционное действие вне репозитория** (за владельцем): установить и
включить таймер на admin-машине; до этого политику можно применять вручную
`bash deploy/heap-dump-retention.sh`.

---

## 4. Чего в этом контракте НЕТ

| Не входит | Почему |
|---|---|
| `cpus` | FR-006/FR-007 требуют только предел памяти; CPU-лимит для контейнера с ffmpeg/melt/Playwright — эксплуатационный риск вне контракта |
| `restart: unless-stopped` для `karaoke-app` | политика перезапуска контейнера — не предмет фичи; при OOM контейнер остаётся остановленным, чтобы инженер снял дамп до рестарта |
| `healthcheck` для `karaoke-app` | в образе есть bash, но healthcheck — отдельная задача (BR-547 сделал его только для прод-`karaoke-web`) |
| Изменение `deploy/.env` | файл вне git и содержит секреты (Constitution VIII) — не коммитим, не печатаем, не правим |
| Правки `deploy/docker-compose.yml` / `docker-compose-web.yml` | вне объёма: FR-008 относится к `karaoke-app` |

---

## 5. Проверка на стенде (SC-004)

1. `docker compose -f deploy/docker-compose-app.yml up -d karaoke-app` (через
   `deploy/do.sh start_app`).
2. Искусственно исчерпать память внутри контейнера → в
   `deploy/karaoke-db/dumps/` появляется `java_pid*.hprof`.
3. `bash deploy/heap-dump-retention.sh` → остаются последние 2 файла, суммарный
   объём ≤ 20 ГБ, удалённые — самые старые.
4. `docker exec karaoke-app ls -la /dumps` — том виден внутри контейнера.
