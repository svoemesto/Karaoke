# Per-Feature: Блок «Последние 5 новостей» на главной странице сайта

> **Feature Key**: `homepage-latest-news`
> **Status**: active
> **Slug**: `homepage-latest-news`
> **Спека**: [specs/144-homepage-latest-news/spec.md](../../specs/144-homepage-latest-news/spec.md)
> **План**: [specs/144-homepage-latest-news/plan.md](../../specs/144-homepage-latest-news/plan.md)
> **Tasks**: [specs/144-homepage-latest-news/tasks.md](../../specs/144-homepage-latest-news/tasks.md)

## Что делает

Добавляет на главную страницу сайта (`/`) компактный блок «последние 5 новостей» в трёх местах:

- **SPA `karaoke-public`** — новый компонент `LatestNewsSection.vue`, встроенный в `HomeView.vue` между блоком статистики (карточки «Песен в коллекции / В открытом доступе / По подписке / В работе») и блоком навигационных карточек.
- **Legacy Thymeleaf `karaoke-web`** — таблица из 5 строк в `main.html`, отрендеренная сервером через `MainController.main()`.
- **Данные** — переиспользуется существующий эндпоинт `GET /api/public/news?page=0&size=5` (без изменений в бэкенде).

Каждая строка состоит из трёх колонок: **иконка-монетка** (категория новости) + **дата/время** (`dd.MM.yyyy HH:mm`) + **заголовок** (`News.title`). Заголовок блока содержит справа ссылку «Все новости» → `/news`. **Вся строка кликабельна** (а не только текст заголовка) — `cursor: pointer` + лёгкий ховер-фон; клик ведёт на `News.link`.

**Иконка-монетка по `News.category`** (добавлено в PR `147-homepage-latest-news-refine`):

| `News.category` | Иконка | Смысл |
|---|---|---|
| `premium` | 🟡 золотая | Новость «В коллекции» |
| `air` | ⚪ серебряная | Новость «В эфире» |
| `feature`, `general`, прочие | 🟢 зелёная | Функционал / прочее |

В SPA — через `<SvgIcon :name="coinIconName(n)" />`, где `coinIconName` маппит `category → coin-gold/silver/green`. В Thymeleaf — inline-SVG с тем же viewBox/путями, что в `SvgIcon.vue`.

## Зачем

- Дать посетителю на первом экране главной живой сигнал «проект развивается, новые песни выходят» (на проде сейчас ≈19000+ опубликованных новостей благодаря `specs/089-auto-news-song-release`, но на главной они до сих пор не видны).
- Поддержать ключевую воронку роста **visitor→registration** (см. [docs/strategy/growth.md](../strategy/growth.md)): посетитель видит свежесть контента → регистрируется → возвращается.
- Минимальное, обратимое расширение — никаких новых таблиц/миграций/sync-целей, фича чисто презентационная.

## Как работает

### SPA `karaoke-public` (Vue 3 + Vite + Bootstrap 5)

`LatestNewsSection.vue` — компонент, который при `mounted()` делает `fetch('/api/public/news?page=0&size=5')`:

```js
fetch('/api/public/news?page=0&size=5')
  .then(r => r.ok ? r.json() : Promise.reject(r.status))
  .then(data => items.value = data.items)
  .catch(() => {}) // тихая деградация — состояние items остаётся пустым
```

В шаблоне:

```vue
<table v-if="items && items.length" class="km-latest-news-table">
  <tr v-for="n in items.filter(x => x.link && x.title?.trim())" :key="n.id">
    <td class="km-latest-news-date">{{ formatRu(n.publishAt) }}</td>
    <td class="km-latest-news-title">
      <a :href="n.link" @click.prevent="goTo(n)">{{ n.title }}</a>
    </td>
  </tr>
</table>
```

Клик по ссылке отправляет существующее событие трекинга (см. `services/tracking.js`) — для авто-новостей `trackLinkToSong(songId)`, для ручных — `trackUi('click', 'homeNews:' + n.id)`.

### Legacy Thymeleaf `karaoke-web` (Spring Boot)

`MainController.main()` (`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt:50`) при рендеринге шаблона вызывает `News.loadPublished(WORKING_DATABASE, limit = 5, offset = 0)` и кладёт результат в `model.addAttribute("latestNews", ...)`.

`main.html` итерирует по `latestNews` и рендерит таблицу из 5 строк:

```html
<table th:if="${!#lists.isEmpty(latestNews)}">
  <tr th:each="n : ${latestNews}"
      th:if="${!#strings.isEmpty(n.link) and !#strings.isEmpty(n.title)}">
    <td th:text="${#dates.format(n.publishAt, 'dd.MM.yyyy HH:mm')}">—</td>
    <td><a th:href="${n.link}" th:text="${n.title}">title</a></td>
  </tr>
</table>
```

### Источник данных

Переиспользуется `News.loadPublished` (`karaoke-app/.../model/News.kt:212`) — никакого нового SQL:

```sql
SELECT id, title, body, category, link, publish_at, created_at, source
FROM tbl_news
WHERE publish_at IS NOT NULL AND publish_at <= now()
ORDER BY publish_at DESC, id DESC
LIMIT 5
```

Существующий индекс по `publish_at` (или seq-scan с ранним выходом на `LIMIT 5`) даёт <300 мс на объёме 19000+ записей.

## Инварианты

- **Бэкенд не меняется** — фича только читает `tbl_news` через существующий эндпоинт. Никаких новых контроллеров, никаких миграций, никаких изменений SyncRegistry.
- **`tbl_news` не меняется** — фича только переиспользует существующие поля. Никаких новых колонок/индексов/триггеров.
- **Фильтр «только осмысленные строки»** — строки с пустым `link` или пустым/пробельным `title` НЕ попадают в блок (правило FR-006 спеки). Альтернатива «disabled-ссылка» отвергнута как худший UX.
- **Тихaя деградация при ошибке бэкенда** — если запрос упал/таймаут/невалидный JSON, блок просто не рендерится. Никакого спиннера, никакого сообщения об ошибке (FR-013 спеки).
- **Legacy Thymeleaf не падает** — если `News.loadPublished` бросил исключение или вернул пустой результат, страница рендерится со статусом 200 и без блока (или с эквивалентом пустого состояния).
- **Анонимный доступ** — блок виден без авторизации/подписки.
- **Сортировка идентична ленте `/news`** — `publish_at DESC, id DESC` (детерминированность на границах страниц).

## Известные ловушки

- **Двойной источник правды для сортировки** — не «оптимизировать» SQL в `LatestNewsSection` (например, добавлять `LIMIT 5 OFFSET 0` руками) — переиспользуем `News.loadPublished`, чтобы не было дрейфа с лентой `/news`.
- **Thymeleaf `th:if` vs `v-if`** — фильтрация на обоих фронтах применяется **одинаково** (пустой `link` ИЛИ пустой/пробельный `title`); расхождение приведёт к тому, что SPA и Thymeleaf покажут разный набор 5 строк (нарушит SC-004 спеки).
- **Иконка-монетка: дублирование путей SPA↔Thymeleaf** — три SVG (coin-gold/silver/green) живут в двух местах: в `SvgIcon.vue` (SPA) и в `main.html` inline (Thymeleaf). При добавлении новой категории/нового варианта — обновлять **оба места**, иначе нарушится SC-004. Альтернатива — вынести SVG в общий ассет MinIO или статику `/static/news-coins.svg` — не сделано, потому что текущий объём (3 варианта × 1 файл) ниже порога окупаемости.
- **Thymeleaf: «вся строка кликабельна»** — реализовано через `onclick="if (!event.target.closest('a')) window.open(...)"` на `<tr>`. Проверка `closest('a')` нужна, чтобы клик по ссылке `<a>` внутри `<td>` не дублировал открытие (target уже сам откроет). Удалять эту проверку нельзя.
- **`window.open(n.link, '_blank', ...)` зависит от nginx-конфига на проде** — для **любой** ссылки, ведущей на `/song?id={id}` (или любой другой путь, который nginx маппит на неправильный backend), пользователь получит не то, что ожидает. Конкретно для этой фичи: после PR #144 блок новостей на главной использует `window.open(News.link)` — если nginx на проде неправильно маппит `/song` (Pass 35, 2026-08-05 — было именно так), клик по новости показывал OG-картинку вместо страницы песни. Фикс — в `deploy/web-server-deploy/deploy/80to8897`, `location /song { if ($http_user_agent ~* "vkShare|...") rewrite ... last; proxy_pass http://127.0.0.1:7907; }`. Проверить, что **новые** относительные ссылки (`News.link` для новых категорий новостей) попадают в SPA-ветку, а не в OG-endpoint или legacy-Thymeleaf. Если добавляется новый тип ссылки, который не SPA — добавить его в User-Agent-список ботов в `80to8897` явно НЕ нужно (тогда всё пойдёт через SPA); а вот добавить **исключение** в `location` nginx, если новый путь должен идти в karaoke-web — обязательно.
- **Не использовать `date-fns`/`moment`/`dayjs`** — нативный `Intl.DateTimeFormat('ru-RU', ...)` достаточен, новая зависимость не нужна (для Thymeleaf — стандартный `#dates.format` + Spring locale).
- **Service Worker** — если позже в `karaoke-public` появится SW с persistent кешем, блок новостей сломается (US-2.4 спеки); см. T013 — следить за этим.
- **Race при публикации новой новости** — допустимо показать любой согласованный снимок из 5 строк на момент начала запроса; реалтайм-синхронизация не требуется.
- **Поле `News.body`** — НЕ используется в блоке (только `title`); если `title` пустое, строка отбрасывается, а не подставляется fallback из `body` (намеренное упрощение UX).
- **CI-gate для master** — фича **не может** быть закоммичена напрямую в master (см. AGENTS.md «CI-gate»); только через feature-ветку `NNN-homepage-latest-news*` + PR + 7/7 CI SUCCESS.

## Ссылки

- [specs/144-homepage-latest-news/spec.md](../../specs/144-homepage-latest-news/spec.md) — спецификация фичи
- [specs/144-homepage-latest-news/plan.md](../../specs/144-homepage-latest-news/plan.md) — имплементационный план
- [specs/144-homepage-latest-news/data-model.md](../../specs/144-homepage-latest-news/data-model.md) — модель данных (read-only)
- [specs/144-homepage-latest-news/contracts/public-news-api.md](../../specs/144-homepage-latest-news/contracts/public-news-api.md) — контракт переиспользуемого API
- [specs/144-homepage-latest-news/quickstart.md](../../specs/144-homepage-latest-news/quickstart.md) — ручная валидация (8 сценариев)
- [docs/features/news-publish-backfill.md](news-publish-backfill.md) — backfill флагов публикации
- [docs/features/news-templates.md](news-templates.md) — шаблоны авто-новостей
- [specs/089-auto-news-song-release](../../specs/089-auto-news-song-release/spec.md) — авто-создание новостей о выходе песни (источник данных для блока)
- [specs/090-news-pagination](../../specs/090-news-pagination/spec.md) — пагинация ленты `/news` (брат-близнец)
- [docs/strategy/growth.md](../strategy/growth.md) — стратегия роста (visitor→registration)
