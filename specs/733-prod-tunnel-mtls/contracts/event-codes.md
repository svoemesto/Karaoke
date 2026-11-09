# Contract: коды событий наблюдаемости (TUNNEL-*, DB-CERT-001)

> Часть Phase 1 (`/speckit-plan`). Формат строки — как у всех остальных событий
> приложения (`knowledge/guidelines/runbooks/log-correlation.md` § 1a):
> `event=… code=… key=value … [cause=…]`, без стектрейсов
> (`LOG_STACKTRACES=true` включает полный стек).

Категория логгера: `infra.prod.tunnel` (туннель), `infra.prod.db` (отказ по
сертификату).

| Код | Событие | Когда | Ключевые поля | Что делать |
|---|---|---|---|---|
| `TUNNEL-001` | `tunnel.up` | Туннель поднят (интерфейс создан, рукопожатие есть) | `interface`, `peer`, `handshakeAgeSec` | — (информационное) |
| `TUNNEL-002` | `tunnel.down` | Туннель не поднят / потерян | `interface`, `reason` (`no_permission`, `no_key`, `no_config`, `udp_blocked`, `command_failed`, `interface_missing`) | Смотреть `reason`: `no_permission` → в контейнере нет `NET_ADMIN`; `udp_blocked` → закрыт UDP на пути; `no_key` → не смонтирован secret-каталог |
| `TUNNEL-003` | `tunnel.stale` | Возраст рукопожатия превысил `PROD_TUNNEL_HANDSHAKE_TIMEOUT` — туннель перезапускается | `interface`, `handshakeAgeSec`, `timeoutSec` | Проверить WAN шлюза `10.0.1.1` (runbook § 1d) |
| `TUNNEL-004` | `tunnel.restored` | Туннель восстановлен после сбоя | `interface`, `downtimeSec`, `restarts` | — |
| `TUNNEL-005` | `tunnel.disabled` | Туннель выключен конфигурацией (`PROD_TUNNEL_ENABLED=false`) | `mode` | Канал идёт по публичному пути; при смене IP прод-хоста нужна автоматика allow-list |
| `TUNNEL-006` | `tunnel.config.invalid` | Конфигурация не прошла валидацию (нет ключа, нет адреса, пересечение подсетей, битый base64) | `rule`, `value` | Править конфигурацию; туннель не поднимается |
| `TUNNEL-007` | `tunnel.fallback` | Туннель недоступен, канал переключён на публичный путь (или явно отключён) | `interface`, `reason`, `fallback` (`public`/`disabled`) | При `fallback=disabled` канал закрыт намеренно |
| `DB-CERT-001` | `db.cert.rejected` | Сервер БД отклонил клиентский сертификат | `target`, `certReason` (`expired`/`revoked`/`untrusted`/`unknown`), `host` | `expired` → перевыпустить по runbook'у; `untrusted` → проверить `sslrootcert`; `revoked` → новый сертификат |

## Правила различения (FR-028, SC-008, SC-010)

| Что сломалось | Код |
|---|---|
| Туннель | `TUNNEL-002` / `TUNNEL-003` / `TUNNEL-006` / `TUNNEL-007` |
| Сертификат | `DB-CERT-001` |
| БД (не туннель и не сертификат) | `DB-CONNECT-001` / `DB-UNAVAILABLE-002` (как раньше) |
| Сайт | `MON-CHECK-001` с `check=ProdContainerCheck` (как раньше) |

Правило дежурного: **«прод недоступен» без кода участника — недостаточно**;
каждый отказ прод-канала обязан иметь код из этой таблицы.

## Проверка мониторинга

`ProdTunnelCheck` (ключ `prod.tunnel`, категория «Прод-канал»):

| Состояние туннеля | Алерт | Severity |
|---|---|---|
| туннель выключен конфигурацией | нет (информационно, в лог `TUNNEL-005`) | — |
| туннель поднят | нет | — |
| туннель не поднят, канал ушёл в fallback | «Туннель к проду не поднят» | WARNING |
| туннель не поднят, `fallback=disabled` | «Туннель к проду не поднят, канал отключён» | CRITICAL |
| туннель поднят, но рукопожатие старше порога | «Туннель к проду не отвечает (устаревшее рукопожатие)» | WARNING |
