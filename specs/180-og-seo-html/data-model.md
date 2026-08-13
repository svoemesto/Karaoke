# Data Model: SEO-HTML endpoint для ботов (specs/180-og-seo-html)

**Принцип**: новые сущности **не вводятся**. Вся информация для SEO-HTML
берётся из существующей сущности `Song` (`tbl_songs`). Миграций БД нет
(FR-011 спеки).

## Существующие сущности (источник данных)

### Song (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`)

**Назначение**: главная сущность проекта — песня. Используется
endpoint'ом `PublicOgSongController.ogSongHtml()` для построения
SEO-HTML ответа.

#### Поля, используемые в SEO-HTML

| Поле | Тип | Описание | Использование в HTML |
|------|-----|----------|----------------------|
| `id` | Long | Идентификатор песни | canonical URL, og:url, JSON-LD @id |
| `songName` | String | Название песни | `<title>`, og:title, h1, JSON-LD name |
| `author` | String | Исполнитель | `<title>`, og:title, h2, JSON-LD byArtist.name |
| `album` | String | Альбом | JSON-LD inAlbum.name |
| `year` | Long | Год | JSON-LD datePublished |
| `track` | Long | Номер трека | `<dl>` definition list |
| `key` | String | Тональность (Am, C#, etc) | `<dl>` definition list |
| `bpm` | Long | Темп (BPM) | `<dl>` definition list |
| `description` | String | Краткое описание песни (до ~250 символов) | meta description, og:description, JSON-LD description |
| `shortDescription` | String | Короткая пометка | Видимый `<p>` рядом с h1 |
| `warning` | String | Предупреждение | Видимый `<p class="warning">` |
| `tags` | String | Теги через пробел (lowercase, "rock love SKIP") | JSON-LD genre (без SKIP) |
| `formattedTextSong` | String | HTML-форматированный текст песни | Видимый `<pre>` в секции lyrics |
| `formattedTextChords` | String | Аккорды | Видимый `<pre>` в секции chords |
| `formattedTextTabs` | String | Табы | Видимый `<pre>` в секции chords |
| `ms` | Long | Длительность в мс | `<dl>` definition list (формат mm:ss) |
| `songType` | SongType | Тип: song/instrumental/poetry | Условный заголовок секции текста |
| `idStatus` | Long | Статус пайплайна (0..7) | Условное отображение текста/аккордов (только если ≥ 3) |
| `free` | Boolean | Всегда бесплатна | JSON-LD isAccessibleForFree |
| `isFreelyAvailableNow` | Boolean | Доступна бесплатно сейчас | JSON-LD isAccessibleForFree |
| `pictureAlbum` | Pictures? | Обложка альбома (если есть) | URL в og:image (если есть) |
| `pictureAuthor` | Pictures? | Обложка автора (если есть) | Fallback для og:image |

#### Платформенные ссылки (используются в секции «Послушать»)

| Поле | Платформа |
|------|-----------|
| `linkSponsrPlay` | Sponsr |
| `linkBoostyTxt` | Boosty (тексты) |
| `linkVkKaraoke`, `linkVkLyrics`, `linkVkTabs`, `linkVkChords` | VK Видео |
| `linkTgKaraoke`, `linkTgLyrics`, `linkTgTabs`, `linkTgChords` | Telegram |
| `linkDzenKaraoke`, `linkDzenLyrics`, `linkDzenTabs`, `linkDzenChords` | Yandex Zen (Dzen) |
| `linkMaxKaraoke`, `linkMaxLyrics`, `linkMaxTabs`, `linkMaxChords` | Yandex Music (Max) |
| `linkPlKaraoke`, `linkPlLyrics`, `linkPlTabs`, `linkPlChords` | YouTube (Pl — playlist) |

#### Вычисляемые поля (геттеры)

| Поле | Тип | Описание |
|------|-----|----------|
| `pathToFileLogoAlbum` | String | Путь к файлу обложки альбома на диске admin-машины (`$rootFolder/$fileName [album].png`) |
| `pathToFileLogoAuthor` | String | Путь к файлу обложки автора на диске admin-машины |

> **NB**: `pathToFileLogoAlbum`/`pathToFileLogoAuthor` — пути на диске
> admin-машины, НЕ используются в новом SEO-endpoint. Используем
> **только** `pictureAlbum.storageFileName` (если `pictureAlbum` не
> null), чтобы построить абсолютный URL в MinIO. Это исключает
> обращение к диску admin-машины (FR-007 спеки).

### Pictures (`karaoke-app/.../model/Pictures.kt`)

**Назначение**: обложка альбома или автора. Загружается из MinIO
вручную или через существующий endpoint `/api/public/song-picture/{id}`
(PublicApiController.kt:344, ленивая загрузка через `Pictures.getPictureByName`).

#### Поля, используемые в SEO-HTML

| Поле | Тип | Описание |
|------|-----|----------|
| `storageFileName` | String | Имя файла в MinIO bucket'е `karaoke`, например `Кино/1986 - Ночь/Kино - 1986 - Ночь.album.png` |
| `full` | String | base64-картинка (НЕ используется, для HTML нужен URL) |

#### Как строится URL

Шаблон URL обложки альбома в SEO-HTML:

```
https://sm-karaoke.ru/minio/karaoke/{pictureAlbum.storageFileName}
```

Пример:
- `pictureAlbum.storageFileName = "Кино/1986 - Ночь/Кино - 1986 - Ночь.album.png"`
- URL в HTML: `https://sm-karaoke.ru/minio/karaoke/Кино/1986%20-%20Ночь/Кино%20-%201986%20-%20Ночь.album.png`
  (URL-encoded по правилам RFC 3986; nginx MinIO-прокси принимает оба
  варианта)

> **NB**: nginx-конфиг `80to8897:6-12` проксирует `/minio/` напрямую в
> MinIO (`http://89.125.103.63:9000/`) без участия Java-приложения.
> Это гарантирует, что og:image в HTML указывает на публично доступный
> URL без нагрузки на karaoke-web.

### KaraokeLogoUrl (fallback)

**Назначение**: URL дефолтного логотипа сайта, используется если
обложка альбома отсутствует.

**Реализация**: статическая константа в `PublicOgSongController.kt`:
```kotlin
private const val FALLBACK_LOGO_URL = "https://sm-karaoke.ru/KARAOKE_LOGO.png"
```

**Альтернативный вариант** (если `KARAOKE_LOGO.png` не существует в корне
nginx): `https://sm-karaoke.ru/minio/karaoke/KARAOKE_LOGO.png` — тот же
URL, что уже используется в `PublicApiController.kt:462` (302 redirect
при отсутствии обложки/автора). Проверить на этапе реализации.

### SongType (`karaoke-app/.../model/SongType.kt`)

**Назначение**: enum, отличает обычную песню, инструментал и стихи.

#### Значения

| Значение | `dbValue` | `caption` | `description` |
|----------|-----------|-----------|---------------|
| `SONG` | `song` | Песня | Песня (вокал + музыка) |
| `INSTRUMENTAL` | `instrumental` | Инструментальная композиция | Инструментал (только музыка) |
| `POETRY` | `poetry` | Поэзия (без музыки) | Стихи (только вокал) |

#### Использование в SEO-HTML

| songType | Заголовок секции lyrics |
|----------|-------------------------|
| `SONG` | `<h3>Текст песни</h3>` |
| `INSTRUMENTAL` | `<h3>Описание</h3>` (без секции lyrics, instrumental не имеет слов) |
| `POETRY` | `<h3>Текст</h3>` (стихи — тот же формат, что песня) |

### SongField (lowercase-ключи в `Song.fields` map)

**Назначение**: enum-ключи для `MutableMap<SongField, String>`, через
который reflection-loader читает поля из БД. Существующая
инфраструктура проекта (см. DEVELOPMENT.md «DB без JPA/Hibernate»),
не меняется.

> **NB**: в новом SEO-endpoint не используется напрямую — все данные
> читаются через геттеры `Song` (например, `song.songName`,
> `song.author`). Геттеры сами обращаются к `fields` map под
> капотом. Это стандартный паттерн проекта.

## Не вводим новых сущностей

Спецификация **не требует**:
- новых таблиц БД;
- новых колонок в `tbl_songs`;
- новых записей в `SyncRegistry`;
- новых файлов в MinIO (используем существующие
  `Pictures.storageFileName` и `KARAOKE_LOGO.png`);
- новых полей в `KaraokeProperties` (не нужен feature-flag в первой
  версии — см. User Story 4 P3, backlog).

## Транзиентные сущности (формируются на лету)

### OG HTML Response

**Назначение**: transient HTML-документ, формируемый `PublicOgSongController.ogSongHtml()`
из полей `Song`. Не сохраняется в БД, не кэшируется на стороне
karaoke-web (см. research.md R5).

**Структура**:
- HTTP status: 200 (норма), 400 (невалидный id), 404 (песня не найдена).
- Content-Type: `text/html; charset=UTF-8`.
- Размер: обычно 5–50 КБ (с текстом песни), максимум 1 МБ (с маркером
  обрезки).
- TTFB: < 100 мс (SC-001 спеки).

**Не сохраняется** в логи (только `id` и `User-Agent` запроса — через
стандартный `LoggerFactory`).

## Связанные документы

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` —
  основная сущность, источник данных.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongType.kt`
  — enum типа песни.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt`
  — endpoint, который формирует HTML.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt`
  — образец выбора публичных полей для API (FR не вводят новых полей
  в DTO — используются существующие).
