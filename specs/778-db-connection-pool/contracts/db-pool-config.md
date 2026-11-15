# Contract: Конфигурация пула соединений

**Feature**: `specs/778-db-connection-pool/spec.md` | **План**: [plan.md](../plan.md)

Контракт описывает, как параметры пула попадают из окружения в приложение. Секретов среди параметров нет: логин/пароль/URL приходят существующим путём (`url`/`username`/`password` в `KaraokeConnection`).

## Переменные окружения

| Env | Свойство Spring | Тип | Дефолт (app) | Дефолт (web) | Смысл |
|---|---|---|---|---|---|
| `KARAOKE_DB_POOL_ENABLED` | `karaoke.db.pool.enabled` | bool | `false` | `false` | Включение пула. `false` → поведение как до фичи (FR-011, откат) |
| `KARAOKE_DB_POOL_MAX_LOCAL` | `karaoke.db.pool.max-local` | int | `128` | `96` | Потолок физических каналов к LOCAL (FR-001) |
| `KARAOKE_DB_POOL_MAX_SERVER` | `karaoke.db.pool.max-server` | int | `20` | `10` | Потолок физических каналов к SERVER |
| `KARAOKE_DB_POOL_MIN_IDLE` | `karaoke.db.pool.min-idle` | int | `2` | `2` | Прогретых каналов в простое |
| `KARAOKE_DB_POOL_ACQUIRE_TIMEOUT_MS` | `karaoke.db.pool.acquire-timeout-ms` | long | `5000` | `5000` | Ожидание свободного слота (Q3=A) |
| `KARAOKE_DB_POOL_MAX_LIFETIME_MS` | `karaoke.db.pool.max-lifetime-ms` | long | `1800000` | `1800000` | Абсолютный срок жизни канала |
| `KARAOKE_DB_POOL_KEEPALIVE_MS` | `karaoke.db.pool.keepalive-ms` | long | `60000` | `60000` | Пинг простаивающих каналов (туннель) |
| `KARAOKE_DB_POOL_VALIDATION_TIMEOUT_MS` | `karaoke.db.pool.validation-timeout-ms` | long | `3000` | `3000` | Проверка живости канала при выдаче |

## Правила

1. **Дефолт различается по модулям.** Свойства задаются в `application.yml` каждого модуля (`karaoke-app/src/main/resources/application.yml`, `karaoke-web/src/main/resources/application.yml`); env перекрывает. Причина — разная верхняя граница handle'ов, посчитанная **от конфигурации**, а не от наблюдаемой загрузки:
   - app LOCAL: 50 (`server.tomcat.threads.max`) + 36 (`Schedulers.parallel()` = nproc) + 30 (HealthReport: `POOL_SIZE=10` + `POOL_SIZE_WAITING=20`) + 4 (лейны очереди) + 4 (`karaoke-scheduler`) ≈ 124 → **128**;
   - web LOCAL: 50 (Tomcat max) + 36 (пул MessageBroker = nproc) + 4 служебных ≈ 90 → **96**.
   Дефолты зависят от числа ядер: при другом `nproc` пересчитать по формуле в `plan.md` § «Размеры пулов» и сверить с фактическим числом потоков перед включением.
2. **Пул выключен по умолчанию.** Код приезжает в прод выключенным; включение — изменение env + рестарт. Это первая линия отката (FR-011).
3. **Валидация на старте.** Некорректное значение (`≤ 0`, `minIdle > maxPoolSize`, `keepalive ≥ maxLifetime`, сумма потолков ≥ лимита БД 300) → **отказ на старте** с внятным сообщением, а не молчаливое усечение.
4. **Лимит БД не настраивается пулом.** `max_connections=300` живёт в compose-файлах БД (`DB_MAX_CONNECTIONS`); пул обязан укладываться в него (FR-010), но не меняет его.
5. **Параметры пула не содержат секретов** — учётные данные БД приходят существующим путём.

## Способы установки (по стеку)

| Стек | Файл | Переменные |
|---|---|---|
| Стенд (локальный) | `deploy/docker-compose-web.yml`, `deploy/docker-compose-app.yml` | `KARAOKE_DB_POOL_*` в блоке `environment` |
| Прод | `deploy/prod-single-host/docker-compose-web.yml`, `deploy/prod-single-host/docker-compose-app.yml` | то же |

**Внимание:** на проде развёрнут только `karaoke-web` (`karaoke-app` — только на админ-машине).

## Пример

```yaml
# deploy/prod-single-host/docker-compose-web.yml
    environment:
      KARAOKE_DB_POOL_ENABLED: ${KARAOKE_DB_POOL_ENABLED:-0}
      KARAOKE_DB_POOL_MAX_LOCAL: ${KARAOKE_DB_POOL_MAX_LOCAL:-96}
      KARAOKE_DB_POOL_MAX_SERVER: ${KARAOKE_DB_POOL_MAX_SERVER:-10}
      KARAOKE_DB_POOL_MIN_IDLE: ${KARAOKE_DB_POOL_MIN_IDLE:-2}
      KARAOKE_DB_POOL_ACQUIRE_TIMEOUT_MS: ${KARAOKE_DB_POOL_ACQUIRE_TIMEOUT_MS:-5000}
      KARAOKE_DB_POOL_MAX_LIFETIME_MS: ${KARAOKE_DB_POOL_MAX_LIFETIME_MS:-1800000}
      KARAOKE_DB_POOL_KEEPALIVE_MS: ${KARAOKE_DB_POOL_KEEPALIVE_MS:-60000}
      KARAOKE_DB_POOL_VALIDATION_TIMEOUT_MS: ${KARAOKE_DB_POOL_VALIDATION_TIMEOUT_MS:-3000}
```

## Проверка

```bash
# значения в контейнере после деплоя (Constitution, § Compliance-проверка в production)
docker exec karaoke-web env | grep KARAOKE_DB_POOL

# события пула в журнале
docker logs karaoke-web 2>&1 | grep -E "db\.pool"
```
