# Data Model: постоянный туннель админ-машина→прод

> Phase 1 `/speckit-plan`. Сущности спеки (Key Entities) и их машинные представления.

## 1. Туннель (ProdTunnel)

| Поле | Тип | Источник | Значение по умолчанию | Проверка |
|---|---|---|---|---|
| `enabled` | Boolean | env `PROD_TUNNEL_ENABLED` | `false` | туннель выключен → приложение работает по публичному пути |
| `interface` | String | env `PROD_TUNNEL_INTERFACE` | `wg0` | имя существующего интерфейса, `[a-z0-9]{1,15}` |
| `configPath` | String | env `PROD_TUNNEL_CONFIG` | `/run/karaoke/tunnel/wg0.conf` | файл существует, права `600` |
| `secretDir` | String | env `PROD_TUNNEL_SECRET_DIR` | `/run/karaoke/tunnel` | каталог существует, права `700` |
| `localAddress` | String | env `PROD_TUNNEL_LOCAL_ADDRESS` | `10.77.0.1/24` | валидный IPv4/CIDR; подсеть не пересекается с LAN/VPN/Docker |
| `peerAddress` | String | env `PROD_TUNNEL_PEER_ADDRESS` | `10.77.0.2/24` | валидный IPv4/CIDR; `local` и `peer` — разные адреса |
| `peerPublicKey` | String | env `PROD_TUNNEL_PEER_PUBLIC_KEY` | — | base64 32 байта; не пусто, если туннель включён |
| `endpoint` | String | env `PROD_TUNNEL_ENDPOINT` | `188.127.240.124:51820` | `host:port`, порт 1..65535 |
| `keepaliveSeconds` | Int | env `PROD_TUNNEL_KEEPALIVE` | `25` | 5..300 |
| `handshakeTimeoutSeconds` | Int | env `PROD_TUNNEL_HANDSHAKE_TIMEOUT` | `180` | ≥ 3 × keepalive |
| `checkIntervalSeconds` | Int | env `PROD_TUNNEL_CHECK_INTERVAL` | `30` | 5..600 |
| `prodHost` | String | env `PROD_TUNNEL_PROD_HOST` | `10.77.0.2` | постоянный внутренний адрес прод-хоста |
| `dnsOverrideHost` | String | env `PROD_TUNNEL_DNS_OVERRIDE_HOST` | `sm-karaoke.ru` | имя, для которого включается переопределение резолвинга |
| `hostsFile` | String | env `PROD_TUNNEL_HOSTS_FILE` | `/etc/hosts` | существует и доступен для записи |
| `fallbackEnabled` | Boolean | env `PROD_TUNNEL_FALLBACK_ENABLED` | `true` | при `false` канал явно отключён, а не переключён на публичный путь |

**Инварианты**:
- `localAddress` и `peerAddress` — разные адреса одной подсети.
- Подсеть туннеля не пересекается с `172.28.3.0/24`, `172.18.0.0/16`,
  `172.21.0.0/16`, `172.23.0.0/16`, `10.128.0.0/16`, `10.0.1.0/24`.
- При `enabled = true` обязательны: `configPath`, `peerPublicKey`, `endpoint`.

## 2. Состояние туннеля (TunnelState)

| Поле | Тип | Смысл |
|---|---|---|
| `up` | Boolean | интерфейс присутствует и поднят |
| `handshakeAgeSeconds` | Long? | возраст последнего рукопожатия; `null` — рукопожатий не было |
| `lastCheckedAt` | Instant | момент последней проверки |
| `reason` | String? | причина состояния `down` (`no_permission`, `no_key`, `no_config`, `udp_blocked`, `stale_handshake`, `command_failed`) |
| `restarts` | Int | счётчик перезапусков туннеля за жизнь процесса |

**Переходы состояний**:

```text
DISABLED ──(enabled=false)──────────────────────────────► DISABLED
DISABLED ──(enabled=true)──► STARTING ──ok──► UP
                                  │
                                  └─fail──► DOWN(reason) ──(интервал)──► STARTING
UP ──(возраст рукопожатия > handshakeTimeout)──► STALE ──► STARTING
UP ──(интерфейс исчез)──► DOWN(reason=interface_missing) ──► STARTING
```

## 3. Пир туннеля (ProdTunnelPeer)

| Поле | Тип | Смысл |
|---|---|---|
| `name` | String | `admin` / `prod` |
| `publicKey` | String | публичный ключ (не секрет) |
| `endpoint` | String? | `host:port` (только у инициирующей стороны) |
| `allowedIps` | List\<String\> | подсети, принимаемые от пира |
| `persistentKeepalive` | Int? | секунды keepalive |

## 4. Доверенный центр (CA) и клиентский сертификат

| Сущность | Поля | Где живёт |
|---|---|---|
| CA | `subject`, `notBefore`, `notAfter`, `privateKeyPath` (вне репозитория, `600`), `certPath` | `/etc/karaoke/tunnel/ca` на хосте |
| Сертификат сервера БД | `subject`, `san` (включает `10.77.0.2`), `notAfter`, `keyPath` | `/etc/karaoke/tunnel/db-server` |
| Клиентский сертификат | `subject` (CN = пользователь БД), `notAfter`, `keyPath`, `certPath` | `/etc/karaoke/tunnel/client` (админская машина), копия — на проде для `pg_hba`/`ssl_ca_file` |

**Инварианты**: срок действия сертификата — 90 дней; процедура перевыпуска описана
в runbook'е (`knowledge/guidelines/runbooks/prod-tunnel.md`) и не требует остановки
конвейера дольше окна переключения (FR-014).

## 5. Прод-канал (ProdChannel)

| Поле | Тип | Значения |
|---|---|---|
| `purpose` | Enum | `DB`, `STORAGE`, `HTTP_ADMIN` |
| `access` | Enum | `TUNNEL` / `PUBLIC` / `FALLBACK` |
| `endpoint` | String | `10.77.0.2:5433`, `http://10.77.0.2:9000`, `https://sm-karaoke.ru/…` |
| `allowListAddress` | String? | `10.77.0.1` (единственный адрес в allow-list прод-хоста) |

## 6. Событие наблюдаемости (TunnelEvent)

| Поле | Тип | Смысл |
|---|---|---|
| `code` | String | `TUNNEL-001…007`, `DB-CERT-001` |
| `event` | String | `tunnel.up`, `tunnel.down`, `tunnel.stale`, `tunnel.restored`, `tunnel.disabled`, `tunnel.config.invalid`, `tunnel.fallback`, `db.cert.rejected` |
| `fields` | Map | `interface`, `peer`, `handshakeAgeSec`, `reason`, `restarts`, `certReason` |

Полный контракт — [`contracts/event-codes.md`](./contracts/event-codes.md).

## 7. Классификация отказа (FailureKind)

| Значение | Признаки | Решение |
|---|---|---|
| `CHANNEL` | сеть/таймаут/TLS-handshake/`SQLException` с сетевым cause | задание → `WAITING` (повтор, до `maxChannelRetries`) |
| `CERTIFICATE` | маркеры сертификата в цепочке | событие `DB-CERT-001`; задание → `WAITING` (сертификат может быть перевыпущен) |
| `LOGICAL` | «данные не найдены», ошибка пайплайна | задание → `ERROR` (как раньше) |
| `UNKNOWN` | не классифицировано | задание → `ERROR` (как раньше) |
