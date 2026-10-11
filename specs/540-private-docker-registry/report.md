# Отчёт: свой Docker-registry на sm-karaoke.ru (issue #286)

> Спека — контракт: [`spec.md`](./spec.md). Это отчёт о выполнении, не спецификация.
> Терминология: «образ» здесь и далее — **контейнерный образ** (см. `spec.md` § Терминология).

## Что сделано

Развёрнут приватный registry **Zot v2.1.22** на прод-хосте `188.127.240.124`
(контейнер `karaoke-registry`), доступный по HTTPS на **`sm-karaoke.ru:5443`** —
через существующий сертификат GlobalSign, без нового домена и без правок
`daemon.json`. Все проектные и сторонние контейнерные образы переехали в него:

| Что | Было | Стало |
|---|---|---|
| Свои образы | `svoemestodev/*` в Docker Hub | `sm-karaoke.ru:5443/karaoke/*` |
| Базовые образы сборки | `node:22-alpine`, `nginx:stable`, `eclipse-temurin`… | `sm-karaoke.ru:5443/library/*` (pull-through) |
| Сторонние образы | напрямую из Docker Hub / quay / ghcr | `sm-karaoke.ru:5443/mirror/*@sha256:…` (вендоринг, 8 шт.) |

Прод получает образы **только** из нашего registry: `karaoke-web` и
`karaoke-public` уже работают с него, `karaoke-db` и `karaoke-storage` —
при следующем пересоздании контейнера (compose-файлы уже переключены и пинованы).

## Артефакты

**Новое**

- `deploy/prod-single-host/docker-compose-registry.yml` — контейнер registry;
- `deploy/prod-single-host/registry/` — `config.json`, `images.lock`, `RUNBOOK.md`,
  `README.md`, `htpasswd.example`;
- `deploy/prod-single-host/5443to5000` — серверный блок nginx;
- `tools/registry-smoke.sh`, `tools/registry-vendor.sh`, `tools/check-cert-expiry.sh`,
  `tools/check-do-sh-guards.sh`;
- `knowledge/system/infra/registry.md`, `docs/features/private-docker-registry.md`.

**Изменено**

- `deploy/do.sh`, `deploy/prod-single-host/do.sh` — логин в наш registry явным
  сервером и паролем через stdin (пароль больше не виден в `ps`), окно dual-push,
  пин `demucs` вместо `latest`;
- пять `Dockerfile` — базовые образы из нашего registry;
- шесть compose-файлов — сторонние образы пинованы дайджестами;
- `tools/check-docker-image-tags.sh` — нормализация имён registry-квалифицированных
  ссылок + самотест.

## Ключевые находки (проверены на стенде и на проде)

1. **Таймаут Zot 60 с убивал публикацию большого слоя.** Монолитный `PUT` 5.6 ГБ
   через nginx падал с `502` ровно через 67 с. После `readTimeout`/`writeTimeout: 2h`
   та же публикация прошла за **41 с**. Без этого FR-005 недостижим.
2. **Отзыв доступа не работал при монтировании файла.** Удалённый пользователь
   продолжал получать `200`. Причина — inode: bind-mount отдельного файла не
   перечитывается. Лечится монтированием **каталога** `registry/`; проверено —
   `401` без перезапуска.
3. **Изоляции по namespace не было вовсе** — второй пользователь читал чужой
   `karaoke/**` (`200`). Добавлен `http.accessControl.repositories`; стало `403`.
4. **Пин по дайджесту из `docker image inspect` сломал бы `pull`.** Для multi-arch
   это дайджест **индекса**, а в registry лежит платформенный манифест. Дайджест
   обязано брать **из registry** (`Docker-Content-Digest`).
5. **MinIO перестал быть воспроизводимым из upstream**: `quay.io/minio/minio`
   отдаёт `401` на любой тег, в Docker Hub репозитория нет. Образ перенесён из
   локальной копии на admin, тождество доказано совпадением `.Id` и `Created`.
6. **Деплой мог быть «молча зелёным»**: восемь build-функций `do.sh` заканчивались
   вызовом `announce`, который съедал код возврата, а прод-`pull_*` сносил контейнер
   до проверки наличия образа. Оба дефекта исправлены и **доказаны опытами**
   (`tools/check-do-sh-guards.sh`).

## Инцидент 2026-10-11 (сайт 502 ≈ 1.5 минуты)

При деплое `karaoke-public` сборка упала (`npm install` → `ETIMEDOUT`), но
`do.sh` вернул `0`; деплой пошёл дальше, `pull` не нашёл образ, а контейнер к тому
моменту уже был удалён — сайт отдавал `502`. Восстановлено из прежнего локального
образа за ~2 минуты; процедура записана в `RUNBOOK.md` § 2а. Причины (оба дефекта
`do.sh`) устранены, есть тест-стенд для их проверки.

## Проверки

```bash
bash tools/check-do-sh-guards.sh --prod     # PASS=5 FAIL=0: падающая сборка → код 1; нет образа → контейнер жив
bash tools/check-docker-image-tags.sh       # Checked 4 … OK
bash tools/check-docker-image-tags.sh --selftest
python3 tools/lint-knowledge.py
bash tools/check-knowledge-structure.sh
bash tools/check-feature-doc.sh docs/features/private-docker-registry.md
bash tools/registry-vendor.sh --host sm-karaoke.ru:5443 --scheme https --verify
```

## Два состояния, которые важно понимать правильно

### 1. `karaoke-db` и `karaoke-storage` ещё работают со старых образов — это решено, а не забыто

Compose-файлы `docker-compose-database.yml` и `docker-compose-storage.yml` **уже
переключены** на наш registry и **пинованы** дайджестами, но контейнеры подняты
две недели назад и продолжают работать с прежних образов (`postgres:16`,
`quay.io/minio/minio:…`). Владелец решил переключиться **при следующем плановом
пересоздании** — чтобы не трогать работающие сервисы ради самого переключения.
Проверить состояние можно так:

```bash
docker inspect --format '{{.Config.Image}}' karaoke-db karaoke-storage
```

Это принятое состояние. Если однажды покажется, что «что-то не доделали» — это оно.

### 2. Память registry: цифра `docker stats` вводит в заблуждение

`docker stats` показывает по registry **320 МиБ** (в момент замера — до 500 МиБ),
тогда как в плане записан порог «≤ 150 МиБ в покое». Разобрались, в чём дело:
`docker stats` берёт `memory.current` cgroup, а туда попадает **page cache** от
чтения и записи блобов. Разбивка на проде:

| Метрика | Значение | Что это |
|---|---|---|
| `memory.current` (то, что видно в `docker stats`) | 500 МиБ | anon + **page cache** + slab |
| `anon` | **44 МиБ** | собственная память процесса Zot |
| `file` | 444 МиБ | page cache от блобов — **вытесняется ядром по требованию** |
| `VmRSS` процесса | 71 МиБ | резидентная память процесса |
| slab | 11 МиБ | структуры ядра |

То есть собственные 44 МиБ — **внутри** записанного порога, а «320/500 МиБ» — это
в основном reclaimable-кэш, а не утечка. На хосте при этом 2.5 ГиБ свободно, swap
почти не тронут (207 МиБ из 8 ГиБ).

**Предложение:** перемерить в покое (когда публикаций давно не было) и записать в
`quickstart.md`, **какую именно** метрику мы сравниваем с порогом — `anon` или
`docker stats`. Иначе порог будет вечно выглядеть нарушенным.

## Риски и открытые вопросы

- **Память registry** — см. § 2 выше: порог не нарушен, но метрику надо уточнить.
- **Dual-push окно закрыто** (`DUAL_PUSH=0`) после двух успешных прод-деплоев.
  Архив `svoemestodev` в Hub сохранён — откат без пересборки ещё возможен.
- `karaoke-db`/`karaoke-storage` продолжают работать с прежних образов; переход
  на наш registry произойдёт при их следующем пересоздании (нужно слово владельца).
- Реестр **не резервируется**; восстановление — по `RUNBOOK.md` § 2, проверено
  репетицией. Единственное исключение — MinIO: его upstream недоступен, копии
  нужно беречь.

## Вне области этой работы

Закрытие issue #286 — после ревью владельца.
