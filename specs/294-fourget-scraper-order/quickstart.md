# Quickstart: 294 — Порядок и состав scraper'ов fourget

**Дата**: 2026-09-02 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Это **validation guide** — набор команд и сценариев для end-to-end
проверки фичи **после** её реализации. Используется разработчиком
для smoke-теста изменений перед PR, и пользователем — для приёмки
после деплоя.

**Предварительно**: фича реализована, изменения в
`Tools.kt`/`KaraokeProperties.kt`/`ToolsTest.kt` смержены в `master`,
новая версия `karaoke-app` собрана и развёрнута.

## Prerequisites

- Локально или на admin-машине работает `fourget` (Docker-контейнер
  `fourget`, порт 80, `lyrics-search.base-url: http://fourget:80`).
- `karaoke-app` (или `karaoke-web`, если поиск текста используется
  через `karaoke-web`) собран и запущен.
- Доступ к логам `karaoke-app` (файл или stdout, формат логов —
  см. `docs/ops/log-correlation.md`).
- Доступ к `/sm-karaoke/system/Karaoke.properties` (или эквивалентный
  механизм — UI настроек Karaoke).

## Setup

### 1. Проверить, что новые свойства зарегистрированы

```bash
# Запустить поиск по одному тестовому запросу (любой метод)
curl -X POST http://localhost:8080/api/.../findLyrics \
    -H "Content-Type: application/json" \
    -d '{"author":"Кино","songTitle":"Группа крови"}'

# В логах должны появиться строки (порядок важен):
grep "🔍 \[SearchTool\]" karaoke-app.log
# Ожидаемо:
# 🔍 [SearchTool] Запрос к fourget (scraper=yep): ...
# ✅ [SearchTool] scraper=yep — найдено URL: 5
# 🔧 [SearchTool] post-filter: было 5, осталось 5 (отброшено 0)
```

Если первой строкой идёт `(scraper=brave)` — значит, новый порядок не
применился (regression). Проверить `KaraokeProperties.kt` и
`Tools.kt`.

### 2. Проверить, что post-filter работает на мусорных URL

Через прямой вызов `SearchTool` (через unit-тест или integration
endpoint) или через модификацию запроса так, чтобы fourget вернул
известный мусор:

```bash
# Через unit-тест (см. раздел Tests ниже)
./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.llm.ToolsTest"
```

В логах прод при SC-005 (доля поисков с K>0) ожидаемо 5-30%.

### 3. Проверить fallback на brave при деградации yep

Чтобы временно «имитировать» деградацию yep — добавить в
`lyricsSearchScrapers` `mojeek` (заведомо нерабочий scraper) перед
`yep`:

```bash
# Через прямое изменение Karaoke.properties (если UI нет):
echo "lyricsSearchScrapers=mojeek;yep;brave" >> /tmp/kp-update.sh
# Или через UI настроек Karaoke: lyricsSearchScrapers = "mojeek;yep;brave"
```

После изменения (без перезапуска `karaoke-app`):
- Поиск сначала пробует `mojeek` → отбрасывается (HTTP 400 или пустой ответ)
- Потом `yep` → возвращает результат.
- В логах:
  ```
  ❌ [SearchTool] fourget (scraper=mojeek) вернул статус 400
  🔍 [SearchTool] Запрос к fourget (scraper=yep): ...
  ✅ [SearchTool] scraper=yep — найдено URL: 5
  ```

Восстановить: `lyricsSearchScrapers = "yep;brave"`.

### 4. Проверить fallback на brave при «мало URL от yep»

Чтобы имитировать «yep вернул мало URL», добавить третий scraper,
который вернёт пусто:

```bash
# Через UI: lyricsSearchScrapers = "yep;wikipedia;brave"
# (wikipedia не поддерживает lyrics-поиск — вернёт мало/пусто)
```

Ожидаемо в логах:
```
🔍 [SearchTool] Запрос к fourget (scraper=yep): ...
✅ [SearchTool] scraper=yep — найдено URL: 1
🔧 [SearchTool] post-filter: было 1, осталось 1 (отброшено 0)
🔍 [SearchTool] Запрос к fourget (scraper=wikipedia): ...
❌ [SearchTool] fourget (scraper=wikipedia) вернул статус ...
🔍 [SearchTool] Запрос к fourget (scraper=brave): ...
✅ [SearchTool] scraper=brave — найдено URL: 4
🔧 [SearchTool] post-filter: было 4, осталось 4 (отброшено 0)
```

Восстановить: `lyricsSearchScrapers = "yep;brave"`.

## Сценарии приёмки (acceptance scenarios из спеки)

### AC-US1.1: yep возвращает 5+ URL → brave не дёргается

**Команда**:
```bash
# Запустить поиск через UI или API endpoint
curl -X POST http://localhost:8080/api/.../findLyrics \
    -d '{"author":"Adele","songTitle":"Rolling in the Deep"}'
```

**Ожидаемо в логах**:
```
🔍 [SearchTool] Запрос к fourget (scraper=yep): http://fourget/api/v1/web?s=...
✅ [SearchTool] scraper=yep — найдено URL: 8
🔧 [SearchTool] post-filter: было 8, осталось 8 (отброшено 0)
```

**НЕ должно быть** строки `(scraper=brave)`.

### AC-US1.2: yep возвращает 1 URL → fallback на brave

**Имитация** (см. шаг 4 выше — добавить wikipedia перед brave):
```
lyricsSearchScrapers = "yep;wikipedia;brave"
```

**Ожидаемо**:
```
... scraper=yep — найдено URL: 1 ...
🔧 ... post-filter: было 1, осталось 1 (отброшено 0)
... scraper=wikipedia ... (ошибка или пусто)
... scraper=brave — найдено URL: N
🔧 ... post-filter: было N, осталось M (отброшено K)
```

**Результат поиска** = URL от brave (после post-filter).

### AC-US1.3: оба scraper'а деградировали → emptyList()

**Имитация**:
```
lyricsSearchScrapers = "wikipedia;qwant"  # оба нерабочих
```

**Ожидаемо**:
```
🔍 [SearchTool] Запрос к fourget (scraper=wikipedia): ...
❌ [SearchTool] fourget (scraper=wikipedia) вернул статус 400
🔍 [SearchTool] Запрос к fourget (scraper=qwant): ...
❌ [SearchTool] fourget (scraper=qwant) вернул captcha-redirect
```

**Результат** = `emptyList()`. `LyricsFinderService.findLyrics`
вернёт `success=false`.

### AC-US2.1: post-filter отбрасывает homepage/sitemap

**Имитация** (через unit-тест):
```bash
./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.llm.ToolsTest.filter отбрасывает homepage"
```

**Ожидаемо**: тест проходит, 4 из 10 URL отброшены (3 homepage + sitemap).

### AC-US2.2: post-filter дедуплицирует

**Имитация** (через unit-тест):
```bash
./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.llm.ToolsTest.filter убирает дубликаты"
```

**Ожидаемо**: тест проходит, из 5 одинаковых URL остаётся 1.

### AC-US2.3: post-filter отбрасывает tracking-маркеры

**Имитация** (через unit-тест):
```bash
./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.llm.ToolsTest.filter отбрасывает utm_source"
```

**Ожидаемо**: тест проходит.

### AC-US2.4: только мусор → fallback на следующий scraper

**Имитация**: добавить scraper, который возвращает только мусор
(например, через специальный тестовый fourget endpoint, или через
mock в unit-тесте). Проверить, что fallback срабатывает.

### AC-US3.1: изменение scrapers через KaraokeProperties без перезапуска

**Шаги**:
1. Найти работающий `karaoke-app`, отправить поисковый запрос.
2. В логах видно `(scraper=yep)` (текущий порядок).
3. Через UI настроек (или прямое изменение БД) — поменять
   `lyricsSearchScrapers` на `"brave"`.
4. Отправить тот же запрос.
5. В логах видно `(scraper=brave)`.

**НЕ должно быть**: рестарта `karaoke-app` между шагами 2 и 4.

## Tests

### Unit-тесты (автоматизированные)

```bash
# Все тесты в модуле karaoke-app (включая ToolsTest)
./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.llm.ToolsTest"

# Или все тесты модуля (медленнее)
./gradlew :karaoke-app:test
```

**Ожидаемо**:
- `ToolsTest` — все тесты проходят (`BUILD SUCCESSFUL`).
- Все остальные тесты — без regression (если тесты в Karaoke активны).

### Integration-тест (опционально, через docker-compose)

```bash
# Запустить fourget + karaoke-app локально
cd deploy && bash do.sh start_karaoke_app  # или эквивалент

# Отправить реальные запросы
curl -X POST http://localhost:8080/api/.../findLyrics \
    -H "Content-Type: application/json" \
    -d '{"author":"Кино","songTitle":"Группа крови"}'

# Проверить логи на наличие строк (см. сценарии выше)
docker logs karaoke-app 2>&1 | grep "SearchTool"
```

## Мониторинг (SC-001, SC-002, SC-005)

После деплоя в production, в течение 7 дней:

### SC-001: доля brave-запросов снижается

```bash
# Подсчёт scraper-вызовов за период (адаптировать к формату логов)
grep "🔍 \[SearchTool\] Запрос к fourget" karaoke-app.log.7days \
    | awk '{print $NF}' | sort | uniq -c
# Ожидаемо: yep >> brave (соотношение > 60/40 в пользу yep)
```

### SC-002: ошибки `Brave did not return a result object` снижаются

```bash
grep "Brave did not return a result object" karaoke-app.log.7days | wc -l
# Ожидаемо: < 5% от общего числа поисков текстов
```

### SC-005: доля поисков с post-filter отбрасыванием

```bash
# Поисков с K > 0 (что-то отброшено):
grep "🔧 \[SearchTool\] post-filter:" karaoke-app.log.7days \
    | grep -v "(отброшено 0)" | wc -l
# Всего поисков:
grep "🔧 \[SearchTool\] post-filter:" karaoke-app.log.7days | wc -l

# Доля = первое / второе. Ожидаемо: 5-30%.
```

### SC-006: unit-тесты проходят

```bash
./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.llm.ToolsTest"
# BUILD SUCCESSFUL
```

## Troubleshooting

### Проблема: первый scraper всё равно brave

- Проверить, что `KaraokeProperties.kt` содержит новые ключи в
  `listKaraokeProperties` (а не только дефолт-фолбэк).
- Проверить, что `Tools.kt#searchUrls` использует
  `lyricsSearchScrapersList()`, а не старую константу
  `LYRICS_SEARCH_SCRAPERS`.
- Проверить, что значение в `/sm-karaoke/system/Karaoke.properties`
  = `"yep;brave"`.

### Проблема: post-filter слишком агрессивен (отбрасывает легитимные URL)

- Проверить логи: `🔧 [SearchTool] post-filter: было N, осталось 0 (отброшено N)`.
- Если 0 — post-filter отбрасывает всё. Проверить `KaraokeProperties.lyricsSearchUselessUrlPatterns`
  (может быть повреждено/перезаписано).
- Временно через UI убрать паттерны, чтобы post-filter стал no-op,
  и посмотреть, возвращается ли результат.

### Проблема: fallback на brave не срабатывает

- Проверить `lyricsSearchMinResults` в `Karaoke.properties`.
  Если = 0 — порог отключён, любой результат (включая 1 URL) считается успехом.
- Проверить, что `KaraokeProperties.getInt("lyricsSearchMinResults")`
  возвращает реальное значение (не дефолт 0 при отсутствии ключа).

### Проблема: unit-тесты падают на IDN-доменах

- Это нормально — `URI.create("https://текст.рф/page")` может бросить
  exception. Проверить, что `toolsTest.filter отбрасывает невалидный URL`
  не покрывает IDN как false-positive (т.е. не тестирует IDN с
  ожиданием «отброшен»).
- Если IDN реально нужны — расширить фильтр, чтобы он принимал
  URL, у которых `URI.create` throws, но `URLEncoder`-эквивалент
  проходит. **Не делать в этой фиче** — отдельная задача.

## Связанные документы

- [spec.md](spec.md) — спецификация (FR, NFR, SC).
- [plan.md](plan.md) — план реализации (tasks, шаги).
- [research.md](research.md) — решения по архитектуре.
- [data-model.md](data-model.md) — модель данных.
- [contracts/README.md](contracts/README.md) — контракты.
- `specs/014-lyrics-search-replacement/research.md` — выбор 4get.
- `archive/docs/features/llm-lyrics-search.md` — общая документация.
- `docs/ops/log-correlation.md` — карта логов (SC-001, SC-002, SC-005).
