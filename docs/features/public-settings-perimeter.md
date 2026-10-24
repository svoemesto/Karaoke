# Feature: Периметр публичных настроек и fail-closed проверка капчи

**Spec**: [`specs/559-close-public-properties-captcha/spec.md`](../../specs/559-close-public-properties-captcha/spec.md)
**Status**: active (спека 564, OpenProject #359 + #362, аудит BR-019 + BR-518)
**Created**: 2026-10-24
**Last updated**: 2026-10-24
**Source**: OpenProject #359 — «S1: /api/properties/** доступен анонимно — публичная запись в tbl_public_settings и утечка серверного ключа капчи (BR-019)» и #362 — «S1: проверка капчи fail-open при пустом серверном ключе — регистрация проходит без капчи (BR-518)»
**Feature Key**: `public-settings-perimeter`

## Что делает

Две связанные S1-дыры аудита кода #336 закрываются одной поставкой (спека 564):

1. **#359 (BR-019) — периметр публичных настроек** (`karaoke-web`, префикс `/api/properties/**`):
   - **запись** `POST /api/properties/setproperty` закрывается двумя рубежами — общий секрет в
     заголовке `X-Internal-Secret` (fail-safe) **и** сетевой фильтр nginx по адресу админской
     машины; при отказе — `403`, значение в базе **не меняется**, попытка видна в журнале;
   - **чтение** остаётся, но по белому списку: публичные ключи — анонимно, операционные
     (kill-switch, шаблоны новостей) — только по защищённому каналу, **секретные** (серверный
     ключ капчи) — не выдаются никогда.
2. **#362 (BR-518) — проверка капчи**: серверный ключ переезжает в переменную окружения
   (`CAPTCHA_SERVER_KEY`), читается при старте, окно кеша в 5 минут исчезает; проверка
   становится **fail-closed** и различает четыре исхода; при незаконной конфигурации
   регистрация отклоняется с отдельным кодом `captcha_not_configured`.

## Зачем

Аудит кода #336 (находки P-35-01 / BR-019 и P-35-02 / BR-518, severity **S1**) обнаружил
замкнутую цепочку:

```text
аноним пишет в tbl_public_settings (POST /api/properties/setproperty — авторизации нет)
  → обнуляет/подменяет yandexSmartCaptchaServerKey
    → проверка капчи считается «пройденной» (fail-open: пустой ключ = «ок»)
      → массовая регистрация аккаунтов без капчи
```

Починка только одной половины цепочки не закрывает обход: правка #359 оставляет fail-open
при любой ошибочной конфигурации (ручная правка БД, откат деплоя, уже отравленное
значение), правка #362 оставляет публичную запись в прод-БД (возможность подменить ключ на
«свою» рабочую пару). Поэтому обе половины поставляются вместе (FR-016).

## Как работает

### Запись — `karaoke-web`, `PublicSettingsWebController`

- `POST /api/properties/setproperty` требует заголовок `X-Internal-Secret` со значением
  общего server-to-server секрета.
- Проверка **fail-safe**: сравнение выполняется только при непустом настроенном секрете.
  Пустой секрет = «не настроено» → доступ закрыт всегда, в том числе для запроса с
  присутствующим, но пустым заголовком.
- Отказ → `403`, тело `false` (прежний формат ответа), **ни одной выполненной SQL-команды**,
  запись в журнал `infra.settings.security` (`settings:security:denied`, `reason=`).
- Запись ключа из `SECRET_KEYS` (серверный ключ капчи) отклоняется всегда — его источник
  истины теперь env, а не таблица.
- Успех → `200` + `true` (прежний формат), кеш чтения сбрасывается dirty-флагом, поэтому
  новое значение видно без ожидания TTL.

### Чтение — границы выдачи

| Множество | Ключи | Анонимно | По защищённому каналу |
|---|---|---|---|
| `SECRET_KEYS` | `yandexSmartCaptchaServerKey` | нет | **нет** |
| `PUBLIC_KEYS` | `yandexSmartCaptchaClientKey` | да | да |
| операционные | `newsAutoPublishKillSwitch`, `newsTemplate*`, прочие | нет (пустая строка) | да |

`GET /api/properties/digest` — анонимно только публичные ключи; по защищённому каналу — все
ключи, кроме секретных (тот же снимок, что и раньше, минус серверный секрет капчи).

### Сетевой рубеж — nginx

`deploy/prod-single-host/80to8897`, `location /api/properties/setproperty`:

```nginx
allow 185.26.28.109;
allow 83.137.50.231;
allow 185.26.28.199;
deny all;
```

Список — тот же, что у портов 5433/9000 (`deploy/prod-single-host/nginx.conf`) и у
`location /changerecords`. nginx выбирает location по **наибольшему совпадению префикса**,
поэтому фильтр действует только на путь записи, а чтение (`getproperty`, `digest`) остаётся
доступным и ограничено белым списком на стороне приложения.

### Проверка капчи — `karaoke-web`

- `CaptchaConfigService`: **серверный ключ** — из env `CAPTCHA_SERVER_KEY` (свойство
  `captcha.server-key`), читается при старте, кеша нет; **клиентский ключ** — из
  `tbl_public_settings`, окно кеша 60 секунд (было 5 минут вместе с серверным ключом).
- `YandexCaptchaValidationService.validate()` возвращает один из четырёх исходов:

  | Исход | Когда | Регистрация | Код отказа |
  |---|---|---|---|
  | `PASSED` | внешний сервис подтвердил токен | разрешена | — |
  | `FAILED` | токен пуст или ответ «не ок» | отклонена | `captcha` |
  | `NOT_CONFIGURED` | серверный ключ отсутствует/пуст | отклонена | `captcha_not_configured` |
  | `UNAVAILABLE` | внешний сервис недоступен | отклонена | `captcha` |

- `PublicAuthController.register()` отклоняет регистрацию при любом исходе, кроме `PASSED`;
  формат ответа прежний — `400` + `{"error": …}`.
- Публичный сайт (`karaoke-public/src/views/RegisterView.vue`) показывает сообщение
  «Регистрация временно недоступна. Попробуйте позже.» для кода `captcha_not_configured`.

### Журнал

| Категория | Событие | Уровень | Содержимое |
|---|---|---|---|
| `infra.settings.security` | `settings:security:denied` | WARN | `reason=` (`missing_secret` / `invalid_secret` / `secret_not_configured` / `secret_key_write`), `source=`, `time=` |
| `infra.settings.security` | `settings:security:read-denied` | WARN | `reason=key_not_public`, `key=`, `source=`, `time=` |
| `infra.settings.security` | `settings:security:secret-read-blocked` | WARN | `reason=secret_key`, `key=`, `source=`, `time=` |
| `infra.captcha.config` | `captcha:config:server-key=configured\|not_configured` | INFO | факт наличия ключа (без значения) |
| `infra.captcha.config` | `captcha:config:not_configured` | WARN | факт незаконной конфигурации (без значения ключа) |

Адрес источника — `X-Real-IP` (его выставляет наш nginx) с фолбэком на `request.remoteHost`;
`X-Forwarded-For` не используется (клиент его подделывает, аудит BR-003). Значения настроек и
значение серверного ключа капчи в журнал не пишутся.

### Операционный регламент

Изменение публичной настройки выполняется по защищённому каналу (см.
[`specs/564-impl-properties-captcha/quickstart.md`](../../specs/564-impl-properties-captcha/quickstart.md)):

```bash
# с боевого хоста (nginx не участвует — работает рубеж приложения)
curl -s -X POST "http://localhost:8897/api/properties/setproperty" \
  -H "X-Internal-Secret: $SYNC_INTERNAL_SECRET" \
  -d "key=newsAutoPublishKillSwitch&stringValue=true"

# с админской машины через публичный nginx (работают оба рубежа)
curl -s -X POST "https://sm-karaoke.ru/api/properties/setproperty" \
  -H "X-Internal-Secret: $SYNC_INTERNAL_SECRET" \
  -d "key=newsAutoPublishKillSwitch&stringValue=true"
```

## Инварианты

1. Запрос на изменение настройки без корректного `X-Internal-Secret` **не выполняет ни одной**
   SQL-команды и **не меняет** значение в базе.
2. Пустой (или пробельный) настроенный секрет = доступ закрыт всегда (fail-safe).
3. Серверный ключ капчи не выдаётся **никогда** — ни анонимному клиенту, ни по защищённому
   каналу; его источник истины — env.
4. Ключ из `SECRET_KEYS` нельзя записать в таблицу (403, `secret_key_write`).
5. При незаконной конфигурации проверки капчи регистрация **отклоняется** (fail-closed) с
   кодом `captcha_not_configured`; «пустой ключ = проверка пройдена» невозможно.
6. Ошибка или недоступность внешнего сервиса проверки → отказ регистрации, а не пропуск.
7. Формат успешной записи (`true`/`false`), формат чтения (`String`) и формат дайджеста
   (`{"properties": [...]}`) — прежние.
8. Наружу не возвращаются ни текст ошибки БД, ни имена таблиц, ни значения настроек.
9. Значения секретов не попадают в репозиторий, в сборку и в журналы.

## Известные ловушки

- **Образец `InternalStemJobController.authorized()` нельзя копировать буквально.** Там
  сравнение `request.getHeader("X-Internal-Secret") == internalSecret`: при незаданном секрете
  (`""`) и запросе с присутствующим, но пустым заголовком сравнение проходит — доступ
  открывается. Проверка разделена на две ветви: «секрет не настроен» и «точное сравнение».
- **Открытый `/api/properties/**` — это не «просто ещё один публичный эндпоинт».** В
  `karaoke-web` авторизация сайта делается перехватчиком `SiteAuthInterceptor`, а
  `SecurityConfig` помечает `permitAll()` и `/api/**`, и `anyRequest()` — снимать `permitAll`
  нельзя, это сломало бы публичный сайт. Закрытие сделано отдельной проверкой в контроллере
  плюс сетевым фильтром nginx.
- **Админка `webvue3` ходит в `karaoke-app`, а не в `karaoke-web`.** Её маршрут
  `webvue3/nginx_webvue3.conf` проксирует `/api` в `karaoke-app`; «Настройки сайта» —
  `/api/publicsettings/digest|update` (выбор базы `target=local|remote`). Закрытие
  `/api/properties/**` на `karaoke-web` этот сценарий не затрагивает. Свойства
  `KaraokeProperties` (файловый слой) — тоже другой слой: `/api/properties/setproperty` в
  `karaoke-app` пишет в файл, а не в таблицу.
- **Адрес админской машины плавает у провайдера**: пока новый адрес не добавлен в
  `allow`-список nginx, операция с него не проходит (ожидаемое поведение, а не регрессия) —
  компенсируется вторым рубежом (внутренний ключ) и своевременным добавлением адреса.
- **Ключ капчи в env действует только после рестарта контейнера.** До рестарта сервис
  использует то значение, которое было при старте; «горячей» ротации нет (окно = 0, но
  требуется рестарт). Порядок развёртывания: сначала env, потом рестарт, потом миграция
  таблицы.
- **Пока строка `yandexSmartCaptchaServerKey` не удалена из таблицы**, она остаётся
  мёртвым значением: сервис её не читает и не выдаёт, но инвентаризация (снимок SQL) её
  покажет — это ожидаемо, удаление делает миграция
  `deploy/karaoke-db/51_public_settings_drop_captcha_server_key.sql` (за владельцем).
- **Второй вариант раскладки nginx** (`deploy/web-server-deploy/deploy/80to8897`) — та же
  раскладка без изменений (вне объёма спеки 564, требует отдельного решения владельца).

## Ссылки

- [`specs/559-close-public-properties-captcha/spec.md`](../../specs/559-close-public-properties-captcha/spec.md)
- [`specs/564-impl-properties-captcha/quickstart.md`](../../specs/564-impl-properties-captcha/quickstart.md)
- [`specs/564-impl-properties-captcha/research.md`](../../specs/564-impl-properties-captcha/research.md)
- [`specs/564-impl-properties-captcha/contracts/public-settings-secret-contract.md`](../../specs/564-impl-properties-captcha/contracts/public-settings-secret-contract.md)
- [`specs/554-close-public-sql-endpoint/spec.md`](../../specs/554-close-public-sql-endpoint/spec.md) — образец «сетевой рубеж + общий секрет»
- [`knowledge/domains/karaoke-web/components/public-controllers-6.md`](../../knowledge/domains/karaoke-web/components/public-controllers-6.md)
- [`knowledge/domains/karaoke-web/components/config.md`](../../knowledge/domains/karaoke-web/components/config.md)
- [`knowledge/domains/karaoke-web/components/services-overview.md`](../../knowledge/domains/karaoke-web/components/services-overview.md)
- [`knowledge/domains/monitoring/components/log-categories.md`](../../knowledge/domains/monitoring/components/log-categories.md)
- [`docs/features/internal-secret-sync.md`](internal-secret-sync.md) — тот же контракт заголовка для `POST /changerecords`

## Связанные ADR

- ADR нет (изменение локальное: периметр доступа и место хранения секрета).
- Конвенции: `knowledge/adr/local-0001-karaoke-properties-defaults.md` (секреты только через env),
  `knowledge/adr/local-0006-logging-and-error-handling-karaoke-web.md` (секреты не логируются).
