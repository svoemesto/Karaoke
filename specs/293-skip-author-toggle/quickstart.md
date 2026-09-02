# Quickstart: 293 — Галочка «Работа со SKIP-авторами и песнями»

**Status**: Phase 1 design | **Date**: 2026-09-02

Этот документ — runnable validation guide для фичи. Описывает prerequisites,
шаги развёртывания, проверочные сценарии end-to-end.

## Prerequisites

- Запущенный локальный Karaoke (`docker compose up` из `deploy/`,
  см. `deploy/do.sh`). Karaoke-web на `localhost:8897`, karaoke-db в
  контейнере `karaoke-db`.
- Аккаунт администратора в webvue3 (для редактирования пользователей).
- Минимум 1 тестовый редактор (`is_editor = true`) в `tbl_site_users`.
- Минимум 1 SKIP-автор (`tbl_authors.skip = true`) с хотя бы одной
  песней (с тегом `SKIP` или без).
- Минимум 1 НЕ-SKIP-автор с SKIP-песней (тег `SKIP` в `tbl_songs.tags`).

## Setup / Apply

### Шаг 1: Применить миграцию V45 локально

```bash
# Из корня Karaoke:
docker exec -i karaoke-db psql -U postgres -d karaoke \
    < deploy/karaoke-db/45_site_user_can_work_with_skipped.sql

# Проверить:
docker exec -i karaoke-db psql -U postgres -d karaoke \
    -c "SELECT column_name, data_type FROM information_schema.columns
        WHERE table_name = 'tbl_site_users' AND column_name = 'can_work_with_skipped';"
# Ожидается: 1 строка, boolean.
```

### Шаг 2: Пересобрать backend

```bash
# На nsa-i9/nsa — также :karaoke-app:bootJar (см. AGENTS.md, Pass 282)
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
./gradlew :karaoke-web:ktlintCheck
./gradlew :karaoke-web:bootJar :karaoke-app:bootJar --parallel
```

### Шаг 3: Пересобрать frontend (webvue3 + karaoke-public)

```bash
cd webvue3 && npm run build && npm run lint && npm run format:check
cd ../karaoke-public && npm run build && npm run lint && npm run format:check
```

### Шаг 4: Пересобрать Docker-образы

```bash
cd deploy && bash do.sh build_webvue3
# Если менялся karaoke-public:
bash do.sh build_public
```

### Шаг 5: Перезапустить локальные контейнеры

> ⚠️ **НЕ перезапускать `karaoke-app`** на nsa-i9/nsa без явного согласия
> пользователя (см. AGENTS.md, машинно-специфичное исключение).

```bash
# Перезапуск только karaoke-web (он подхватит обновлённый bootJar):
bash do.sh start_web  # или эквивалент

# Перезапуск фронтов (webvue3, karaoke-public) — обычно автоматически
# при build_* командах, но если нет:
bash do.sh start_webvue3
bash do.sh start_public
```

### Шаг 6: Применить миграцию на прод (ТОЛЬКО после локальной проверки)

> ⚠️ Требует явного согласия пользователя (Constitution, AGENTS.md).

```bash
ssh root@${PROD_HOST:-188.119.64.111} \
    'docker exec -i karaoke-db psql -U postgres -d karaoke \
     < /root/Karaoke/deploy/karaoke-db/45_site_user_can_work_with_skipped.sql'
```

## Validation Scenarios

### Сценарий 1: Регресс-тест для анонимного пользователя (SC-003)

**Цель**: убедиться, что для анонима поведение НЕ изменилось.

```bash
# 1. До фичи (или на текущем state) — записать baseline:
curl -s 'http://localhost:8897/api/public/zakroma' \
    -o /tmp/zakroma_anon_before.json
curl -s 'http://localhost:8897/api/public/zakroma?author=Skip-автор Тест' \
    -o /tmp/zakroma_skip_anon_before.json

# 2. После применения фичи — повторить:
curl -s 'http://localhost:8897/api/public/zakroma' \
    -o /tmp/zakroma_anon_after.json
curl -s 'http://localhost:8897/api/public/zakroma?author=Skip-автор Тест' \
    -o /tmp/zakroma_skip_anon_after.json

# 3. Сравнить:
diff /tmp/zakroma_anon_before.json /tmp/zakroma_anon_after.json
# Ожидается: NO diff.
diff /tmp/zakroma_skip_anon_before.json /tmp/zakroma_skip_anon_after.json
# Ожидается: NO diff (SKIP-автор по-прежнему скрыт для анонима).
```

### Сценарий 2: Админ выдаёт галочку (US1)

**Цель**: проверить, что админ может выставить галочку и она сохранится в БД.

1. Открыть `webvue3` (обычно `http://localhost:8899/` или другой порт —
   см. `deploy/do.sh`).
2. В списке пользователей найти тестового редактора (например,
   `editor@example.com`).
3. Открыть карточку редактирования.
4. Найти новый блок «Может работать со SKIP-авторами и песнями:»
   (должен быть после блока «Может сам назначать себе задания»).
5. Поставить галочку. Нажать «Сохранить».
6. **Проверка**:
   ```bash
   docker exec -i karaoke-db psql -U postgres -d karaoke \
       -c "SELECT can_work_with_skipped FROM tbl_site_users WHERE email = 'editor@example.com';"
   # Ожидается: t (true).
   ```

### Сценарий 3: Редактор видит SKIP-контент (US2)

**Цель**: проверить, что редактор с галочкой видит SKIP-авторов и SKIP-песни.

1. Выйти из webvue3 (выйти как админ — или открыть приватное окно).
2. Открыть `karaoke-public` (`http://localhost:8897/`).
3. Залогиниться как редактор (`editor@example.com`).
4. Перейти в «Закрома» (`/zakroma`).
5. **Проверка A**: в списке авторов виден «Skip-автор Тест»
   (раньше был скрыт для не-админов).
6. Кликнуть на «Skip-автор Тест» → открывается подробка с песнями.
7. **Проверка B**: в карточке автора виден бейдж «SKIP» рядом с именем.
8. **Проверка C**: в списке песен видна SKIP-песня с тегом `SKIP`, и
   рядом с ней — бейдж «SKIP».

### Сценарий 4: API-проверка для редактора (SC-002)

**Цель**: убедиться, что `/api/public/zakroma` возвращает SKIP-контент
для пользователя с галочкой.

```bash
# 1. Получить токен редактора (login endpoint):
TOKEN=$(curl -s -X POST 'http://localhost:8897/api/public/auth/login' \
    -H 'Content-Type: application/json' \
    -d '{"email":"editor@example.com","password":"..."}' \
    | jq -r .token)

# 2. Запросить Закрома с токеном:
curl -s 'http://localhost:8897/api/public/zakroma' \
    -H "Authorization: Bearer $TOKEN" \
    -o /tmp/zakroma_editor.json

# 3. Проверить, что в ответе есть Skip-автор:
jq '.[].author' /tmp/zakroma_editor.json | grep -i 'Skip-автор'
# Ожидается: найдено хотя бы одно совпадение.

# 4. Контрольный запрос БЕЗ токена — Skip-автор НЕ должен быть:
curl -s 'http://localhost:8897/api/public/zakroma' \
    -o /tmp/zakroma_anon.json
jq '.[].author' /tmp/zakroma_anon.json | grep -i 'Skip-автор'
# Ожидается: NO match (пустой grep).
```

### Сценарий 5: Share-link запрещён для SKIP-песни (SC-006)

**Цель**: убедиться, что share-link для SKIP-песни возвращает 409 Conflict.

```bash
# Получить токен редактора:
TOKEN=$(...)

# Попытка создать share-link на SKIP-песню (id известен):
curl -s -X POST 'http://localhost:8897/api/public/share/create' \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d '{"songId": <SKIP_SONG_ID>}' \
    -w "\nHTTP_CODE: %{http_code}\n"
# Ожидается:
#   HTTP_CODE: 409
#   Тело: {"error":"share_link_forbidden","message":"Невозможно создать share-link для SKIP-контента"}
```

### Сценарий 6: Админ видит колонку в таблице (US3)

1. Открыть `webvue3` → список пользователей.
2. **Проверка**: новая колонка «SKIP-доступ» отображается в таблице,
   показывает ✓ для редакторов с галочкой, пусто для остальных.

### Сценарий 7: История прослушиваний

1. Залогиниться как редактор с галочкой в `karaoke-public`.
2. Послушать SKIP-песню (если доступ к ней не через share-link, а
   через прямой URL — см. сценарий 3).
3. Перейти в «Историю прослушиваний» (`/account/history`).
4. **Проверка**: SKIP-песня видна в истории (раньше была отфильтрована).

## Cleanup / Rollback

```bash
# 1. Удалить галочку у тестовых пользователей (через webvue3).

# 2. Если нужно откатить миграцию на LOCAL:
docker exec -i karaoke-db psql -U postgres -d karaoke -c "
ALTER TABLE public.tbl_site_users DROP COLUMN IF EXISTS can_work_with_skipped;
CREATE OR REPLACE FUNCTION public.update_tbl_site_users_recordhash() RETURNS trigger
    LANGUAGE plpgsql AS \$\$
BEGIN
    -- [прежняя версия триггера БЕЗ can_work_with_skipped]
    NEW.recordhash = md5(...);
    RETURN NEW;
END;
\$\$;
UPDATE public.tbl_site_users SET recordhash = md5(...) WHERE id > 0;
"

# 3. Revert кода:
git revert <commit-hash>
./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel
cd webvue3 && npm run build
cd ../karaoke-public && npm run build
```

## Troubleshooting

| Проблема | Возможная причина | Решение |
|----------|-------------------|---------|
| Миграция не применена | Колонка отсутствует в БД | Повторить `docker exec` команду; проверить `flyway_schema_history` |
| Галочка в webvue3 не сохраняется | Не перезапущен karaoke-web после rebuild | `bash do.sh start_web` |
| Бейдж SKIP не виден в karaoke-public | Не пересобран karaoke-public | `cd karaoke-public && npm run build && bash do.sh build_public` |
| Sync LOCAL↔SERVER падает на tbl_site_users | recordhash-триггер не пересоздан | Перепроверить SQL миграции, запустить UPDATE вручную |
| Share-link для не-SKIP песни не работает | Избыточная блокировка в SongShareLinkService | Проверить, что условие `if (songHasSkipTag(...))` стоит ДО генерации токена, а не в обработчике ошибок |

## Связанные документы

- [spec.md](./spec.md) — фиче-спека
- [research.md](./research.md) — технические решения
- [data-model.md](./data-model.md) — модель данных
- [contracts/](./contracts/) — API-контракты
- `docs/features/editor-skipped-content-access.md` — per-feature документ
  (Constitution §VI FR-009, создаётся в том же PR)