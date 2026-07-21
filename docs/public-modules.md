# Публичные модули (karaoke-public / плеер / аккаунт) — карта

> **Status**: active
> **Last Updated**: 2026-07-21 (PR #24, 012-development-md-rewrite)

Детальные истории этих фич — в [docs/architecture-notes-archive.md](./architecture-notes-archive.md)
(Часть 2) и в memory. Здесь — карта + инварианты.

## karaoke-public (Vue SPA)

Публичный фронт (Vue 3 + Vite, Bootstrap 5), заменяет Thymeleaf `karaoke-web`.

**Двойной дизайн** classic/modern (`composables/useDesign.js`, выбор в localStorage; тонкие
view-обёртки `v-if design==='modern'`, каталоги `views/classic|modern`, CSS-переменные `--km-*`).
Таблицы Закрома/Поиск — `table-layout: fixed` с явной шириной. Сборка — `nvm use v25.7.0`.

**Закрома (`ZakromaView.vue`)** — быстрый клиентский фильтр по названию песни (sticky-панель
под шапкой, `computed filteredZakroma` над Vuex-геттером `zakroma`, без запроса к бэку); watch
на готовность плеера/плейлистов остаётся на исходном `zakroma`, не на отфильтрованном — иначе
каждая нажатая клавиша дёргала бы сеть.

## Поиск (`SearchView.vue`) с алиасами авторов

У каждого автора (`tbl_authors.aliases`, текст, `;`-разделитель, напр.
`Владимир Ткаченко;Владимир Кучеренко`) может быть список алиасов — имена солистов/участников
группы, по которым фанаты ищут вместо названия коллектива («цой» находит «КИНО»).
`Author.resolveByTerm()` (karaoke-app) резолвит термин в реальные имена авторов + СОВПАВШИЕ алиасы
(лёгкий raw-SELECT, безопасен для вызова из karaoke-web — не задевает karaoke-web Settings trap).
`Settings.getWhereList` ищет песни по набору имён через ключ `author_in`, разделитель
`Settings.AUTHOR_IN_DELIMITER = "\u0001"` (ASCII SOH, чтобы не пересечься с `;`/`,`/пробелами в именах).
Фолбэк на старое строгое `author=`, если резолвинг не дал совпадений — не ломает поиск по авторам
без записи в `tbl_authors`. `SettingsPublicDto.authorAlias` — какой именно алиас совпал (пусто при
совпадении по реальному имени), рендерится в `SearchView.vue` курсивом мельче в скобках
(несколько — через запятую).

Заполнено веб-поиском для всех 122 авторов (78 получили хотя бы один алиас, seed —
`deploy/karaoke-db/author_aliases_seed.sql`, часть помечена «ТРЕБУЕТ ПРОВЕРКИ» — желательна
ручная перепроверка).

## Онлайн-плеер (`/player/:id`)

Браузерный плеер, визуально идентичный MLT-рендеру. **ДВЕ намеренные копии `KaraokePlayer.js`**
(`webvue3/src/player/` — admin, читает локальные файлы `/api/song/{id}/..`; и
`karaoke-public/src/player/` — публичный, читает MinIO). **Любую правку логики плеера вносить в обе
копии.**

Публичный плеер гейтится: admin — клиентски по `idStatus>=3`; public — доступ по `onAir`/премиуму
+ **секретный жест** разблокировки (тройной Shift-клик по «Тональность»; вся логика на бэкенде, токен,
эндпоинты отдают 404 без токена).

Регулировка скорости воспроизведения (0.5x–3x) — живой `AudioParam` на уже играющих
`AudioBufferSourceNode` (без рестарта источников), позиция считается через якорь `_rateAnchorPos` вместо
прямого `currentTime-startedAt`. Громкость/якорь/скорость персистентны не только в рамках плейлиста,
но и глобально в `localStorage['karaoke-player-settings']` — подхватываются при открытии плеера на
любой другой песне.

**Демо-режим.** Не-премиум/анонимный посетитель при готовом контенте (не в эфире/не подписан) вместо
paywall получает короткий фрагмент вместо отказа. Фрагмент = «куплет группы 0» (`SETTING GROUP|N`,
дефолт группа 0) минус 5 сек отступа с фейд-ином — до конца куплета; `Settings.demoFragmentStartSeconds/
EndSeconds/FadeInSeconds` (karaoke-app). Обрезка — **на сервере**, `Mp3Trimmer.trimToRange`
(karaoke-web, чистый JVM-парсер mp3-фреймов, без ffmpeg, работает с VBR) — полный файл физически
не покидает сервер. Публичный плеер (только `karaoke-public`, НЕ admin-копия) рисует водяной знак
«ДЕМО», фейдит аудио на входе/выходе фрагмента, по окончании показывает DOM-оверлей (не Vue-модалку
— общего контекста между iframe-встройкой и отдельной вкладкой `/player/:id` нет) с переходом на
страницу песни.

## Регистрация/авторизация site users

`/login`, `/register`, `/account`. Отдельная от админки система — таблица `tbl_site_users`
(+ `tbl_site_user_tokens`); админских логинов (`tbl_users`) в проекте больше нет.

Токен сессии **персистентный** (не JWT): `SiteUserTokenService` на каждый запрос делает живой
SELECT (бан/logout мгновенны). Защита — `SiteAuthInterceptor` (не Spring Security chain).

Премиум — `is_premium` + независимый «вечный» `is_permanent_premium` → вычисляемый `isEffectivePremium`
(единая точка проверки доступа). Капча — Yandex SmartCaptcha (ключи в `tbl_public_settings`,
fail-open).

## Онлайн-редактор разметки (`/account/editor`) с модерацией

Упрощённый публичный аналог `SubsEdit.vue`: пользователь расставляет слоговые маркеры для
ВСЕХ голосов песни (задание = вся песня целиком), админ модерирует (workflow
`assigned→in_progress→submitted→approved/rejected`). **ДВЕ таблицы** (sync-движок пишет строку
целиком по одному направлению), **ОБЕ `SERVER_TO_LOCAL`** — `tbl_song_assignments`
(конверт+вердикт админа) и `tbl_song_assignment_drafts` (правки пользователя, JSON-массивы
по голосам).

Реальный рабочий цикл (назначить→работа→апрув) часто идёт ЦЕЛИКОМ на PROD — `assign`/`approve`/
`reject`/`delete` (`SongEditorController`) все target-aware (local|remote). Доступ к разделу —
отдельная роль `is_editor` на `SiteUser`, включается админом в webvue3 по образцу премиума.

## «Избранное» и «Плейлисты» + плейлист автора

`tbl_site_playlists`(+`_items`); «Избранное» = плейлист с `is_favorites=true`. Не-премиум:
только «Избранное». Гейтинг по премиуму — плейлисты **скрываются**, не удаляются.
Плеер плейлиста = тот же `/player/:id` в iframe с `?pl=1` (очередь/авто-переход внутри iframe,
токен следующего трека «точно в срок»). Плейлист автора — динамический read-only.

## «Чат с автором проекта»

Личная переписка премиум-пользователей сайта с автором — один непрерывный тред на пользователя
(append-only), `tbl_site_chat_messages`. Синхронизируется через универсальный sync-движок
(`chatmessages`, SERVER_TO_LOCAL) — см. [docs/features/dual-db-sync.md](./features/dual-db-sync.md).

## См. также

- [docs/architecture-notes-archive.md](./architecture-notes-archive.md) — dated-история фич
- [docs/features/premium-stems.md](./features/premium-stems.md) — premium-функционал
- [docs/features/mp4-render.md](./features/mp4-render.md) — рендер видео из плеера
