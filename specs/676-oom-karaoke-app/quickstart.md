# Quickstart: проверка фичи 676 после реализации

> **Ветка**: `676-oom-karaoke-app` | **Дата**: 2026-10-30
> **Спека**: [spec.md](spec.md) · **План**: [plan.md](plan.md)

Руководство по проверке: что запустить и что должно получиться. Реализация
(код, тесты) — в `tasks.md`; контракты — в [contracts/](contracts/).

---

## 0. Предусловия

```bash
cd /home/nsa/Karaoke
git branch --show-current        # ожидается: 676-oom-karaoke-app
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")/.gradle}"
```

Контейнеры **не** трогаются без согласия владельца (AGENTS.md, Hard Gate).
`docker restart` запрещён.

---

## 1. Модульный прогон (обязательный гейт)

```bash
./gradlew :karaoke-app:test --parallel
```

Ожидается: `BUILD SUCCESSFUL`. В отчёте зелёные:

| Тест | Что доказывает |
|---|---|
| `audit.PkgS212BehaviorTest` (существующий) | `popPublicationStacks` — сохранён (FR-004) |
| `audit.PkgS212SourceContractsTest` (существующий) | source-контракт BR-188 — сохранён (FR-004) |
| `model.PublicationGridCallerTest` (новый) | уровень вызывающих: 500 песен → конечная сетка 50×10 (FR-004, SC-002) |
| `monitor.HeapUsageCheckTest` (новый) | пороги 75 %/90 %, severity, recovery, бюджет времени (FR-015…FR-017) |

Точечный прогон одного теста:

```bash
./gradlew :karaoke-app:test --tests '*PublicationGridCallerTest*' --tests '*HeapUsageCheckTest*'
```

---

## 2. Линтеры и сборка

```bash
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
./gradlew :karaoke-web:ktlintCheck
cd webvue3 && npm run lint && cd ..
cd karaoke-public && npm run lint && cd ..
./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel
```

Фронтенд не менялся — Vite-сборки нужны только если правится фронт (не в объёме).

---

## 3. Конфигурация развёртывания

```bash
# Синтаксис и «пара heap ↔ предел»
grep -nE 'mem_limit|-Xmx|HeapDump|karaoke-db/dumps' deploy/docker-compose-app.yml

# Guard деплой-контура (включает новое правило BR-629)
bash tools/check-deploy-hardening.sh
bash tools/check-deploy-hardening.sh --self-test

# Политика хранения дампов
bash -n deploy/heap-dump-retention.sh
bash deploy/heap-dump-retention.sh --self-test
```

Ожидается: guard зелёный (`PASS=… FAIL=0`), self-testы зелёные.

**Что должно быть видно в `deploy/docker-compose-app.yml`:**

```yaml
    mem_limit: 8g
    command: ${APP_JAVA_OPTS} -Xmx6g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps -jar /app.jar
    volumes:
      - ./karaoke-db/dumps:/dumps
    environment:
      - MONITOR_HEAP_WARN_PERCENT=75
      - MONITOR_HEAP_CRITICAL_PERCENT=90
```

---

## 4. База знаний

```bash
python3 tools/lint-knowledge.py
bash tools/check-knowledge-structure.sh
python3 tools/check-ssot-impact.py          # по diff — проверяет, что knowledge/ обновлён
bash tools/check-feature-doc.sh knowledge/product/features/heap-memory-guardrails.md
```

Ожидается: без новых нарушений; ссылки в 12 документах из FR-020 валидны;
Linking Protocol L3→L2→L1 соблюдён (новая проверка `HeapUsageCheck` перечислена
в `knowledge/domains/monitoring/domain.md`; новый ADR — в `knowledge/adr/README.md`).

---

## 5. Проверка на стенде (требует согласия владельца на контейнеры)

### 5.1 Дамп при OOM (SC-004)

```bash
cd deploy && bash do.sh start_app          # по согласию владельца
docker exec karaoke-app bash -c 'dd if=/dev/zero of=/tmp/fill bs=1M count=7000'
# ждём OOM; затем:
ls -la deploy/karaoke-db/dumps/             # ожидается java_pid*.hprof
docker exec karaoke-app ls -la /dumps
```

Ожидается: файл `.hprof` в `deploy/karaoke-db/dumps/` и он же виден в `/dumps`
внутри контейнера.

### 5.2 Политика хранения (SC-004)

```bash
bash deploy/heap-dump-retention.sh --dir deploy/karaoke-db/dumps
ls -la deploy/karaoke-db/dumps/             # остаются последние 2 .hprof
```

Ожидается: `.sql`-файлы (дампы БД) **не тронуты**, `.hprof` — не более двух,
суммарный объём ≤ 20 ГБ, самые старые удалены первыми.

### 5.3 Алерт по памяти (SC-005)

```bash
docker logs karaoke-app 2>&1 | grep 'infra.monitor.heap' | tail -20
```

Ожидается: строка `heap:usage usedBytes=… maxBytes=… percent=…` раз в минуту.
При искусственном росте heap выше 75 % — `heap:thresholdExceeded … severity=WARNING`,
выше 90 % — `severity=CRITICAL`; при возврате в норму — `heap:recovered`.

Алерт также виден в существующих каналах: `GET /api/monitor/alerts` и SSE
`MONITOR_ALERTS` в webvue3.

### 5.4 Предел памяти контейнера (SC-003)

```bash
docker inspect karaoke-app --format '{{.HostConfig.Memory}}'   # ожидается 8589934592 (8 ГБ)
docker exec karaoke-app java -XX:+PrintFlagsFinal -version 2>/dev/null | grep MaxHeapSize
```

Ожидается: `Memory = 8589934592`; `MaxHeapSize` ≈ 6 GiB (6442450944).

---

## 6. Чего НЕ делать

| Действие | Почему |
|---|---|
| `docker restart karaoke-app` | запрещено (R-374); при OOM контейнер остаётся остановленным — сначала снять дамп |
| Перезапуск без остановки очереди | Pass 380: сначала «Стоп» очереди, потом рестарт |
| Пуш/PR/merge | только по слову лида |
| Правка `deploy/.env`, `deploy/do.env` | секреты вне git (Constitution VIII); значения JVM теперь в отслеживаемом compose |
| `docker compose up` в обход `deploy/do.sh` | R-374 |
