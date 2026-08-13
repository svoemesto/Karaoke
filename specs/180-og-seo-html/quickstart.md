# Quickstart: SEO-HTML endpoint для ботов (specs/180-og-seo-html)

Этот документ описывает сценарии ручной валидации фичи после
реализации. Каждый сценарий проверяет одно требование (FR) или
success criterion (SC) из спецификации.

## Предусловия

1. **karaoke-web запущен** локально (`bash deploy/do.sh build_start_web`).
2. **nginx** сконфигурирован с текущим `80to8897` (без изменений).
3. **Postgres** содержит тестовые песни с разными статусами:
   - обычная песня с полным контентом (idStatus ≥ 6, обложка альбома в MinIO);
   - песня с тегом `SKIP`;
   - песня с idStatus < 3;
   - песня без обложки альбома.
4. **curl** установлен, есть доступ к локальному nginx (`https://localhost/`)
   или прямому karaoke-web (`http://localhost:8897/api/public/og/song`).

## Сценарии валидации

### Сценарий 1: обычный запрос бота → SEO-HTML за < 100 мс (SC-001, FR-001, FR-002)

**Цель**: проверить, что endpoint возвращает валидный SEO-HTML быстро и
содержит все обязательные мета-теги.

```bash
# Запрос с User-Agent YandexBot (реальный бот Яндекса)
time curl -sS \
  -H "User-Agent: Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)" \
  "https://localhost/song?id=11661" \
  -o /tmp/og-response.html \
  -w "HTTP %{http_code}, TTFB: %{time_starttransfer}s, Size: %{size_download} bytes\n"
```

**Ожидаемый результат**:
- `HTTP 200`
- `TTFB < 0.1s` (< 100 мс).
- `Size` — 5–50 КБ.
- Файл `/tmp/og-response.html` содержит:
  - `<title>... — Караоке на sm-karaoke.ru</title>`
  - `<link rel="canonical" href="https://sm-karaoke.ru/song?id=11661">`
  - `<meta property="og:title" content="...">`
  - `<meta property="og:image" content="...">`
  - `<meta name="twitter:card" content="summary_large_image">`
  - `<script type="application/ld+json">{"@context":"https://schema.org","@type":"MusicRecording",...}</script>`
  - `<h1>...</h1>`, `<h2>...</h2>`
  - `<section id="meta">...</section>`
  - `<section id="lyrics">...</section>` (если `idStatus ≥ 3`)
  - `<section id="listen">...</section>` (если есть платформенные ссылки)

**Проверка JSON-LD на валидность**:
```bash
# Извлечь JSON-LD блок и валидировать через python
python3 -c "
import json, re, sys
html = open('/tmp/og-response.html').read()
m = re.search(r'<script type=\"application/ld\+json\">(.*?)</script>', html, re.DOTALL)
if not m:
    print('FAIL: no JSON-LD block found')
    sys.exit(1)
data = json.loads(m.group(1))
assert data['@type'] == 'MusicRecording', f'Expected MusicRecording, got {data[\"@type\"]}'
assert 'name' in data, 'name is required'
assert 'byArtist' in data, 'byArtist is required'
print('OK: JSON-LD valid MusicRecording with name and byArtist')
"
```

---

### Сценарий 2: отсутствие обращений к `/api/public/song-vk-image/{id}` (SC-002, FR-007)

**Цель**: проверить, что новый SEO-HTML endpoint не вызывает старый
PNG-генератор.

```bash
# Мониторинг access.log nginx во время 10 запросов от ботов
tail -f /var/log/nginx/access.log | grep --line-buffered "/api/public/song-vk-image" &
TAIL_PID=$!

# 10 параллельных запросов от разных ботов
for ua in "Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)" \
          "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)" \
          "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"; do
  for id in 11661 3062 2796 1342 3184; do
    curl -sS -H "User-Agent: $ua" "https://localhost/song?id=$id" -o /dev/null &
  done
done

wait
sleep 2
kill $TAIL_PID 2>/dev/null
```

**Ожидаемый результат**:
- `tail -f` НЕ показывает НИ ОДНОЙ строки с `/api/public/song-vk-image/`.
- В логе `/var/log/nginx/access.log` (или в логе karaoke-web) нет записей
  о генерации PNG.

**Альтернативная проверка** (если нет доступа к логам nginx):
```bash
# На админ-машине включить DEBUG-логирование PublicOgSongController
# и grep на старые сообщения:
grep "song-vk-image\|Generating PNG" /var/log/karaoke-web.log
```
Если grep возвращает 0 матчей за последние 24 часа — SC-002 выполнен.

---

### Сценарий 3: Schema.org JSON-LD без ошибок в Google Rich Results Test (SC-003, FR-003)

**Цель**: проверить, что JSON-LD корректен для Google Search.

**Шаги**:
1. Сохранить ответ endpoint'а в файл `/tmp/song-test.html`.
2. Развернуть `/tmp/song-test.html` на публично доступном URL
   (например, через `python3 -m http.server` на тестовом сервере).
3. Открыть https://search.google.com/test/rich-results
4. Вставить URL → нажать «Test URL».
5. Проверить, что в результатах:
   - **Нет ошибок** (Errors: 0).
   - **Нет предупреждений** (Warnings: 0) или они некритичные
     (например, «Missing optional field `image`» — это OK, если
     картинка есть, но Google считает её необязательной).

**Альтернативная проверка** (без публичного URL):
- Использовать https://validator.schema.org/ — вставить JSON-LD блок
  (извлечь из HTML через grep) → получить OK.

---

### Сценарий 4: HTML содержит все обязательные элементы (SC-004, FR-002, FR-004)

**Цель**: автоматическая проверка через grep, что все обязательные
элементы присутствуют.

```bash
RESPONSE=/tmp/og-response.html

# Базовые мета-теги
grep -q '<title>.*— Караоке на sm-karaoke.ru</title>' $RESPONSE || echo "FAIL: title"
grep -q '<meta name="description"' $RESPONSE || echo "FAIL: description"
grep -q '<link rel="canonical"' $RESPONSE || echo "FAIL: canonical"
grep -q '<meta name="robots"' $RESPONSE || echo "FAIL: robots"

# Open Graph
grep -q '<meta property="og:title"' $RESPONSE || echo "FAIL: og:title"
grep -q '<meta property="og:description"' $RESPONSE || echo "FAIL: og:description"
grep -q '<meta property="og:url"' $RESPONSE || echo "FAIL: og:url"
grep -q '<meta property="og:type" content="music.song"' $RESPONSE || echo "FAIL: og:type"
grep -q '<meta property="og:image"' $RESPONSE || echo "FAIL: og:image"

# Twitter Card
grep -q '<meta name="twitter:card" content="summary_large_image"' $RESPONSE || echo "FAIL: twitter:card"
grep -q '<meta name="twitter:image"' $RESPONSE || echo "FAIL: twitter:image"

# JSON-LD
grep -q '"@type": "MusicRecording"' $RESPONSE || echo "FAIL: JSON-LD MusicRecording"
grep -q '"byArtist"' $RESPONSE || echo "FAIL: JSON-LD byArtist"

# Видимый контент
grep -q '<h1>' $RESPONSE || echo "FAIL: h1"
grep -q '<h2>' $RESPONSE || echo "FAIL: h2"
grep -q '<section id="meta">' $RESPONSE || echo "FAIL: section#meta"
grep -q '<section id="lyrics">' $RESPONSE || echo "FAIL: section#lyrics"
grep -q '<section id="listen">' $RESPONSE || echo "FAIL: section#listen"
grep -q '<footer>' $RESPONSE || echo "FAIL: footer"

echo "All checks done."
```

**Ожидаемый результат**: 0 строк `FAIL: ...`.

---

### Сценарий 5: крайние случаи (FR-006)

#### 5.1. Невалидный id → HTTP 400

```bash
curl -sS -w "HTTP %{http_code}\n" "https://localhost/api/public/og/song"
curl -sS -w "HTTP %{http_code}\n" "https://localhost/api/public/og/song?id=0"
curl -sS -w "HTTP %{http_code}\n" "https://localhost/api/public/og/song?id=-1"
curl -sS -w "HTTP %{http_code}\n" "https://localhost/api/public/og/song?id=abc"
```

**Ожидаемый результат**: все 4 запроса → `HTTP 400`, body содержит
«Не указан id песни» или «id должен быть положительным числом».

#### 5.2. Песня не найдена → HTTP 404

```bash
curl -sS -w "HTTP %{http_code}\n" "https://localhost/api/public/og/song?id=999999"
```

**Ожидаемый результат**: `HTTP 404`, body содержит
«Песня не найдена: id=999999». **НЕТ** stack trace.

#### 5.3. Песня с тегом SKIP → HTTP 200, noindex

```bash
curl -sS -H "User-Agent: Mozilla/5.0 (compatible; YandexBot/3.0)" \
  "https://localhost/api/public/og/song?id={SKIP_SONG_ID}" \
  -o /tmp/skip-response.html \
  -w "HTTP %{http_code}\n"
```

**Ожидаемый результат**:
- `HTTP 200`.
- `<meta name="robots" content="noindex, nofollow">`.
- Видимый `<p class="warning">Контент удалён по требованию правообладателя</p>`.
- **НЕТ** секций `#lyrics`, `#chords`, `#listen`.
- **НЕТ** `formattedTextSong` в HTML.
- `<h1>` содержит название, `<h2>` содержит автора.

#### 5.4. Песня с `idStatus < 3` → HTTP 200, без текста

```bash
curl -sS "https://localhost/api/public/og/song?id={EARLY_STATUS_SONG_ID}" \
  -o /tmp/early-response.html
```

**Ожидаемый результат**:
- `HTTP 200`.
- **НЕТ** `<section id="lyrics">` (или она пустая).
- **НЕТ** `<section id="chords">`.
- `<section id="meta">` присутствует с названием, автором, годом.

#### 5.5. Песня без обложки → fallback на `KARAOKE_LOGO.png`

```bash
curl -sS "https://localhost/api/public/og/song?id={NO_COVER_SONG_ID}" \
  -o /tmp/no-cover-response.html
```

**Ожидаемый результат**:
- `<meta property="og:image" content="https://sm-karaoke.ru/KARAOKE_LOGO.png">` (или с `/minio/karaoke/`, см. data-model.md).
- HTTP 200.

---

### Сценарий 6: формат строки логирования (SC-006, FR-008)

```bash
# Сделать один запрос
curl -sS -H "User-Agent: YandexBot/3.0" "https://localhost/api/public/og/song?id=11661" -o /dev/null

# Проверить лог
tail -1 /var/log/karaoke-web.log | grep -E "OG render for song id=11661, User-Agent=YandexBot/3\.0"
```

**Ожидаемый результат**: строка в логе точно соответствует формату
`OG render for song id={id}, User-Agent={userAgent}`.

---

### Сценарий 7: проверка через `curl --user-agent` имитацию реального бота (полная проверка)

**Цель**: воспроизвести реальный сценарий — бот обходит страницу.

```bash
# Имитация обхода YandexBot по 10 случайным песням
for id in $(seq 1 18 200); do
  curl -sS -H "User-Agent: Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)" \
    "https://localhost/song?id=$id" \
    -o /dev/null \
    -w "id=$id, HTTP %{http_code}, TTFB %{time_starttransfer}s\n"
done
```

**Ожидаемый результат**:
- Все запросы → `HTTP 200` (для существующих песен) или `HTTP 404` (для несуществующих).
- TTFB для всех < 100 мс.

---

### Сценарий 8: проверка отсутствия регрессии в nginx-конфиге (FR-012)

```bash
# Убедиться, что 80to8897 не изменился
cd /home/nsa/Karaoke
git diff HEAD -- deploy/web-server-deploy/deploy/80to8897
```

**Ожидаемый результат**: пустой вывод (нет изменений в nginx-конфиге).

---

### Сценарий 9: проверка отсутствия миграций БД (FR-011)

```bash
# Убедиться, что нет новых SQL-файлов в deploy/karaoke-db/
cd /home/nsa/Karaoke
git status --porcelain deploy/karaoke-db/

# Убедиться, что нет изменений в существующих SQL-файлах
git diff HEAD -- deploy/karaoke-db/
```

**Ожидаемый результат**: пустой вывод (нет новых миграций).

---

### Сценарий 10: проверка обратной совместимости — обычный браузер → SPA

```bash
# Запрос без User-Agent бота → должен идти в SPA, не в OG-endpoint
curl -sS -H "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" \
  "https://localhost/song?id=11661" \
  -o /tmp/browser-response.html
```

**Ожидаемый результат**:
- Ответ содержит JavaScript-бандл `karaoke-public` (Vue).
- Содержит `<div id="app">` или аналогичный.
- НЕ содержит JSON-LD, OG-теги (это поведение **OG-endpoint**, не SPA).

---

## Чек-лист после реализации

После успешного прохождения всех 10 сценариев фича считается
готовой к деплою. Дополнительно:

- [ ] Обновить per-feature документ `docs/features/seo-html-for-bots.md` (или расширить существующий, см. FR-015 спеки).
- [ ] Добавить запись в `docs/architecture-notes.md` (Pass 51) с описанием PR.
- [ ] Проверить `ktlintCheck` (`./gradlew ktlintCheck`) — без новых нарушений.
- [ ] Обновить KDoc в `PublicOgSongController.kt` (FR-014 спеки).
- [ ] Создать PR через `gh pr create --base master`.
- [ ] Дождаться CI 7/7 SUCCESS (см. AGENTS.md «CI-gate»).
- [ ] Merge через `gh pr merge --merge` (без `--delete-branch`, см. AGENTS.md «Жизненный цикл feature-ветки»).
- [ ] Деплой на прод через `deploy/deploy_web.sh` (только пользователь).
- [ ] Проверить в логах прод-сервера, что `OG render for song id=` строки идут без обращений к `/api/public/song-vk-image/`.
- [ ] Через 7 дней после деплоя — проверить в Google Search Console / Yandex.Вебмастер, что страницы `/song?id=NNN` корректно индексируются (SC-003 спеки).

## Связанные документы

- `specs/180-og-seo-html/spec.md` — функциональные требования.
- `specs/180-og-seo-html/research.md` — обоснование технических решений.
- `specs/180-og-seo-html/data-model.md` — модель данных.
- `specs/180-og-seo-html/contracts/og-html-endpoint.md` — контракт endpoint'а.
- `AGENTS.md` — CI-gate, жизненный цикл feature-ветки, документирование.
