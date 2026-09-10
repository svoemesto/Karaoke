# Quickstart: Настраиваемое количество строк на странице таблиц

**Spec**: [spec.md](./spec.md) | **Branch**: `359-rows-per-page` | **Issue**: OpenProject #74

## Сценарий проверки (manual E2E)

### Предусловия

1. Запущены локальные контейнеры: `karaoke-web`, `webvue3`, `karaoke-app`, `postgres`.
   ```bash
   docker ps --format '{{.Names}}\t{{.Status}}' | grep -E 'karaoke|webvue3|postgres'
   ```
2. Логин в админку как admin.
3. Файл `/sm-karaoke/system/Karaoke.properties` существует (на проде) или
   `karaoke-app` стартует с дефолтами (на чистом окружении).

### Шаг 1: проверить дефолты

Открыть таблицу «Песни» (`/songs`).

**Ожидаемое**:
- В верхнем блоке пагинации слева — поле «Строк на странице» со значением `50`.
- Таблица отображает 50 строк.

**Если нет поля** — UI не подцепил новый код. Проверить:
- `webvue3/src/components/Songs/SongsTable.vue` — добавлен `<b-form-input>`.
- `webvue3/src/store/modules/tableSettings.js` — зарегистрирован.
- Консоль браузера — нет ли ошибок при `loadTableSettings`.

### Шаг 2: изменить значение

В поле «Строк на странице» ввести `100`, нажать Enter.

**Ожидаемое**:
- Поле показывает loading-индикатор (spinner или disabled state) ~200 мс.
- После ответа — таблица отображает 100 строк, нумерация страниц пересчитывается.
- HTTP-запрос в DevTools → `/api/properties/setproperty` с телом `key=ui.songs.rows_per_page&stringValue=100`.

### Шаг 3: проверить persistence

Закрыть вкладку, открыть заново.

**Ожидаемое**:
- Поле «Строк на странице» — снова `100` (восстановлено).

### Шаг 4: проверить isolation между таблицами

Открыть таблицу «Авторы».

**Ожидаемое**:
- Поле «Строк на странице» — `30` (свой дефолт для Authors), а не `100` от Songs.

### Шаг 5: проверить невалидное значение

В таблице «Песни» ввести `0` или `10000`, нажать Enter.

**Ожидаемое**:
- Поле НЕ применяется (значение остаётся `100`).
- Toast/alert с сообщением «Значение должно быть от 1 до 1000».

### Шаг 6: проверить backend валидацию

Через `curl` отправить невалидное значение:
```bash
curl -X POST http://localhost:8080/api/properties/setproperty \
    -d "key=ui.songs.rows_per_page&stringValue=99999"
```

**Ожидаемое** (после реализации серверной валидации, FR-006):
- HTTP 400 + тело `{"error": "value must be integer in 1..1000"}`.

⚠️ До реализации серверной валидации — `200 OK` + значение сохраняется как `99999`.
UI будет показывать дефолт при следующей загрузке, если не парсится.

### Шаг 7: проверить все 14 таблиц

Пройти по всем 14 таблицам (Songs, Authors, Albums, Pictures, SiteUsers,
Subscriptions, ShareLinks, Dictionaries, Properties, SitePlaylists,
ListeningHistory, Processes, News, Stats):

| Таблица | Дефолт | URL |
|---------|--------|-----|
| Songs | 50 | `/songs` |
| Authors | 30 | `/authors` |
| Albums | 30 | `/albums` |
| Pictures | 30 | `/pictures` |
| SiteUsers | 30 | `/site-users` |
| Subscriptions | 25 | `/subscriptions` |
| ShareLinks | 25 | `/share-links` |
| Dictionaries | 30 | `/dictionaries` |
| Properties | 50 | `/properties` |
| SitePlaylists | 30 | `/site-playlists` |
| ListeningHistory | 500 | `/listening-history` |
| Processes | 50 | `/processes` |
| News | 30 | `/news` |

**Ожидаемое**: на каждой странице — поле «Строк на странице» с указанным дефолтом.

**ИСКЛЮЧЕНО**: `Stats/TopUsersTable` и `Stats/TopListenedSongsTable` — это chart-cards
с собственным `pageSize` через `$emit('page-size')`, другой паттерн (вне scope).

### Шаг 8: проверить UI «Настройки»

Открыть `/properties` → найти параметры `ui.songs.rows_per_page`, `ui.authors.rows_per_page`, и т.д.

**Ожидаемое**: 14 новых параметров видны и редактируемы (бонус — можно менять и через
этот UI).

## Команды для CI / smoke

```bash
# Backend compile
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel

# Lint
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck
cd webvue3 && npm run lint:check && cd ..

# Backend bootJar
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel

# Frontend Vite build
cd webvue3 && npm run build && npm run format:check && cd ..

# Docker
cd deploy && bash do.sh build_webvue3
```

## Известные ограничения

- **Global, not per-user** (см. Clarifications Q1) — все админы видят одно значение.
- **Backend валидация `1..1000`** добавляется в этой фиче (FR-006); до её реализации
  невалидные значения могут сохраниться в файл.
- **Параметры `ui.*.rows_per_page` НЕ скрыты** (`isHidden=false`) — будут видны в UI
  «Настройки» (бонус, можно редактировать оттуда). Если это мешает — изменить на `true`
  в `listKaraokeProperties`.
