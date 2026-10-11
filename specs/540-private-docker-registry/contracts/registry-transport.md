# Contract: транспорт и внешняя поверхность registry

**Спека**: [spec.md](../spec.md) (FR-002, FR-005, FR-007, FR-009, FR-012) |
**План**: [plan.md](../plan.md) (D1, D2, D3, D5) | **Модель**: [data-model.md](../data-model.md) § E3, E7

Контракт фиксирует **внешнюю поверхность**: что слушает прод-хост, что видит docker
клиента, какие лимиты nginx и какие ответы обязан давать registry. Внутренняя
раскладка контейнера — `docker-compose-registry.yml` + `registry/config.json`.

---

## 1. Топология

```
docker CLI (admin, push)  ──┐
                            ├──> HTTPS sm-karaoke.ru:5443 ── host-nginx (TLS termination)
docker CLI (прод, pull)   ──┘                                    │
                                                                v  http://127.0.0.1:5000
                                                        karaoke-registry (Zot v2.1.22)
                                                                │
                                                                v  bind-mount
                                                        /sm-karaoke/system/registry
```

- Контейнер `karaoke-registry` публикует порт **только на loopback**:
  `ports: ["127.0.0.1:5000:5000"]`. Наружу не выходит ни один неаутентифицированный
  путь — как уже сделано для MinIO (`127.0.0.1:8890`).
- `sm-karaoke.ru:443` и `:80` **не трогаются**: registry живёт на своём порту, сайт и
  почта продолжают работать на существующих блоках.
- Firewall не настраивается: его на проде нет (проверено #284), открыть порт =
  заставить nginx слушать.

---

## 2. Серверный блок nginx (`deploy/prod-single-host/5443to5000`)

```nginx
server {
    listen 5443 ssl;
    server_name sm-karaoke.ru;

    # Существующий сертификат домена (certbot/поддомен не выпускаем — решение #285).
    ssl_certificate     /etc/keys/www.sm-karaoke.ru.crt;
    ssl_certificate_key /etc/keys/www.sm-karaoke.ru.key;

    # В http{} этого nginx стоит `client_max_body_size 20M` — для образа 5.4 ГБ
    # это 413. Официальный рецепт distribution: «disable any limits to avoid HTTP 413».
    client_max_body_size 0;

    # «required to avoid HTTP 411» — официальный рецепт distribution.
    chunked_transfer_encoding on;

    # Не буферизовать гигабайты на диск в /var/lib/nginx/tmp.
    proxy_request_buffering off;

    location /v2/ {
        proxy_pass http://127.0.0.1:5000;
        proxy_http_version 1.1;

        # «required for docker client's sake».
        proxy_set_header Host              $http_host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 5.4 ГБ на медленном канале — не обрывать по таймауту.
        proxy_connect_timeout 10s;
        proxy_send_timeout    900;
        proxy_read_timeout    900;
    }

    location / { return 404; }
}
```

**Обязательства контракта.**

| # | Обязательство | Почему именно так |
|---|---|---|
| T1 | `client_max_body_size 0` в **server**-блоке | Глобальные 20M (`nginx.conf:103`) дают 413 на большом слое (FR-005) |
| T2 | `proxy_request_buffering off` | иначе nginx копит гигабайты в `/var/lib/nginx/tmp` |
| T3 | `chunked_transfer_encoding on` | иначе HTTP 411 (docker push шлёт chunked) |
| T4 | `proxy_set_header Host $http_host` | docker-клиент сверяет; Zot строит путь из request-URL |
| T5 | таймауты ≥ 900 с | push 5.4 ГБ (SC-002: 9 из 10 попыток) |
| T6 | `location / → 404` | единственная открытая поверхность — `/v2/` (FR-002) |
| T7 | TLS-терминация на nginx, Zot без TLS | официальный рецепт distribution; сертификат не копируется в контейнер |
| T8 | правка **только** `reload`, не `restart` | `systemctl reload nginx` не рвёт активные соединения (FR-012) |

---

## 3. Аутентификация: где она живёт

Аутентификацию делает **сам Zot** (`http.auth.htpasswd`), не nginx.

- Плюс: nginx не «съедает» заголовок `Docker-Distribution-Api-Version`, и не нужен
  `map $upstream_http_docker_distribution_api_version` из рецепта distribution.
- Плюс: отзыв доступа — это правка файла, который перечитывает Zot (FR-004, SC-004),
  а не reload nginx.
- `realm = "karaoke-registry"`, `failDelay: 5` (задержка ответа на неудачную
  аутентификацию — защита от перебора).

**Учётные данные в открытом виде не ходят нигде:** `insecure-registries` не
используется (FR-009), потому что документация distribution прямо говорит: «You
cannot use authentication with authentication schemes that send credentials as clear
text». Сертификат — публичный DV GlobalSign, он есть в системных `cacerts` и на admin,
и на проде ⟹ каталог `/etc/docker/certs.d/` не нужен вообще.

---

## 4. Поведение sync (влияет на транспорт)

| Настройка | Значение | Обязательность |
|---|---|---|
| `extensions.sync.registries[].urls` | `["https://registry-1.docker.io"]` — ровно один upstream | решение #276 |
| `onDemand` | `true` | pull-through по запросу |
| `content` | `[{"prefix": "library/**"}]` | **обязательно** (ловушка #275, FR-006) |
| `destination` / `stripPrefix` | **не задаются** | сохранение upstream-пути проверено на стенде: `library/postgres:16` ложится ровно как `library/postgres` |
| `http.compat` | `["docker2s2"]` | без него Zot отвергает Docker schema 2 контент |

**Следствие для транспорта (проверено чтением исходников).** `Location`, который Zot
отдаёт при инициализации blob-upload, строится из request-URL
(`getBlobUploadSessionLocation`, `pkg/api/routes.go:3199`), а `http.ExternalURL`
используется только для OIDC/cookie (`pkg/api/authn.go:728`) и на `Location` не
влияет. За nginx request-URL приходит в origin-form, значит `Location`
**относительный** и docker разрешает его относительно `https://sm-karaoke.ru:5443`.
Это вывод из кода, а не из живого прогона — обязательный шаг приёмки (Q2 в
[quickstart.md](../quickstart.md)).

---

## 5. Наблюдаемая поверхность (что можно спросить снаружи)

| Запрос | Ожидание | Смысл |
|---|---|---|
| `GET /v2/` без кредов | **401** | FR-002, SC-006 |
| `GET /v2/_catalog` без кредов | **401** | SC-006 |
| `GET /v2/<name>/manifests/<tag>` без кредов | **401** | SC-006 |
| `GET /v2/` с верными кредами | **200** | FR-002 (не сломан доступ) |
| `GET /v2/` с неверным паролем | **401** | стенд: проверено |
| `GET /` (вне `/v2/`) | **404** | T6 |
| `GET /v2/_catalog` с кредами | список репозиториев | инвентарь (I-2) |
| `POST /v2/<name>/blobs/uploads/` (push) | 202 + относительный `Location` | R-2/§4 |

Health-check контейнера — `GET /v2/` с ожиданием `401`: это признак «процесс жив и
auth включён», и он не требует кредов в compose-файле.

---

## 6. Чего контракт НЕ делает

- Не добавляет `registry-mirrors` и не создаёт `/etc/docker/daemon.json` ни на
  проде, ни на admin (FR-007): «reload Docker for the change to take effect» =
  простой четырёх прод-контейнеров, запрещённый Pass 380-логикой.
- Не выпускает сертификат и не заводит поддомен: используется существующий домен и
  лист (`notAfter = 2027-04-03T10:34:27Z` = 13:34 МСК).
- Не открывает порт через firewall: firewall отсутствует.
- Не публикует UI/search Zot (решение #277).
