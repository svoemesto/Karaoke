# Quickstart: 262-search-pagination

Этот документ — руководство по валидации фичи «Пагинация / динамическая
подгрузка результатов поиска» в продакшен-окружении (или staging).
Все сценарии ниже запускаются вручную; автоматизированных тестов в CI нет
(см. AGENTS.md «Тесты: в CI нет»).

## Prerequisites

- Запущенный бэкенд (`karaoke-web`) с подключением к БД karaoke
  (таблица `tbl_songs`, ≥100 песен для тестов на малом объёме, ≥500 —
  для основного сценария).
- Запущенный фронтенд (`karaoke-public`) на `http://localhost:8081`
  (или production URL).
- Установленные `curl`, `jq` (опционально, для парсинга JSON), браузер
  (Chrome/Firefox актуальных версий).
- Базовые знания `PublicApiController.songs` (`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:650`).

## Setup

1. Убедиться, что feature-ветка `262-search-pagination` собрана и задеплоена:
   ```bash
   git checkout 262-search-pagination
   ./gradlew clean karaoke-app:bootjar karaoke-web:bootjar --parallel
   deploy/deploy_web.sh   # или локальный запуск
   ```

2. Открыть `http://localhost:8081/search` в браузере.

## Backend validation

### V1: базовый запрос с пагинацией

```bash
curl -s 'http://localhost:8081/api/public/songs?songName=Михайлов&page=1&pageSize=10' | jq .
```

**Ожидаемый результат**:
- HTTP 200.
- JSON — **объект** с полями `items`, `totalCount`, `page`, `pageSize`, `hasMore`.
- `items.length <= 10`.
- `page == 1`, `pageSize == 10`.
- `totalCount` — целое число, не зависит от `page`/`pageSize`.

**Проверка соответствия SC-001**: `items.length <= pageSize` (10), а не
весь массив результатов.

### V2: стабильность totalCount между страницами

```bash
COUNT1=$(curl -s 'http://localhost:8081/api/public/songs?songName=Михайлов&page=1&pageSize=10' | jq '.totalCount')
COUNT2=$(curl -s 'http://localhost:8081/api/public/songs?songName=Михайлов&page=2&pageSize=10' | jq '.totalCount')
[ "$COUNT1" = "$COUNT2" ] && echo "PASS: totalCount consistent ($COUNT1)" || echo "FAIL: $COUNT1 != $COUNT2"
```

**Ожидаемый результат**: `PASS: totalCount consistent (NN)`.

### V3: непересечение элементов между страницами

```bash
IDS1=$(curl -s 'http://localhost:8081/api/public/songs?songName=Михайлов&page=1&pageSize=10' | jq -c '[.items[].id]')
IDS2=$(curl -s 'http://localhost:8081/api/public/songs?songName=Михайлов&page=2&pageSize=10' | jq -c '[.items[].id]')
DUPES=$(jq -n "$IDS1 + $IDS2" | jq 'group_by(.) | map(select(length>1)) | length')
[ "$DUPES" = "0" ] && echo "PASS: no duplicates between pages" || echo "FAIL: $DUPES duplicates"
```

**Ожидаемый результат**: `PASS: no duplicates between pages`.

### V4: стабильность порядка (повторный вызов)

```bash
IDS_A=$(curl -s 'http://localhost:8081/api/public/songs?songName=Михайлов&page=1&pageSize=10' | jq -c '[.items[].id]')
IDS_B=$(curl -s 'http://localhost:8081/api/public/songs?songName=Михайлов&page=1&pageSize=10' | jq -c '[.items[].id]')
[ "$IDS_A" = "$IDS_B" ] && echo "PASS: stable order" || echo "FAIL"
```

**Ожидаемый результат**: `PASS: stable order`.

### V5: `hasMore` корректен на границе

```bash
# Запрос, возвращающий ровно 10 песен
RESP=$(curl -s 'http://localhost:8081/api/public/songs?songName=РедкийТермин&page=1&pageSize=10')
TOTAL=$(echo "$RESP" | jq '.totalCount')
HAS_MORE=$(echo "$RESP" | jq '.hasMore')
[ "$TOTAL" = "10" ] && [ "$HAS_MORE" = "false" ] && echo "PASS: hasMore=false on exact boundary" || echo "FAIL (total=$TOTAL, hasMore=$HAS_MORE)"
```

**Ожидаемый результат**: `PASS: hasMore=false on exact boundary`.

### V6: обратная совместимость (старый формат)

```bash
curl -s 'http://localhost:8081/api/public/songs?songName=Михайлов' | jq 'type'
```

**Ожидаемый результат**: `"array"` — старый формат сохранён.

### V7: нормализация невалидных параметров

```bash
# pageSize=99 — не из списка → должен нормализоваться к 35
curl -s 'http://localhost:8081/api/public/songs?songName=Михайлов&pageSize=99' | jq '.pageSize'
```

**Ожидаемый результат**: `35`.

### V8: пустой результат

```bash
RESP=$(curl -s 'http://localhost:8081/api/public/songs?songName=абвгдежз&page=1&pageSize=35')
echo "$RESP" | jq '{items: .items | length, totalCount, hasMore}'
```

**Ожидаемый результат**: `{"items": 0, "totalCount": 0, "hasMore": false}`.

### V9: скорость первой порции (SC-002)

Запрос с ≥500 результатов (например, `text=любовь` на проде):

```bash
time curl -s 'http://localhost:8081/api/public/songs?text=любовь&page=1&pageSize=35' -o /dev/null
```

**Ожидаемый результат**: <1s (baseline был — полный возврат всех песен,
≥3s на проде). Сравнить с baseline **до изменений** — должно быть
ощутимо быстрее.

## Frontend validation

### V10: первая порция + счётчик «X из Y»

1. Открыть `http://localhost:8081/search`.
2. Ввести в поле «Автор» значение, возвращающее много песен
   (например, `Михайлов` или `Smith`).
3. Нажать «Искать».

**Ожидаемый результат**:
- В списке — ровно 35 (или меньше) результатов.
- В верхней части списка — счётчик «Показано 35 из NN» (где NN —
  общее число совпадений).
- Внизу списка — кнопка «Загрузить ещё».

### V11: подгрузка следующей порции

1. После V10 — нажать «Загрузить ещё».

**Ожидаемый результат**:
- В списке появляются **новые** строки (35 штук), дописанные в конец.
- Счётчик обновляется: «Показано 70 из NN».
- URL в адресной строке: `?page=2` (без `&pageSize`, если только он не менялся).
- Ранее отрисованные строки остаются на месте (нет «прыжка» скролла).

### V12: повторные клики «Загрузить ещё» — rapid-click protection

1. После V10 — дважды быстро кликнуть «Загрузить ещё».

**Ожидаемый результат**: подгружается **ровно одна** порция (35 строк),
а не две. Кнопка становится `disabled` на время запроса.

### V13: окончание списка

1. Кликать «Загрузить ещё», пока `hasMore == true`.
2. На последней странице кнопка исчезает; счётчик показывает «Показано
   X из X».

### V14: F5-устойчивость

1. Загрузить 3 страницы результатов (через «Загрузить ещё»).
2. Скопировать URL из адресной строки.
3. Нажать F5.

**Ожидаемый результат**:
- Страница загружается, в URL — `?page=3` (или восстановлен из
  `localStorage`).
- Список содержит все подгруженные ранее результаты (или показывает
  индикатор «Загрузите предыдущие страницы» — допустимо по Story 1).
- Счётчик «X из Y» соответствует восстановленному состоянию.

### V15: shareable URL

1. Загрузить 2 страницы.
2. Скопировать URL вида `http://localhost:8081/search?author=Михайлов&page=2`.
3. Открыть в **приватном окне** (чистая сессия).

**Ожидаемый результат**: страница открывается с тем же результатом —
2-я страница видна, фильтр «Автор=Михайлов» применён.

### V16: ошибка подгрузки (retry)

1. Открыть DevTools → Network → «Block request URL» → заблокировать
   `/api/public/songs?author=...&page=2`.
2. Кликнуть «Загрузить ещё».

**Ожидаемый результат**:
- Появляется inline-сообщение «Не удалось загрузить ещё. Повторить?»
  с кнопкой retry.
- Ранее загруженные результаты остаются на экране.
- Разблокировать URL → retry работает, новая порция подгружается.

### V17: смена фильтра сбрасывает пагинацию

1. Загрузить 3 страницы для автора «Михайлов».
2. Изменить поле «Автор» на «Петров», нажать «Искать».

**Ожидаемый результат**:
- Список заменяется на новую первую порцию (для «Петров»).
- URL: `?author=Петров&page=1`.
- Счётчик: «Показано 35 из NN».

### V18: регресс спеки 261 (UI строки)

1. Любой запрос с результатами → проверить, что **все** элементы
   спеки 261 работают на любой странице (1, 2, 3, …):
   - Иконка плеера — зелёная/золотая/серая в зависимости от доступа.
   - Превью альбома — загружается или плейсхолдер «♪».
   - Превью автора — загружается или плейсхолдер «👤».
   - Клик по названию → `/song?id=...`.
   - Клик по имени автора → `/zakroma/...`.
   - Подпись «Автор - год, альбом».
   - Иконка «В корзину» для премиум-контента.
   - Подпись «В эфире с ДД.ММ.ГГГГ» / «В эфире до ДД.ММ.ГГГГ».

### V19: мобильный вьюпорт

1. Открыть DevTools → Toggle Device Toolbar → iPhone SE (или другой
   узкий viewport).
2. Повторить V10–V17.

**Ожидаемый результат**: те же сценарии работают; кнопка «Загрузить ещё»
полная ширина экрана; строки адаптируются через CSS (как в спеке 261).

## Smoke checklist

| # | Что проверяется | Где | Ожидаемый результат |
|---|---|---|---|
| V1 | Базовый запрос с пагинацией | curl | JSON-обёртка, ≤35 элементов |
| V2 | `totalCount` одинаков для всех страниц | curl | `PASS` |
| V3 | ID не пересекаются между страницами | curl | `PASS` |
| V4 | Порядок стабилен между вызовами | curl | `PASS` |
| V5 | `hasMore=false` на границе | curl | `PASS` |
| V6 | Старый формат сохранён (без `page`/`pageSize`) | curl | `"array"` |
| V7 | Невалидный `pageSize=99` → `35` | curl | `35` |
| V8 | Пустой результат | curl | `totalCount=0`, `hasMore=false` |
| V9 | Скорость первой порции (SC-002) | curl | <1s, заметно быстрее baseline |
| V10 | UI: первая порция + счётчик | браузер | OK |
| V11 | UI: подгрузка следующей порции | браузер | OK |
| V12 | UI: rapid-click protection | браузер | только одна подгрузка |
| V13 | UI: окончание списка | браузер | кнопка исчезает |
| V14 | UI: F5-устойчивость | браузер | восстановление среза |
| V15 | UI: shareable URL | браузер | работает |
| V16 | UI: ошибка + retry | браузер | inline-сообщение + retry |
| V17 | UI: смена фильтра сбрасывает пагинацию | браузер | OK |
| V18 | UI: регресс спеки 261 | браузер | OK |
| V19 | UI: мобильный вьюпорт | браузер | OK |

Если все 19 пунктов PASS — фича готова к деплою.

## Что НЕ покрывается

- **Виртуальный скролл** — отдельная спека (см. Story 1 Edge Cases).
- **Keyset-пагинация** — отдельная оптимизация, не в рамках 262.
- **Изменение pageSize через UI** — `pageSize` меняется только через
  URL (для shareable-ссылок); UI-контрол «размер страницы» не входит
  в спеку.
- **Server-side rendering / SEO** — не затрагивается, `?page=N` —
  стандартная семантика для поисковых роботов.