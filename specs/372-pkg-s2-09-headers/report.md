# Отчёт по #372 — PKG-S2-09 «deploy: SEC» (BR-558)

> **Пакет**: PKG-S2-09 · **волна C** · **severity S2** · **модуль**: `deploy`
> **Ветка**: `576-pkg-s2-09-headers` (worktree `.worktrees/waveC-headers`, от master `4b5f16a7`)
> **Аудит**: #336, порция **P-72** (`specs/336-code-audit/findings/P-72.md`, запись
> P-72-06), реестр `specs/336-code-audit/registry.csv`: BR-558 →
> `work_package=372`, файл `deploy/prod-single-host/80to8897:1–5`.
> **Дата**: 2026-10-24.
> **Объём**: 1 прод-конфиг nginx + guard + документация; код приложения не менялся.

## Что было

`deploy/prod-single-host/80to8897` — host-nginx публичного сайта `sm-karaoke.ru`
(TLS-терминация 443 + редирект 80). В файле **не было ни одного** защитного
заголовка: `X-Frame-Options` / `Content-Security-Policy` /
`X-Content-Type-Options` / `Strict-Transport-Security` / `Referrer-Policy`.

**ДО** (файл из `HEAD` = `4b5f16a7`):

```
$ git grep -l "X-Frame-Options" HEAD -- deploy/ | wc -l
0
$ git show HEAD:deploy/prod-single-host/80to8897 | grep -n "add_header"
20:    add_header Cache-Control "public, max-age=86400";
21:    add_header ETag $upstream_http_etag;
22:    add_header Last-Modified $upstream_http_last_modified;
33:    add_header Cache-Control "public, max-age=300";
177:    add_header Cache-Control "public, max-age=86400";
178:    add_header ETag $upstream_http_etag;
179:    add_header Last-Modified $upstream_http_last_modified;
```

Все существующие `add_header` — только про HTTP-кэш MinIO. Заголовков
безопасности ноль.

**Путь атаки (из карточки P-72-06).** Внешний пользователь →
`https://sm-karaoke.ru/` → host-nginx → любой `location` отдаёт ответ без
защитных заголовков → злоумышленник встраивает страницу сайта (регистрация,
оплата ЮKassa) в iframe своего домена и перехватывает клики (clickjacking);
для HSTS — первый заход на `http://sm-karaoke.ru/...` (редирект 301) остаётся
без принудительного TLS.

**Риск для прода**: публичный сайт с регистрацией и оплатой; `server_tokens off;`
в `nginx.conf` закомментирован (это BR-544, отдельная задача #371 — здесь не
трогали).

## Что стало

### 1. `deploy/prod-single-host/80to8897` — заголовки безопасности

Добавлены на уровне **server-блоков** (не `http{}` в `nginx.conf` — этот файл
меняет параллельный пакет #371, BR-544/BR-545; и `80to8897` — единственный
файл, описывающий публичный сайт):

```nginx
# server { listen 443 ssl; ... }
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Strict-Transport-Security "max-age=31536000" always;

# server { listen 80; ... } — тот же набор без HSTS
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
```

**Ключевая тонкость — наследование `add_header` в nginx.** Директивы
`add_header` наследуются с верхнего уровня (`http{}` → `server{}` →
`location{}`) **только если в самом блоке нет ни одной своей `add_header`**.
У `location /minio/` и `location @minio_404` свои заголовки есть
(`Cache-Control`/`ETag`/`Last-Modified`) → они теряют серверный набор целиком.
Поэтому в них нужные заголовки объявлены **явно, заново**:

```nginx
location /minio/ {
  ...
  add_header X-Frame-Options "SAMEORIGIN" always;
  add_header Referrer-Policy "strict-origin-when-cross-origin" always;
  # security-headers-exempt: X-Content-Type-Options (причина — ниже)
  add_header Cache-Control "public, max-age=86400";
  ...
}
```

**Осознанное исключение: `nosniff` НЕ применяется к MinIO-контуру**
(`/minio/` и `@minio_404`, в обоих server-блоках). Причина — проверенный по
коду факт, а не опасение:

- объекты в MinIO кладутся **без** `Content-Type`:
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageApiClient.kt:255`
  — `PutObjectArgs` собирается **без** `.contentType(...)` → MinIO отдаёт объект
  как `application/octet-stream`;
- эти файлы грузит браузер публичного сайта напрямую:
  `karaoke-web/.../dto/AuthorTilePublicDto.kt:69` — `authorPictureUrl = "/minio/$BUCKET/$encoded"`,
  плюс обложки альбомов и превью;
- браузер с `nosniff` + `application/octet-stream` на `<img>`/`<audio>` может
  отказаться показывать ресурс → битые картинки автора и обложек.

`X-Frame-Options` и `Referrer-Policy` для бинарных файлов безопасны — в
`/minio/` оставлены. Исключение оформлено **маркером в самом конфиге** (виден
на ревью): `# security-headers-exempt: X-Content-Type-Options (причина)`.

**Прочие решения (осознанные, с обоснованием):**

| Решение | Почему |
|---|---|
| `X-Frame-Options: SAMEORIGIN`, а не `DENY` | публичный SPA встраивает сам себя в iframe (`/player/:id`, `karaoke-public/src/views/PlaylistEditView.vue:27`) — это same-origin |
| `HSTS` без `includeSubDomains` | поддомены домена не проверялись; решение «includeSubDomains — только после проверки, что все поддомены на HTTPS» за владельцем. `www.sm-karaoke.ru` обслуживается тем же server-блоком и получает HSTS отдельно (политика HSTS — per-host). Порт 5443 (registry) — тоже только TLS, HSTS его не ломает |
| `HSTS` нет в server-блоке на 80 | браузер игнорирует HSTS, полученный по небезопасному каналу (RFC 6797 §7.2) — заголовок был бы вводящим в заблуждение |
| `nosniff` не дублируется в location'ах без своих `add_header` | наследуется от server-блока |
| полный `Content-Security-Policy` не добавляем | требует инвентаря inline-скриптов, внешних API (ЮKassa, Яндекс SmartCaptcha) и стилей — риск сломать SPA и оплату; `frame-ancestors` перекрывается `X-Frame-Options` |
| `X-XSS-Protection` не добавляем | устаревший XSS-аудитор браузеров, полезной защиты не даёт |
| `Permissions-Policy` не добавляем | требует инвентаря фич (микрофон/камера/геолокация) — может отключить используемую возможность |
| `server_tokens off;` — не трогали | это BR-544 (`nginx.conf:74`), отдельная задача #371 |

### 2. `tools/check-security-headers.py` — guard (новый)

Статический анализ конфига: собственный мини-парсер nginx-синтаксиса строит
дерево `server{}`/`location{}` и проверяет:

1. каждый server-блок объявляет свой набор заголовков, все с `always`;
2. значения допустимы (`SAMEORIGIN`/`DENY`, `nosniff`, известные значения
   `Referrer-Policy`, `max-age=` в HSTS);
3. `Strict-Transport-Security` не объявлен в server без `ssl`;
4. **ни один location не теряет заголовки молча**: location с собственными
   `add_header` обязан объявить нужные заголовки сам либо пометить исключение
   маркером `# security-headers-exempt: <Header> (причина)`;
5. исключать можно **только** `X-Content-Type-Options` и **только** с
   указанием причины; `X-Frame-Options`/`Referrer-Policy` — нельзя.

Подключён в двух местах: pre-commit-хук `security-headers-guard`
(`.pre-commit-config.yaml`) и CI-джоб `security-headers-guard`
(`.github/workflows/lint.yml`).

### 3. SSoT и per-feature документ

- `knowledge/guidelines/security-headers.md` (**новый**) — регламент: набор и
  значения заголовков, правило наследования `add_header`, исключение для
  MinIO-контура, что осознанно не делаем, enforcement, порядок деплоя;
- `knowledge/guidelines/README.md` — строка в индекс guidelines;
- `knowledge/domains/storage/domain.md` — «Решение 6» (почему `/minio/` без
  `nosniff` + инвариант на будущее) и запись в Changelog;
- `docs/features/security-headers.md` (**новый**) — per-feature документ
  (FR-009: 6 обязательных секций + Status/Feature Key).

## Проверки «до/после» (факты с диска)

### Guard: ДО — падает на конфиге до правки

```
$ git show HEAD:deploy/prod-single-host/80to8897 > .scratch/audit372/80to8897.before
$ python3 tools/check-security-headers.py .scratch/audit372/80to8897.before
FAIL: заголовки безопасности в nginx-конфиге:
  - …80to8897.before:1: server-блок «server» не объявляет заголовок безопасности «X-Frame-Options»
  - …80to8897.before:1: server-блок «server» не объявляет заголовок безопасности «X-Content-Type-Options»
  - …80to8897.before:1: server-блок «server» не объявляет заголовок безопасности «Referrer-Policy»
  - …80to8897.before:1: server-блок «server» не объявляет заголовок безопасности «Strict-Transport-Security»
  - …80to8897.before:6: location «location /minio/» имеет свои add_header → не наследует серверные заголовки, но «X-Frame-Options» не объявлен. …
  - …80to8897.before:32: location «location @minio_404» имеет свои add_header → не наследует серверные заголовки, но «X-Frame-Options» не объявлен. …
  - …80to8897.before:167: server-блок «server» не объявляет заголовок безопасности «X-Frame-Options»
  … (всего 20 нарушений)
EXIT=1
```

### Guard: ПОСЛЕ — зелёный

```
$ python3 tools/check-security-headers.py
OK [deploy/prod-single-host/80to8897]: заголовки безопасности на месте
EXIT=0
```

### Мутационная проверка guard'а (что он реально ловит)

| Мутация | Результат |
|---|---|
| убрать `always` у `X-Frame-Options` | exit 1 — «объявлен без флага «always»» |
| убрать `X-Frame-Options` из `/minio/` (443) | exit 1 — «location имеет свои add_header → не наследует серверные заголовки» |
| убрать маркер исключения в `/minio/` | exit 1 — «X-Content-Type-Options не объявлен» |
| добавить `HSTS` в HTTP-сервер (порт 80) | exit 1 — «в server без ssl — по HTTP браузер его игнорирует» |
| подменить `nosniff` на `sniff` | exit 1 — «недопустимое значение» |
| убрать `HSTS` из 443-сервера | exit 1 — «не объявляет заголовок безопасности «Strict-Transport-Security»» |
| исключить `X-Frame-Options` маркером | exit 1 — «исключать его нельзя» |
| исключение без причины | exit 1 — «без причины — укажите её в скобках после имени заголовка» |

### `nginx -t` — контейнер `nginx:stable`, временный самоподписанный сертификат

```
$ openssl req -x509 -nodes -newkey rsa:2048 -days 2 \
    -keyout .scratch/audit372/nginx-t/keys/www.sm-karaoke.ru.key \
    -out .scratch/audit372/nginx-t/keys/www.sm-karaoke.ru.crt -subj "/CN=sm-karaoke.ru"
$ docker run --rm -v "$PWD/.scratch/audit372/nginx-t:/tmp/nginx-test:ro" \
    -v "$PWD/.scratch/audit372/nginx-t/keys:/etc/keys:ro" \
    nginx:stable nginx -t -c /tmp/nginx-test/nginx.conf
nginx: the configuration file /tmp/nginx-test/nginx.conf syntax is ok
nginx: configuration file /tmp/nginx-test/nginx.conf test is successful
```

(nginx 1.30.3 из образа `nginx:stable`; в тестовой копии `nginx.conf` include'ы
перенаправлены на каталог с конфигом, сертификат — временный самоподписанный;
прод-хоста и прод-сертификата тест не касался.)

### Фактические заголовки в ответах (контейнер с изменённым конфигом)

**ДО** (конфиг из `HEAD`):

```
$ curl -sSI -k -H "Host: sm-karaoke.ru" https://127.0.0.1:18444/
HTTP/1.1 502 Bad Gateway
Server: nginx/1.30.3
Content-Type: text/html
Content-Length: 157
Connection: keep-alive
```

**ПОСЛЕ** (изменённый конфиг):

```
$ curl -sSI -k -H "Host: sm-karaoke.ru" https://127.0.0.1:18443/
HTTP/1.1 502 Bad Gateway
Server: nginx/1.30.3
Content-Type: text/html
Content-Length: 157
Connection: keep-alive
X-Frame-Options: SAMEORIGIN
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
Strict-Transport-Security: max-age=31536000

$ curl -sSI -k -H "Host: sm-karaoke.ru" https://127.0.0.1:18443/minio/karaoke/pic.jpg
HTTP/1.1 502 Bad Gateway
…
X-Frame-Options: SAMEORIGIN
Referrer-Policy: strict-origin-when-cross-origin

$ curl -sSI -H "Host: sm-karaoke.ru" http://127.0.0.1:18080/
HTTP/1.1 301 Moved Permanently
…
Location: https://sm-karaoke.ru/
X-Frame-Options: SAMEORIGIN
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
```

Три вывода из этих ответов:

1. заголовки приходят **на ошибочном ответе 502** — это работает флаг `always`
   (без него на 4xx/5xx заголовков не было бы, и защита обходилась бы запросом
   несуществующего URL);
2. `/minio/` отдаёт `X-Frame-Options` и `Referrer-Policy`, но **без** `nosniff` —
   исключение сработало ровно как задумано;
3. на порту 80 HSTS нет, остальные три заголовка есть.

(Upstream'ы в тесте не подняты — отсюда 502; для проверки заголовков это
достаточно, поведение живого сайта не менялось.)

### Проверки проекта (до коммита)

| Проверка | Результат |
|---|---|
| `python3 tools/check-security-headers.py` | **OK** — заголовки на месте |
| `python3 tools/check-security-headers.py <конфиг из HEAD>` | **FAIL, exit 1** — 20 нарушений (регресс ловится) |
| `nginx -t` (контейнер `nginx:stable`) | **PASS** — syntax is ok / test is successful |
| `bash tools/check-knowledge-structure.sh` | **OK** — 9/9 проверок |
| `python3 tools/check-doc-references.py` | **OK** — 4116 ссылок, 2245 путей, 306 эндпоинтов (whitelist: 51) |
| `bash tools/check-knowledge-cross-links.sh` | **OK** — 686 cross-links валидны |
| `python3 tools/lint-knowledge.py` | **OK** — «All documentation passes architectural style and structural checks» |
| `bash tools/check-feature-doc.sh docs/features/security-headers.md` | **OK** — 6 обязательных секций на месте |
| `python3 tools/check-ssot-impact.py` | **INFO: no significant changes** — правил `.ssot-map.yml` на `deploy/**` нет; `knowledge/` обновлён осознанно |
| `bash tools/check-subagent-isolation.sh` | **OK** (см. ниже) |
| `pre-commit run --all-files` | **PASS** — все always-run guard'ы зелёные |

## Knowledge-first (что прочитано до правки)

| Шаг | Что | Релевантное |
|---|---|---|
| 1 | `AGENTS.md` (v3.5.0), `knowledge/README.md`, `knowledge/domains/README.md`, `knowledge/guidelines/README.md` | hard-gates (OpenProject-workflow, git-CI-gate, SSoT, subagent isolation) |
| 2 | grep по `knowledge/`: `nginx` (30 совпадений), `add_header\|X-Frame\|Content-Security\|Strict-Transport\|X-Content-Type\|security header\|HSTS` (0 совпадений — правила не было) | `guidelines/architecture-conventions.md` — R-04 (`nginx:stable`), строка «Nginx conventions — production headers»; `domains/storage/components/storage-api-client.md` — живой конфиг nginx, таймауты |
| 3 | `knowledge/domains/storage/domain.md` + компоненты (`storage-api-client.md`, `karaoke-storage-service.md`, `storage-flow.md`) | nginx path-proxy `/minio/`, бакеты, публичные/приватные политики, `minio-proxy` во внутреннем обращении |
| 4 | `knowledge/adr/` — `local-0003-shared-minio-image-cache.md` (кеш картинок + TTL), `local-0001` (никаких реальных секретов/prod-IP), `0004-karaoke-app-admin-only.md` | контекст MinIO-контура |
| 5 | `codegraph`/grep по коду | `StorageApiClient.kt:255` (нет `contentType` → `application/octet-stream`), `AuthorTilePublicDto.kt:69` (`/minio/...` в браузере), `PlaylistEditView.vue:27` (iframe same-origin) |

## Что осталось за владельцем

1. **Скопировать конфиг на прод и сделать reload.** Файл в репозитории не равен
   файлу на хосте. После merge:

   ```bash
   scp deploy/prod-single-host/80to8897 root@188.127.240.124:/etc/nginx/sites-enabled/80to8897
   ssh root@188.127.240.124 'nginx -t && systemctl reload nginx'
   ```

   Именно **reload**, не restart (не рвём активные соединения — тот же приём
   описан в `deploy/prod-single-host/5443to5000`). Деплой и reload в этом
   пакете **не выполнялись** (жёсткий запрет задания).
2. **Решение по `includeSubDomains` в HSTS.** Сейчас HSTS = `max-age=31536000`
   без `includeSubDomains`. Если владелец проверит, что все поддомены
   `sm-karaoke.ru` работают по HTTPS, — можно добавить `includeSubDomains`
   (правка в `deploy/prod-single-host/80to8897`, guard это пропустит).
3. **При желании — уменьшить `max-age` на время раскатки** (например, 86400 →
   31536000 после недели наблюдения). Текущее значение — как в карточке аудита
   («max-age=31536000»), риск ограничен тем, что сайт и так живёт только по
   HTTPS, а сертификат прод-хоста контролируется `tools/check-cert-expiry.sh`.
4. **Снять исключение `nosniff` для MinIO-контура**, когда загрузка в MinIO
   начнёт задавать корректный `Content-Type` (`image/jpeg`, `audio/mpeg`).
   Это правка кода загрузки (`StorageApiClient.kt`) — другой пакет/модуль.
5. **Реестр аудита не трогали.** `specs/336-code-audit/registry.csv` и
   `specs/336-code-audit/findings/P-72.md` (статус P-72-06) ведёт лид
   (прецедент волны B: «пакеты не трогали общий registry.csv — собирает лид»).
   Статус BR-558 → `исправлено` проставляет лид.
6. **Наблюдение (не входило в BR-558, не исправлено).** В server-блоке на
   порту 80 `location /minio/` ссылается на `error_page 404 = @minio_404;`, но
   именованный location `@minio_404` определён только в 443-сервере. В nginx
   именованные location'ы ищутся **в том же server** → на 80 ветка 404
   отдаёт стандартную страницу nginx вместо кешированной 404-ветки (на
   публичные файлы это влияет только на код ответа и кеш, не на контент).
   Правка — отдельная находка.

## Ветка и коммиты

- Ветка: `576-pkg-s2-09-headers` (worktree `/home/nsa/Karaoke/.worktrees/waveC-headers`,
  от master `4b5f16a7`). Коммит в master не делался, push/PR — не выполнялись.
- Коммиты ветки (2):
  - `85eec3fc` — `fix(576): заголовки безопасности в прод-nginx sm-karaoke.ru + guard (BR-558)`
    (конфиг `80to8897`, `tools/check-security-headers.py`, `.pre-commit-config.yaml`,
    `.github/workflows/lint.yml`);
  - второй коммит (tip ветки) — `docs(576): SSoT-регламент заголовков безопасности,
    per-feature документ и отчёт #372` (`knowledge/guidelines/security-headers.md`,
    `knowledge/guidelines/README.md`, `knowledge/domains/storage/domain.md`,
    `docs/features/security-headers.md`, `specs/372-pkg-s2-09-headers/report.md`).
    Хэш второго коммита здесь не приводится: отчёт лежит внутри него самого,
    а amend меняет хэш (актуальный — `git log -2` по ветке).
- Изменённые файлы (9):
  - `deploy/prod-single-host/80to8897` — заголовки безопасности;
  - `tools/check-security-headers.py` — **новый**, guard;
  - `.pre-commit-config.yaml` — хук `security-headers-guard`;
  - `.github/workflows/lint.yml` — CI-джоб `security-headers-guard`;
  - `knowledge/guidelines/security-headers.md` — **новый**, регламент (SSoT);
  - `knowledge/guidelines/README.md` — строка в индекс;
  - `knowledge/domains/storage/domain.md` — «Решение 6» + Changelog;
  - `docs/features/security-headers.md` — **новый**, per-feature документ (FR-009);
  - `specs/372-pkg-s2-09-headers/report.md` — этот отчёт.
- Трекер: `claim-issue 372` → `add-comment 372` (этот отчёт) → `mark-review 372`.
