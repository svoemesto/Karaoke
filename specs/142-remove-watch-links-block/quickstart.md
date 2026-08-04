# Quickstart: Удалить блок «Ссылки на просмотр» со страницы песни

> Phase 1 output для спеки `specs/142-remove-watch-links-block/`.
> Сгенерировано `/speckit.plan` 2026-08-04.

Практическое руководство по проверке фичи в локальном окружении и на проде.
Сценарии ручные — автоматизированных e2e/visual-тестов в проекте нет.

## Предусловия (для ЛОКАЛЬНОЙ проверки)

- Node 22 LTS (`node --version` → `v22.*`). См. AGENTS.md, «Runtime —
  node:22-alpine (LTS, детерминирован)».
- Проект собирается: `cd karaoke-public && npm install && npm run lint && npm run build`.
- Локально крутится dev-сервер: `cd karaoke-public && npm run dev` (Vite,
  `http://localhost:5173`).
- В локальной БД есть хотя бы одна песня со статусом `onAir=true` — иначе
  блок не отрисуется даже до правки (см. условие `v-if="currentSong.onAir"`
  в `SongView.vue`). Чтобы не искать «эфирную» песню руками:
  ```bash
  # в psql к локальной БД
  SELECT id, song_name FROM tbl_settings WHERE on_air = true ORDER BY id LIMIT 5;
  ```
  Если пусто — можно временно поставить `on_air = true` у любой песни через
  админку (`webvue3`) — потом вернуть.

## Предусловия (для ПРОДА)

- Мержен PR в `master`, CI 7/7 PASS (см. AGENTS.md «CI-gate для master»).
- Деплой выполнен пользователем: `cd deploy && bash do.sh build_start_public`.
- Прод-URL страницы песни: `https://sm-karaoke.ru/song?id=<id>`.
- В прод-БД есть песня с `on_air = true` (для проверки блока до и после).

## Сценарий 1 — Локальная проверка «ДО» (baseline)

Проверяем, что блок СЕЙЧАС есть (чтобы потом было с чем сравнить).

1. Запустить локальный dev-сервер:
   ```bash
   cd karaoke-public && npm run dev
   ```
2. Открыть `http://localhost:5173/song?id=<id_эфирной>`.
3. Скроллить ниже онлайн-плеера до карточки «Ссылки на просмотр».
4. **Ожидаемый результат**: блок виден, в нём 5 групп (Все / Karaoke / Lyrics / TABS / Chords) с иконками Sponsr / Dzen / VK / Telegram / Max.
5. В DevTools: элемент `<div class="km-links-card">` присутствует в DOM.

## Сценарий 2 — Применение правки

1. Переключиться на feature-ветку (после создания PR):
   ```bash
   git fetch origin
   git checkout -b 142-remove-watch-links-block origin/master
   ```
2. Pre-чек (из `plan.md → Open Questions`, должен пройти ДО правки):
   ```bash
   grep -n "km-link" karaoke-public/src/views/SongView.vue
   ```
   - **Ожидаемый результат**: все строки находятся ВНУТРИ блока
     `v-if="currentSong.onAir"` (строки ~199–334). Если что-то за
     пределами — НЕ удалять CSS, пересмотреть подход.
3. В файле `karaoke-public/src/views/SongView.vue`:
   - Удалить блок `<div v-if="currentSong.onAir" class="km-links-card">…</div>`
     полностью (DOM + всё содержимое).
   - Удалить CSS-правила `.km-links-*` и `.km-link-*` + адаптивное
     `.km-links-grid { gap: 0.5rem; }` (если pre-чек подтвердил, что они
     не используются вне блока).
   - Удалить `import PlatformLink from '../components/PlatformLink.vue'`
     и регистрацию `PlatformLink` в `components: { … }`, если других
     использований в файле больше нет.
4. Прогнать локальные проверки:
   ```bash
   cd karaoke-public
   npm run lint:check
   npm run build
   ```
   - **Ожидаемый результат**: обе команды завершаются с кодом 0;
     новых ошибок/предупреждений нет.

## Сценарий 3 — Локальная проверка «ПОСЛЕ» (валидация)

1. После правки снова:
   ```bash
   cd karaoke-public && npm run dev
   ```
2. Открыть `http://localhost:5173/song?id=<id_эфирной>`.
3. Проверить визуально:
   - **Нет** блока «Ссылки на просмотр».
   - Под плеером — пусто (никаких заглушек, никаких placeholder'ов).
4. В DevTools: элемента `<div class="km-links-card">` НЕТ в DOM.
5. Переключить дизайн (если в localStorage активен `modern`, переключить
   на `classic` через UI; либо наоборот — через `localStorage` `km.design`):
   ```js
   localStorage.setItem('km.design', 'classic')  // или 'modern'
   ```
   - **Ожидаемый результат**: блок отсутствует в обоих дизайнах
     (т.к. `SongView.vue` общий).

## Сценарий 4 — Проверка «НЕ сломали соседей»

1. Открыть `http://localhost:5173/search?q=*` — убедиться, что в
   результатах поиска ссылки на платформы (PlatformLink-иконки) на месте.
2. Открыть `http://localhost:5173/zakroma` (если есть локальные данные) —
   убедиться, что PlatformLink-ссылки на платформы на месте.
3. **Ожидаемый результат**: визуально и функционально ничего не изменилось.

## Сценарий 5 — Прогон success criteria (SC-001…SC-005)

После `npm run build` (т.е. до коммита/деплоя) выполнить:

```bash
# SC-001 — после деплоя на прод; для локальной проверки — DevTools в браузере

# SC-002 — после удаления CSS нигде в karaoke-public/src не должно остаться km-links-*
grep -rn '\.km-links-card\|\.km-links-title\|\.km-links-grid\|km-links\|km-link-' karaoke-public/src/
# ожидается: пусто (или — если остались CSS-правила вне SongView.vue — это К баг,
# см. pre-чек задачу #1)

# SC-003 — PlatformLink не импортируется в SongView.vue
grep -n "PlatformLink" karaoke-public/src/views/SongView.vue
# ожидается: пусто

# SC-004 — npm run build без новых ошибок (уже прогоняли в Сценарии 2)

# SC-005 — PlatformLink продолжает жить в SearchView и ZakromaView
grep -rn "PlatformLink" karaoke-public/src/views/
# ожидается: SearchView.vue и ZakromaView.vue (НЕ SongView.vue)
```

## Сценарий 6 — Проверка на ПРОДЕ (после деплоя)

1. Проверить, что деплой прошёл (см. AGENTS.md, раздел «Деплой»):
   - в логах `do.sh build_start_public` нет `EOF` / `400 Bad request`;
   - на сервере `Status: Downloaded newer image` (не `Image is up to date`).
2. На любой эфирной песне проверить HTML-исходник:
   ```bash
   curl -s "https://sm-karaoke.ru/song?id=<id_эфирной>" \
     | grep -E 'km-links-card|km-links-title|km-link-group'
   # ожидается: пусто
   ```
3. В браузере открыть `https://sm-karaoke.ru/song?id=<id_эфирной>` и
   визуально убедиться: блок «Ссылки на просмотр» отсутствует, страница
   рендерится без ошибок, плеер, метаданные, карточка демо/подписки — на
   месте (см. `SongView.vue` строки 100–200, до и после удаляемого блока).
4. Опционально — в DevTools убедиться, что нет «висящих» ссылок на
   отсутствующий CSS/JS (Vite обычно отдаёт 404 на отсутствующие чанки,
   но мы CSS удаляем вместе с блоком, так что 404 не ожидается).

## Известные тонкости

- **Кеш браузера**: после деплоя — `Ctrl+Shift+R` (hard reload), иначе
  клиент может увидеть старую версию статики.
- **Vite HMR**: локально изменения подхватываются автоматически, иногда
  с глюками на SFC с условным `<script>` — если не обновляется — `Ctrl+R`.
- **`onAir=false`**: блок и ДО правки не показывался для таких песен; для
  них `v-if="currentSong.onAir"` уже возвращал false и не рендерил.
  После правки разница только в удалённом коде. Сценарий 3 для таких
  песен — не информативен (блока и так не было); нужен эфирный кейс.
- **`PlatformLink.vue` props**: для отката достаточно `git revert <merge-commit>`
  (DOM-блок + CSS + импорт вернутся одним коммитом).

## Готово

После прохождения всех 6 сценариев — фича проверена локально и на проде.
Дальше: создать PR, дождаться CI 7/7 PASS, смёрджить, после деплоя
проверить Сценарий 6.
