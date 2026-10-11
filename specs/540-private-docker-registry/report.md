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

## Риски и открытые вопросы

- **RSS registry на проде 304 МиБ** против ожидаемых ≈50 МиБ покоя и ≈114 МиБ пика
  из `quickstart.md`. Замер сделан сразу после серии публикаций; требует
  наблюдения (Go возвращает память не мгновенно).
- **Dual-push окно закрыто** (`DUAL_PUSH=0`) после двух успешных прод-деплоев.
  Архив `svoemestodev` в Hub сохранён — откат без пересборки ещё возможен.
- `karaoke-db`/`karaoke-storage` продолжают работать с прежних образов; переход
  на наш registry произойдёт при их следующем пересоздании (нужно слово владельца).
- Реестр **не резервируется**; восстановление — по `RUNBOOK.md` § 2, проверено
  репетицией. Единственное исключение — MinIO: его upstream недоступен, копии
  нужно беречь.

## Вне области этой работы

Закрытие issue #286 — после ревью владельца.
