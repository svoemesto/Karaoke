# Чек-лист миграции прода на новый сервер (justhost Нск)

> **Конфигурация нового сервера:** 4 vCPU / 4 GB RAM / 50 GB SSD / 1 Gbps.
> **Старый сервер:** reg.ru `79.174.95.69` (2/2/40), Ubuntu 22.04.5.
> **Дата создания:** 2026-08-03.

## Что переносим

На проде бегут 3 контейнера + хостовый nginx + 2 systemd-таймера:

| Компонент | Образ / тип | Источник |
|---|---|---|
| `karaoke-web` (Spring Boot) | `svoemestodev/karaoke-web:1` | Docker Hub, `-Xmx1048m` |
| `karaoke-db` (Postgres 16) | `postgres:16` | Docker Hub |
| `karaoke-public` (nginx SPA) | `svoemestodev/karaoke-public:1` | Docker Hub |
| хостовый nginx (`80to8897`) | `nginx` на хосте | `/etc/nginx/sites-enabled/80to8897` |
| `karaoke-docker-prune.timer` | systemd | `~/Karaoke/deploy/` |

**MinIO-хранилище НЕ переносим** — остаётся на `89.125.103.63:9000` (justhost Мск) до октября.

## Развёрнутая карта переносимых артефактов

### Docker-образы (тянутся с Docker Hub, rsync не нужны)
- `svoemestodev/karaoke-web:1`
- `svoemestodev/karaoke-public:1`
- `postgres:16` (из Docker Hub)

### Файлы и директории (rsync / scp)
- `~/Karaoke/db/` — **530 МБ**, данные Postgres (главное)
- `~/Karaoke/deploy/` — все compose-файлы, `do.sh`, `do.env`, `.env`, nginx-конфы, скрипты
- `~/Karaoke/dumps/` — 44 КБ, старые дампы (опционально, для отката)
- `~/Karaoke/storage/` — 152 КБ, локальный mountpoint (не данные, просто папка)
- `/etc/keys/` — **SSL-сертификаты `www.sm-karaoke.ru`** (3 файла: `.crt`, `.key`, `certificate_ca.crt`)

### Конфиги на хосте (копировать вручную)
- `/etc/nginx/sites-enabled/80to8897` — главный nginx-конфиг (HTTPS, proxy на 8897/7907, `/minio/`, `/smartcaptcha/`, `/yookassa/`)
- `/etc/nginx/nginx.conf` — хостовый nginx с `stream`-блоком (проброс Postgres 5433 → 8832 с IP-allowlist для sync LOCAL↔SERVER)
- systemd: `karaoke-docker-prune.service` + `karaoke-docker-prune.timer` + `prune-images.sh`

### Секреты (переносить вручную, НЕ через rsync deploy/)
- `~/Karaoke/deploy/do.env` — `DOCKER_PASSWORD`, `YOOKASSA_SECRET_KEY`, `STEMJOBS_INTERNAL_SECRET`, `VK_CLIENT_SECRET`
- `~/Karaoke/deploy/.env` — `DB_LOCAL_POSTGRES_PASSWORD`, `DB_SERVER_POSTGRES_PASSWORD`, `STORAGE_SECRET`

### DNS
- `sm-karaoke.ru` и `www.sm-karaoke.ru` → A-записи на IP нового сервера

## Важные нюансы (найденные при разведке)

1. **`-Xmx1048m` (не 2g).** На проде `karaoke-web` ограничен 1 ГБ heap (`.env:2` на сервере). На новом сервере с 4 GB RAM можно оставить 1048m (хватит) или поднять до 1536m (буфер).
2. **`WORK_ON_SERVER=1`** в `docker-compose-web.yml` на сервере — этот флаг отличает прод от локального запуска. В репо-версии `docker-compose-web.yml` стоит `WORK_ON_SERVER=0` — **не перетереть серверную версию репо-версией**.
3. **MTU-обход.** `80to8897` проксирует `/smartcaptcha/` и `/yookassa/` через хостовый nginx, потому что docker-bridge MTU=1500 vs физический ens3=1450 → TLS-handshake из контейнера виснет. На новом сервере MTU может быть другой — **проверить `ip link show` и при необходимости оставить тот же обход**.
4. **`extra_hosts: minio-proxy:host-gateway`** в `docker-compose-web.yml` — `karaoke-web` обращается к хостовому nginx как к `minio-proxy`. `80to8897` разруливает `/minio/` → `89.125.103.63:9000` (хранилище Мск). Оставить как есть.
5. **Stream-блок в `/etc/nginx/nginx.conf`** — пробрасывает порт 5433 → 8832 (Postgres) с IP-allowlist (`83.137.0.0/16`, `95.31.39.146`, `185.191.56.226`) — это для sync LOCAL↔SERVER с admin-машины. **Нужен на новом сервере** — иначе sync сломается.
6. **IP-allowlist в stream-блоке** содержит IP admin-машины. Если IP admin-машины не меняется — оставить как есть. Если меняется — обновить (узнать у пользователя).
7. **`docker image prune` timer** — на проде есть systemd-таймер (04:15 ежедневно) для очистки висящих образов. Обязательно перенести, иначе диск забьётся (инцидент 2026-07-09).
8. **SSL-сертификаты** в `/etc/keys/` — `www.sm-karaoke.ru.crt` перевыпущен 2026-07-08, срок действия ~3 месяца (Let's Encrypt?). Проверить `openssl x509 -enddate`. Если скоро истекает — обновить через certbot на новом сервере или перевыпустить.
9. **`karaoke-web` образ пушится всегда под тегом `:1`** — Docker Hub, `svoemestodev/karaoke-web:1`. На новом сервере достаточно `docker pull`.
10. **Postgres 16** — `karaoke-db` использует `PGDATA=/var/lib/postgresql/data/16`. Версия образа строго `postgres:16` (не 15, не 17).
11. **`/smartcaptcha/` и `/yookassa/` прокси** — критичны для премиум-фич (Yandex SmartCaptcha, ЮKassa-платежи). Без них регистрация/оплата сломаются.
12. **`docker-compose-web.yml` на сервере отличается от репо-версии**: добавлены `WORK_ON_SERVER=1`, `YOOKASSA_*`, `VK_*`, `CAPTCHA_PROXY_URL`, `extra_hosts: minio-proxy:host-gateway`, `volumes: WEB_FOLDER`, `image: ...:1` (явный тег). **Использовать серверную версию, не репо-версию**.

---

## Этап 0: Подготовка (на текущей машине, до начала)

- [ ] Проверить срок SSL-сертификата: `ssh root@79.174.95.69 "openssl x509 -enddate -noout -in /etc/keys/www.sm-karaoke.ru.crt"`
- [ ] Зафиксировать baseline: открыть `https://sm-karaoke.ru` из браузера, записать время загрузки главной, время открытия одной песни (чтобы сравнить после миграции)
- [ ] Снизить TTL DNS-записей `sm-karaoke.ru` и `www.sm-karaoke.ru` до 60 сек (за 24ч до переключения)
- [ ] Получить от пользователя: IP нового сервера, root-доступ по SSH
- [ ] Получить от пользователя: IP admin-машины (для allowlist в stream-блоке nginx, если изменился)

## Этап 1: Базовая настройка нового сервера

- [ ] SSH на новый сервер: `ssh root@<NEW_IP>`
- [ ] Проверить ОС (Ubuntu 22.04 желательно для единообразия): `cat /etc/os-release`
- [ ] Установить Docker: `curl -fsSL https://docs.docker.com/engine/install/ubuntu/ | sudo sh`
- [ ] Установить docker-compose: `apt install docker-compose`
- [ ] Установить mc (опционально): `apt install mc`
- [ ] Создать Docker-сеть `deploy_karaokenet`: `docker network create deploy_karaokenet`
- [ ] Создать папки: `mkdir -p ~/Karaoke/deploy ~/Karaoke/db ~/Karaoke/storage ~/Karaoke/dumps`
- [ ] Войти в Docker Hub: `docker login -u svoemestodev -p <DOCKER_PASSWORD>`
- [ ] Проверить MTU: `ip link show | grep mtu` — зафиксировать значение (сравнить со старым сервером 1450)

## Этап 2: Перенос deploy/ и конфигов

- [ ] Перенести `~/Karaoke/deploy/` со старого сервера (БЕЗ `do.env` и `.env`):
  ```bash
  ssh root@79.174.95.69 "tar czf /tmp/deploy.tar.gz --exclude='do.env' --exclude='.env' -C ~/Karaoke deploy"
  scp root@79.174.95.69:/tmp/deploy.tar.gz /tmp/deploy.tar.gz
  scp /tmp/deploy.tar.gz root@<NEW_IP>:/tmp/deploy.tar.gz
  ssh root@<NEW_IP> "tar xzf /tmp/deploy.tar.gz -C ~/Karaoke"
  ```
- [ ] Перенести `do.env` вручную (секреты):
  ```bash
  ssh root@79.174.95.69 "cat ~/Karaoke/deploy/do.env" | ssh root@<NEW_IP> "cat > ~/Karaoke/deploy/do.env"
  ```
- [ ] Перенести `.env` вручную (секреты) — аналогично
- [ ] Проверить, что `docker-compose-web.yml` на новом сервере содержит `WORK_ON_SERVER=1` (серверная версия, не репо)
- [ ] В `.env` проверить `DB_FOLDER=/root/Karaoke/db` (путь на новом сервере)
- [ ] В `.env` проверить `WEB_FOLDER_HOST` — на сервере было `/home/nsa/Documents/Karaoke/webpictures`; на новом сервере этой папки нет — создать или убрать volume

## Этап 3: Перенос SSL-сертификатов

- [ ] Перенести `/etc/keys/`:
  ```bash
  ssh root@<NEW_IP> "mkdir -p /etc/keys"
  ssh root@79.174.95.69 "tar czf /tmp/keys.tar.gz -C /etc keys"
  scp root@79.174.95.69:/tmp/keys.tar.gz /tmp/keys.tar.gz
  scp /tmp/keys.tar.gz root@<NEW_IP>:/tmp/keys.tar.gz
  ssh root@<NEW_IP> "tar xzf /tmp/keys.tar.gz -C /etc && chmod 600 /etc/keys/*.key"
  ```

## Этап 4: Перенос БД (Postgres)

**Вариант A (быстрый, 530 МБ): прямой pg_dump → restore**

- [ ] Сделать дамп на старом сервере:
  ```bash
  ssh root@79.174.95.69 "docker exec karaoke-db pg_dump -U postgres --file='/tmp/karaoke_dump.sql' --dbname=karaoke --if-exists --clean --create"
  ssh root@79.174.95.69 "docker cp karaoke-db:/tmp/karaoke_dump.sql /tmp/karaoke_dump.sql"
  ```
- [ ] Перенести дамп на новый сервер:
  ```bash
  scp root@79.174.95.69:/tmp/karaoke_dump.sql /tmp/karaoke_dump.sql
  scp /tmp/karaoke_dump.sql root@<NEW_IP>:/tmp/karaoke_dump.sql
  ```
- [ ] Поднять `karaoke-db` на новом сервере:
  ```bash
  ssh root@<NEW_IP> "cd ~/Karaoke/deploy && bash do.sh start_db"
  ```
- [ ] Дождаться готовности Postgres: `docker logs karaoke-db 2>&1 | grep "ready to accept"`
- [ ] Restore:
  ```bash
  ssh root@<NEW_IP> "docker cp /tmp/karaoke_dump.sql karaoke-db:/docker-entrypoint-initdb.d/karaoke_dump.sql"
  ssh root@<NEW_IP> "docker exec karaoke-db psql -U postgres --file='/docker-entrypoint-initdb.d/karaoke_dump.sql' karaoke"
  ```
- [ ] Проверить количество записей: `SELECT count(*) FROM tbl_settings;` — сравнить со старым сервером

**Вариант B (альтернативный, rsync данных):**
- [ ] Остановить `karaoke-db` на старом сервере: `docker stop karaoke-db`
- [ ] rsync `~/Karaoke/db/` → новый сервер (530 МБ, ~1 мин по гигабитному каналу)
- [ ] Запустить `karaoke-db` на новом сервере
- [ ] **Минус:** downtime старого прода на время rsync. **Плюс:** точная копия, без dump/restore

## Этап 5: Настройка хостового nginx

- [ ] Установить nginx на хост: `apt install nginx`
- [ ] Скопировать `/etc/nginx/nginx.conf` со старого сервера (с `stream`-блоком для Postgres 5433)
- [ ] Скопировать `/etc/nginx/sites-enabled/80to8897` со старого сервера
  ```bash
  scp root@79.174.95.69:/etc/nginx/nginx.conf /tmp/nginx.conf
  scp root@79.174.95.69:/etc/nginx/sites-enabled/80to8897 /tmp/80to8897
  scp /tmp/nginx.conf root@<NEW_IP>:/etc/nginx/nginx.conf
  scp /tmp/80to8897 root@<NEW_IP>:/etc/nginx/sites-enabled/80to8897
  ```
- [ ] Проверить, что IP-allowlist в `stream`-блоке актуален (IP admin-машины)
- [ ] Проверить, что `/minio/` проксируется на `89.125.103.63:9000` (хранилище не меняется)
- [ ] `nginx -t` — проверить конфиг
- [ ] `systemctl reload nginx`

## Этап 6: Перенос systemd-таймера (docker prune)

- [ ] Файлы уже перенесены через `deploy/` (Этап 2)
- [ ] Установить:
  ```bash
  ssh root@<NEW_IP> "cp ~/Karaoke/deploy/karaoke-docker-prune.service /etc/systemd/system/ && cp ~/Karaoke/deploy/karaoke-docker-prune.timer /etc/systemd/system/ && systemctl daemon-reload && systemctl enable --now karaoke-docker-prune.timer"
  ```
- [ ] Проверить: `systemctl list-timers | grep karaoke`

## Этап 7: Сборка и запуск контейнеров

- [ ] Pull образов на новом сервере:
  ```bash
  ssh root@<NEW_IP> "cd ~/Karaoke/deploy && docker pull svoemestodev/karaoke-web:1 && docker pull svoemestodev/karaoke-public:1 && docker pull postgres:16"
  ```
- [ ] Запустить БД (если ещё не запущена на Этапе 4): `cd ~/Karaoke/deploy && bash do.sh start_db`
- [ ] Запустить `karaoke-web`: `cd ~/Karaoke/deploy && bash do.sh start_web`
- [ ] Запустить `karaoke-public`: `cd ~/Karaoke/deploy && bash do.sh start_public`
- [ ] Проверить статус: `docker ps --format 'table {{.Names}}\t{{.Status}}'`
- [ ] Проверить логи `karaoke-web` на ошибки: `docker logs karaoke-web 2>&1 | tail -50`

## Этап 8: Тестирование по IP (без DNS)

- [ ] Из браузера: `https://<NEW_IP>/` — должна открыться главная (с SSL-предупреждением, т.к. сертификат на `sm-karaoke.ru`, а не на IP)
- [ ] Проверить `/api/public/stats` — должен вернуть JSON со счётчиками
- [ ] Открыть одну песню по IP — проверить, что stems играют (MinIO проксируется через `80to8897` → `89.125.103.63:9000`)
- [ ] Проверить `/smartcaptcha/` — Yandex SmartCaptcha должен отвечать
- [ ] Проверить `/yookassa/` — ЮKassa API (можно `curl https://<NEW_IP>/yookassa/payments` — должен вернуть 401 от ЮKassa, не 502)
- [ ] Проверить SSE `/changerecords` — должен держать connection
- [ ] Проверить stream Postgres 5433: `psql -h <NEW_IP> -p 5433 -U SvoeMestoKaraokeUser905 -d karaoke` (с admin-машины, если IP в allowlist)

## Этап 9: Переключение DNS (требует подтверждения пользователя)

- [ ] **ПОДТВЕРЖДЕНИЕ ПОЛЬЗОВАТЕЛЯ** — этот шаг необратим
- [ ] Изменить A-записи `sm-karaoke.ru` и `www.sm-karaoke.ru` → `<NEW_IP>` в панели регистратора домена
- [ ] Ждать 60 сек (TTL) + проверить: `dig sm-karaoke.ru +short`
- [ ] Открыть `https://sm-karaoke.ru` — SSL-сертификат должен валидироваться (домен → IP → cert)
- [ ] Проверить из браузера: главная, песня, /api/public/stats

## Этап 10: Наблюдение и отключение старого сервера

- [ ] Наблюдать 24-48ч: логи `karaoke-web`, `karaoke-db`, nginx access/error
- [ ] Проверить sync LOCAL↔SERVER с admin-машины (через 5433 порт)
- [ ] Проверить telegram-бот / auto-publish (если использует `VK_*` переменные)
- [ ] Проверить ЮKassa-платежи (тестовая оплата, если возможно)
- [ ] После 48ч стабильной работы — остановить контейнеры на старом сервере:
  ```bash
  ssh root@79.174.95.69 "cd ~/Karaoke/deploy && bash do.sh stop"
  ```
- [ ] **НЕ удалять данные на старом сервере ещё 7 дней** (backup на случай отката)
- [ ] Через 7 дней — удалить старый сервер или оставить оплаченным до конца месяца (reg.ru)

## Откат (если что-то пошло не так)

- [ ] Откат DNS: `sm-karaoke.ru` → `79.174.95.69` (старый сервер всё ещё работает)
- [ ] Откат БД: на старом сервере БД не останавливалась (Вариант A) — просто переключить DNS обратно
- [ ] Откат БД (Вариант B): `docker start karaoke-db` на старом сервере + переключить DNS
- [ ] Откат данных: если на новом сервере уже были записи после миграции — сделать дамп с нового и restore на старый

## Чек-лист перед переключением DNS (gate)

Перед Этапом 9 проверить, что ВСЁ из списка работает на новом сервере по IP:
- [ ] `https://<NEW_IP>/` — главная
- [ ] `/api/public/stats` — JSON
- [ ] Открытие песни — stems играют (MinIO проксируется)
- [ ] `/smartcaptcha/` — отвечает (не 502)
- [ ] `/yookassa/` — отвечает (не 502)
- [ ] `/changerecords` — SSE держит
- [ ] Postgres 5433 — доступен с admin-машины
- [ ] docker-prune timer — установлен
- [ ] SSL-сертификат — валидный (или запланирован certbot)
