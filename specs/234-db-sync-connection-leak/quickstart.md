# Quickstart: Устранить утечку JDBC-соединений при «Синхронизации БД в 1 клик»

**Feature**: 234-db-sync-connection-leak
**Phase**: 1 (validation guide)
**Date**: 2026-08-16
**Branch**: `234-db-sync-connection-leak`

---

## Предусловия

1. **Машина**: Linux с hostname `dev-pc` под OS-пользователем `dev` (см. Constitution § «Ограничения агента», п. 1) — для полного цикла «правка кода → сборка → перезапуск контейнера → проверка» без согласия пользователя.
2. **Docker up**: `karaoke-app` + `karaoke-db` запущены (`docker ps | grep karaoke`).
3. **Postgres**: `max_connections = 100` (дефолт; в этой спеке не меняется).
4. **Ветка**: `git checkout 234-db-sync-connection-leak` (после мёрджа PR — `master`).
5. **Web-доступ**: открыть `http://localhost:8080/` (или прод-домен, если тестируется не на dev-pc) → авторизоваться как админ → перейти в «Sync admin».

> ⚠️ **Если машина НЕ `dev-pc` под `dev`** (например, текущая `nsa-i9` под `nsa`):
> - Агент правит код и собирает gradle-джары, но **НЕ перезапускает контейнеры** — это делает пользователь вручную.
> - Проверка SC-001..SC-005 — пользователем после ручного перезапуска.

---

## Шаги реализации (для разработчика)

### Шаг 1: Правка `karaoke-app/.../Connection.kt`

Заменить три фабрики в `companion object` на singleton-инстансы через `by lazy(SYNCHRONIZED)`:

```kotlin
companion object {
    private val USERNAME = if (APP_WORK_ON_SERVER) DB_SERVER_POSTGRES_USER else DB_LOCAL_POSTGRES_USER
    private val PASSWORD = if (APP_WORK_ON_SERVER) DB_SERVER_POSTGRES_PASSWORD else DB_LOCAL_POSTGRES_PASSWORD

    // NEW: singleton-фабрики (FR-001 spec.md)
    private val LOCAL_INSTANCE: Connection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Connection(name = "LOCAL", url = connectionLocalUrl(), username = USERNAME, password = PASSWORD)
    }
    private val REMOTE_INSTANCE: Connection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Connection(name = "SERVER", url = connectionRemoteUrl(), username = DB_SERVER_POSTGRES_USER, password = DB_SERVER_POSTGRES_PASSWORD)
    }
    @Suppress("unused")
    private val VIRTUAL_INSTANCE: Connection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Connection(name = "VIRTUAL", url = connectionVirtualUrl(), username = USERNAME, password = PASSWORD)
    }

    fun local(): KaraokeConnection = LOCAL_INSTANCE
    fun remote(): KaraokeConnection = REMOTE_INSTANCE
    @Suppress("unused")
    fun virtual(): KaraokeConnection = VIRTUAL_INSTANCE

    private fun connectionLocalUrl(): String = /* без изменений */
    private fun connectionRemoteUrl(): String = /* без изменений */
    private fun connectionVirtualUrl(): String = /* без изменений */
}
```

**KDoc обновить** (FR-010 spec.md): явно указать «фабрики возвращают singleton, а не новый инстанс на каждый вызов; ThreadLocal кеширует соединение по потоку (см. спеку `087-fix-shared-db-connection`)».

### Шаг 2: Правка `karaoke-app/.../KaraokeConnection.kt`

Добавить `private val log = LoggerFactory.getLogger(KaraokeConnection::class.java)` (импорт `org.slf4j.LoggerFactory`).

В `getConnection()` после `println(...)` добавить:

```kotlin
log.warn(
    "KaraokeConnection connect failure target={} thread={} cause={}",
    name,
    Thread.currentThread().name,
    e.message ?: "unknown",
)
```

В `closeThreadConnection()` — аналогично (для симметрии).

### Шаг 3: Симметричный фикс в `karaoke-web/.../Connection.kt`

Идентичные правки, но в неймспейсе `com.svoemesto.karaokeweb`. Использует `WEB_WORK_ON_SERVER`/`WEB_WORK_IN_CONTAINER` (уже в коде).

### Шаг 4: Документация `archive/docs/features/dual-db-sync.md`

- Добавить секцию «Singleton Connection-фабрики» с описанием нового поведения.
- Обновить секцию «Известные ловушки»: «новый `Connection` на каждый вызов → утечка `ThreadLocal` → `too many clients`» (теперь решена singleton'ом).

### Шаг 5: Сборка

```bash
./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel
```

### Шаг 6: Перезапуск контейнеров

> ⚠️ **На dev-pc под `dev`**: можно автоматически.
> **На других машинах**: пользователь вручную.

```bash
cd deploy
bash do.sh build_app
bash do.sh build_web
bash do.sh start_app
bash do.sh start_web
```

(или эквивалент через `deploy/deploy_web.sh` — см. `AGENTS.md` § «Сборка / деплой / тесты»).

---

## Сценарии проверки (SC-001..SC-005)

### SC-001: 10 кликов «1 клик» → 0 сообщений «too many clients»

```bash
# 1. Чистый старт (опционально)
docker restart karaoke-app karaoke-web karaoke-db
sleep 30  # ждём старта Postgres + Spring Boot

# 2. Открыть http://localhost:8080/ → Sync admin → "Синхронизация БД в 1 клик" → 10 раз подряд

# 3. Проверить лог
docker logs karaoke-app --since 5m | grep -c "too many clients"
# Ожидаемый результат: 0
```

**Pre-fix**: возвращает ~50-100 (по числу 18 SyncTarget × 2 БД × 10 кликов = 360 попыток, но с лимитом Postgres).
**Post-fix**: возвращает 0.

### SC-002: `pg_stat_activity` ≤ 10 соединений от `karaoke-app`

```bash
# В отдельном терминале — цикл проверки во время 10 кликов
for i in 1 2 3 4 5 6 7 8 9 10; do
  echo "--- Click $i ---"
  echo "SELECT count(*) FROM pg_stat_activity WHERE application_name='karaoke-app';" | docker exec -i karaoke-db psql -U postgres -d karaoke
done

# Ожидаемый результат: каждое значение ≤ 10
```

**Pre-fix**: значения растут линейно с каждым кликом до 100+.
**Post-fix**: значения стабильны ≤ 10.

### SC-003: Структурированный SLF4J warn при перегрузке БД

```bash
# 1. Занять 99 соединений в Postgres (отдельный терминал)
docker exec -it karaoke-db psql -U postgres -d karaoke <<EOF
SELECT pg_sleep(60) FROM generate_series(1, 99);
EOF
# Этот терминал оставить открытым

# 2. В браузере: нажать "Синхронизация БД в 1 клик"

# 3. Проверить лог
docker logs karaoke-app --since 1m | grep "KaraokeConnection connect failure"
# Ожидаемый результат:
# WARN ... KaraokeConnection connect failure target=LOCAL thread=http-nio-8080-exec-3 cause=FATAL: sorry, too many clients already
# (вместо/в дополнение к голому println)

# 4. Закрыть psql-терминал
```

**Pre-fix**: только `println("KaraokeConnection getConnection Exception: FATAL: sorry, too many clients already")`.
**Post-fix**: дополнительно структурированный `log.warn` с `target`, `thread`, `cause`.

### SC-004: Smoke-тест (нет регрессий)

1. **«Статистика»**: открыть `http://localhost:8080/` → «Статистика» → графики загружаются (FR-006 спеки 174).
2. **Редактор песни**: открыть любую песню → отредактировать → сохранить → данные в БД.
3. **Sync по одной сущности**: открыть `Sync admin` → выбрать только `songs` → синхронизировать → результат идентичен текущему.
4. **Фоновая задача**: запустить любую задачу из `KaraokeProcessQueue` → дождаться завершения → нет `SocketTimeoutException`.
5. **Sync «в 1 клик»** (финальный): синхронизация проходит без `too many clients` (SC-001).

**Ожидаемый результат**: все 5 шагов проходят без ошибок.

### SC-005: 5 параллельных HTTP-запросов → ≤ 30 соединений

```bash
# Используем hey или wrk для нагрузки
hey -n 100 -c 5 http://localhost:8080/api/sync/oneclick -m POST &

# Параллельно — открыть Статистику в браузере

# Проверить pg_stat_activity
echo "SELECT count(*) FROM pg_stat_activity WHERE application_name='karaoke-app';" | docker exec -i karaoke-db psql -U postgres -d karaoke
# Ожидаемый результат: ≤ 30
```

---

## Что делать, если SC провалились

### SC-001 провален (есть `too many clients`)

- Проверить, что `karaoke-app` пересобран и контейнер перезапущен **новым** bootJar.
- Проверить, что `by lazy(SYNCHRONIZED)` действительно применён (см. `git diff Connection.kt`).
- Проверить, что симметричный фикс в `karaoke-web` тоже применён (иначе утечка остаётся в `webvue3`-эндпоинтах).
- Проверить, что Postgres `max_connections` не снижен (по умолчанию 100).

### SC-002 провален (>10 соединений)

- Скорее всего, `WORKING_DATABASE` или другой singleton указывает на старый `Connection`-инстанс (не на singleton).
- Проверить все `private val X = Connection.local()` в `services/*.kt` — после рестарта должны указывать на singleton.

### SC-003 провален (нет структурированного warn)

- Проверить, что `LoggerFactory` импортирован в `KaraokeConnection.kt`.
- Проверить, что Spring Boot logging starter в classpath (должен быть по умолчанию).
- Проверить уровень логирования (`log.level.com.svoemesto.karaokeapp.KaraokeConnection=WARN` в `application.yml` или через env).

### SC-004 провален (регрессия)

- **Статистика**: возможно, `withDb { ... }` в `StatsController` сломан после singleton (но это маловероятно — `withDb` после фикса избыточен, но не вредит).
- **Редактор песни**: проверить, что `Song.saveToDb()` использует `WORKING_DATABASE` (singleton) — должно работать.
- **Sync**: проверить `ApiController.postSyncOneClick` — после фикса использует singleton через `runEntitySync` → `Connection.local()/remote()`.
- **Фоновая задача**: проверить `KaraokeProcessWorker` — после фикса singleton + ThreadLocal работает идентично.

---

## Cleanup (после успешной проверки)

1. **Merge PR**: `git checkout master && git merge --no-ff 234-db-sync-connection-leak` (после `gh pr merge --merge`).
2. **LiveDocs update** (опционально, для cross-machine документации): обновить `livedocs/features/sync.md` или аналогичный LiveDoc — но это вне scope FR-012 (там только `archive/docs/features/dual-db-sync.md`).
3. **Закрыть задачу** в `specs/` — `git log` покажет ссылку на эту спеку.

---

## Done When

- [ ] Шаги 1-6 реализации выполнены (правка кода + сборка + перезапуск).
- [ ] SC-001: `grep -c "too many clients"` = 0 после 10 кликов.
- [ ] SC-002: `pg_stat_activity WHERE application_name='karaoke-app'` ≤ 10.
- [ ] SC-003: Структурированный SLF4J warn виден в `docker logs karaoke-app`.
- [ ] SC-004: Smoke-тест (Статистика + редактор + sync + фоновая задача) — без регрессий.
- [ ] SC-005: 5 параллельных запросов → ≤ 30 соединений.
- [ ] Документация `archive/docs/features/dual-db-sync.md` обновлена.
- [ ] PR смержен в master через CI-gate (см. AGENTS.md § «Git — CI-gate для master»).
