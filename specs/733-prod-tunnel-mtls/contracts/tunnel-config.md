# Contract: конфигурация туннеля (env + файлы)

> Часть Phase 1 (`/speckit-plan`). Источник истины — переменные окружения
> контейнера `karaoke-app`; файлы — только для ключей и сертификатов.

## 1. Переменные окружения

| Переменная | Обязательна | По умолчанию | Назначение |
|---|---|---|---|
| `PROD_TUNNEL_ENABLED` | нет | `false` | Включает туннель. `false` → приложение работает по публичному пути, в лог пишется `TUNNEL-005`. |
| `PROD_TUNNEL_INTERFACE` | нет | `wg0` | Имя интерфейса WireGuard в netns контейнера. |
| `PROD_TUNNEL_CONFIG` | при `ENABLED=true` | `/run/karaoke/tunnel/wg0.conf` | Конфиг `wg-quick` (права `600`), монтируется из secret-каталога. |
| `PROD_TUNNEL_SECRET_DIR` | нет | `/run/karaoke/tunnel` | Каталог с ключами/сертификатами (read-only mount). |
| `PROD_TUNNEL_LOCAL_ADDRESS` | нет | `10.77.0.1/24` | Адрес админской стороны туннеля. |
| `PROD_TUNNEL_PEER_ADDRESS` | нет | `10.77.0.2/24` | Адрес прод-стороны туннеля (= постоянный внутренний адрес прод-каналов). |
| `PROD_TUNNEL_PEER_PUBLIC_KEY` | при `ENABLED=true` | — | Публичный ключ пира «prod» (не секрет). |
| `PROD_TUNNEL_ENDPOINT` | при `ENABLED=true` | `188.127.240.124:51820` | UDP-endpoint прод-хоста. |
| `PROD_TUNNEL_KEEPALIVE` | нет | `25` | `PersistentKeepalive`, секунды. |
| `PROD_TUNNEL_HANDSHAKE_TIMEOUT` | нет | `180` | Возраст рукопожатия, после которого туннель перезапускается. |
| `PROD_TUNNEL_CHECK_INTERVAL` | нет | `30` | Период опроса состояния туннеля, секунды. |
| `PROD_TUNNEL_PROD_HOST` | нет | `10.77.0.2` | Постоянный внутренний адрес прод-хоста для каналов БД/MinIO. |
| `PROD_TUNNEL_DNS_OVERRIDE_HOST` | нет | `sm-karaoke.ru` | Имя, резолвинг которого переключается на `PROD_TUNNEL_PROD_HOST`. |
| `PROD_TUNNEL_HOSTS_FILE` | нет | `/etc/hosts` | Файл, в котором живёт переопределение резолвинга. |
| `PROD_TUNNEL_FALLBACK_ENABLED` | нет | `true` | `false` → при недоступном туннеле канал явно отключён (в лог `TUNNEL-007`), без отката на публичный путь. |
| `PROD_TUNNEL_MAX_CHANNEL_RETRIES` | нет | `3` | Сколько раз задание возвращается в очередь при канальном отказе (FR-033). |
| `DB_REMOTE_HOST` | — | `188.127.240.124` (до переключения) | В режиме туннеля: `10.77.0.2` (FR-003). |
| `DB_REMOTE_PORT` | — | `5433` | Порт прод-БД через nginx stream. |
| `DB_REMOTE_SSL_MODE` | — | `prefer` | В режиме mTLS: `verify-full` (FR-012). |
| `DB_REMOTE_SSL_CERT` | при mTLS | — | Путь к клиентскому сертификату внутри контейнера. |
| `DB_REMOTE_SSL_KEY` | при mTLS | — | Путь к приватному ключу клиентского сертификата (права `600`). |
| `DB_REMOTE_SSL_ROOT_CERT` | при mTLS | — | Путь к корневому сертификату CA (цепочка доверия). |
| `STORAGE_REMOTE_ENDPOINT` | — | `http://188.127.240.124:9000` | В режиме туннеля: `http://10.77.0.2:9000` (FR-004). |

## 2. Файлы в secret-каталоге

| Файл | Права | Содержимое |
|---|---|---|
| `wg0.conf` | `600` | Конфиг `wg-quick`: `[Interface]` (Address, PrivateKey), `[Peer]` (PublicKey, Endpoint, AllowedIPs, PersistentKeepalive). Приватный ключ — **внутри** файла, файл не попадает в git. |
| `db-client.crt` / `db-client.key` | `644` / `600` | Клиентский сертификат и ключ для mTLS к прод-БД. |
| `ca.crt` | `644` | Корневой сертификат CA (цепочка доверия клиента). |

Приватные ключи **никогда** не попадают в репозиторий, в `argv` и в лог
(FR-013, FR-030, Constitution VIII).

## 3. Правила валидации

1. `PROD_TUNNEL_ENABLED=true` → обязательны `PROD_TUNNEL_CONFIG`, `PROD_TUNNEL_PEER_PUBLIC_KEY`,
   `PROD_TUNNEL_ENDPOINT`; иначе — `TUNNEL-006` (`tunnel.config.invalid`) и отказ от подъёма.
2. `PROD_TUNNEL_PEER_PUBLIC_KEY` — base64, 44 символа (32 байта).
3. Подсеть туннеля не пересекается с LAN `10.0.1.0/24`, `tun0` `172.28.3.0/24`,
   Docker-сетями `172.18.0.0/16`, `172.21.0.0/16`, `172.23.0.0/16`, VPN `10.128.0.0/16`.
4. `PROD_TUNNEL_HANDSHAKE_TIMEOUT ≥ 3 × PROD_TUNNEL_KEEPALIVE`.
5. `PROD_TUNNEL_LOCAL_ADDRESS` и `PROD_TUNNEL_PEER_ADDRESS` — разные адреса.

Нарушение правил → туннель не поднимается, в лог пишется `TUNNEL-006` с
указанием нарушенного правила; канал уходит в режим `fallback` (или отключается,
если `PROD_TUNNEL_FALLBACK_ENABLED=false`).
