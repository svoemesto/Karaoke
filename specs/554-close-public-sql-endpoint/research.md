# Research: 554 — Закрыть публичный доступ к `POST /changerecords`

**Дата**: 2026-10-24 · **Ветка**: `554-close-public-sql-endpoint` · **SHA кода на момент исследования**: 1977838a

Phase 0: все неясности спеки закрыты решениями ниже. Новых `[NEEDS CLARIFICATION]` не осталось.

## D1 — Два рубежа вместо одного

**Decision**: закрываем эндпоинт двумя независимыми рубежами — (1) список разрешённых адресов на уровне nginx, (2) проверка общего секрета в заголовке `X-Internal-Secret` на стороне приложения.

**Rationale**: решение владельца от 2026-10-24 (вариант «a» — фильтр nginx по IP админской машины, как уже реализовано) плюс требование FR-001…FR-004 спеки. Два рубежа означают, что компрометация одного не открывает эндпоинт.

**Alternatives considered**: (а) только nginx-фильтр — отклонён: при смене адреса у провайдера синхронизация падает до ручного вмешательства, а при ошибке конфигурации nginx дыра открыта полностью; (б) только секрет — отклонён: эндпоинт остаётся на публичном периметре, единственный барьер — один заголовок; (в) перенос эндпоинта на `127.0.0.1` без проксирования — отклонён: клиент синхронизации ходит по публичному HTTPS-имени, это сломает схему.

## D2 — Fail-safe: секрет должен быть непустым (важная ловушка образца)

**Decision**: проверка на сервере — `internalSecret.isNotEmpty() && request.getHeader("X-Internal-Secret") == internalSecret`. Сравнение — только при непустом настроенном секрете.

**Rationale**: образец `InternalStemJobController.authorized()` сделан как `request.getHeader("X-Internal-Secret") == internalSecret`. При незаданном секрете (`""`) и запросе **с присутствующим, но пустым** заголовком `X-Internal-Secret:` servlet вернёт `""`, сравнение пройдёт — доступ откроется. Это прямо нарушает FR-002 спеки. Поэтому образец копируется по смыслу (имя заголовка, fail-safe-декларация), но не буквально.

**Alternatives considered**: (а) копировать образец буквально — отклонён (см. выше); (б) сравнение в постоянном времени — отклонён как избыточное: секрет передаётся по HTTPS внутри одного контура, а эндпоинт и так не будет на публичном периметре после D1.

## D3 — Код отказа и отсутствие текста ошибки БД

**Decision**: при неверном/отсутствующем секрете — HTTP **403** и короткий ответ без деталей (как у `InternalStemJobController`); текст ошибки БД наружу не возвращается.

**Rationale**: сейчас обработчик заканчивает `catch (e: Exception) { return e.message!! }` — наружу уходит текст ошибки БД (имена таблиц/колонок). FR-006 требует этого не допускать. 403 выбран как «аутентификация не пройдена», тогда как 404 прятал бы сам факт существования эндпоинта; для server-to-server канала 403 информативнее и совпадает с принятым в проекте образцом.

**Alternatives considered**: 404 — отклонён (меняет семантику для клиента и расходится с образцом проекта).

## D4 — Категория журнала

**Decision**: новая категория `infra.sync.security` (по конвенции `infra.<subsystem>.<feature>` из `knowledge/domains/monitoring/components/log-categories.md`), регистрируется в этом же документе. Сообщения: `sync:security:denied` (WARN) — с временем, источником и причиной; тело запроса не пишется.

**Rationale**: конвенция проекта требует, чтобы мониторинг/безопасность логировались через `infra.*`; существующая `infra.sync.remote` описывает клиентскую сторону. Категория добавляется в реестр категорий, чтобы её можно было искать grep'ом.

**Alternatives considered**: переиспользовать `infra.sync.remote` — отклонён: она описывает исход клиента, а здесь событие на стороне сервера.

## D5 — Имена параметров

**Decision**:
- `karaoke-web`: `application.yml` → `sync: internal-secret: ${SYNC_INTERNAL_SECRET:}` (по образцу `stemjobs.internal-secret`, `karaoke-web/src/main/resources/application.yml:61–67`); env в compose: `SYNC_INTERNAL_SECRET=${SYNC_INTERNAL_SECRET}` (три compose-файла: `deploy/docker-compose-web.yml`, `deploy/prod-single-host/docker-compose-web.yml`, `deploy/web-server-deploy/deploy/docker-compose-web.yml`).
- `karaoke-app`: новое свойство `syncInternalSecret` в реестре `listKaraokeProperties` (`KaraokeProperties.kt`, рядом с `stemJobsInternalSecret`), доступ — `Karaoke.syncInternalSecret` (как `Karaoke.stemJobsInternalSecret` в `Karaoke.kt:362–368`).

**Rationale**: обе стороны уже умеют хранить общий секрет для `X-Internal-Secret`; новый секрет — отдельный, чтобы отзыв/ротация одного не ломала другой канал и чтобы отличать его в журналах.

**Alternatives considered**: переиспользовать `stemJobsInternalSecret` — отклонён: разные назначения, разный жизненный цикл, разные потребители.

## D6 — Список адресов в nginx

**Decision**: в `location /changerecords` (`deploy/prod-single-host/80to8897:74–90`) добавить `allow 185.26.28.109; allow 83.137.50.231; allow 185.26.28.199; deny all;` — тот же список, что уже используется для портов 5433 и 9000 (`deploy/prod-single-host/nginx.conf:22–32, 41–50`), с тем же комментарием про плавающий адрес.

**Rationale**: адрес админской машины у провайдера менялся трижды (2026-09-30, 2026-10-02); по решению владельца от 2026-10-07 в списке остаются все три исторических адреса, чтобы при смене адреса доступ не терялся. Копирование списка без изменений — поведение, уже проверенное на бою.

**Alternatives considered**: (а) один адрес — отклонён: отвал синхронизации при смене; (б) подсеть — отклонён: грубее, чем принятый в проекте подход.

## D7 — Поведение клиента при отказе

**Decision**: клиент (`SyncRemoteClient`) отправляет заголовок `X-Internal-Secret`. Ответ 403 — **не** транзиентная ошибка: повтор не выполняется (текущий `isTransient` считает транзиентными только сетевые сбои — 403 и так не попадает в список, но это фиксируется явно в тесте).

**Rationale**: FR-008. Неверный секрет — это не «сеть моргнула», повтор его не исправит и лишь размножит отказы в журнале.

## D8 — Тесты «до/после»

**Decision**:
- Сервер: unit-тест на `karaoke-web` — контроллер без заголовка → 403 и SQL не выполняется; с корректным заголовком → выполняется; с пустым настроенным секретом → 403 даже при пустом заголовке (ловушка D2).
- Клиент: unit-тест на `karaoke-app` — существующий test-seam `SyncRemoteClient.postChangeRecords(body, retryDelayMs, send)` позволяет проверить, что заголовок уходит в запрос, и что 403 не приводит к повтору.

**Rationale**: FR-010. Test-seam уже существует (Pass 431) — новой тестовой инфраструктуры не требуется.

**Alternatives considered**: интеграционный тест с контейнером — отклонён: правка не меняет схему и контракт, unit-уровень достаточен и быстрее.

## D9 — Что НЕ меняем

- Транзакционность применения SQL-действий (отдельный дефект BR-012, отдельная задача).
- Формат тела запроса, кодирование кодового слова, состав выполняемых действий (FR-011).
- Публичное поведение сайта, другие эндпоинты (FR-012).
- Порядок/состав `SyncRegistry` (Principle III).

## Источники (проверено на диске)

| Что | Файл:строки |
|---|---|
| Обработчик эндпоинта | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt:303–366` |
| Образец проверки секрета | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/InternalStemJobController.kt:38–43, 52, 77` |
| Параметр на стороне web | `karaoke-web/src/main/resources/application.yml:61–67` |
| Клиент синхронизации | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SyncRemoteClient.kt:31, 68–83` |
| Test-seam клиента | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SyncRemoteClient.kt:60–67` |
| Свойство на стороне app | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:422–428`, `Karaoke.kt:362–368` |
| Образец nginx-фильтра | `deploy/prod-single-host/nginx.conf:22–32, 41–50` |
| Изменяемый location | `deploy/prod-single-host/80to8897:74–90` |
| Конвенция категорий журнала | `knowledge/domains/monitoring/components/log-categories.md` |
