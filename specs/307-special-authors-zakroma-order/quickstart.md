# Quickstart Validation Guide: Порядок плашек в Закромах и явная сортировка спец-авторов

**Date**: 2026-09-06
**Branch**: `307-special-authors-zakroma-order`
**Spec**: [spec.md](./spec.md)
**Data model**: [data-model.md](./data-model.md)
**API contract**: [contracts/authors-tiles-api.md](./contracts/authors-tiles-api.md)

Этот документ — пошаговая проверка end-to-end. Используйте его для ручной валидации после имплементации.

---

## Prerequisites

- Доступ к admin-машине (LOCAL Postgres, исходники Karaoke).
- Доступ к прод-серверу (PROD Postgres, через SSH).
- `psql` клиент для проверки БД.
- Браузер (Chromium / Firefox / Safari) для проверки `/zakroma`.

---

## Шаг 0: Подготовка (до имплементации)

1. Убедиться, что ветка `307-special-authors-zakroma-order` активна:
   ```bash
   git branch --show-current
   # Ожидаемый вывод: 307-special-authors-zakroma-order
   ```

2. Убедиться, что зависимости установлены (стандартный `karaoke-public` + `karaoke-web`):
   ```bash
   cd /home/nsa/Karaoke
   ls deploy/karaoke-db/ | tail -5
   # Должны быть файлы 45_*.sql
   ```

---

## Шаг 1: Применение миграции на LOCAL

```bash
cd /home/nsa/Karaoke/deploy
psql -h localhost -U postgres -d karaoke -f karaoke-db/46_author_sort_order.sql
```

### Ожидаемый вывод

```
ALTER TABLE
CREATE FUNCTION
UPDATE <число>
```

### Проверка

```bash
psql -h localhost -U postgres -d karaoke -c "\d tbl_authors" | grep sort_order
# Ожидаемый вывод:
#  sort_order | integer | not null | default 0

psql -h localhost -U postgres -d karaoke -c "SELECT count(*) FROM tbl_authors WHERE sort_order = 0"
# Ожидаемый вывод: count(*) > 0 (все существующие авторы имеют sort_order = 0)
```

**Если ошибка**: см. SC-004 (идемпотентность). Повторный запуск миграции не должен падать.

---

## Шаг 2: Применение миграции на PROD

```bash
# Через стандартный deploy-процесс (см. deploy/deploy_web.sh или руками через SSH):
ssh prod-server "psql -U postgres -d karaoke -f /path/to/46_author_sort_order.sql"
```

### Проверка (тот же вывод, что и для LOCAL)

```bash
ssh prod-server "psql -U postgres -d karaoke -c '\d tbl_authors' | grep sort_order"
# Должно быть: sort_order | integer | not null | default 0
```

**⚠️ Не продолжать, если миграция применена только на одной стороне** (см. FR-013, Clarification Q4, Edge Case «рассинхрон схемы»).

---

## Шаг 3: Сборка бэкенда

```bash
cd /home/nsa/Karaoke
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
```

### Ожидаемый результат

```
BUILD SUCCESSFUL in <N>s
```

**Если ошибка линтера**:
```bash
./gradlew :karaoke-web:ktlintCheck
# Исправить нарушения вручную или через ktlintFormat
```

### Дальнейшая сборка

```bash
./gradlew :karaoke-web:bootJar --parallel
```

Образ `karaoke-web` готов к деплою.

---

## Шаг 4: Деплой на PROD (если работаем с прода)

```bash
cd /home/nsa/Karaoke/deploy
bash deploy_web.sh
```

### Проверка деплоя

```bash
ssh prod-server "docker ps | grep karaoke-web"
# Ожидаемый вывод: контейнер karaoke-web запущен с новым образом
```

---

## Шаг 5: Визуальная проверка `/zakroma` (SC-001)

1. Открыть в браузере: `https://<ваш-домен>/zakroma` (или `http://localhost:8897/zakroma` на LOCAL).
2. **Проверить**: первый тайл в первой строке сетки — «Отдельные песни разных авторов» с иконкой 📁.

**Скриншот для архива** (опционально): сохранить как `zakroma-after.png`.

---

## Шаг 6: SQL-проверка порядка (SC-002)

```sql
-- Найти авторов с разными sort_order:
SELECT id, author, sort_order
FROM tbl_authors
WHERE skip = false
  AND is_special_order = false
  AND (ready_songs_count > 0 OR total_songs_count > 0)
ORDER BY sort_order ASC, author ASC
LIMIT 20;
```

### Ожидаемый порядок

- Сначала отрицательные `sort_order` (от меньшего к большему).
- Затем положительные ненулевые (от меньшего к большему).
- Затем `sort_order = 0` — в алфавитном порядке по `author`.

### Сравнение с API

```bash
curl 'https://<ваш-домен>/api/public/authors-tiles?scope=main' | \
  jq -r '.[] | "\(.sortOrder)\t\(.author)"'
```

### Ожидаемый вывод

Должен совпадать с SQL-выдачей выше (порядок идентичен).

---

## Шаг 7: End-to-end проверка редактирования `sort_order` (SC-003)

1. В админке (`https://<admin-домен>/authors`) открыть таблицу `Authors`.
2. Найти автора с `sort_order = 0`. Отредактировать ячейку `sort_order`, ввести `100`.
3. Сохранить.
4. Подождать **≤60 секунд** (TTL кеша `authorsTilesCache`).
5. Открыть `/zakroma` в публичной части.
6. **Проверить**: этот автор теперь находится среди ненулевых `sort_order` (выше всех нулевых), в правильном порядке (если есть другие ненулевые — см. Шаг 6).

### Если не появился за 60 секунд

1. Проверить, что миграция применена на обеих сторонах (Шаг 1, 2).
2. Проверить, что бэкенд перезапущен с новым кодом (Шаг 4).
3. Проверить `console.log` в браузере на ошибки API `/authors-tiles`.
4. Проверить, что `sortOrder` действительно записан в БД:
   ```sql
   SELECT id, author, sort_order FROM tbl_authors WHERE author = '<имя автора>';
   ```
5. Если значение в БД есть, а в API его нет — возможно, закешировано старое значение. Подождать ещё 60 секунд или дёрнуть `consumeDirty()` через `StatBySong` (через изменение любой песни).

---

## Шаг 8: Per-feature документ (SC-007)

```bash
ls docs/features/zakroma-tiles-sort-order.md
# Должен существовать

head -30 docs/features/zakroma-tiles-sort-order.md
# Должен содержать ссылку на specs/307-special-authors-zakroma-order/spec.md
```

Если документа нет — это блокер для merge (FR-012).

---

## Шаг 9: PR-чеклист (FR-013)

Перед merge убедиться, что в PR-описании отмечены:

```markdown
- [x] Миграция `46_author_sort_order.sql` применена на LOCAL
- [x] Миграция `46_author_sort_order.sql` применена на PROD
- [x] Линтеры ktlint + ESLint прошли
- [x] Бэкенд собран (`karaoke-web:bootJar`)
- [x] Фронт собран (`npm run build` для webvue3 и karaoke-public, если менялся)
- [x] Per-feature документ `docs/features/zakroma-tiles-sort-order.md` создан
- [x] Скриншот `/zakroma` с новым порядком прикреплён
- [x] Ручная проверка API `/authors-tiles` показала новое поле `sortOrder` в JSON
```

---

## Шаг 10: Откат (если что-то пошло не так)

### Откат миграции БД (если нужно)

```sql
-- Удаление колонки (не идемпотентно, но безопасно):
ALTER TABLE public.tbl_authors DROP COLUMN IF EXISTS sort_order;

-- Восстановление recordhash-триггера без sort_order (см. миграцию 27_author_special_order.sql).
-- Это критично — без правильного recordhash sync сломается.
```

### Откат кода

```bash
git revert <commit-sha>
# или
git checkout master
```

### Откат деплоя

```bash
# Пересобрать старый bootJar и передеплоить через deploy_web.sh
```

**⚠️ Внимание**: если прод-миграция применена, откат кода без отката миграции невозможен — `recordhash` на SERVER будет включать `sort_order`, а на LOCAL (без миграции) колонки нет. Синхронизация сломается.

---

## Сводный чек-лист для исполнителя

```markdown
- [ ] Шаг 1: миграция на LOCAL
- [ ] Шаг 2: миграция на PROD
- [ ] Шаг 3: сборка бэкенда
- [ ] Шаг 4: деплой
- [ ] Шаг 5: визуальная проверка /zakroma
- [ ] Шаг 6: SQL-проверка порядка
- [ ] Шаг 7: end-to-end редактирование sort_order
- [ ] Шаг 8: per-feature документ
- [ ] Шаг 9: PR-чеклист
- [ ] Шаг 10: готовность к откату (план зафиксирован)
```

После прохождения всех шагов — задача готова к `mark-review` в OpenProject (#56).
