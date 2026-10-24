# Feature: Заголовки безопасности публичного сайта (BR-558, аудит #336, OpenProject #372)

> **Status**: active
> **Feature Key**: security-headers
> **Last Updated**: 2026-10-24
> **Spec**: [specs/372-pkg-s2-09-headers/report.md](../../specs/372-pkg-s2-09-headers/report.md)
> **Created**: 2026-10-24
> **История**: аудит #336 (порция P-72-06, BR-558) → пакет PKG-S2-09, OpenProject #372,
> ветка `576-pkg-s2-09-headers`.

## Что делает

Host-nginx публичного сайта `sm-karaoke.ru`
(`deploy/prod-single-host/80to8897` → на проде
`/etc/nginx/sites-enabled/80to8897`) отдаёт **заголовки безопасности** в каждом
ответе сайта:

| Заголовок | Значение | Контур |
|---|---|---|
| `X-Frame-Options` | `SAMEORIGIN` | 443 + 80, все location'ы |
| `X-Content-Type-Options` | `nosniff` | 443 + 80, кроме MinIO-контура |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 443 + 80, все location'ы |
| `Strict-Transport-Security` | `max-age=31536000` | только 443 (TLS) |

Все заголовки объявлены с флагом **`always`** — они приходят и на ошибочные
ответы (4xx/5xx). Правило зафиксировано guard'ом
`tools/check-security-headers.py` (pre-commit + CI-джоб
`security-headers-guard`), регламент — `knowledge/guidelines/security-headers.md`.

## Зачем

Аудит #336 (BR-558, порция P-72-06) зафиксировал: в прод-конфигурации nginx
**нет ни одного** защитного заголовка — `grep -rl "X-Frame-Options" deploy/`
пусто, `server_tokens off;` в `nginx.conf` закомментирован. Публичный сайт
содержит регистрацию и оплату (ЮKassa), поэтому:

- без `X-Frame-Options` / `frame-ancestors` любую страницу сайта можно
  встроить в iframe чужого домена и перехватить клики (clickjacking) —
  в том числе в форме оплаты;
- без `X-Content-Type-Options` браузер угадывает MIME-тип ответа, что вместе
  с загрузкой пользовательских файлов — вектор для подмены типа;
- без `Referrer-Policy` в `Referer` наружу уходят полные URL публичных
  share-ссылок (идентификаторы песен/альбомов);
- без HSTS первый запрос к сайту остаётся открытым для перехвата/редиректа
  на `http://`.

## Как работает

**Где объявлено.** На уровне **server-блоков** файла `80to8897` — не в `http{}`
файла `nginx.conf`: `nginx.conf` — общий конфиг хоста (и в нём же работают
BR-544/BR-545, отдельная задача #371), а `80to8897` — единственный файл,
который описывает публичный сайт.

**Наследование `add_header` — главная тонкость nginx.** Директивы
`add_header` наследуются с верхнего уровня (`http{}` → `server{}` →
`location{}`) **только если в самом блоке нет ни одной своей `add_header`**.
У `location /minio/` и `location @minio_404` есть свои (`Cache-Control`,
`ETag`, `Last-Modified`) — значит, они теряют серверный набор целиком, и
заголовки в них объявлены **явно, заново**.

**Исключение `nosniff` для MinIO-контура.** Объекты в MinIO кладутся **без**
`Content-Type` (`PutObjectArgs` в
`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageApiClient.kt`
собирается без `.contentType(...)`) → MinIO отдаёт их как
`application/octet-stream`. Публичный сайт грузит эти файлы прямо в браузер
(картинки автора, обложки альбомов, превью — URL вида
`/minio/karaoke/<файл>`), а браузер с `nosniff` может отказаться показывать
ресурс с «неверным» MIME. Поэтому в `/minio/` и `@minio_404`:
`X-Frame-Options` и `Referrer-Policy` есть, `nosniff` — нет, и это зафиксировано
явным маркером в конфиге:

```nginx
# security-headers-exempt: X-Content-Type-Options (объекты MinIO хранятся без
# Content-Type → отдаются как application/octet-stream; nosniff может
# заблокировать показ картинки автора/обложки альбома в браузере)
```

Guard разрешает исключать **только** `X-Content-Type-Options` и **только** с
указанием причины; `X-Frame-Options`/`Referrer-Policy` исключить нельзя.

**HSTS.** Только в TLS-сервере (443), без `includeSubDomains`: поддомены домена
не проверялись, решение о `includeSubDomains` — за владельцем (после проверки,
что все поддомены на HTTPS). `www.sm-karaoke.ru` обслуживается тем же
server-блоком и получает HSTS отдельно (политика HSTS — per-host).
На HTTP-контуре (80) HSTS не объявляется: браузер игнорирует HSTS, полученный
по небезопасному каналу (RFC 6797 §7.2).

**Guard.** `tools/check-security-headers.py` статически разбирает конфиг
(собственный мини-парсер nginx-синтаксиса), строит дерево
`server{}`/`location{}` и проверяет: набор заголовков в каждом server-блоке,
допустимость значений, наличие `always`, отсутствие HSTS на HTTP-контуре и —
главное — что **ни один location не теряет заголовки молча**.

## Инварианты

1. Каждый server-блок `80to8897` объявляет `X-Frame-Options`,
   `X-Content-Type-Options`, `Referrer-Policy`; TLS-сервер — ещё и
   `Strict-Transport-Security`. Все — с `always`.
2. Ни один location `80to8897` не отдаётся без заголовков безопасности:
   либо наследует серверный набор (нет своих `add_header`), либо объявляет
   нужные явно, либо помечен явным исключением (только `nosniff`, с причиной).
3. `X-Content-Type-Options: nosniff` **не применяется** к `/minio/` и
   `@minio_404` — пока объекты MinIO хранятся без `Content-Type`.
4. `Strict-Transport-Security` объявляется только в server-блоке с `ssl`.
5. Guard `tools/check-security-headers.py` — зелёный на текущем конфиге и
   красный на конфиге до правки (BR-558).
6. Код приложения, публичное поведение сайта, оплата и загрузка файлов не
   меняются — правка только в nginx-конфиге и в документации.

## Известные ловушки

- **Наследование `add_header` работает «наоборот» ожидаемого.** Если в
  `location` есть хоть одна своя `add_header` (даже `Cache-Control`), то
  **все** серверные заголовки в этом location пропадают. Добавление
  `add_header` в новый location — это не «ещё один заголовок», а
  «перекрытие всего набора»: нужно либо объявить набор заново, либо оставить
  location без своих `add_header`.
- **`always` обязателен.** Без него заголовки уходят только с успешными
  ответами (2xx/3xx) — на 404/502 защиты нет, а именно на ошибочных ответах
  её и ждут (например, 404 на `/api/...`).
- **`nosniff` + неверный MIME = сломанные картинки.** MinIO отдаёт объекты как
  `application/octet-stream`; с `nosniff` браузер может не показать
  `<img src="/minio/karaoke/...">`. Поэтому исключение оформлено явно, а не
  «молча».
- **`X-Frame-Options: DENY` сломал бы плеер.** Публичный SPA встраивает
  сам себя в iframe (`/player/:id`, `karaoke-public/src/views/PlaylistEditView.vue`)
  — нужен `SAMEORIGIN`, а не `DENY`.
- **HSTS без `includeSubDomains` — осознанно.** `includeSubDomains` запрещает
  браузеру ходить на http:// любых поддоменов; проверка поддоменов не
  проводилась — решение за владельцем.
- **Конфиг на проде — ручная копия.** Файл в репозитории не равен файлу на
  хосте: после merge нужно `scp` → `nginx -t && systemctl reload nginx`
  (**reload**, не restart).
- **Параллельные пакеты волны C трогают тот же каталог.** `nginx.conf`
  меняет пакет #371 (BR-544/BR-545), `nginx_karaoke-public.conf` — пакет #393
  (BR-549). Здесь правился только `80to8897` — чтобы не пересекаться.

## Ссылки

- [knowledge/guidelines/security-headers.md](../../knowledge/guidelines/security-headers.md) —
  регламент (SSoT): набор заголовков, наследование, исключения, enforcement.
- [deploy/prod-single-host/80to8897](../../deploy/prod-single-host/80to8897) —
  конфиг с заголовками.
- [tools/check-security-headers.py](../../tools/check-security-headers.py) — guard.
- [knowledge/domains/storage/domain.md](../../knowledge/domains/storage/domain.md) —
  «Решение 6»: почему `/minio/` без `nosniff`.
- [specs/372-pkg-s2-09-headers/report.md](../../specs/372-pkg-s2-09-headers/report.md) —
  отчёт по задаче (проверки «до/после», что осталось за владельцем).
- [specs/336-code-audit/findings/P-72.md](../../specs/336-code-audit/findings/P-72.md) —
  карточка аудита (запись P-72-06 = BR-558).
