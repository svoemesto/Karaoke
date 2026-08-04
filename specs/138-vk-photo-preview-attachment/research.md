# Research: Надёжное превью публикации ВК через прикрепление обложки фото

**Branch**: `132-vk-photo-preview-attachment` | **Date**: 2026-08-04
**Spec**: [spec.md](./spec.md)

## Цель исследования

Подтвердить техническую реализуемость загрузки PNG-обложки песни через
VK API `photos.*` методы с уже настроенным user-token (scope `photos`),
и обосновать выбор между альтернативными способами получения превью в
постах ВКонтакте.

## Изученные варианты

### Вариант A — `photos.getWallUploadServer` + `photos.saveWallPhoto` (✅ ОСНОВНОЙ)

**Решение**: перед `wall.post` вызывается `photos.getWallUploadServer({group_id})`,
получаем `upload_url`. POST multipart/form-data с полем `photo` на `upload_url`.
Получаем JSON `{server, photo, hash}`. Вызываем `photos.saveWallPhoto({server, photo, hash, group_id})`,
получаем массив фото с `id` и `owner_id`. Передаём
`attachments=photo<owner_id>_<photo_id>` в `wall.post`.

**Преимущества**:
- ✅ VK берёт фото из API-параметра `attachments`, не парся URL.
- ✅ Работает одинаково для бот-публикации и ручной.
- ✅ User-token с scope `photos` уже настроен.
- ✅ Превью появляется в 100% случаев (если шаг выполнен).
- ✅ Превью-фото становится первым прикреплением → VK отображает его
  как сниппет на стене и в ленте.

**Параметры VK API** (исследованы по документации и существующему коду):

| Метод | Параметры | Возвращает |
|-------|-----------|------------|
| `photos.getWallUploadServer` | `group_id=<id>` (обязателен для группы), `access_token=user` | `{upload_url: string, album_id: int, user_id: int}` |
| `POST <upload_url>` | multipart/form-data, поле `photo=<file>` | `{server: int, photo: string, hash: string, gid: int?}` |
| `photos.saveWallPhoto` | `server=<int>`, `photo=<json_string>`, `hash=<string>`, `group_id=<id>`, `access_token=user` | массив `[{id, owner_id, ...}]` |

**Рациональ выбора**: это **самый надёжный** способ получить превью в
постах ВКонтакте. Альтернативные способы (Open Graph, прогрев PNG,
документы) дают худший результат при бот-публикации или хуже выглядят
визуально (см. варианты B-D ниже).

### Вариант B — `docs.getWallUploadServer` + `docs.save` (⚠️ FALLBACK)

**Решение**: если `photos.*` методы вернули ошибку авторизации
(`error_code=27`, `15`, `5`), пробуем `docs.*` методы с **community-token**:
получаем `upload_url` через `docs.getWallUploadServer`, POST multipart
с полем `file=<png>`, вызываем `docs.save` с параметром `type=image`,
получаем `doc<owner>_<id>`. Передаём `attachments=doc<owner>_<id>` в `wall.post`.

**Преимущества**:
- ✅ Работает с **community-token** (право `docs` доступно сообществам).
- ✅ Не требует user-token.
- ✅ Даёт второй шанс на прикрепление картинки.

**Недостатки**:
- ❌ Документы с MIME image отображаются в посте как файл-документ,
  а НЕ как сниппет-превью (зависит от версии API и UI).
- ❌ Визуально менее привлекательно: вместо большой картинки-превью —
  маленькая иконка файла.

**Параметры VK API**:

| Метод | Параметры | Возвращает |
|-------|-----------|------------|
| `docs.getWallUploadServer` | `type=image`, `access_token=community` | `{upload_url: string}` |
| `POST <upload_url>` | multipart/form-data, поле `file=<png>` | `{file: string}` |
| `docs.save` | `file=<url>`, `title=<name>`, `access_token=community` | `[{id, owner_id, title, size, ext, url, ...}]` |

**Рациональ выбора fallback**: сохраняем функциональность даже при потере
user-scope `photos`. Лучше документ с картинкой, чем ничего.

### Вариант C — `wall.edit` после `wall.post` (ОТКЛОНЁН)

**Решение**: после `wall.post` вызвать `wall.edit` с тем же текстом — ВК
**может** триггернуть повторный парсинг URL и обновление сниппета.

**Не отобран** потому что:
- ❌ Поведение VK API в этом случае **не документировано**.
- ❌ Если не сработает — пост останется без превью.
- ❌ Лишний запрос к VK API.
- ❌ Это хак, а не решение — лучше делать правильно с первого раза.

### Вариант D — URL в первом комментарии через `wall.createComment` (ОТКЛОНЁН)

**Решение**: `wall.post` без URL → `wall.createComment(owner_id, post_id, message="<URL>")` —
ВК парсит URL в комментариях отдельно.

**Не отобран** потому что:
- ❌ Превью появляется **в комментарии**, а не в самом посте.
- ❌ ВК может блокировать комментарии от ботов.
- ❌ Менее заметно для пользователя.

### Вариант E — Selenium/Playwright UI-публикация (ОТКЛОНЁН)

**Решение**: headless Chromium имитирует действия пользователя в UI ВКонтакте.

**Не отобран** потому что:
- ❌ Сложно реализовать и поддерживать.
- ❌ Хрупко: VK может менять UI.
- ❌ Требует логина от владельца группы.

## Размер PNG: 537×240 → 1200×630

**Решение**: расширить PNG-обложку, генерируемую endpoint `/api/public/song-vk-image/{id}`,
с **537×240** до **1200×630** (стандарт Open Graph, рекомендуемый VK
для сниппетов).

**Рациональ**:
- 537×240 — допустимый минимум, но приводит к менее качественному
  отображению (картинка растягивается в UI).
- 1200×630 — рекомендуемый VK размер для сниппетов.
- 1200×630 PNG с альбомом + автором + текстом ≈ 100-300 КБ — укладывается
  в лимит VK API на загрузку фото (50 МБ).
- Никаких других изменений в endpoint не требуется: алгоритм генерации,
  прогрев (`VkPreviewWarmupClient`), атомарная запись, проверка PNG
  magic-signature (specs/130) — всё сохраняется.

**Изменение**: одна константа размера в `PublicApiController.songVkImage` —
`frameW = 537, frameH = 240` → `frameW = 1200, frameH = 630`. Параметры
`albumW`, `authorW`, `picAreaH`, `padding` пересчитываются пропорционально
(или используются фиксированные значения из существующего шаблона 800×194,
масштабированные на коэффициент ~2.4).

## User-token и scope `photos`

**Проверено в коде**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:7065`:

```kotlin
val scopes = "video,photos,wall,offline"
```

User-token получен 02.08.2026 через Implicit Flow Standalone-приложения
со scopes `video,photos,wall,offline` и сохранён в
`KaraokeProperties.vkUserAccessToken`. Этот токен **уже** имеет scope
`photos` и **должен** работать для `photos.*` методов.

**Историческая ремарка**: в `specs/123-vk-og-preview-fix` (03.08.2026)
был сделан неверный вывод о необходимости запрашивать модерацию
Standalone-приложения. На тот момент user-token ещё не был настроен
(он появился позже в тот же день, 02.08.2026, как фикс для `video.save`).
Сейчас токен есть — никакой модерации не требуется.

**Гипотеза для проверки**: при первом запуске нового кода бот вызовет
`photos.getWallUploadServer` с user-token. Возможные исходы:
1. ✅ `upload_url` возвращается → загрузка работает (ожидаемый сценарий).
2. ⚠️ `error_code=27` (Group authorization failed) → значит user-scope
   `photos` потерян или Standalone-приложение не активировано для
   группы → fallback на `docs.*`.
3. ⚠️ `error_code=5` (User authorization failed) → user-token невалиден
   → fallback на `docs.*`.

Если сценарий 2 или 3 происходит сразу — значит, нужно разбираться,
почему user-token не работает для `photos.*`. Если это происходит
стабильно — переключаемся на `docs.*` как основной путь.

## Интеграция с существующим потоком

Текущий поток публикации (specs/121-vk-news-auto-publish):

```
1. FR-008: проверка Song.idVk
2. FR-022: проверка isContentReady
3. FR-020: проверка/рендер демо-MP4
4. FR-023: формирование текста по шаблону
5. specs/130: прогрев PNG
6. FR-019: video.save → wall.post (attachments=video)
7. FR-004: запись Song.idVk
```

Новый поток:

```
1. FR-008: проверка Song.idVk
2. FR-022: проверка isContentReady
3. FR-020: проверка/рендер демо-MP4
4. FR-023: формирование текста по шаблону
5. specs/130: прогрев PNG (1200×630)
6. [NEW] FR-001: photos.getWallUploadServer → upload → photos.saveWallPhoto
         → photo<owner>_<id> (fallback FR-006: docs.*)
7. FR-019: video.save → wall.post
         attachments=photo<owner>_<id>,video<owner>_<video_id>
         (или photo если нет видео; или video если фото не удалось)
8. FR-004: запись Song.idVk
```

Шаг 6 — единственная новая вставка в потоке. Все остальные шаги
сохраняются без изменений.

## Существующие инварианты — сохраняются

- ✅ Идемпотентность по `Song.idVk` (FR-008 specs/121).
- ✅ Process-local lock по `song.id` (`VkAutoPublishService.songLocks`).
- ✅ Rate-limit 3 поста/час (`vkAutoPublishRateLimitPerHour`).
- ✅ Retry 30с→2мин→5мин для `wall.post` (FR-009 specs/121).
- ✅ Прогрев PNG через `VkPreviewWarmupClient` (specs/130).
- ✅ Атомарная запись PNG-кэша с проверкой magic-signature (specs/130).
- ✅ Сохранение `vkAutoPublishState` через `Song.saveToDb()` (FR-004 specs/121).
- ✅ Шаблоны `vkTemplateAir` / `vkTemplatePremium` (specs/121, specs/128).

## Ограничения и допущения

- Альбом группы ВКонтакте пополняется ~1 фото на пост. При текущем темпе
  ~3 поста/день это ~90 фото/мес, ~1000 фото/год. Очистка через
  `photos.delete` — отдельная задача (backlog).
- VK API не документирует точный лимит на размер PNG для `photos.*`;
  эмпирически 1200×630 PNG с обложкой + автором + текстом ≈ 100-300 КБ,
  что укладывается в лимит 50 МБ с большим запасом.
- При первом запуске после merge возможны сюрпризы (VK мог изменить
  API или добавить новые требования к фото). Предусмотрен fallback
  на `docs.*` + деградация (пост без превью).
- User-token может быть отозван VK (смена пароля владельца,
  подозрительная активность). В этом случае `photos.*` вернёт
  `error_code=5` → fallback на `docs.*`.

## Открытые вопросы

Нет — все вопросы сняты в `spec.md` секция «Clarifications» (Q1, Q2, Q3
от 2026-08-04). Размер PNG, fallback, деградация — все решения приняты
с обоснованием.