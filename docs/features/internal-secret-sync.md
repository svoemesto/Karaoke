# Feature: Внутренний секрет синхронизации (X-Internal-Secret для POST /changerecords)

**Spec**: [`specs/554-close-public-sql-endpoint/spec.md`](../../specs/554-close-public-sql-endpoint/spec.md)
**Status**: active (спека 554, OpenProject #357, аудит BR-011)
**Created**: 2026-10-24
**Last updated**: 2026-10-24
**Source**: OpenProject #357 — «S1: POST /changerecords — публичный endpoint, который исполняет присланный клиентом SQL (BR-011)»
**Feature Key**: `internal-secret-sync`

## Что делает

Закрывает эндпоинт `POST /changerecords` на `karaoke-web` — приём изменений для
двух-БД синхронизации песен — двумя независимыми рубежами:

1. **Сетевой рубеж (nginx)** — `location /changerecords` в `deploy/prod-single-host/80to8897`
   принимает запросы только с адреса админской машины (`allow` для трёх известных адресов
   провайдера + `deny all`), ровно как уже сделано для внутренних портов 5433 (прод-БД)
   и 9000 (MinIO).
2. **Рубеж приложения (внутренний ключ)** — обработчик требует заголовок
   `X-Internal-Secret` с общим секретом, который клиент синхронизации (`karaoke-app`)
   отправляет в каждом запросе.

Дополнительно: при отказе наружу уходит короткий `403` без деталей (текст ошибки БД
больше не возвращается клиенту), а попытка пишется в журнал категории
`infra.sync.security` — время, адрес источника, причина; без тела запроса.

## Зачем

Аудит кода #336 (порция P-35, находка P-35-01 / BR-011, severity **S1**): эндпоинт
`POST /changerecords` был доступен анонимно из интернета и исполнял SQL-действия,
присланные клиентом. nginx маршрутизировал `/changerecords` без `allow`/`deny`,
а в приложении не было проверки прав — неаутентифицированный канал исполнения
произвольного SQL в боевой БД.

Правка закрывает канал двумя рубежами: компрометация одного из них не открывает
эндпоинт.

## Как работает

### Сервер — `karaoke-web`

- `karaoke-web/.../controllers/MainController.kt`:
  - `doChangeRecords` — `POST /changerecords`; требует заголовок `X-Internal-Secret`.
  - `syncDenialReason` — проверка ключа. **Fail-safe**: сравнение выполняется только
    при непустом настроенном секрете; пустой секрет = «не настроен» → доступ закрыт.
  - `syncSourceAddress` — адрес источника для журнала: `X-Real-IP` (его выставляет наш
    nginx) либо `request.remoteHost`; `X-Forwarded-For` не используется (клиент его
    подделывает, см. BR-003).
  - При отказе — `403` с коротким телом `Forbidden`, ни одной выполненной SQL-команды.
  - При внутренней ошибке — `500` с телом `Internal error`; текст ошибки БД уходит
    только в журнал.
  - `handleChangeRecords` — тело обработчика с тест-швом `connectionProvider`
    (поддельное JDBC-соединение в unit-тестах).
- Параметр: `sync.internal-secret` в `karaoke-web/src/main/resources/application.yml`
  (`${SYNC_INTERNAL_SECRET:}`), env `SYNC_INTERNAL_SECRET` в трёх compose-файлах
  (`deploy/docker-compose-web.yml`, `deploy/prod-single-host/docker-compose-web.yml`,
  `deploy/web-server-deploy/deploy/docker-compose-web.yml`).

### Клиент — `karaoke-app`

- `karaoke-app/.../services/SyncRemoteClient.kt`: в каждый запрос добавляется заголовок
  `X-Internal-Secret` со значением `Karaoke.syncInternalSecret`.
  Ответ `403` — отказ по правам, а не сетевая ошибка: повтор **не** выполняется.
  Сетевые ошибки (таймаут, сброс, TLS) — по-прежнему одна повторная попытка.
- Свойство `syncInternalSecret` в реестре `KaraokeProperties` (default `""`) и доступ
  `Karaoke.syncInternalSecret`; на админской машине задаётся в `Karaoke.properties`.

### Сетевой рубеж — nginx

`deploy/prod-single-host/80to8897`, `location /changerecords`:

```nginx
allow 185.26.28.109;
allow 83.137.50.231;
allow 185.26.28.199;
deny all;
```

Список — тот же, что у портов 5433/9000 (`deploy/prod-single-host/nginx.conf`):
адрес админской машины у провайдера менялся трижды, все три исторических адреса
оставлены по решению владельца от 2026-10-07.

### Журнал

Категория `infra.sync.security`, сообщение `sync:security:denied`, уровень WARN,
поля: `reason=` (`missing_secret` / `invalid_secret` / `secret_not_configured`),
`source=`, `time=`. Тело запроса и текст ошибки БД не пишутся.

### Порядок выдачи и ротации секрета

1. Админ задаёт значение на боевом хосте: `SYNC_INTERNAL_SECRET` в `do.env`
   (шаблон — `deploy/prod-single-host/do.env.example`) → попадает в контейнер
   `karaoke-web` как env.
2. То же значение задаётся на админской машине: свойство `syncInternalSecret`
   в `Karaoke.properties` (или `Karaoke.syncInternalSecret`).
3. Секреты на обеих сторонах должны совпадать; при расхождении синхронизация
   не проходит (в журнале — `sync:security:denied` с `reason=invalid_secret`).
4. Ротация — смена значения на обеих сторонах; окно неработоспособности — между
   сменой на сервере и клиенте (запросы будут отклоняться, повторов не будет).

## Инварианты

1. Запрос без корректного `X-Internal-Secret` **не выполняет ни одной** SQL-команды.
2. Пустой (или пробельный) настроенный секрет = доступ закрыт всегда (fail-safe).
3. Наружу не возвращается ни текст ошибки БД, ни имена таблиц/колонок.
4. Ответ `403` клиент не повторяет; сетевые ошибки — одна повторная попытка (как раньше).
5. Формат тела запроса, состав и порядок SQL-действий, кодовое слово — без изменений.
6. Транзакционность применения действий не менялась (отдельная задача BR-012).
7. Значение секрета не попадает в репозиторий, в сборку и в журналы.

## Известные ловушки

- **Образец `InternalStemJobController.authorized()` нельзя копировать буквально.**
  Там сравнение `request.getHeader("X-Internal-Secret") == internalSecret`: при незаданном
  секрете (`""`) и запросе с присутствующим, но пустым заголовком сравнение проходит —
  доступ открывается. Поэтому проверка разделена на две ветви: «секрет не настроен» и
  «точное сравнение».
- **`X-Forwarded-For` клиент подделывает** (аудит BR-003): в журнале безопасности адрес
  берётся из `X-Real-IP` (наш nginx) — иначе атакующий подписывает в логе любой
  «источник».
- **Адрес админской машины плавает у провайдера**: пока новый адрес не добавлен в
  `allow`-список nginx, синхронизация с него не проходит (ожидаемое поведение, а не
  регрессия) — компенсируется вторым рубежом (внутренний ключ) и своевременным
  добавлением адреса.
- **Второй вариант раскладки nginx** (`deploy/web-server-deploy/deploy/80to8897`) содержит
  такой же `location /changerecords` без фильтра — под изменение не попал (вне объёма
  спеки 554), требует отдельного решения.
- **Пустое значение секрета на клиенте** не является ошибкой сборки: запрос уйдёт без
  ключа и будет отклонён сервером; в журнале клиента — `sync:remote:secret_missing`.

## Ссылки

- [`specs/554-close-public-sql-endpoint/spec.md`](../../specs/554-close-public-sql-endpoint/spec.md)
- [`specs/554-close-public-sql-endpoint/contracts/internal-secret-contract.md`](../../specs/554-close-public-sql-endpoint/contracts/internal-secret-contract.md)
- [`specs/554-close-public-sql-endpoint/research.md`](../../specs/554-close-public-sql-endpoint/research.md)
- [`knowledge/domains/karaoke-web/components/main-controller.md`](../../knowledge/domains/karaoke-web/components/main-controller.md)
- [`knowledge/domains/karaoke-web/components/internal-stem-job-controller.md`](../../knowledge/domains/karaoke-web/components/internal-stem-job-controller.md)
- [`knowledge/domains/processing/components/two-db-sync.md`](../../knowledge/domains/processing/components/two-db-sync.md)
- [`knowledge/domains/monitoring/components/log-categories.md`](../../knowledge/domains/monitoring/components/log-categories.md)
- [`docs/features/sync-resilience.md`](sync-resilience.md)
- [`specs/336-code-audit/findings/P-35.md`](../../specs/336-code-audit/findings/P-35.md) (находка P-35-01 / BR-011)

## Связанные ADR

- ADR нет (изменение локальное, в рамках существующего sync-контура).
