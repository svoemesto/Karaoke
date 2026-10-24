# Quickstart: проверка 564 — периметр публичных настроек и fail-closed проверка капчи

Документ — руководство по проверке и по операционному регламенту, а не инструкция
реализации. Код живёт в [`tasks.md`](tasks.md).

Спека: [`../559-close-public-properties-captcha/spec.md`](../559-close-public-properties-captcha/spec.md) ·
задачи OpenProject **#359** (BR-019) и **#362** (BR-518).

## Предпосылки

- Репозиторий: `/home/nsa/Karaoke/.worktrees/impl359`, ветка `564-impl-properties-captcha`.
- Gradle с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle` (Hard Gate R-372).
- Локальный стенд не требует БД: проверка идёт через unit-тесты.
- Для проверки nginx-конфига — контейнер `nginx:stable` (локальный, без согласия на
  прод-деплой).
- Переменные окружения, которые появляются у сервиса: `SYNC_INTERNAL_SECRET` (общий
  server-to-server секрет — **уже настроен** после спеки 554) и `CAPTCHA_SERVER_KEY`
  (серверный ключ капчи — **новый**, выдаётся владельцем).

---

## Часть A. Проверка правки

### Проверка 1. Периметр публичных настроек (#359)

```bash
cd /home/nsa/Karaoke/.worktrees/impl359
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test \
  --tests '*PublicSettingsWebControllerTest*'
```

Ожидаемо (15 тестов, все зелёные):

- «запись без заголовка `X-Internal-Secret` → `403` и ни одной SQL-команды» — **зелёный**;
- «запись с неверным секретом → `403`» — **зелёный**;
- «пустой настроенный секрет + пустой заголовок → `403`» — **зелёный** (ловушка образца,
  research D2);
- «запись с корректным заголовком → `200` + `true`, выполнена ровно одна SQL-команда» — **зелёный**;
- «запись секретного ключа капчи → `403`» — **зелёный**;
- «анонимное чтение непубличного ключа → пустая строка; по защищённому каналу → значение» — **зелёный**;
- «чтение секретного ключа не выдаёт значение даже по защищённому каналу» — **зелёный**;
- «дайджест анонимно содержит только публичные ключи; по защищённому каналу — все, кроме
  секретных» — **зелёный**;
- «отказ попадает в журнал `infra.settings.security` без значения настройки» — **зелёный**.

Обратная проверка («до/после»): на коде **до** правки файл тестов не компилируется (нет
`handleSetProperty`/`handleGetProperty`/`SECRET_HEADER`); при частичном откате (снятие только
проверки секрета или только белого списка) тесты падают по существу — ожидается `403`,
а запись выполняется.

### Проверка 2. Fail-closed проверка капчи (#362)

```bash
cd /home/nsa/Karaoke/.worktrees/impl359
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test \
  --tests '*YandexCaptchaValidationServiceTest*' --tests '*PublicAuthControllerCaptchaTest*'
```

Ожидаемо (6 + 4 теста, все зелёные):

- «пустой серверный ключ даёт `NOT_CONFIGURED` а не `PASSED`» — **зелёный**;
- «пустой токен даёт `FAILED`» — **зелёный**;
- «недоступность внешнего сервиса даёт `UNAVAILABLE` а не `PASSED`» — **зелёный**;
- «регистрация при незаконной конфигурации отклоняется с кодом `captcha_not_configured`» —
  **зелёный**;
- «регистрация без токена капчи отклоняется с прежним кодом `captcha`» — **зелёный**;
- «серверный ключ не попадает в журнал» — **зелёный**.

### Проверка 3. Сетевой рубеж (nginx)

```bash
cd /home/nsa/Karaoke/.worktrees/impl359
# синтаксическая проверка изменённого конфига в nginx-контейнере.
# Конфиг требует сертификат — для проверки подкладываем временный самоподписанный.
mkdir -p .tmp/nginx-certs
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=sm-karaoke.ru" \
  -keyout .tmp/nginx-certs/www.sm-karaoke.ru.key \
  -out .tmp/nginx-certs/www.sm-karaoke.ru.crt
DOCKER_CONFIG=/home/nsa/Karaoke/.docker docker run --rm \
  -v "$PWD/deploy/prod-single-host/80to8897:/etc/nginx/conf.d/default.conf:ro" \
  -v "$PWD/.tmp/nginx-certs:/etc/keys:ro" \
  nginx:stable nginx -t
rm -rf .tmp
```

Ожидаемо: `syntax is ok` / `test is successful`.

Ручная проверка логики фильтра (на стенде, не на проде): запрос с адреса не из списка → `403`
от nginx; запрос с адреса из списка → доходит до сервиса (и дальше отсекается заголовком
`X-Internal-Secret`, если его нет).

### Проверка 4. Полный набор проверок проекта

```bash
cd /home/nsa/Karaoke/.worktrees/impl359
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:compileKotlin :karaoke-app:compileKotlin --parallel
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test :karaoke-app:test
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ktlintCheck
bash tools/check-subagent-isolation.sh
bash tools/check-knowledge-structure.sh
python3 tools/check-doc-references.py
bash tools/check-feature-doc.sh docs/features/*.md
```

Ожидаемо: все проверки проходят (7/7 по CI + локальные guard'ы).

### Проверка 5. Отсутствие секретов в изменениях

```bash
cd /home/nsa/Karaoke/.worktrees/impl359
git diff master...HEAD | grep -inE 'secret\s*=\s*["'"'"'][0-9a-f]{16,}' || echo "OK: значение секрета не попало в diff"
git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$' || echo "OK: секрет-файлы не отслеживаются"
```

Ожидаемо: два `OK`. В изменениях есть только **имена** параметров
(`SYNC_INTERNAL_SECRET`, `CAPTCHA_SERVER_KEY`) и шаблон `CHANGE_ME`.

---

## Часть B. Операционный регламент (за владельцем)

### B1. Инвентаризация и чистка `tbl_public_settings`

**Порядок (решение владельца от 2026-10-24, п. 4): сначала инвентаризация, потом закрытие
записи.** Ниже — шаги 1–4; выполняются **до** развёртывания правки и миграции.

**Шаг 1. Снимок всех строк таблицы** (на боевом хосте, psql в контейнер `karaoke-db`):

```bash
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "SELECT key, value, description, last_update FROM tbl_public_settings ORDER BY key;"
```

Снимок сохранить в отчёт (файл с датой). **Именно psql, а не HTTP-API:** после правки
значение серверного ключа капчи через API не выдаётся намеренно.

**Шаг 2. Проверка ключа проверки капчи и переключателей:**

```bash
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "SELECT key, value FROM tbl_public_settings
    WHERE key IN ('yandexSmartCaptchaServerKey','yandexSmartCaptchaClientKey','newsAutoPublishKillSwitch');"
```

Критерии «подозрительного» значения (их определяет владелец):

| Ключ | Ожидаемое состояние | Признак инцидента |
|---|---|---|
| `yandexSmartCaptchaServerKey` | соответствует выданному Yandex-ключу | значение отличается от выданного / пусто при заявленной настройке капчи |
| `yandexSmartCaptchaClientKey` | соответствует выданному Yandex-клиентскому ключу | то же |
| `newsAutoPublishKillSwitch` | `true`/`false`/пусто | значение, которое владелец не выставлял |
| любые другие ключи | известны владельцу (`newsTemplate*`) | ключ не в списке известных → вероятно добавлен анонимно |

**Шаг 3. Удаление неопознанных ключей** (добавленных анонимно) — по решению владельца:

```bash
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "DELETE FROM tbl_public_settings WHERE key IN ('<неопознанный-ключ-1>', '<неопознанный-ключ-2>');"
```

**Шаг 4. Ротация ключа капчи** (обязательна, если на шаге 2 найдено подозрительное
значение — это инцидент безопасности): новая пара ключей в панели Yandex SmartCaptcha,
старое значение аннулировано.

**Шаг 5. Удаление строки с серверным ключом** — миграцией
`deploy/karaoke-db/51_public_settings_drop_captcha_server_key.sql`
(`DELETE FROM tbl_public_settings WHERE key = 'yandexSmartCaptchaServerKey';`).
Выполняется **после** шага 1 и **до** переключения сервиса на env-ключ.

### B2. Переключение настройки одной командой с сервера

Прежний регламент (`specs/124-news-flags-backfill/quickstart.md`, curl без секрета)
**больше не работает** — запись закрыта. Рабочий вариант (тот же защищённый канал):

```bash
# С боевого хоста (nginx не участвует, работает рубеж приложения):
export SYNC_INTERNAL_SECRET='<значение из do.env на боевом хосте>'
curl -s -X POST "http://localhost:8897/api/properties/setproperty" \
  -H "X-Internal-Secret: $SYNC_INTERNAL_SECRET" \
  -d "key=newsAutoPublishKillSwitch&stringValue=true"

# Проверка значения (тот же канал):
curl -s -X POST "http://localhost:8897/api/properties/getproperty" \
  -H "X-Internal-Secret: $SYNC_INTERNAL_SECRET" \
  -d "key=newsAutoPublishKillSwitch"
# Ожидаемо: true
```

С админской машины — через публичный nginx (работают оба рубежа):

```bash
curl -s -X POST "https://sm-karaoke.ru/api/properties/setproperty" \
  -H "X-Internal-Secret: $SYNC_INTERNAL_SECRET" \
  -d "key=newsAutoPublishKillSwitch&stringValue=true"
```

Административная панель (`webvue3` → «Настройки сайта») работает как раньше: она ходит в
`karaoke-app` (`/api/publicsettings/digest|update`, `target=local|remote`) — другой сервис
и другой путь, правкой не затронута.

### B3. Порядок развёртывания (решает владелец)

Порядок важен: при неверном порядке регистрация на сайте временно отклоняется
(ожидаемое поведение, не регрессия) — и наоборот, при неверном порядке можно оставить
работающую дыру.

1. **Инвентаризация и чистка** (B1, шаги 1–3) — снимок, проверка ключа и переключателей,
   удаление неопознанных ключей.
2. **Ротация ключа капчи** (если найдено подозрительное значение) и выдача нового ключа:
   `CAPTCHA_SERVER_KEY=<новое значение>` в `do.env` на боевом хосте → env контейнера
   `karaoke-web`. Значение — только на хосте, в репозиторий не попадает.
3. **Миграция** `51_public_settings_drop_captcha_server_key.sql` — удаление строки с
   серверным ключом из таблицы.
4. **Деплой `karaoke-web`** (новый образ: серверный ключ из env, проверка fail-closed) и
   **рестарт контейнера** — только тогда сервис подхватит новое значение ключа.
5. **Обновление nginx на боевом хосте** (`/etc/nginx/sites-enabled/80to8897` ←
   `deploy/prod-single-host/80to8897`) + `nginx -t` + `nginx -s reload`.
6. **Проверка** (часть C).

> Если на шаге 2 значение ключа **не** задано, регистрация на сайте будет отклоняться с
> кодом `captcha_not_configured` — это ожидаемое fail-closed-поведение, а не поломка.

### B4. Что делать, если что-то пошло не так

| Симптом | Причина | Действие |
|---|---|---|
| `403` на запись с админской машины, секрет верный | новый адрес машины не в `allow`-списке nginx | добавить адрес в `location /api/properties/setproperty` и перезагрузить nginx |
| `403` на запись, `reason=invalid_secret` | значения секрета на хосте и в контейнере разошлись | сверить `SYNC_INTERNAL_SECRET` в `do.env` и в `docker inspect karaoke-web` |
| `403` на запись, `reason=secret_not_configured` | секрет не задан в env | задать `SYNC_INTERNAL_SECRET` в `do.env`, рестарт контейнера |
| регистрация отклоняется с `captcha_not_configured` | `CAPTCHA_SERVER_KEY` не задан или не доехал | задать env, рестарт контейнера; в журнале — `captcha:config:not_configured` |
| регистрация отклоняется с `captcha` при заданном ключе | внешний сервис проверки недоступен (сеть/таймаут) | в журнале — `captcha:validation:unavailable`; проверить прокси `/smartcaptcha/` (nginx `location /smartcaptcha/`) |

---

## Часть C. Что проверить после развёртывания на боевом контуре

| Сценарий | Ожидание |
|---|---|
| `POST https://sm-karaoke.ru/api/properties/setproperty` с внешнего адреса | `403` (сетевой рубеж nginx) |
| то же с админской машины, но без заголовка `X-Internal-Secret` | `403` (рубеж приложения), в журнале — `settings:security:denied reason=missing_secret` |
| то же с админской машины и корректным заголовком | `200`, тело `true`, значение изменено |
| `GET https://sm-karaoke.ru/api/properties/digest` без заголовка | только публичные ключи; серверного ключа капчи нет |
| `GET .../api/properties/getproperty?key=yandexSmartCaptchaServerKey` | пустая строка (в любом режиме) |
| `GET .../api/properties/getproperty?key=newsAutoPublishKillSwitch` без заголовка | пустая строка; с заголовком — текущее значение |
| регистрация на сайте с действующей капчей | проходит (аккаунт создан) |
| регистрация на сайте без токена капчи | `400`, код `captcha` |
| `POST .../api/public/auth/register` при незаданном `CAPTCHA_SERVER_KEY` | `400`, код `captcha_not_configured`; в журнале — `captcha:config:not_configured` |

Наблюдение после деплоя: 7 суток (SC-001…SC-011) — ни одной успешной анонимной записи,
ни одной выдачи серверного секрета, ни одной регистрации без проверки, все отказы видны в
журнале в течение 60 секунд.
