# registry/ — приватный контейнерный registry на прод-хосте

> **Статус**: Active. Канон прода: этот каталог → `/root/Karaoke/deploy/registry/`.
> **Контекст**: wayfinder #274 «Свой Docker-registry», спека исполнения
> `specs/540-private-docker-registry/`, issue #286.
> **Терминология**: «образ» здесь = **контейнерный образ** (коллизия с ADR
> `local-0003-shared-minio-image-cache.md`, где «образ» — медиа-изображение).

## Что где лежит

| Файл | Что | В git |
|---|---|---|
| `config.json` | конфиг Zot v2.1.22 (storage/retention/http/auth/sync) | да |
| `htpasswd.example` | шаблон учётных данных + как выдать/отозвать доступ | да |
| `htpasswd` | **реальные** креды проекта (bcrypt) | **нет** (`.gitignore`) |
| `images.lock` | карта пинов сторонних образов: upstream → наш ref + дайджест | да |
| `RUNBOOK.md` | эксплуатация: доступ, восстановление, срок сертификата | да |
| `README.md` | этот файл | да |

Рядом, в `deploy/prod-single-host/`:

| Файл | Что |
|---|---|
| `docker-compose-registry.yml` | контейнер `karaoke-registry` (Zot), публикуется только на `127.0.0.1:5000` |
| `5443to5000` | блок host-nginx: HTTPS `sm-karaoke.ru:5443` → `127.0.0.1:5000` |

## Раскладка на хосте

```
/root/Karaoke/deploy/
  ├── docker-compose-registry.yml     # контейнер registry
  ├── 5443to5000 → /etc/nginx/sites-enabled/5443to5000
  └── registry/                       # ← монтируется ЦЕЛИКОМ в /etc/zot:ro
        ├── config.json               #   /etc/zot/config.json
        ├── htpasswd                  #   /etc/zot/htpasswd (0600)
        └── images.lock               #   в контейнер не нужен, но едет вместе с каталогом

/sm-karaoke/system/registry/           # bind-mount → /var/lib/registry (данные)
```

**Почему монтируется каталог, а не отдельные файлы** (проверено на стенде
2026-10-11, важно для FR-004/SC-004): bind-mount **файла** привязан к inode. Правка
`htpasswd` на хосте через `sed -i`, `cp` или `scp` заменяет файл новым inode, и
контейнер продолжает видеть **старый**: удалённый пользователь по-прежнему получал
`200`, а в логе Zot было `loaded htpasswd file users: 2` при одном пользователе в
файле. При монтировании **каталога** Zot получает fsnotify-события и перечитывает и
`htpasswd`, и `config.json` на лету — перезапуск не нужен.

- Данные лежат **вне** docker-конфайнмента: `docker image prune` и
  `karaoke-docker-prune.timer` их не касаются. Рост ограничивает retention Zot
  (`storage.retention`, inline GC без остановки).
- Порт наружу — только `5443`; контейнер наружу не опубликован.

## Порядок развёртывания (на хосте, по слову владельца)

```bash
# 1. Каталог данных и файлы
mkdir -p /sm-karaoke/system/registry
scp -r deploy/prod-single-host/* root@sm-karaoke.ru:/root/Karaoke/deploy/
chmod 600 /root/Karaoke/deploy/registry/htpasswd

# 2. Контейнер (новый; существующие четыре не трогаем — FR-012)
cd /root/Karaoke/deploy && docker compose -f docker-compose-registry.yml up -d

# 3. nginx
cp 5443to5000 /etc/nginx/sites-enabled/5443to5000
nginx -t && systemctl reload nginx        # reload, не restart
```

## Чем проверяется живучесть

Контейнерного `healthcheck` у registry **нет намеренно**: образ Zot не содержит ни
shell, ни HTTP-клиента, поэтому любой `healthcheck:` был бы фикцией. Живучесть
проверяется снаружи:

```bash
bash tools/registry-smoke.sh sm-karaoke.ru:5443
#  /v2/ без кредов → 401  = «процесс жив и аутентификация включена»
#  /v2/ с кредами   → 200
```

## Модель доступа (кратко)

Namespace = проект: `karaoke/**` — наши образы, `mirror/**` — перенесённые сторонние
с сохранением исходного пути, `library/**` — **только** pull-through из `docker.io`
(туда не пушим никогда). Один комплект учётных данных на проект; выдача и отзыв —
строкой в `htpasswd`, без перезапуска. Подробности — `RUNBOOK.md`.
