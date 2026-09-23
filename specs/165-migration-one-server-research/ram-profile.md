# RAM-профиль нового сервера `sm-karaoke` (spec #165)

**Дата**: 2026-09-23
**Тип**: research (замеры, без изменений прода)
**Вопрос**: выдержит ли сервер с 3.8 ГБ RAM / 8 ГБ swap прод-стек
`karaoke-web` + `karaoke-public` + PostgreSQL 16 + MinIO + host-nginx?

---

## 1. Обзор

Новый сервер `sm-karaoke` (188.127.240.124): 6 vCPU, **MemTotal = 4 003 224 kB ≈ 3.8 GiB**,
swap = 0, диск 985 ГБ, Ubuntu 26.04, Docker **не установлен** (в apt доступен
`docker.io 29.1.3`; `docker-ce` в репозиториях отсутствует). cgroup v2,
корневой cgroup без лимита (`memory.max` отсутствует / `system.slice` = `max`),
ulimit `max locked memory = 8192 kB`, `open files = 1024` (дефолт, кандидат на тюнинг).

Ключевой вопрос — влезет ли Java-приложение (heap + metaspace + stack + direct buffers)
вместе с Postgres, MinIO, двумя nginx и ОС в ~3.8 ГБ **без OOM-kill**.

Главный вывод: **влезает**, но с небольшим запасом. Рекомендуемый heap —
**`-Xmx1200m`** (безопасно) / `-Xmx1400m` (верхняя граница при ограниченном MinIO).
Swap 8 ГБ — **да, обязателен** как страховка от OOM-kill; `vm.swappiness=10`.

---

## 2. Замеры старого прода (188.119.64.111)

`free -h`:

| Метрика | Значение |
|---|---|
| Mem total | 3.8Gi |
| Mem used | 1.5Gi |
| Mem free | 477Mi |
| buff/cache | 2.3Gi |
| Mem available | 2.3Gi |
| **Swap** | **0B (отсутствует)** |

`head -5 /proc/meminfo`: `MemTotal 4009152 kB`, `MemFree 488932 kB`,
`MemAvailable 2452740 kB`, `Buffers 93708 kB`, `Cached 2171404 kB`.

`docker stats --no-stream`:

| Контейнер | MemUsage | % от 3.823Gi |
|---|---|---|
| karaoke-public | 15.22 MiB | 0.39% |
| karaoke-web | 836.8 MiB | 21.37% |
| karaoke-db | 336.4 MiB | 8.59% |

`ps aux --sort=-%mem` (главное): java `RSS 808 900 kB` (`-Xmx1048m`),
несколько backend-процессов postgres `RSS ~145–155 MB` (shared buffers
отображаются в RSS каждого backend), `systemd-journald 119 836 kB`,
`dockerd 95 264 kB`, `containerd 47 644 kB`.

**Важно**: `docker stats` (836 MiB) > `smaps_rollup Rss` (808 900 kB) —
разница cgroup vs процесс. `docker inspect` на всех контейнерах:
`Memory=0`, `MemorySwap=0`, `NanoCpus=0` — **никаких cgroup-лимитов на память
не выставлено**, limit = вся RAM хоста (3.823GiB). `restart: always`.
`mem_limit` в compose и `memory:` отсутствуют (проверено grep по серверным *.yml).

Cgroup-разбивка `karaoke-web` (memory.stat):

| Категория | Байт |
|---|---|
| anon | 801 116 160 (~764 MiB) |
| file | 267 014 144 (~255 MiB) |
| inactive_file | 211 070 976 (~201 MiB) |
| active_file | 55 943 168 (~53 MiB) |
| slab | 17 331 352 (~17 MiB) |
| memory.current | 1 088 507 904 (~1.01 GiB) |

JVM breakdown (`/proc/<pid>/smaps_rollup`): `Rss 808 900 kB`, `RssAnon 782 340 kB`,
`RssFile 26 560 kB`, `Private_Dirty 782 348 kB`, `VmData 970 832 kB`,
`VmStk 132 kB`, потоков **47**. Т.е. реальный RSS Java ≈ **809 MB при heap 1048 MB**
(heap не заполнен; G1 отдаёт память неохотно). JVM-флаги (эргономика, `-XX:+PrintFlagsFinal`):
`MaxHeapSize = 1 027 604 480` (~980 MiB при Xmx1048m), `InitialHeapSize = 65 011 712` (~62 MiB),
`UseG1GC = true`, `ReservedCodeCacheSize = 251 658 240` (~240 MiB зарезервировано),
`MetaspaceSize = 22 020 096` (~21 MiB), `MaxMetaspaceSize = unlimited`,
`MaxRAMPercentage = 25` (дефолт, перекрыт явным Xmx).

Postgres 16 (`postgresql.conf`): `shared_buffers = 128MB`, `work_mem = 4MB`,
`max_connections = 100`, `effective_cache_size ≈ 4GB`, размер БД
**483 MB** (`pg_database_size`). `docker stats` 336 MiB объясняется тем, что
shared_buffers (128 MB) + page cache БД видны в cgroup контейнера.

Host-nginx: суммарный RSS всех worker'ов **≈ 56.5 MB**.

Итого на старом проде: `java ~809` + `db ~336` + `public ~15` + `nginx ~57`
+ `dockerd+containerd ~143` + `journald ~120` = **~1.48 GB**, совпадает с `used 1.5Gi`.

---

## 3. Замеры MinIO (89.125.103.63)

`docker stats` (3 замера подряд — стабильно): `karaoke-storage 1.527GiB / 3.823GiB`.
`free -h`: used 872Mi, buff/cache 3.1Gi, **swap 2.0Gi** установлен.
`minio version RELEASE.2025-09-07T16-13-09Z`, go1.24.6.

**НО** cgroup `MemoryCurrent=135 614 464` (~129 MiB), `MemoryPeak ~130 MiB`,
`MemorySwap=0`. `memory.stat` контейнера:

| Категория | Байт |
|---|---|
| **anon** | **315 207 680 (~301 MiB)** |
| file | 2 064 744 448 (~1.92 GiB) |
| inactive_file | 1 062 711 296 (~0.99 GiB) |
| active_file | 1 002 033 152 (~0.96 GiB) |
| slab | 321 153 056 (~306 MiB) |
| memory.current | 2 702 589 952 (~2.52 GiB) |

`ps aux` для MinIO: `RSS 369 816 kB`. Данных в `/data` — **446 GB** (диск 485 ГБ, 94%).

**Вывод**: «1.5 GiB» в `docker stats` — это на ~85% **page cache** от чтения 446 ГБ
файлов (file = 1.92 GiB, reclaimable), а не реальная анонимная память.
Реальное несжимаемое потребление MinIO ≈ **300–370 MiB** (anon + slab + Go-runtime).
Page cache **вытесняется** под давлением автоматически, поэтому при расчёте
бюджета MinIO надо закладывать **~350 MiB**, а не 1.5 GiB.

---

## 4. Требования компонентов

Конфиги деплоя (факты из репозитория):

| Источник | `APP_JAVA_OPTS` | Комментарий |
|---|---|---|
| `deploy/.env` (локальный dev / admin-машина) | `-Xmx8g` | Pass 369, HealthReport 18 097 песен |
| `deploy/.env` `WEB_JAVA_OPTS` | `-Xmx8g -Duser.timezone=Europe/Moscow` | — |
| `deploy/web-server-deploy/deploy/.env` (**старый прод**) | `-Xmx1048m` | реально применён |
| `deploy/new_comp/.../deploy/.env` (черновик переезда) | `-Xmx2g` | **опасно для 3.8 ГБ** |
| `deploy/new_comp/.../docker-compose-web-new-comp.yml` | `-Xmx1048m` (хардкод в command) | дублирует |

`-Xmx8g` — это **admin/dev-машина** (`nsa-i9`), НЕ прод. На старом проде работает
`-Xmx1048m`. Заготовка `new_comp` с `-Xmx2g` — для другой машины (SpaceBox4096),
на 3.8 ГБ её использовать нельзя.

Топология: на старом проде `karaoke-db` и `karaoke-web` — на одном хосте
(188.119.64.111), а **MinIO был на отдельном хосте** (89.125.103.63).
Новый сервер совмещает **всё**: java + postgres + minio + host-nginx + nginx(SPA).

---

## 5. Бюджет памяти нового хоста (таблица)

MemTotal = 3.8 GiB (4 003 224 kB). Оставляем ~300 MiB на ядро/dockerd/journald/прочее.

| Компонент | Ожидаемый RSS | Обоснование |
|---|---|---|
| ОС (ядро, systemd, journald, sshd) | 250–300 MiB | journald на старом проде 120 MiB, + ядро/slab/прочее |
| dockerd + containerd | 140–160 MiB | замер прода: 95 + 48 MiB |
| **karaoke-web (Java)** | **1200 heap + 350–450 overhead = ~1550–1650 MiB** | пик RSS ≈ Xmx + metaspace/code cache/stack/GC/direct |
| PostgreSQL 16 | 300–450 MiB | 128 MB shared_buffers + backends + page cache; БД 483 MB |
| MinIO | 300–400 MiB | anon 301 MiB + slab 306 MiB (page cache вытесняемый) |
| host-nginx (TLS/прокси) | 50–60 MiB | замер: 56.5 MiB |
| karaoke-public (nginx SPA) | 15–25 MiB | замер: 15.2 MiB |
| **Итого (без page cache)** | **~2.6–3.0 GiB** | из 3.8 GiB |
| Свободный запас | **~0.8–1.2 GiB** | на пики и page cache |

**Вывод: fits** — при `-Xmx1200m` стек укладывается в 3.8 ГБ с запасом ~0.8–1.2 ГБ.
При `-Xmx2g` (черновик `new_comp`) запас падает до ~0–0.4 ГБ → **риск OOM**.
При `-Xmx8g` (admin) — **гарантированный OOM-kill**, использовать нельзя.

Оговорка: MinIO и Postgres активно используют page cache (446 ГБ и 483 МБ
соответственно). Page cache — reclaimable, ядро вытеснит его под давлением,
но при холодном старте/первом сканировании возможны всплески RSS. Именно поэтому
swap нужен как буфер.

---

## 6. Рекомендуемый `-Xmx`

Расчёт: `Xmx = MemTotal − (ОС + docker + Postgres + MinIO + nginx) − overhead JVM − reserve`.
`3800 − (300 + 150 + 400 + 350 + 60+20) − 400 − 300 ≈ 1820 MiB` — теоретический потолок,
но брать его нельзя из-за пиков page cache и фрагментации.

**Рекомендация: `-Xmx1200m`** (значение по умолчанию для нового хоста).

Обоснование:
- Старый прод живёт на `-Xmx1048m` с RSS ~809 MB и `used 1.5Gi` из 3.8 — то есть
  **2.3 GiB остаются свободными**. 1200m даёт ~15% запаса к проверенному значению,
  не приближаясь к границе.
- Overhead JVM над heap: metaspace ~50–80 MB + code cache (ReservedCodeCache 240 MB,
  реально используется меньше) + thread stacks (47 потоков × ~1 MB ≈ 47 MB)
  + direct/GC структуры. Наблюдаемый RSS 809 MB при heap 1048m — но это **не**
  худший случай (heap не был заполнен). На пике RSS ≈ heap + **300–500 MB**.
  Для 1200m это ~1500–1700 MB.
- Альтернатива `-Xmx1400m` допустима, если Postgres не будет расти
  (`shared_buffers` оставить 128 MB) и MinIO ограничить через `mem_limit`.

Конкретные флаги для `deploy/web-server-deploy/deploy/.env` нового хоста:

```env
APP_JAVA_OPTS=-Xmx1200m -XX:MaxMetaspaceSize=256m -XX:MaxDirectMemorySize=256m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps
```

`MaxMetaspaceSize`/`MaxDirectMemorySize` ограничивают нативные утечки — важно,
чтобы OOM приложения не превращался в OOM-kill всего контейнера.

**Обязательно зафиксировать** `-Xmx` и **не** использовать дефолтный
`MaxRAMPercentage=25` без явного Xmx: при 3.8 ГБ и cgroup-лимите в 3.8 GiB
JVM возьмёт ~950 MiB — это ещё допустимо, но непредсказуемо. Явный Xmx надёжнее.

---

## 7. Swap и swappiness

**Swap 8 ГБ — да, обязателен.** Причины:
1. **Страховка от OOM-kill.** Без swap любой всплеск (HealthReport по 18 097 песням,
   холодный старт, параллельные SELECT) при `vm.overcommit_memory=0` приводит к выбору
   жертвы OOM-killer; чаще всего жертвой становится самый жирный процесс — Java.
   Swap превращает жёсткий kill в замедление, давая шанс завершить операцию.
2. **Postgres и MinIO** любят page cache; swap даёт ядру манёвр для анонимных страниц.
3. 8 ГБ swap на 985 ГБ диска — бесплатно по месту.

Параметры (согласованы с решением владельца Q8):

```bash
# swap-файл 8G
fallocate -l 8G /swapfile
chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# swappiness: Java-swap вреден для latency, но лучше OOM
sysctl -w vm.swappiness=10
sysctl -w vm.vfs_cache_pressure=50
# persistence
printf 'vm.swappiness=10\nvm.vfs_cache_pressure=50\n' > /etc/sysctl.d/99-karaoke-memory.conf
```

`vm.swappiness=10` (вместо дефолтных 60) — чтобы ядро свапило только при реальном
давлении, а не превентивно; при 8 ГБ swap даже 10 достаточно для защиты от OOM.
`vfs_cache_pressure=50` — удерживать dentry/inode cache для 446 ГБ MinIO дольше.

Дополнительно: `vm.overcommit_memory=0` (текущий) оставить; рассмотреть
`vm.min_free_kbytes` — на новом хосте 67 584 kB (67 MB), разумно.

---

## 8. Риски OOM

| Риск | Вероятность | Митигация |
|---|---|---|
| Java heap пик при HealthReport | средняя | `-Xmx1200m` + swap + `-XX:MaxMetaspaceSize` |
| MinIO холодное сканирование (446 ГБ) | средняя (при старте) | swap; после прогрева page cache стабилизируется |
| Postgres autovacuum/Big SELECT | низкая | `shared_buffers=128MB`, `work_mem=4MB` не менять |
| Суммарные page cache > свободной RAM | низкая | ядро reclaim'ит автоматически |
| Docker без cgroup-лимита: один контейнер съедает всё | средняя | задать `mem_limit` (см. ниже) |
| OOM-killer убьёт `karaoke-web` вместо MinIO | средняя | `oom_score_adj` для критичных контейнеров |

**Рекомендуемые ограничения Docker** (сейчас на старом проде их нет вообще —
`Memory=0`, это опасно). Для нового хоста:

```yaml
karaoke-web:
  mem_limit: 1800m
  memswap_limit: 1800m   # без доп. swap для Java (latency)
karaoke-storage:
  mem_limit: 900m        # page cache вытеснится, anon ~350m
karaoke-db:
  mem_limit: 700m
```

Без cgroup-лимитов один контейнер может выесть всю RAM и убить соседей.
`mem_limit` заставит ядро reclaim'ить page cache **внутри** контейнера, а не
свалить весь хост.

**Не забыть**: `ulimit -n 1024` на новом хосте — для nginx/Postgres/Java
на проде обычно поднимают (`nofile` 65535) через `/etc/security/limits.conf`
и `LimitNOFILE` для docker.service.

---

## 9. Мониторинг

1. **Базовый**: `docker stats --no-stream` после старта стека; `free -h`; `cat /proc/meminfo`.
2. **По контейнерам** (cgroup v2, точнее чем docker stats):
   ```bash
   cat /sys/fs/cgroup/system.slice/docker-<id>.scope/memory.current
   grep -E '^(anon|file|inactive_file|slab)' .../memory.stat
   cat .../memory.events   # oom / oom_kill счётчики
   ```
3. **Алерт на OOM**: `memory.events` → `oom_kill` > 0. Мониторить `dmesg -T | grep -i oom`.
4. **JVM**: `-Xlog:gc*:file=/dumps/gc.log:time,uptime:filecount=5,filesize=10M`
   (в образе нет `jcmd`/`jstat` — только `java`, `jfr`, `keytool`; включать GC-логи флагом).
   Смотреть частоту Full GC и RSS через `smaps_rollup`.
5. **До переезда** прогнать нагрузочный сценарий (HealthReport / холодный старт)
   и снять пик `memory.current` + `memory.peak`.
6. **Непрерывно**: если `available` в `free -h` устойчиво < 400 MiB — снижать Xmx
   или поднимать `mem_limit` соседям.

---

## 10. Итог

- **Влезает ли**: да, при `-Xmx1200m` и swap 8 ГБ. Запас ~0.8–1.2 ГБ.
- **Рекомендуемый Xmx**: `1200m` (безопасно), максимум `1400m` при ограниченном MinIO.
- **Swap**: 8 ГБ обязателен, `vm.swappiness=10`.
- **Критично**: убрать `-Xmx2g` из `new_comp`-черновика; выставить cgroup `mem_limit`
  (на старом проде их нет); поднять `ulimit -n`.
