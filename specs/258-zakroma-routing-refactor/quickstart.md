# Quickstart: Закрома — header-back-link из SongView + рефакторинг URL-routing

**Feature**: 258 — `specs/258-zakroma-routing-refactor`
**Date**: 2026-08-27

Phase 1 — руководство по валидации end-to-end сценариев после имплементации.

## Предусловия

1. **Backend**: локально поднят `karaoke-web` (порт 8080) + Postgres (с заполненными `tbl_authors`, `tbl_songs`).
2. **Frontend**: локально поднят `karaoke-public` (порт 5173, через `npm run dev`) ИЛИ собран и задеплоен через `bash deploy/do.sh build_start_public`.
3. **Браузер**: открыть DevTools → Console (для проверки console.warn) и Network (для проверки redirect'ов).

## Сценарий 1: базовый flow — back-link из SongView ведёт на страницу песен автора (P1)

**Цель**: подтвердить, что после имплементации клик «← Назад» из SongView возвращает на страницу песен автора, а не на тайлы.

### Шаги

1. Открыть `http://localhost:5173/zakroma` (или `http://<server>/zakroma` для прода).
2. В DevTools → Console выполнить: `document.querySelectorAll('.at-tile').length` → должно быть > 0 (сетка тайтлов видна).
3. Кликнуть на любой тайл автора (например, «Машина Времени»).
4. **Проверить URL**: должен стать `http://localhost:5173/zakroma/<id>` (например, `/zakroma/42`).
   - ❌ НЕ должен быть `/zakroma?author=Машина%20Времени` (это legacy, после имплементации редиректится в beforeEach).
5. В DevTools → Console: `location.pathname` → должно быть `/zakroma/42`.
6. В DevTools → Console: `document.querySelector('.km-author-header-sticky')` → должен существовать (фильтр-блок автора виден).
7. Кликнуть на любую песню в списке.
8. **Проверить URL**: должен стать `/song?id=<songId>&authorId=42`.
9. В DevTools → Console: `location.pathname === '/song'` → `true`.
10. В DevTools → Console: `new URLSearchParams(location.search).get('authorId')` → должно быть `'42'`.
11. В шапке `SongView` кликнуть на «← К песням автора».
12. **Проверить URL**: должен стать `/zakroma/42` (НЕ `/zakroma`).
13. В DevTools → Console: `document.querySelector('.km-author-header-sticky')` → должен существовать (список песен виден).
14. В DevTools → Console: `document.querySelector('.at-grid, .km-author-tiles, [data-author-tiles]')` → должен быть `null` (тайлы НЕ видны, мы на странице песен).

### Ожидаемый результат

Все 14 шагов проходят без ошибок. URL после back-link клика = `/zakroma/42` (та же страница, что до клика на песню).

---

## Сценарий 2: legacy URL `/zakroma?author=X` редиректится на `/zakroma/:id` (FR-A2)

**Цель**: подтвердить, что старые ссылки (из закладок, чатов) корректно редиректятся.

### Шаги

1. Открыть `http://localhost:5173/zakroma?author=Машина%20Времени` (legacy URL).
2. В DevTools → Network → должна быть одна запись `authors-tiles` (запрос за тайлами), затем redirect.
3. После ~200мс проверить URL: должен стать `http://localhost:5173/zakroma/42` (или любой существующий ID для «Машина Времени»).
4. В DevTools → Console: `location.pathname` → `/zakroma/42`.
5. В DevTools → Console: `location.search` → `''` (без query).
6. В DOM: должен быть виден список песен автора, а не тайлы.

### Ожидаемый результат

Пользователь, открывший старую закладку, попадает на новый URL без 404, без ручного вмешательства.

---

## Сценарий 3: legacy URL `/zakroma?specialBucket=true` редиректится на `/zakroma/special-bucket` (FR-A7)

**Цель**: подтвердить, что старая ссылка на спец-корзину тоже работает.

### Шаги

1. Открыть `http://localhost:5173/zakroma?specialBucket=true` (legacy URL).
2. После ~100мс проверить URL: должен стать `http://localhost:5173/zakroma/special-bucket`.
3. В DOM: должна быть видна спец-корзина (таблица с авторами-спецзаказами).
4. В DevTools → Console: `location.pathname` → `/zakroma/special-bucket`.

### Ожидаемый результат

Спец-корзина открывается корректно, без 404.

---

## Сценарий 4: прямой заход на `/zakroma/:authorId` (deep link)

**Цель**: подтвердить, что прямое открытие страницы песен автора (без истории переходов) работает.

### Шаги

1. Открыть **новую вкладку** (без истории переходов с `/zakroma`).
2. Перейти на `http://localhost:5173/zakroma/42` (предполагая, что ID 42 существует).
3. В DevTools → Network → должен быть запрос `zakroma/stream?author=...` (загрузка песен автора).
4. После загрузки (~1-3 сек) в DOM должен быть виден список песен автора.
5. В DevTools → Console: `document.querySelector('.km-author-header-sticky')` → должен существовать.

### Ожидаемый результат

Страница песен автора открывается напрямую, без ошибок. Стрим загружает данные, прогресс-бар работает (если стрим медленный).

---

## Сценарий 5: валидация `:authorId` — невалидный ID → 404 (RT-6)

**Цель**: подтвердить, что Vue-router сам отклоняет невалидные URL.

### Шаги

1. Открыть `http://localhost:5173/zakroma/abc` (невалидный — не цифры).
2. **Ожидание**: vue-router показывает 404 (или fallback на дефолтную страницу).
3. Открыть `http://localhost:5173/zakroma/0` (ID = 0 — потенциально невалидный).
4. **Ожидание**: компонент загружается, но `mounted()` не находит автора в `authorTiles` → показывает тайты с toast «Автор не найден».

### Ожидаемый результат

Невалидные URL не приводят к падению приложения. `/zakroma/abc` → 404. `/zakroma/0` → безопасный fallback.

---

## Сценарий 6: browser-back работает корректно (US3)

**Цель**: подтвердить, что history.back из SongView возвращает на страницу песен автора.

### Шаги

1. Открыть `http://localhost:5173/zakroma/42`.
2. Кликнуть на любую песню → URL становится `/song?id=Y&authorId=42`.
3. Нажать кнопку «←» (browser back) или `Alt+←` (macOS).
4. **Проверить URL**: должен стать `/zakroma/42`.
5. В DOM: список песен автора.
6. В DevTools → Console: `history.length` — не должно быть «лишних» шагов в истории (т.к. legacy redirect'ы используют `replace: true`).

### Ожидаемый результат

Browser back работает идентично клику на «← К песням автора» в шапке.

---

## Сценарий 7: спец-корзина на новом route (FR-A6)

**Цель**: подтвердить, что `/zakroma/special-bucket` работает как самостоятельная страница.

### Шаги

1. Открыть `http://localhost:5173/zakroma/special-bucket`.
2. В DOM: должна быть видна спец-корзина (таблица со спецзаказными авторами).
3. В шапке слева — back-link «← К списку авторов».
4. Кликнуть на back-link → URL становится `/zakroma`, тайлы авторов видны.
5. В шапке — back-link скрыт (т.к. мы на тайлах).

### Ожидаемый результат

Спец-корзина — самостоятельный route со своим back-link, корректно стыкуется с `/zakroma`.

---

## Сценарий 8: watcher из спеки 255 удалён (FR-A4)

**Цель**: подтвердить, что watcher `'$route.query.author'` больше не нужен и не сломал UI.

### Шаги

1. Открыть `http://localhost:5173/zakroma/42`.
2. В DevTools → Console: `document.querySelector('.km-author-header-sticky')` → существует (фильтр-блок виден).
3. Открыть Vue DevTools (если установлен) → найти `ZakromaView` → `data` → `authorChosen` должно быть `true`.
4. Перейти на `http://localhost:5173/zakroma` (через шапку или URL bar).
5. **Без перезагрузки страницы**: `authorChosen` должно стать `false` (компонент пересоздан vue-router'ом).
6. В DOM: `.km-author-header-sticky` должен исчезнуть, `.at-grid` появиться.

### Ожидаемый результат

Vue-router пересоздаёт компонент при смене path → state сбрасывается естественно. Watcher не нужен.

---

## Сценарий 9: regression — все существующие RouterLink продолжают работать (US4)

**Цель**: подтвердить, что никакие существующие ссылки не сломались.

### Шаги

Запустить grep по всему проекту:

```bash
grep -rn 'to="/zakroma"' karaoke-public/src/views/ --include="*.vue"
```

Список файлов, использующих `to="/zakroma"`:
- `CartView.vue` — должна вести на тайлы (как раньше `/zakroma` без query).
- `HistoryView.vue` — то же.
- `AboutView.vue` — то же.
- `HomeView.vue` — то же.
- `EditorWorkView.vue` — то же.
- `SearchView.vue` — то же.

### Проверка

Для каждого файла:
1. Открыть соответствующую страницу.
2. Кликнуть на ссылку «Закрома» / «Каталог» / etc.
3. **Должны попасть на `/zakroma`** (тайлы авторов), без query.

### Ожидаемый результат

Все 6+ ссылок продолжают работать без изменений. Никаких 404, никаких редиректов на `/zakroma/...` (т.к. это тайлы без автора).

---

## Сценарий 10: console errors / warnings

**Цель**: подтвердить, что в console нет ошибок и неожиданных warnings.

### Шаги

1. Открыть `http://localhost:5173/zakroma`.
2. Открыть DevTools → Console.
3. Очистить консоль (`clear` или кнопка «🚫»).
4. Пройти все сценарии 1-9.
5. **Проверить**: в console нет красных ошибок (`[Vue warn]`, `Uncaught`, `404`, `Cannot read property of undefined`).
6. Допустимы: `[HMR]`, `[Vite]`, `trackUi navigate` (наш трекинг).

### Ожидаемый результат

0 ошибок в console. Все warnings (если есть) — понятные и не блокирующие.

---

## Build & lint проверки

### Frontend

```bash
cd karaoke-public
npm run build     # → PASS, 0 errors
npm run lint      # → 0 warnings, 0 errors
bash ../tools/check-eslint-baseline.sh karaoke-public  # → 0/0 новых нарушений
```

### Backend

```bash
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
# → BUILD SUCCESSFUL
./gradlew :karaoke-web:bootJar --parallel
# → :karaoke-web:bootJar UP-TO-DATE (если только data-model не менялся)
#     или BUILD SUCCESSFUL (если DTO изменялся)
```

**Если DTO `AuthorTilePublicDto` изменялся** — нужно пересобрать `karaoke-web:bootJar`:
```bash
./gradlew :karaoke-web:bootJar --parallel
```

---

## Smoke-test после деплоя

После `bash deploy/deploy_web.sh` (выполняется пользователем):

1. Открыть `https://<prod>/zakroma` — должна быть сетка тайтлов.
2. Кликнуть на тайл → `https://<prod>/zakroma/<id>` — песни автора.
3. Кликнуть на песню → `https://<prod>/song?id=Y&authorId=<id>` — страница песни.
4. Клик «← К песням автора» → возврат на `https://<prod>/zakroma/<id>`.
5. Старая ссылка `https://<prod>/zakroma?author=Машина%20Времени` → редирект на `https://<prod>/zakroma/<id>`.

---

## Чеклист для code review

- [ ] `AuthorTilePublicDto.kt` содержит новое поле `val id: Long`.
- [ ] `fromAuthorName` принимает параметр `id: Long`.
- [ ] `PublicApiController.authorsTiles` использует `Author.loadIdsByNames(...)` для получения ID.
- [ ] `Author.loadIdsByNames` реализован с chunking по 100 и обработкой `SQLException`.
- [ ] В `router/index.js` есть маршруты `/zakroma/:authorId(\\d+)` и `/zakroma/special-bucket`.
- [ ] В `router/index.js` есть global `beforeEach` guard для legacy URL.
- [ ] `ZakromaView.vue`:
  - [ ] `data()` читает `params.authorId` вместо `query.author`.
  - [ ] Watcher `'$route.query.author'` удалён.
  - [ ] В `mounted()` есть резолвинг ID → name через `authorTiles.find`.
  - [ ] Обработка случая «автор не найден» (toast + сброс `authorChosen`).
- [ ] `SongView.vue`:
  - [ ] Computed `songHeaderBack` динамически строит back-link.
  - [ ] В `<AppHeader :back="songHeaderBack" />` (вместо хардкода).
- [ ] `ZakromaView.vue`:
  - [ ] RouterLink на `/song` содержит `query.authorId`.
- [ ] Никаких изменений в `ZakromaView.vue` стейт-машине `selectedAuthor/specialBucket` (логика та же, инициализация через path).
- [ ] Никаких изменений в backend `Zakroma.getZakroma`, `Zakroma.getZakromaBySpecialOrder`, `PublicApiController.zakroma*`.
- [ ] Никаких изменений в Vuex store (только data-чтение из новых sources).

---

## Связанные артефакты

- **Спека**: `specs/258-zakroma-routing-refactor/spec.md`
- **Research**: `specs/258-zakroma-routing-refactor/research.md` (RT-1..RT-8)
- **Data Model**: `specs/258-zakroma-routing-refactor/data-model.md`
- **Contracts**: `specs/258-zakroma-routing-refactor/contracts/index.md`
- **Tasks**: `specs/258-zakroma-routing-refactor/tasks.md` (генерируется `/speckit.tasks`)
- **Связанные спеки**: 254 (header-back-link), 255 (state-reset-on-back-nav — watcher удаляется)
