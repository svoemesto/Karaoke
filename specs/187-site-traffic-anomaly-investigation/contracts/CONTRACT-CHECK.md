# Contract Check: Обратная совместимость изменений

**Spec**: [spec.md](./spec.md) | **Date**: 2026-08-14

Этот чек-лист проверяет, что ни одно из изменений не ломает обратную совместимость. FR-019 явно требует этого.

## Проверка изменений

### C1: `AuthorTilePublicDto.authorPictureUrl`
- [ ] Старый формат `/api/public/picture?file=...` всё ещё принимается endpoint'ом (FR-001).
- [ ] Если где-то в `karaoke-public` всё ещё есть вызовы старого формата — они продолжают работать через 302 redirect.
- [ ] Если где-то в `webvue3` есть вызовы — продолжают работать.

### C2: `song-picture` rate-limit
- [ ] При ≤ 60 req/мин — поведение ИДЕНТИЧНО baseline (тот же 200 OK, тот же PNG).
- [ ] При > 60 req/мин — 429 + `Retry-After` (новый код ошибки, раньше не было).

### C3: `song-vk-image` rate-limit
- [ ] Аналогично C2.

### C4: `news/since` server-side cache
- [ ] Ответ ИДЕНТИЧЕН baseline (тот же JSON).
- [ ] Свежесть данных — может лагать на ≤ 60 сек (приемлемо для news, спека Pass 52).
- [ ] Для анонимов — пустой массив (Pass 52 поведение сохранено).

### C5: `chat/unreadcount` server-side cache
- [ ] Ответ ИДЕНТИЧЕН baseline.
- [ ] Свежесть данных — может лагать на ≤ 10 сек (приемлемо для бейджа, polling и так 20 сек).

### C6: `share/heartbeat` server-side cache
- [ ] Семантически эквивалентно: lease продлевается фактически раз в (25 - 15) = 10 сек минимум.
- [ ] Heartbeat response: `{"ok": true}` (без изменений).

### C7: `/api/public/debug/db`
- [ ] По умолчанию ОТКЛЮЧЕН (404). Не виден обычным юзерам.
- [ ] Если IP в allowlist — отдаёт JSON. Иначе — 403.

### C8: nginx `/minio/` cache headers
- [ ] Файлы из MinIO по-прежнему доступны (не блокируется).
- [ ] Добавляются cache headers (новый функционал).
- [ ] 404 ошибки от MinIO кешируются на 5 минут (раньше nginx кешировал ответ как есть).

### C9: KaraokeProperties env-переменные
- [ ] Дефолты разумные и безопасные.
- [ ] Если переменная не задана — берётся дефолт (no crash).
- [ ] Переменные читаются через `KaraokeProperties.getXxx` (стандартный паттерн).

### C10: `doRegisterEvent` sampling/dedup
- [ ] Для `eventType=PLAY/CLICK/PLAYER/UI` — поведение НЕ МЕНЯЕТСЯ (100% INSERT).
- [ ] Для `eventType=CALL_REST` — sampling применяется, но админ видит 100% (rate=1/1).
- [ ] Логирование SQLException не подавляет исходное исключение.

### C11: `EventsRetentionScheduler`
- [ ] Удаляет ТОЛЬКО строки из `tbl_events` (не трогает другие таблицы).
- [ ] Условие `last_update < now() - interval 'N days'` — безопасно (не удаляет свежие).
- [ ] Не влияет на sync (таблица не зарегистрирована в `SyncRegistry`).

---

## Зависимости от других фич

| Зависимость | Что проверяем |
|---|---|
| Pass 52 (news/since anon → empty) | Анонимный кэш должен отдавать `{"news": []}` (не из БД) |
| Pass 60 (SEO HTML for bots) | Rate-limit `song-picture` / `song-vk-image` дополняет, не дублирует |
| spec 174 (stats connection leak) | Используем ту же модель `StatsCacheScheduler` для polling cache |
| spec 167 (share heartbeat 500) | Rate-limit НЕ применяется к `/api/public/share/heartbeat` (уже не INSERT'ит) |

---

## Миграция данных

**Не требуется**. Ни одна таблица БД не меняется. `tbl_events` только чистится retention scheduler'ом.

---

## Необходимые действия на стороне пользователя при деплое

1. Накатить env-переменные через `do.env` (см. `AGENTS.md` секция «Деплой»):
   ```
   KARAOKE_WEB_EVENTS_SAMPLING_ANON=20
   KARAOKE_WEB_EVENTS_SAMPLING_LOGGED=5
   KARAOKE_WEB_EVENTS_SAMPLING_ADMIN=1
   KARAOKE_WEB_EVENTS_DEDUP_TTL_SECONDS=30
   KARAOKE_WEB_EVENTS_RETENTION_DAYS=7
   KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS=127.0.0.1,::1,<admin-ip>
   ```
2. Скопировать обновлённый `deploy/80to8897` в `/etc/nginx/sites-enabled/` и сделать `nginx -t && nginx -s reload` (Constitution governance #6).
3. Перезапустить karaoke-web через `deploy/do.sh build_start_web` (Constitution Principle I — пользователь делает деплой).

---

## Чек-лист перед merge

- [ ] Все изменения backward-compatible (см. выше).
- [ ] Новые env-переменные имеют разумные дефолты.
- [ ] nginx reload безопасен (`nginx -t` первым).
- [ ] KDoc/JSDoc на новых классах (FR-020, Constitution FR-006).
- [ ] `docs/features/site-traffic-resilience.md` создан (FR-020).
- [ ] `docs/features/README.md` обновлён (FR-020).
- [ ] Pre-commit проверка: `git ls-files | grep -iE '\.env$|do\.env$'` → пусто (Constitution VIII.3).

---

## См. также

- [api-changes.md](./api-changes.md) — детальное описание API-контрактов.
- [spec.md](../spec.md) — FR-019 (обратная совместимость).
- [plan.md](../plan.md) — порядок внедрения изменений.
