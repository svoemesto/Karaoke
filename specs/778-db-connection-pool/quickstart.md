# Quickstart: Проверка ограниченного пула соединений

**Feature**: `specs/778-db-connection-pool/spec.md` | **План**: [plan.md](./plan.md)

Ручные сценарии проверки: подтвердить, что пул ведёт себя как задумано, и что прикладной код не изменился. Сценарии идут в порядке «дешёвый → дорогой»; каждый проверяем отдельно.

## Предусловия

- Локальный стек поднят: `bash deploy/do.sh start_db`, `start_app`, `start_web` (либо через `deploy/do.sh` в рабочем стеке).
- `GRADLE_USER_HOME` и `DOCKER_CONFIG` — от корня репозитория (AGENTS.md, Pass 372–373).
- Значения из `contracts/db-pool-config.md` — доступны через env контейнеров.

## Шаг 0. Сборка и тесты

```bash
cd /home/nsa/Karaoke
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")/.gradle}"
export DOCKER_CONFIG="${DOCKER_CONFIG:-$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")/.docker}"

./gradlew :karaoke-app:test :karaoke-web:test --parallel
```

**Ожидание**: BUILD SUCCESSFUL. Полный набор тестов обоих модулей — это и есть главная проверка FR-005: 284 места вызова `getConnection()` компилируются и работают без единой правки.

## Шаг 1. Путь выключен — поведение не изменилось (FR-011, T9)

```bash
# пул выключен по умолчанию — проверяем, что это не сломало ничего
cd /home/nsa/Karaoke/deploy && bash do.sh start_web
docker logs karaoke-web 2>&1 | grep -c "db.pool"   # ожидание: 0 (пул не создан)
docker exec karaoke-db psql -U postgres -d karaoke -t -A -c \
  "SELECT count(*) FROM pg_stat_activity WHERE datname='karaoke'"
```

**Ожидание**: контейнер healthy; событий `db.pool` нет; соединения в норме (как до фичи). Это подтверждает, что дефолт `KARAOKE_DB_POOL_ENABLED=false` безопасен.

## Шаг 2. Путь включён — пул живёт (этап 4)

```bash
# включаем пул на стенде
cd /home/nsa/Karaoke/deploy
KARAOKE_DB_POOL_ENABLED=1 bash do.sh start_web
sleep 60
docker logs karaoke-web 2>&1 | grep "db.pool" | tail -5
```

**Ожидание**: событий `DB-POOL-002` (исчерпание) нет. Канал(ы) пула созданы; обычная работа приложения не изменилась.

## Шаг 3. Потолок (FR-001, SC-001)

```bash
# число соединений к БД во время нагрузки не превышает потолок пула
for i in $(seq 1 20); do
  curl -s -o /dev/null "http://127.0.0.1:8897/api/public/news" &
done; wait
docker exec karaoke-db bash -c 'awk "NR>1 {split(\$2,l,\":\"); if (l[2]==\"1538\" && \$4==\"01\") c++} END {print c+0}" /proc/net/tcp'
```

**Ожидание**: число ≤ `KARAOKE_DB_POOL_MAX_LOCAL` (+ небольшой запас на административные подключения). Раньше число могло расти неограниченно.

## Шаг 4. Возврат handle по смерти потока (FR-003, SC-005) — ключевой сценарий

```bash
# 1) смотрим, сколько соединений до
docker exec karaoke-db bash -c 'awk "NR>1 {split(\$2,l,\":\"); if (l[2]==\"1538\" && \$4==\"01\") c++} END {print c+0}" /proc/net/tcp'

# 2) поток умирает с handle: короткий запрос к приложению из одноразового потока
#    (обычный HTTP-запрос делает Tomcat-поток, он переиспользуется — нужен именно
#    одноразовый; проще всего — дождаться idle-таймаута Tomcat, либо запустить
#    фоновую задачу через API)
# 3) ждём цикл рейпера (5 минут) и смотрим события
docker logs karaoke-web 2>&1 | grep "db.pool.reap" | tail -5
```

**Ожидание**: событие `DB-POOL-004` с `returned > 0` — handle мёртвого потока возвращён в пул; число соединений **не выросло**. Возврат происходит **не позднее одного цикла рейпера** (`interval-ms` = 300 000 мс = 5 мин): условие «владелец мёртв» не требует дополнительного ожидания `idle-ms`.

**Проверка на стенде без приложения** (детерминированно): см. юнит-тест `KaraokeConnectionPoolTest.рейпер_возвращает_handle_мёртвого_потока` — он воспроизводит сценарий без сети.

## Шаг 5. Исчерпание пула → 503, БД не страдает (FR-008, SC-006, Q3=A)

```bash
# искусственно занимаем все слоты: длинные транзакции из psql
for i in $(seq 1 60); do
  docker exec karaoke-db psql -U postgres -d karaoke -t -A -c "BEGIN; SELECT pg_sleep(30);" &
done
sleep 2
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8897/api/public/news"
docker logs karaoke-web 2>&1 | grep "db.pool" | tail -3
```

**Ожидание**:
- запрос к БД получает **503** (быстрый отказ приложения);
- в журнале — `DB-POOL-002` с `waiting` и `busy`;
- **другие клиенты БД продолжают работать** (например, `SELECT 1` из psql проходит) — это и есть смысл пула: отказ приложения не превращается в отказ БД для всех.

## Шаг 6. Латентность не хуже (SC-002)

```bash
for i in $(seq 1 50); do
  curl -s -o /dev/null -w "%{time_total}\n" "http://127.0.0.1:8897/api/public/news"
done | sort -n | python3 -c "
import sys
v=[float(x) for x in sys.stdin if x.strip()]
print(f'n={len(v)} p50={v[len(v)//2]*1000:.1f} мс p95={v[int(len(v)*0.95)]*1000:.1f} мс max={v[-1]*1000:.1f} мс')
"
```

**Ожидание**: baseline (2026-11-15) — p50 11,9 мс, p95 35,3 мс. Допуск: p50 ≤ 13,1 мс, p95 ≤ 38,8 мс (SC-002).

## Шаг 7. Откат (FR-011)

```bash
cd /home/nsa/Karaoke/deploy
KARAOKE_DB_POOL_ENABLED=0 bash do.sh start_web
sleep 30
docker logs karaoke-web 2>&1 | grep -c "db.pool"   # ожидание: 0
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8897/api/public/news"  # ожидание: 200
```

**Ожидание**: приложение работает как до фичи; следов пула нет. Откат — без пересборки, только env.

## Шаг 8. Прод (этапы 5–6, по согласию владельца)

```bash
# на проде
docker logs karaoke-web 2>&1 | grep -c "db.pool"          # события пула
docker exec karaoke-db bash -c 'awk "NR>1 {split(\$2,l,\":\"); if (l[2]==\"1538\" && \$4==\"01\") c++} END {print c+0}" /proc/net/tcp'
curl -s -o /dev/null -w "%{http_code} %{time_total}\n" "https://sm-karaoke.ru/api/public/news"
```

**Ожидание**: соединений ≤ 58 (web LOCAL 48 + SERVER 10); код 200; латентность в базовых пределах (прод baseline: p50 70,1 мс / p95 84,5 мс).

**Отдельно для app (этап 6)**: проверка очереди — после рестарта контейнера очередь поднимается кнопкой «Старт», задания доходят до `DONE`; рестарт — только после остановки очереди (AGENTS.md, Pass 380).

## Шаг 9. Наблюдение (SC-003, SC-004)

Через 24 ч и через 7 суток — снять те же метрики и убедиться:
- число соединений не выросло (SC-004);
- в логах БД нет `too many clients` (SC-003);
- событий `DB-POOL-002` нет.

Результаты — в `specs/778-db-connection-pool/report.md`.

## Типичные проблемы и что делать

| Симптом | Причина | Действие |
|---|---|---|
| `DB-POOL-002` при нормальной нагрузке | потолок меньше числа занятых handle'ов | поднять `KARAOKE_DB_POOL_MAX_*`; проверить, что рейпер работает (`DB-POOL-004` ≠ 0) |
| `DB-POOL-003` на заданиях очереди | порог детектора меньше длительности легитимной транзакции | поднять порог детектора (это наблюдательное событие, не действие) |
| `too many clients` в логах БД | сумма потолков пулов всех процессов превысила `max_connections` | снизить потолки; проверить, что `karaoke-app` на проде не развёрнут (он и не должен быть) |
| соединения к SERVER-БД не проходят после включения пула | потеряны параметры mTLS в URL | проверить, что URL передаётся в пул как есть (`contracts/db-pool-config.md`, R11) |
| очередь `KaraokeProcess` встала | пул меньше 4 лейнов очереди | поднять `KARAOKE_DB_POOL_MAX_LOCAL` для app |
