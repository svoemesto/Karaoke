# Quickstart: Валидация изменений после рефакторинга DTO

**Дата**: 2026-08-14
**Spec**: [spec.md](./spec.md)

## Цель

Это руководство для разработчика / тестировщика, который хочет **убедиться, что рефакторинг сделан правильно** (или, для разработчика фичи — что он **сделал** его правильно). Не требует CI — только локальный запуск контейнеров и ручные проверки через `curl` / DevTools браузера.

## Prerequisites

- Karaoke-проект собирается: `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel` (см. `AGENTS.md`).
- Локальные Docker-контейнеры подняты: `deploy/do.sh start`.
- Karaoke-web слушает на `http://localhost:8897` (или `:8897` внутри docker-сети `deploy_karaokenet`).
- Karaoke-public dev-сборка: `cd karaoke-public && npm run dev` (или собранный `dist/` через `do.sh build_start_public`).

## 1. Проверка Backend JSON-ответов

### 1.1. `GET /api/public/zakroma?author=КИНО`

Проверяем, что в массиве песен НЕТ удалённых полей.

```bash
curl -s 'http://localhost:8897/api/public/zakroma?author=КИНО' \
  | jq '.[0].albums[0].albumSettings[0] | keys'
```

**Ожидаемый результат** (10 ключей, в любом порядке):

```json
[
  "airTimestamp",
  "alwaysFree",
  "datePublish",
  "freeAccessWindowEndText",
  "freelyAvailableNow",
  "id",
  "onAir",
  "songName",
  "songSubscriptionAvailable",
  "track"
]
```

**Чего НЕ должно быть**: `linkBoosty`, `linkSponsrPlay`, `linkDzenKaraoke`, `linkDzenLyrics`, `linkDzenTabs`, `linkDzenChords`, `linkVkKaraoke`, `linkVkLyrics`, `linkVkTabs`, `linkVkChords`, `linkTgKaraoke`, `linkTgLyrics`, `linkTgTabs`, `linkTgChords`, `linkPlKaraoke`, `linkPlLyrics`, `linkPlTabs`, `linkPlChords`, `linkMaxKaraoke`, `linkMaxLyrics`, `linkMaxTabs`, `linkMaxChords`.

### 1.2. `GET /api/public/songs?songName=Звезда`

Проверяем, что в списке песен нет удалённых полей и `idStatus/haveVkGroupLink/sponsrLinkGeneral/vkPictureBase64`.

```bash
curl -s 'http://localhost:8897/api/public/songs?songName=Звезда' \
  | jq '.[0] | keys'
```

**Ожидаемый результат** (~38 ключей, в любом порядке):

```json
[
  "airTimestamp",
  "album",
  "alwaysFree",
  "author",
  "authorAlias",
  "bpm",
  "contentRemoved",
  "datePublish",
  "description",
  "formattedTextChords",
  "formattedTextSong",
  "formattedTextTabs",
  "freeAccessWindowEndText",
  "freelyAvailableNow",
  "id",
  "idVkChords",
  "idVkChordsID",
  "idVkChordsOID",
  "idVkKaraoke",
  "idVkKaraokeID",
  "idVkKaraokeOID",
  "idVkLyrics",
  "idVkLyricsID",
  "idVkLyricsOID",
  "idVkMelody",
  "idVkMelodyID",
  "idVkMelodyOID",
  "key",
  "onAir",
  "songName",
  "songPictureUrl",
  "songSubscriptionAvailable",
  "track",
  "warning",
  "year",
  "assignment",  // null для не-self-assign редакторов
  "shortDescription"  // пустое для includeDetails=false
]
```

**Чего НЕ должно быть** (25 ключей): `sponsrLinkGeneral`, `haveVkGroupLink`, `idStatus`, `vkPictureBase64`, `linkSponsrPlay`, `linkBoostyTxt`, `linkDzen*` (4), `linkVk*` (4), `linkTg*` (4), `linkMax*` (4), `linkPl*` (4).

### 1.3. `GET /api/public/song/{id}`

```bash
# ID любой существующей песни с idStatus >= 6
curl -s 'http://localhost:8897/api/public/song/12345' \
  | jq 'keys'
```

**Ожидаемый результат** — те же ~38 ключей, что в `/songs` (выше), но `formattedText*`, `description`, `shortDescription`, `warning` — заполнены.

**Чего НЕ должно быть** — те же 25 ключей.

### 1.4. `GET /api/public/zakroma/stream?author=КИНО` (NDJSON)

Проверяем стрим-ответ: каждое `song`-сообщение не содержит удалённых полей.

```bash
curl -s 'http://localhost:8897/api/public/zakroma/stream?author=КИНО' \
  | grep '"type":"song"' \
  | head -1 \
  | jq '.song | keys'
```

**Ожидаемый результат** — те же 10 ключей, что в 1.1.

### 1.5. Проверка размера payload (SC-001)

```bash
# Размер payload для автора «КИНО» (или любого с 20+ песен):
SIZE=$(curl -s 'http://localhost:8897/api/public/zakroma?author=КИНО' \
       | wc -c)
echo "Payload size: $SIZE bytes"
```

**Ожидаемое поведение** (после рефакторинга): payload сокращается минимум на 80% относительно до-рефакторингового состояния. Конкретное число зависит от количества песен автора, но для «КИНО» (~30 песен × 21 ссылка ≈ 30 × 22 × ~60 символов = ~40 KB мусора) — должно стать ~8 KB вместо ~48 KB.

## 2. Проверка karaoke-public (Vue SPA)

### 2.1. `/zakroma` — нет иконки Sponsr

Открыть `http://localhost:5173/zakroma?author=КИНО` (или `https://sm-karaoke.ru/zakroma?author=КИНО` для прод-сборки).

**Ожидаемое поведение**:
- В каждой строке таблицы песен НЕТ иконки `Sponsr` (раньше — пятая колонка, логотип `Sponsr`).
- В карточке песни (мобильный режим ≤ 600px) НЕТ иконки `Sponsr`.
- Ширина таблицы стала уже на ~32px (одна колонка). Остальные колонки не «прыгают».
- Другие иконки (`Cart`, `Player`, `Favorite`, `Playlist`) — на месте.

### 2.2. `/search` — нет иконки Sponsr

Открыть `http://localhost:5173/search?q=Звезда`.

**Ожидаемое поведение** — то же, что в 2.1.

### 2.3. `/song/{id}` — песня отображается корректно

Открыть `http://localhost:5173/song?id=12345` (id любой onAir песни).

**Ожидаемое поведение**:
- Hero-баннер, метаданные (автор, год, альбом, тональность, темп) — на месте.
- Кнопки `Favorite`, `Playlist`, `Share`, `ShareLinkButton` — на месте.
- Если self-assign редактор — кнопка «Взять в работу» — на месте.
- Текст песни / табулатура / аккорды — отображаются (если есть).
- Если `onAir=true` И нет стемов для плеера — отображаются embed-блоки VK-видео (`idVkKaraoke`, `idVkLyrics`, `idVkMelody`, `idVkChords`).
- Если стемы есть — отображается karaoke-плеер.

### 2.4. `/author-playlist` — плейлист работает

Открыть `http://localhost:5173/author-playlist?author=КИНО`.

**Ожидаемое поведение**:
- Список песен автора загружается через `/api/public/zakroma?author=КИНО`.
- Иконка Sponsr в списке не отображается (раньше могла быть — теперь нет).
- Кнопки плеера / лайка / избранного — на месте.

## 3. Проверка Legacy Thymeleaf

### 3.1. `/filter` — таблица без иконок Sponsr/Dzen/VK/Tg/Max/Pl

Открыть `http://localhost:8897/filter`.

**Ожидаемое поведение**:
- В таблице песен НЕТ колонок с иконками `Sponsr`, `Dzen`, `VK`, `Telegram`, `Max`, `Pl`.
- Другие колонки — на месте.
- Если был `linkBoosty` — колонка тоже удалена.

### 3.2. `/zakroma` (legacy Thymeleaf)

Открыть `http://localhost:8897/zakroma`.

**Ожидаемое поведение** — то же, что в 3.1.

### 3.3. `/testpage/{id}` — картинка на основе idStatus/haveVkGroupLink

Открыть `http://localhost:8897/testpage/12345`.

**Ожидаемое поведение**:
- Шаблон рендерится.
- Картинка отображается по логике:
  - `${!sett.haveVkGroupLink && sett.idStatus >= 3}` → `/tmp/${sett.id}.png`
  - Иначе → `KARAOKE_LOGO.png`
- Никаких ссылок на соцсети в HTML нет.

## 4. Проверка админки `webvue3`

### 4.1. Редактирование песни — поля ссылок доступны

Открыть `http://localhost:5174/songs` (admin SPA), выбрать любую песню на редактирование.

**Ожидаемое поведение**:
- В карточке редактирования ВСЕ 21+ поля ссылок на соцсети (`linkDzenKaraokePlay/Edit`, `linkVkKaraoke`, `linkTgLyrics`, `linkMaxChords`, `linkSponsrPlay`, и т.д.) — ДОСТУПНЫ для редактирования.
- Изменения сохраняются в БД (`tbl_songs`) через `ApiController.kt`.
- Никаких ошибок в консоли.

### 4.2. Список песен — фильтр по статусу

Открыть `http://localhost:5174/songs`, применить фильтр `idStatus >= 3`.

**Ожидаемое поведение**:
- Фильтр работает (т.к. `idStatus` доступен в `Song.kt` геттере для admin-side).
- Бэкенд `ApiController.kt` НЕ использует `SongPublicDto` — он использует `Song` напрямую. Поэтому рефакторинг публичного DTO не задевает админку.

## 5. Тест публикационных ботов

### 5.1. TelegramAutoPublishService

Проверить, что бот по-прежнему публикует посты. Бот читает `song.linkTelegramKaraoke` / `linkTelegramLyrics` / и т.п. из `Song.kt` (НЕ из публичного DTO).

**Действие**: запустить `karaoke-app` локально, поставить песню в очередь публикации в Telegram, дождаться результата.

**Ожидаемое поведение**: пост публикуется без ошибок. Логи не содержат `NullPointerException` или `NoSuchFieldError`.

### 5.2. VkAutoPublishService

Аналогично Telegram-боту — запуск локально, постановка в очередь.

**Ожидаемое поведение**: пост публикуется без ошибок.

## 6. Проверка через DevTools

### 6.1. Network panel — `/api/public/zakroma?author=КИНО`

Открыть DevTools → Network, перейти на `/zakroma?author=КИНО`, посмотреть тело ответа `/api/public/zakroma`.

**Ожидаемое поведение**: в JSON-теле НЕТ ключей `linkSponsrPlay`, `linkDzen*`, `linkVk*`, `linkTg*`, `linkMax*`, `linkPl*`, `linkBoosty`.

### 6.2. Network panel — `/api/public/songs?songName=Звезда`

Аналогично 6.1. Должны отсутствовать `sponsrLinkGeneral`, `haveVkGroupLink`, `idStatus`, `vkPictureBase64`, и все 21 ссылка на соцсети.

## 7. Проверка через `jq`

```bash
# Payload /api/public/zakroma для автора «КИНО» НЕ содержит удалённых ключей:
curl -s 'http://localhost:8897/api/public/zakroma?author=КИНО' \
  | jq '.[0].albums[].albumSettings[].linkSponsrPlay' 2>/dev/null
# Ожидаемый результат: null или ошибка (т.к. ключа нет вообще)

curl -s 'http://localhost:8897/api/public/zakroma?author=КИНО' \
  | jq '.[0].albums[0].albumSettings[0] | has("linkSponsrPlay")'
# Ожидаемый результат: false
```

## 8. Проверка через ktlint / линтеры

```bash
# Kotlin (для karaoke-web):
./gradlew :karaoke-web:ktlintCheck

# Vue (для karaoke-public):
cd karaoke-public && npm run lint:check

# ESLint baseline (не должно быть новых нарушений):
./tools/check-eslint-baseline.sh

# KDoc coverage (для удалённых data class'ов НЕ требуется KDoc):
./tools/check-kdoc-coverage.sh
```

**Ожидаемое поведение**: все линтеры — зелёные (или baseline не вырос).

## 9. Acceptance Criteria сводка

| SC | Критерий | Как проверить |
|---|---|---|
| SC-001 | Payload `/zakroma` сократился ≥ 80% | `curl ... | wc -c` |
| SC-002 | `/api/public/song/{id}` НЕ содержит 25 удалённых ключей | `curl ... | jq 'keys'` |
| SC-003 | DOM `/zakroma` НЕ содержит `<a ... data-link-name="sponsr">` (или `PlatformLink[link-name="sponsr"]`) | DevTools Elements |
| SC-004 | Админка `webvue3` сохранила все 21 поле | `webvue3/src/components/Songs/edit/SongEdit.vue:2843+` |
| SC-005 | Telegram/VK-боты публикуют без ошибок | ручной тест |
| SC-006 | Публичный SPA не сломан (`/song/{id}`, `/zakroma`, `/search`, `/author-playlist`) | ручной тест |
| SC-007 | Legacy Thymeleaf работает (без иконок соцсетей, но `idStatus`/`haveVkGroupLink` — на месте) | ручной тест |

## 10. Когда стоп / откат

Если хотя бы один из SC-001..SC-007 fails:

1. Не коммитим изменения в master.
2. Возвращаемся к feature-ветке `185-song-dto-audit-sponsr-remove`.
3. Проверяем `git status` — все ли файлы изменены:
   - `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt`
   - `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt`
   - `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` (только `zakromaStream` — строки 344-389)
   - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt` (строки 207-225 + 265-285)
   - `karaoke-public/src/views/SearchView.vue`
   - `karaoke-public/src/views/ZakromaView.vue`
   - `karaoke-web/src/main/resources/templates/filter.html`
   - `karaoke-web/src/main/resources/templates/zakroma.html`
   - `karaoke-web/src/main/resources/templates/testpage.html`
4. Делаем `git diff --stat`, чтобы понять, что не так.
5. Если SC-004 fails — значит, мы случайно удалили поле из `Song.kt` (что ЗАПРЕЩЕНО в спеке). Возвращаем.
6. Если SC-007 fails — значит, мы удалили `idStatus` или `haveVkGroupLink` из шаблона `testpage.html`. Возвращаем.

Если всё ОК — коммитим, PR, ждём CI 7/7 SUCCESS, merge.