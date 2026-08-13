# Contract: `GET /api/public/og/song` — SEO-HTML endpoint для ботов

**Версия контракта**: 1.0 (2026-08-13, specs/180-og-seo-html)

Этот документ описывает контракт HTTP-endpoint'а
`GET /api/public/og/song` после перевода на SEO-HTML (вместо генерации
PNG «на лету»). Контракт обратно совместим по пути и сигнатуре — меняется
только **содержимое** ответа.

## Endpoint

- **Метод**: `GET`
- **Путь**: `/api/public/og/song`
- **Query params**:
  - `id` (Long, required) — идентификатор песни. Должен быть > 0.
- **Headers**:
  - `User-Agent` (optional) — User-Agent клиента. Используется только для
    логирования (формат строки лога остаётся неизменным, FR-008 спеки).
- **Authentication**: не требуется (endpoint публичный, в
  `permitAll()`-зоне `SecurityConfig.kt`).

## Ответы

### HTTP 200 — OK (песня найдена, не SKIP)

**Content-Type**: `text/html; charset=UTF-8`

**Структура HTML**:

```html
<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="UTF-8">
  <title>{songName} — {author} — Караоке на sm-karaoke.ru</title>
  <meta name="description" content="{description or fallback}">
  <link rel="canonical" href="https://sm-karaoke.ru/song?id={id}">
  <meta name="robots" content="index, follow">
  <meta name="generator" content="sm-karaoke.ru karaoke-pipeline">

  <!-- Open Graph -->
  <meta property="og:title" content="{title}">
  <meta property="og:description" content="{description}">
  <meta property="og:url" content="{canonical}">
  <meta property="og:type" content="music.song">
  <meta property="og:site_name" content="Караоке на sm-karaoke.ru">
  <meta property="og:locale" content="ru_RU">
  <meta property="og:image" content="{album or logo URL}">
  <meta property="og:image:width" content="{400 or logo default}">
  <meta property="og:image:height" content="{400 or logo default}">
  <meta property="og:image:alt" content="{songName} — {author}">

  <!-- Twitter Card -->
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:title" content="{title}">
  <meta name="twitter:description" content="{description}">
  <meta name="twitter:image" content="{og:image}">

  <!-- Schema.org JSON-LD -->
  <script type="application/ld+json">
  {
    "@context": "https://schema.org",
    "@type": "MusicRecording",
    "@id": "{canonical}",
    "name": "{songName}",
    "byArtist": { "@type": "MusicGroup", "name": "{author}" },
    "inAlbum": { "@type": "MusicAlbum", "name": "{album}", "datePublished": "{year}" },
    "datePublished": "{year}",
    "genre": ["{tag1}", "{tag2}"],
    "inLanguage": "ru",
    "description": "{description}",
    "url": "{canonical}",
    "image": "{album or logo URL}",
    "lyrics": { "@type": "CreativeWork", "text": "{formattedTextSong}" },
    "isAccessibleForFree": {true|false}
  }
  </script>
</head>
<body>
  <header>
    <h1>{songName}</h1>
    <h2>{author}</h2>
    <p>{shortDescription}</p>
    <p class="warning">{warning}</p>
  </header>

  <section id="meta">
    <h3>О песне</h3>
    <dl>
      <dt>Исполнитель</dt><dd>{author}</dd>
      <dt>Альбом</dt><dd>{album}</dd>
      <dt>Год</dt><dd>{year}</dd>
      <dt>Трек</dt><dd>{track}</dd>
      <dt>Тональность</dt><dd>{key}</dd>
      <dt>Темп</dt><dd>{bpm} BPM</dd>
      <dt>Жанры</dt><dd>{tags joined by comma}</dd>
      <dt>Длительность</dt><dd>{mm:ss}</dd>
    </dl>
  </section>

  <section id="description">
    <h3>Описание</h3>
    <p>{description}</p>
  </section>

  <section id="lyrics">
    <h3>Текст песни</h3>
    <pre>{formattedTextSong}</pre>
  </section>

  <section id="chords">
    <h3>Аккорды и табы</h3>
    <pre>{formattedTextChords or formattedTextTabs}</pre>
  </section>

  <section id="listen">
    <h3>Послушать</h3>
    <ul>
      <li><a href="{linkSponsrPlay}" rel="noopener noreferrer">Sponsr</a></li>
      <li><a href="{linkVkKaraoke}" rel="noopener noreferrer">VK Видео (караоке)</a></li>
      <li><a href="{linkTgKaraoke}" rel="noopener noreferrer">Telegram</a></li>
      <!-- ... другие непустые ссылки ... -->
    </ul>
  </section>

  <footer>
    <p>© sm-karaoke.ru — Караоке русского рока</p>
    <p><a href="{canonical}">Открыть на сайте</a></p>
  </footer>
</body>
</html>
```

**Условные правила**:

- Если поле пусто (`""` или `null`) — соответствующий HTML-элемент
  опускается полностью (не рендерится как пустой).
- `shortDescription` и `warning` отображаются только если не пусты.
- `formattedTextSong` опускается, если `idStatus < 3` или песня SKIP.
- `formattedTextChords`/`formattedTextTabs` опускаются, если пусты или
  песня SKIP.
- Платформенные ссылки включаются только непустые (пустая строка
  означает «нет публикации на платформе»).
- `og:image` — приоритет: `pictureAlbum.storageFileName` → fallback
  `KARAOKE_LOGO.png`.

**Размер**: 5–50 КБ типично, до 1 МБ максимум (с маркером обрезки).

### HTTP 200 — OK (песня с тегом SKIP)

**Content-Type**: `text/html; charset=UTF-8`

**Особенности**:
- `<meta name="robots" content="noindex, nofollow">` (вместо `index, follow`).
- Видимый контент: только `<header>` с `<h1>{songName}</h1>`,
  `<h2>{author}</h2>` и предупреждение `<p class="warning">Контент удалён
  по требованию правообладателя</p>`.
- НЕТ секций `#lyrics`, `#chords`, `#listen`, `#description`.
- НЕТ текста песни, аккордов, табов, ссылок на стриминг.
- JSON-LD `lyrics` опускается.
- `og:image` — обложка альбома если есть, иначе `KARAOKE_LOGO.png`.

### HTTP 200 — OK (песня с `idStatus < 3`, ещё не в коллекции)

**Content-Type**: `text/html; charset=UTF-8`

**Особенности**:
- `<meta name="robots" content="index, follow">` (нормальный режим).
- Видимый контент: `<header>` с h1/h2 + секция `#meta` (без
  `formattedTextSong`, `formattedTextChords`, `formattedTextTabs`).
- НЕТ секций `#lyrics`, `#chords` (текст ещё не верифицирован).
- Секция `#listen` — пустая (платформенных публикаций нет для таких песен).
- `og:image` — обложка альбома если есть, иначе `KARAOKE_LOGO.png`.

### HTTP 400 — Bad Request

**Когда**: `id == null`, `id <= 0`, или `id` не парсится как Long.

**Content-Type**: `text/html; charset=UTF-8`

**Body**:
```html
<!DOCTYPE html>
<html lang="ru">
<head><meta charset="UTF-8"><title>OG error</title></head>
<body>
  <h1>❌ Ошибка</h1>
  <p>Не указан id песни (добавьте ?id=NNN)</p>
</body>
</html>
```

### HTTP 404 — Not Found

**Когда**: `id` валидный, но песня не найдена в БД.

**Content-Type**: `text/html; charset=UTF-8`

**Body**:
```html
<!DOCTYPE html>
<html lang="ru">
<head><meta charset="UTF-8"><title>OG error</title></head>
<body>
  <h1>❌ Ошибка</h1>
  <p>Песня не найдена: id={id}</p>
</body>
</html>
```

## Производительность

| Метрика | Целевое значение | Источник |
|---------|------------------|----------|
| TTFB (Time To First Byte) | < 100 мс | SC-001 спеки |
| Размер HTML | 5–50 КБ (до 1 МБ) | research.md R7 |
| CPU на запрос | < 5 мс CPU time | FR-010 спеки |
| RAM на запрос | ~50 КБ (StringBuilder) | FR-010 спеки |
| Обращений к MinIO | **0** | FR-007 спеки |
| Обращений к Postgres | **1** (`SELECT FROM tbl_songs WHERE id = ?`) | FR-010 спеки |

## Логирование

**Формат строки лога** (неизменный, FR-008 спеки):
```
OG render for song id={id}, User-Agent={userAgent}
```

**Уровень**: `INFO`.

**Пример**:
```
2026-08-13T10:23:45.123+03:00  INFO 1 --- [nio-7799-exec-9] c.s.k.c.PublicOgSongController           : OG render for song id=11661, User-Agent=Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)
```

## Безопасность

- **HTML escape**: все строковые поля из БД экранируются через
  `escape()` (5 символов: `&<>"'`) перед вставкой в HTML. Это
  исключает HTML-инъекцию через поля песни (FR-005 спеки).
- **JSON-LD escape**: дополнительно экранируются символы, ломающие
  JSON (обратная косая черта `\`, перевод строки `\n`).
- **Длина**: HTML обрезается на 1 МБ с маркером (защита от DoS).
- **Нет новых миграций БД**: spec FR-011. Нет attack-surface для SQL-инъекций.

## Обратная совместимость

- **Путь** `/api/public/og/song` **не меняется**.
- **Query params** `id` **не меняются**.
- **HTTP status codes** (200, 400, 404) **не меняются**.
- **Content-Type** `text/html; charset=UTF-8` **не меняется**.
- **Список User-Agent'ов в nginx `80to8897`** **не меняется** (FR-012 спеки).
- **Endpoint `/api/public/song-vk-image/{id}`** остаётся в коде (FR-009
  спеки) для обратной совместимости с потенциально кэшированными ссылками.
- **Формат строки лога** остаётся неизменным (FR-008 спеки).
- **KDoc** контроллера обновляется для отражения нового поведения (FR-014 спеки).

## Что НЕ входит в контракт

- **SPA-страница `/song?id=NNN` в karaoke-public** (`SongView.vue`) —
  обычные браузеры идут туда, поведение не меняется.
- **Endpoint `/api/public/song-vk-image/{id}`** — не используется
  новым SEO-HTML endpoint, но остаётся в коде.
- **nginx-конфиг `80to8897`** — не меняется в этой фиче.
- **Миграции БД** — нет (FR-011).
- **Feature-flag `ogRenderEnabled`** — не введён в первой версии
  (User Story 4 P3, backlog).

## Связанные документы

- `specs/180-og-seo-html/spec.md` — функциональные требования.
- `specs/180-og-seo-html/research.md` — обоснование технических решений.
- `specs/180-og-seo-html/data-model.md` — модель данных.
- `specs/180-og-seo-html/quickstart.md` — сценарии ручной валидации.
- `karaoke-web/.../controllers/PublicOgSongController.kt` — реализация.
