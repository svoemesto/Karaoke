# How to: deploy web (karaoke-web) или public на прод-сервер

## Prerequisites

- Доступ к прод-серверу через SSH (`root@${PROD_HOST}`).
- Локальный git push (или нет — см. rsync с admin-машины).
- Знание, что деплоим — `web` (karaoke-web + nginx) или `public` (Vue SPA).

## Steps

### Деплой `karaoke-web` (рекомендуется из `karaoke-app/deploy/deploy_web.sh`)

1. **С локальной admin-машины** (или `dev-pc`/`dev`):
   ```bash
   cd /path/to/Karaoke
   ./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel
   cd deploy && bash do.sh build_public  # если изменены Vue
   bash deploy_web.sh
   ```

2. **Что делает `deploy_web.sh`**:
   - rsync `/opt/karaoke-app.jar` → `${PROD_HOST}:/opt/karaoke-app.jar`
   - rsync `nginx 80to8897` → `${PROD_HOST}:/root/Karaoke/deploy/`
   - На проде: `docker compose restart karaoke-web`

### Деплой `karaoke-public` (Vue SPA)

1. **Из `karaoke-public/`**:
   ```bash
   cd karaoke-public
   npm run build  # → dist/ статика
   cd ../deploy && bash do.sh build_start_public
   ```

2. **Что делает `do.sh build_start_public`**:
   - `docker build` Vue SPA → nginx image.
   - `docker compose restart karaoke-public`.
   - Проверка: `curl http://${PROD_HOST}/` → HTML.

### После деплоя — обязательно проверить

```bash
# 1. SSH на прод
ssh root@${PROD_HOST}

# 2. Проверить nginx -t
nginx -t
systemctl reload nginx  # если менялся конфиг

# 3. Проверить контейнер
docker ps | grep karaoke-web
docker logs karaoke-web --tail 50

# 4. Если менялся env — проверить прокидку
docker exec karaoke-web env | grep -E 'PROD_HOST|DB_REMOTE_HOST'

# 5. Smoke-test
curl -s -o /dev/null -w "%{http_code}\n" "https://${PROD_HOST}/api/public/news/recent?limit=1"
```

## Verification

- `https://${PROD_HOST}/` — публичный сайт загружается.
- `https://${PROD_HOST}/api/public/news/recent?limit=5` — 200 + JSON.
- `webvue3` (если используется) — `/api/admin/...` endpoints 200.

## Rollback

Если что-то пошло не так:

```bash
ssh root@${PROD_HOST}

# Откатить jar
ls -lt /opt/karaoke-app.jar.backup*
# (deploy_web.sh делает backup перед rsync)

# Откатить nginx конфиг
ls -lt /etc/nginx/sites-enabled/80to8897.backup*

# Restart
docker compose restart karaoke-web
nginx -t && systemctl reload nginx
```

## Что НЕ входит в деплой

- ❌ **karaoke-app** на проде НЕ разворачивается (только на admin-машине,
  см. Constitution § «Деплой-окружения»).
- ❌ **База данных** — миграции только на admin-машине (см. runbook
  [how-to-migrate-db.md](how-to-migrate-db.md)).
- ❌ **nginx `80to8897`** напрямую — его копируют через rsync + manual
  `cp /root/Karaoke/deploy/80to8897 /etc/nginx/sites-enabled/ && nginx -t &&
  systemctl reload nginx`.

## Related

- LiveDocs: [architecture/nginx-conventions.md](../architecture/nginx-conventions.md),
  [architecture/L2-containers.md](../architecture/L2-containers.md).
- Constitution: § «Деплой-окружения», § «Агенту запрещено» (п.2 — деплой
  только по согласию пользователя).
- [how-to-update-livedocs.md](how-to-update-livedocs.md) — после деплоя обновить
  LiveDocs при значительных изменениях архитектуры.