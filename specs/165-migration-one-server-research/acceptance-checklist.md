# Acceptance Checklist — переезд sm-karaoke.ru на один сервер

> **Статус**: решение зафиксировано (wayfinder #174). Исполнение — отдельной спекой.
> **Артефакты**: тикет #174, карта #165, `tools/migration-smoke.sh`,
> `cutover-runbook.md`.

«Переезд успешен» = **все** проверки ниже PASS. UI-проверка (визуал/плеер/
оплата) — только владелец (единственный источник UI-верификации).

## 0. Автоматический smoke

```bash
tools/migration-smoke.sh 188.127.240.124   # до DNS (по IP, SNI/Host=sm-karaoke.ru)
tools/migration-smoke.sh sm-karaoke.ru     # после DNS
```

Возвращает 0 при всех PASS. Гоняется **до** переключения DNS (по IP) и
**после** — приёмка (#174).

Покрытие: SPA `/`, `/api/public/songs`, `/authors-tiles`, `/zakroma`,
`/news`, `/song?id=1`, `/api/public/og/song?id=1`, `/api/public/authors/17/albums`,
`/api/public/stats`, `/minio/karaoke/`, redirect http→https, наличие данных в
списке песен, живость `/changerecords` (405 на GET), smartcaptcha-прокси.

## 1. Данные

| Проверка | Ожидание | Как |
|---|---|---|
| Песни | `26 549` | `select count(*) from tbl_songs` на новом хосте |
| Пользователи | `86` | `select count(*) from tbl_site_users` |
| Размер БД | ~`483 МБ` | `pg_database_size` |
| recordhash-триггеры | есть | Constitution III — иначе sync не увидит diff |
| MinIO: объектов | `59 707` | `mc ls --recursive --json <alias>/karaoke \| wc -l` |
| MinIO: объём | `≈444.45 GiB` | `mc du <alias>/karaoke` |
| MinIO: выборочная сверка | md5 совпал | скачать 2–3 объекта (картинка/mp3/mp4), `md5sum` против источника |

## 2. Публичный сайт (авто + владелец)

- [ ] `/` — главная (SPA) открывается.
- [ ] Список песен, фильтры, пагинация.
- [ ] Страница песни, плеер, аудио-дорожки (`fileminus/filevoice/filebass/filedrums`).
- [ ] Картинки/обложки через `/minio/`.
- [ ] Закрома: список + real-time stream (`/api/public/zakroma/stream`).
- [ ] Новости.

## 3. Интеграции

| Проверка | Как | Ожидание |
|---|---|---|
| YooKassa | прокси `/yookassa/` отвечает; webhook `/api/public/payment/webhook` жив | без 5xx; **тестовый платёж** — по согласию владельца, миним. сумма + сразу отмена. Либо без реальной оплаты — только доступность API |
| SmartCaptcha | `/smartcaptcha/` прокси | без 5xx |
| VK / VK ID OAuth | redirect-URI по домену → прозрачно при смене IP | вход работает |
| Telegram-публикация | авто-постинг (по возможности) | пост уходит |
| SSE `/changerecords` | GET 405 (эндпоинт жив), реальный стрим — владелец в админке | события идут |
| stemjobs | `/api/public/account/stemjobs/list` (с авторизацией) | список отдаётся |

## 4. Инфраструктура

- [ ] Контейнеры: `karaoke-db`, `karaoke-storage`, `karaoke-web`,
      `karaoke-public` — все `Up`, `restart: always`.
- [ ] host-nginx: `nginx -t` ок; `80to8897` активен; сертификат fullchain.
- [ ] Cert: `openssl s_client` → notAfter 2027-04-03, issuer GlobalSign, SAN apex+www.
- [ ] Память: нет OOM-kill, `free -h` в норме, swap используется умеренно.
- [ ] Systemd-таймеры: `karaoke-db-backup.timer`, `karaoke-docker-prune.timer` активны.
- [ ] Логи: нет circuit-breaker OPEN для local/remote storage, нет ошибок старта.
- [ ] Traefik/порты: наружу только 443/80 (+ 5433 allow admin, 8890 allow admin).

## 5. Two-DB sync (после переключения)

- [ ] `DB_REMOTE_HOST` на admin → `188.127.240.124`.
- [ ] Ручной «Синхронизация в 1 клик» из admin проходит (см. #175).
- [ ] `AutoOneClickSyncScheduler` без ошибок.

## 6. Старый хост

- [ ] После истечения TTL `access.log` на `188.119.64.111` — новых запросов нет (~2 ч).
- [ ] Данные не изменились на старом хосте (заморозка держится).

## Роли

| Кто | Что |
|---|---|
| агент | прогон `migration-smoke.sh` (до/после), проверки БД/MinIO, отчёт |
| владелец | финальная UI-проверка (сайт, плеер, оплата), решение о тест-платеже, гашение старых серверов |
