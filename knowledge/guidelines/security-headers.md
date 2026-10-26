# Заголовки безопасности в прод-nginx (host-nginx публичного сайта)

> **Статус**: действует. Введён 2026-10-24 (аудит #336, BR-558, OpenProject #372).
> **Область**: `deploy/prod-single-host/80to8897` — host-nginx сайта
> `sm-karaoke.ru` (TLS-терминация, 443 + 80). На проде файл лежит в
> `/etc/nginx/sites-enabled/80to8897`.
> **Enforcement**: `tools/check-security-headers.py` (pre-commit + CI-джоб
> `security-headers-guard` в `.github/workflows/lint.yml`).

## Rule

Публичный сайт **обязан** отдавать заголовки безопасности. Набор и значения:

| Заголовок | Значение | Где | Зачем |
| --- | --- | --- | --- |
| `X-Frame-Options` | `SAMEORIGIN` | все ответы сайта | clickjacking: страницу с регистрацией и оплатой (ЮKassa) нельзя встроить в iframe чужого домена |
| `Content-Security-Policy` | `frame-ancestors 'self'` | все ответы сайта | современный эквивалент `X-Frame-Options`: запрет встраивания в iframe чужого origin. Узкая директива — не полный CSP: скрипты, стили и подключения она не ограничивает |
| `X-Content-Type-Options` | `nosniff` | все ответы, кроме MinIO-контура | браузер не угадывает тип ответа (MIME-sniffing → загрузка «картинки» как скрипта) |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | все ответы сайта | в `Referer` наружу уходит только origin, без идентификаторов песен/альбомов в пути |
| `Strict-Transport-Security` | `max-age=31536000` | **только** TLS-сервер (443) | первый запрос к сайту не может быть перехвачен/редиректнут на http |

Дополнительно:

- каждый заголовок объявляется с флагом **`always`** — иначе он не попадёт
  в ошибочные ответы (4xx/5xx), и защита обходится тривиально («открой
  несуществующий URL — и заголовков нет»);
- `X-Frame-Options: SAMEORIGIN`, а не `DENY` — сайт встраивает сам себя в
  iframe (плеер `/player/:id` в `karaoke-public/src/views/PlaylistEditView.vue`),
  это same-origin;
- `Strict-Transport-Security` — **без `includeSubDomains`**: поддомены домена
  не проверялись, а решение «includeSubDomains только после проверки, что все
  поддомены работают по HTTPS» — за владельцем. `www.sm-karaoke.ru`
  обслуживается тем же server-блоком и получает HSTS отдельно (политика HSTS
  действует на **хост**, а не на путь).

## Где объявлять: server{} и наследование `add_header`

Заголовки объявляются на уровне **server-блока** файла `80to8897` (а не в
`http{}` файла `nginx.conf`): `nginx.conf` — общий конфиг хоста, а `80to8897` —
единственный, который описывает публичный сайт; правило живёт рядом с
location'ами, которых касается.

**Критично — правило наследования nginx:** директивы `add_header`
наследуются с предыдущего уровня (`http{}` → `server{}` → `location{}`)
**только если в самом блоке нет ни одной своей `add_header`**. Любой
`location` со своими `add_header` **теряет весь серверный набор целиком**.

В `80to8897` таких location'ов два (в каждом server-блоке): `/minio/` и
`@minio_404` — у них свои `Cache-Control`/`ETag`/`Last-Modified`. Поэтому
нужные заголовки в них объявлены **явно, заново**:

```nginx
location /minio/ {
  expires 24h;
  add_header X-Frame-Options "SAMEORIGIN" always;
  add_header Referrer-Policy "strict-origin-when-cross-origin" always;
  # security-headers-exempt: X-Content-Type-Options (причина)
  add_header Cache-Control "public, max-age=86400";
  ...
}
```

`HSTS` в location'ах не дублируется: политика HSTS — per-host, её достаточно
прислать с любого ответа сайта; отсутствие HSTS на ответе `/minio/` ничего не
меняет для браузера.

## Исключение: `nosniff` не применяется к MinIO-контуру

`X-Content-Type-Options: nosniff` **не применяется** к `location /minio/` и
`location @minio_404`. Причина — фактический MIME-тип ответа:

- объекты в MinIO кладутся **без** `Content-Type`: в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageApiClient.kt`
  `PutObjectArgs` собирается без `.contentType(...)` → MinIO отдаёт объект как
  `application/octet-stream`;
- публичный сайт грузит эти файлы прямо в браузер
  (`authorPictureUrl = "/minio/$BUCKET/$encoded"` в
  `karaoke-web/.../dto/AuthorTilePublicDto.kt`, обложки альбомов, превью);
- браузер, получив `nosniff` + `application/octet-stream` на `<img>`/`<audio>`,
  может отказаться показывать ресурс (правило «не применять nosniff к
  ресурсам с неверным MIME»).

`X-Frame-Options` и `Referrer-Policy` для бинарных файлов безопасны — они в
`/minio/` оставлены.

Исключение оформляется **маркером в самом конфиге** (виден на ревью, а не
зашит в guard):

```nginx
# security-headers-exempt: X-Content-Type-Options (объекты MinIO хранятся без
# Content-Type → отдаются как application/octet-stream; nosniff может
# заблокировать показ картинки автора/обложки альбома в браузере)
```

Guard разрешает исключать **только** `X-Content-Type-Options` и **только** с
указанием причины. `X-Frame-Options` и `Referrer-Policy` исключать нельзя.

## Чего мы НЕ делаем (осознанно)

| Заголовок | Почему нет |
| --- | --- |
| `Content-Security-Policy` (полный) | требует инвентаря inline-скриптов, внешних API (ЮKassa, Яндекс SmartCaptcha) и стилей — может сломать SPA и оплату. Отдаётся **только** узкая директива `frame-ancestors 'self'` (запрет встраивания в iframe чужого origin) — она не трогает скрипты и стили; полный CSP — отдельная задача с регрессионным прогоном |
| `X-XSS-Protection` | устаревший XSS-аудитор браузеров; современная рекомендация — не включать (или выставить `0`), полезной защиты не даёт |
| `Permissions-Policy` | требует инвентаря фич сайта (микрофон/камера/геолокация) — до инвентаря может отключить используемую возможность |
| `server_tokens off;` | не входит в этот пакет: BR-544 (`nginx.conf:74`), отдельная задача #371 |

## Enforcement

```bash
python3 tools/check-security-headers.py            # gate (exit 1 при нарушениях)
python3 tools/check-security-headers.py --quiet    # только exit code (pre-commit)
```

Guard статически разбирает конфиг (собственный мини-парсер nginx-синтаксиса),
строит дерево `server{}`/`location{}` и проверяет:

1. каждый server-блок объявляет свой набор заголовков, все с `always`;
2. значения заголовков допустимы (`SAMEORIGIN`/`DENY`, `nosniff`, известные
   значения `Referrer-Policy`, `max-age=` в HSTS);
3. HSTS не объявлен в server без `ssl` (по HTTP браузер его игнорирует —
   RFC 6797 §7.2);
4. **ни один location не теряет заголовки молча**: location с собственными
   `add_header` обязан объявить нужные заголовки сам либо пометить исключение
   маркером (с причиной, и только для `X-Content-Type-Options`).

## Деплой

Конфиг на проде: `scp deploy/prod-single-host/80to8897` →
`/etc/nginx/sites-enabled/80to8897`, затем `nginx -t && systemctl reload nginx`
(**reload**, не restart — не рвём активные соединения; тот же приём описан в
`deploy/prod-single-host/5443to5000`). Проверка синтаксиса без прод-хоста —
в контейнере `nginx:stable` с временным самоподписанным сертификатом
(см. `specs/372-pkg-s2-09-headers/report.md`).

## Связанные документы

- [`docs/features/security-headers.md`](../../docs/features/security-headers.md) —
  per-feature документ (FR-009).
- [`knowledge/domains/storage/domain.md`](../domains/storage/domain.md) —
  MinIO-контур и почему `/minio/` без `nosniff`.
- [`knowledge/guidelines/architecture-conventions.md`](architecture-conventions.md) —
  общие build/runtime конвенции (R-04 и др.).
- Аудит: BR-558 (порция P-72-06) — `specs/336-code-audit/findings/P-72.md`,
  реестр `specs/336-code-audit/registry.csv`.
