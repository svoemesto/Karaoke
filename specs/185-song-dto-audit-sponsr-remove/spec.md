# Feature Specification: Аудит публичного DTO песни и удаление ссылки на Sponsr из таблиц Закромов и поиска

**Feature Branch**: `185-song-dto-audit-sponsr-remove`

**Created**: 2026-08-14

**Status**: Draft

**Input**: User description: "Убрать ссылку на sponsr из таблиц закромов и поиска. Провести аудит дто песен на проде и убрать из него всё лишнее - например теперь нигде на проде не используются ссылки на публикации в соцсетях (это надо проверить), а значит тащить в дто весь массив ссылок, версий песни на каждой площадке и т.п. не нужно. Проверить на "нужность" в дто каждое поле и обсудить, нужно оно на проде или нет."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Гость видит таблицу Закромов и поиска без иконки Sponsr (Priority: P1)

Гость (анонимный посетитель сайта) заходит на страницу `/zakroma` или `/search`, видит таблицу со списком песен. Раньше в строке каждой песни была отдельная колонка с иконкой-платформой `Sponsr` (открывала персональную страницу песни на `sponsr.ru` в новой вкладке). Сейчас эта колонка исчезает: вместо неё — пустое место или сдвинутые колонки. Таблица становится уже и не рекламирует внешнюю платформу.

**Why this priority**: Это основное действие, запрошенное пользователем явно («убрать ссылку на sponsr из таблиц закромов и поиска»). Без этого изменения вся остальная чистка DTO не имеет видимого смысла для посетителя.

**Independent Test**: Открыть `/zakroma?author=КИНО` (или любой автор с onAir-песнями), убедиться, что в строке любой песни нет иконки `Sponsr`. То же — для `/search?q=...`.

**Acceptance Scenarios**:

1. **Given** посетитель на `/zakroma?author=...`, **When** таблица альбомов отрендерена, **Then** в каждой строке песни нет `<PlatformLink link-name="sponsr" ...>` (раньше 5-я колонка с иконкой).
2. **Given** посетитель на `/search?q=...`, **When** результаты поиска отрендерены, **Then** в каждой строке песни нет `<PlatformLink link-name="sponsr" ...>`.
3. **Given** таблица без иконки Sponsr, **When** она рендерится на мобильном (≤ 600px) в виде карточек, **Then** и в карточке песни нет иконки Sponsr.
4. **Given** тестировщик смотрит JSON ответа `GET /api/public/zakroma` или `GET /api/public/songs`, **Then** в массиве песен **отсутствуют** поля `linkSponsrPlay` (а после полного аудита — также `linkBoosty`, `linkDzen*`, `linkVk*`, `linkTg*`, `linkMax*`, `linkPl*` — см. Story 2).

---

### User Story 2 — Backend-ответ `/api/public/*` содержит только нужные поля песни (Priority: P1)

Поля, которые фронт `karaoke-public` (новый публичный SPA на Vue 3) реально использует в шаблонах `/song/{id}`, `/zakroma`, `/search`, `/author-playlist` — остаются в публичных DTO (`SongPublicDto`, `ZakromaAlbumSongPublicDto`). Все остальные поля — удаляются из этих DTO: они засоряют JSON-ответ (на каждый запрос `/api/public/zakroma` для автора с 30+ песнями — десятки неиспользуемых строк × число песен), увеличивают payload и bandwidth, и создают ложное впечатление, что публичный сайт «живёт» в соцсетях.

**Why this priority**: Это вторая часть задачи пользователя («провести аудит DTO песен на проде и убрать из него всё лишнее»). Без этого payload остаётся большим, а интерфейс админки продолжает «просвечивать» в прод.

**Independent Test**: Сделать `GET /api/public/songs?songName=...` (или `GET /api/public/zakroma?author=...`), изучить JSON — каждое поле должно либо использоваться во фронте `karaoke-public/src/`, либо быть помечено как «требуется для иных потребителей» в Assumptions.

**Acceptance Scenarios**:

1. **Given** ответ `GET /api/public/song/{id}`, **Then** присутствуют ТОЛЬКО поля, которые используются в `karaoke-public/src/views/SongView.vue`, `SearchView.vue`, `ZakromaView.vue`, `AuthorPlaylistView.vue` или в `usePlayerAccess` / `useCart` / `usePlaylistMembership`. См. таблицу «Аудит полей» ниже.
2. **Given** ответ `GET /api/public/zakroma`, **Then** каждое поле в `ZakromaAlbumSongPublicDto` используется во фронте либо явно помечено как нужное (например, `id`, `songName` — нужны; `linkDzenKaraoke` — нет).
3. **Given** поля удалены из DTO, **Then** JSON-ответ сервера НЕ содержит ключей `linkSponsrPlay`, `linkBoosty`, `linkDzenKaraoke/Lyrics/Tabs/Chords`, `linkVkKaraoke/Lyrics/Tabs/Chords`, `linkTgKaraoke/Lyrics/Tabs/Chords`, `linkPlKaraoke/Lyrics/Tabs/Chords`, `linkMaxKaraoke/Lyrics/Tabs/Chords` (для `ZakromaAlbumSongPublicDto`) и аналогичных + `sponsrLinkGeneral`, `vkPictureBase64`, `haveVkGroupLink`, `idStatus`, `authorAlias` (по результатам аудита для `SongPublicDto`).
4. **Given** админ открывает `webvue3` и редактирует песню, **Then** ВСЕ поля ссылок на соцсети (linkDzen*, linkVk*, linkTg*, linkMax*, linkSponsrPlay, linkBoostyTxt и т.п.) по-прежнему доступны в `SongEdit.vue` (они нужны админке и публикационным ботам; из БД и модели `Song` ничего не удаляется).

---

### User Story 3 — Старый Thymeleaf-сайт (filter.html, zakroma.html, testpage.html): согласованное поведение (Priority: P2)

Старый сайт на Thymeleaf (`karaoke-web/src/main/resources/templates/*.html`) тоже использует часть тех же полей (в частности `linkSponsrPlay`, `linkDzen*`) — он отдаётся через `MainController.kt`, а не через `/api/public/*`. Эта поверхность не является целью аудита публичного API, но решение «как с ней поступить» нужно принять явно: либо оставить эти ссылки на старом сайте (минимальное изменение), либо тоже убрать (консистентно), либо удалить старый сайт вовсе.

**Why this priority**: Промежуточная поверхность, не публичный SPA. Если решение не принять — будет полу-состояние: новый сайт без Sponsr, старый — с Sponsr.

**Independent Test**: Зависит от выбранного решения — см. Open Questions / Assumptions.

**Acceptance Scenarios**:

1. **Given** пользователь выбирает «оставить старый Thymeleaf-сайт как есть», **Then** поведение `filter.html` / `zakroma.html` / `testpage.html` не меняется; в БД и модели `Song` поля остаются нетронутыми.
2. **Given** пользователь выбирает «убрать ссылки на соцсети также из старого Thymeleaf-сайта», **Then** все `<a th:linkValue="${sett.linkSponsrPlay}">` и подобные удалены из шаблонов; данные `sett.*` формируются отдельным DTO `SongOldSiteDto` (без полей ссылок на соцсети) в `MainController.kt`.
3. **Given** пользователь выбирает «выключить старый Thymeleaf-сайт», **Then** маршруты `/filter`, `/zakroma`, `/testpage` в `MainController.kt` возвращают 410 Gone или редиректят на новый SPA; Thymeleaf-шаблоны остаются в репо как артефакт истории.

---

## Edge Cases

- **Совместимость с share-ссылками** (Pass 47-50): `/api/public/share/*` — отдельная поверхность, не использует эти DTO. Аудит их не затрагивает.
- **Self-assign редактор** (specs/182): `SongPublicDto.assignment` остаётся в DTO — это нужно только для self-assign редакторов на `/song/{id}`. Проверить, что для НЕ-self-assign редакторов / обычных пользователей поле остаётся `null` (как сейчас), и payload не растёт.
- **`includeDetails=false`** (флаг в `SongPublicDto.fromSong`): уже оптимизирует большие текстовые поля `formattedTextSong/Tabs/Chords`, `description`, `shortDescription`, `warning` для списка. Не сломать этот контракт.
- **Тесты / admin-фичи**: в админке (`webvue3`) ссылки продолжают работать (поля читаются напрямую из `Song.kt` геттеров, не из публичного DTO).
- **Backwards compatibility**: `SongPublicDto` используется в `MainController.kt` (legacy `testpage.html`). Если НЕ убирать ссылки из Thymeleaf — оставить поле в DTO, иначе — удалить. Поведение зависит от US3.
- **Bandwidth на проде**: типичный `GET /api/public/zakroma?author=КИНО` отдаёт ~30 песен × 22 ссылки ≈ 660 строк × ~60 символов ≈ 40 KB мусора. После очистки — экономия ~95% payload для таблиц.
- **API для админки**: `webvue3` НЕ использует публичные DTO — он работает через `ApiController.kt` (другой namespace) и напрямую с моделью `Song.kt`. Поэтому удаление полей из публичных DTO НЕ ломает админку.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Удаление ссылки на Sponsr (явное требование пользователя)

- **FR-001**: В `karaoke-public/src/views/ZakromaView.vue` MUST быть удалена колонка `<PlatformLink link-name="sponsr" :link-value="sett.linkSponsrPlay" :song-id="sett.id" song-version="all" />` И в таблице-строке (desktop), И в карточке (mobile). ВСЕ три вхождения (строки 320-326, 354-360 — desktop+mobile).
- **FR-002**: В `karaoke-public/src/views/SearchView.vue` MUST быть удалена колонка `<PlatformLink link-name="sponsr" :link-value="sett.linkSponsrPlay" :song-id="sett.id" song-version="all" />` И в таблице, И в карточке. ВСЕ три вхождения (строки 141-147, 183-189).
- **FR-003**: После удаления колонки ширина таблицы MUST быть пересчитана: явно заданные `width: Npx` на `<col>` (или эквивалент) перераспределяются так, чтобы общая ширина оставалась постоянной (новое число колонок × средняя ширина).

#### Аудит `SongPublicDto` (karaoke-web)

На основе проведённого аудита (см. таблицу «Аудит полей SongPublicDto» в Assumptions):

- **FR-004**: Из `SongPublicDto` MUST быть удалены следующие поля (НИКТО во фронте `karaoke-public` их не использует):
  - `sponsrLinkGeneral` — захардкожен в DTO как `"https://sponsr.ru/smkaraoke"`, нигде не читается фронтом.
  - `haveVkGroupLink` — используется только в legacy `testpage.html` (Thymeleaf). На проде (новый SPA) — не нужно.
  - `vkPictureBase64` — всегда `""`, фронт подгружает `songPictureUrl` сам.
  - `idStatus` — НЕ используется в публичных шаблонах (только legacy `testpage.html`).
  - `linkSponsrPlay` — использовался только для иконки в таблицах Закромов и поиска (см. FR-001/FR-002).
  - `linkBoostyTxt` — НЕ используется ни в одном публичном Vue-шаблоне.
  - `linkDzenKaraoke`, `linkDzenLyrics`, `linkDzenTabs`, `linkDzenChords` — НЕ используются в публичных Vue-шаблонах.
  - `linkVkKaraoke`, `linkVkLyrics`, `linkVkTabs`, `linkVkChords` — НЕ используются (для embed видео используются `idVk*OID` + `idVk*ID`, не текстовые ссылки).
  - `linkTgKaraoke`, `linkTgLyrics`, `linkTgTabs`, `linkTgChords` — НЕ используются.
  - `linkMaxKaraoke`, `linkMaxLyrics`, `linkMaxTabs`, `linkMaxChords` — НЕ используются.
  - `linkPlKaraoke`, `linkPlLyrics`, `linkPlTabs`, `linkPlChords` — НЕ используются.

  **NB**: поля `idVkKaraoke`, `idVkKaraokeOID`, `idVkKaraokeID`, `idVkLyrics*`, `idVkMelody*`, `idVkChords*` — ОСТАЮТСЯ, они используются в `SongView.vue` для embed VK-видео (строки 226-269).

- **FR-005**: Из `SongPublicDto` НЕ удалять (используются в публичных шаблонах):
  - `id, songName, author, authorAlias, album, year, track, key, bpm, onAir, datePublish, airTimestamp` — базовые поля.
  - `alwaysFree, freelyAvailableNow, freeAccessWindowEndText` — метки доступа (specs/143).
  - `songPictureUrl` — для hero-баннера `SongView.vue`.
  - `formattedTextSong, formattedTextTabs, formattedTextChords` — тексты песен в `SongView.vue`.
  - `description, shortDescription, warning` — карточка метаданных (specs/012).
  - `contentRemoved` — заглушка «удалено по требованию правообладателя».
  - `songSubscriptionAvailable` — управляет кнопкой «оформить подписку на эту песню».
  - `assignment` — для self-assign редакторов (specs/182).

#### Аудит `ZakromaAlbumSongPublicDto` (karaoke-web)

- **FR-006**: Из `ZakromaAlbumSongPublicDto` MUST быть удалены все ссылки на соцсети (21 поле): `linkBoosty`, `linkSponsrPlay`, `linkDzenKaraoke/Lyrics/Tabs/Chords`, `linkVkKaraoke/Lyrics/Tabs/Chords`, `linkTgKaraoke/Lyrics/Tabs/Chords`, `linkPlKaraoke/Lyrics/Tabs/Chords`, `linkMaxKaraoke/Lyrics/Tabs/Chords`. Ни одно из них не используется во фронте `karaoke-public`.
- **FR-007**: Из `ZakromaAlbumSongPublicDto` НЕ удалять: `id, track, songName, onAir, datePublish, airTimestamp, songSubscriptionAvailable, alwaysFree, freelyAvailableNow, freeAccessWindowEndText` — все используются в `ZakromaView.vue`, `AuthorPlaylistView.vue` или в компонентах (`readiness`, `cart`).

#### Аудит `Zakroma.kt` (модель, karaoke-app)

- **FR-008**: `ZakromaAlbumSong` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt` содержит те же 21 поле ссылок. Они используются ТОЛЬКО для формирования `ZakromaAlbumSongPublicDto` (FR-006). После удаления полей из DTO — MUST быть удалены из `ZakromaAlbumSong` и из метода-конвертера (строка 207-225 в `Zakroma.kt`).

#### Старый Thymeleaf-сайт (US3 = B — убрать ссылки)

- **FR-009**: В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt` MUST быть введён новый data class `SongOldSiteDto` (отдельный от `SongPublicDto`). `SongOldSiteDto` содержит все поля, которые использует legacy Thymeleaf, **КРОМ** ссылок на соцсети (`linkSponsrPlay`, `linkBoosty`, `linkDzen*`, `linkVk*`, `linkTg*`, `linkMax*`, `linkPl*`). В частности, `SongOldSiteDto` сохраняет: `id, songName, author, album, year, track, key, bpm, onAir, datePublish, idStatus, haveVkGroupLink, songPictureUrl, formattedTextSong/Tabs/Chords, description/shortDescription/warning`, а также текстовые блоки и метаданные, реально нужные шаблонам. Поля `idStatus` и `haveVkGroupLink` ОСТАЮТСЯ в `SongOldSiteDto` (testpage.html:300-301 использует для логики картинки).
- **FR-010**: В `filter.html`, `zakroma.html`, `testpage.html` MUST быть удалены все `<a th:linkValue="${sett.linkSponsrPlay}">`, `<a th:linkValue="${sett.linkDzen*}" ...>`, `<a th:linkValue="${sett.linkVk*}" ...>`, `<a th:linkValue="${sett.linkTg*}" ...>`, `<a th:linkValue="${sett.linkMax*}" ...>`, `<a th:linkValue="${sett.linkPl*}" ...>`, `<a th:linkValue="${sett.linkBoosty}" ...>`. Также удаляются соответствующие `<col>`, `<th>` и `width: Npx` колонок; оставшиеся колонки получают пересчитанные ширины, чтобы общая ширина таблицы осталась постоянной.
- **FR-011**: `SongOldSiteDto` формируется в `MainController.kt` через `Song.loadFromDbById` / `Song.loadListFromDb` + явный конструктор (аналогично `SongPublicDto.fromSong`). Поля ссылок `Song.kt.linkSponsrPlay` и пр. не читаются — экономия SQL-вычислений (но `Song.kt` геттеры не трогаем).

#### Технические

- **FR-012**: Все изменения MUST быть обратно совместимы по HTTP API (если клиент где-то ещё ждёт эти поля — поведение 404 / unknown field для не указанных полей). Текущие потребители — только `karaoke-public` (Vue) и `MainController.kt` (legacy) + `webvue3` (админка — НЕ через публичный DTO).
- **FR-013**: В KDoc / комментариях к удалённым полям MUST быть ссылка на эту спеку (`specs/185-song-dto-audit-sponsr-remove`), чтобы будущие разработчики не пытались вернуть «случайно».
- **FR-014**: Поля в `Song.kt` (модель, БД) НЕ трогаем — они нужны админке и публикационным ботам. Удаляем только публичные проекции.

### Key Entities *(include if feature involves data)*

- **`SongPublicDto`** (karaoke-web/src/main/kotlin/.../dto/SongPublicDto.kt): сериализуемое представление песни для `/api/public/song/{id}` и `/api/public/songs`. Содержит ~75 полей, из которых по аудиту остаётся ~25.
- **`ZakromaAlbumSongPublicDto`** (karaoke-web/src/main/kotlin/.../dto/ZakromaPublicDto.kt): сериализуемое представление песни в альбоме для `/api/public/zakroma` и `/api/public/zakroma/stream`. Содержит 31 поле, из которых остаётся 10.
- **`ZakromaAlbumSong`** (karaoke-app/src/main/kotlin/.../model/Zakroma.kt): внутреннее представление песни в `Zakroma`-графе. Используется только для построения DTO. Поля ссылок на соцсети удаляются вместе с DTO.
- **`Song`** (karaoke-app/src/main/kotlin/.../model/Song.kt): модель в Karaoke-App. **НЕ трогаем** в этой спеке — поля нужны админке.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: В JSON-ответе `GET /api/public/zakroma?author=КИНО` (или любой автор с 20+ песнями) размер payload уменьшается минимум на 80% (текущий — ~40 KB мусорных строк; ожидаемый — ≤ 8 KB).
- **SC-002**: В JSON-ответе `GET /api/public/song/{id}` отсутствуют ключи `sponsrLinkGeneral`, `linkSponsrPlay`, `linkBoostyTxt`, `linkDzen*`, `linkVk*`, `linkTg*`, `linkMax*`, `linkPl*`, `vkPictureBase64`, `haveVkGroupLink`, `idStatus`. Проверяется через `jq 'keys' /api/public/song/{id}` — список ключей сокращается с ~75 до ~25.
- **SC-003**: В DOM `/zakroma` и `/search` (в режиме таблицы и в режиме карточек) отсутствует `<a ... data-link-name="sponsr">` (или эквивалентный селектор на `PlatformLink[link-name="sponsr"]`).
- **SC-004**: В админке `webvue3` (`SongEdit.vue`) все 21+ поля ссылок на соцсети по-прежнему доступны для редактирования. Проверяется через `npm run dev` + открытие любой песни на редактирование.
- **SC-005**: Никакая бизнес-логика публикаций в соцсети (TelegramAutoPublishService, VkAutoPublishService, публикационные боты) не сломана. Эти сервисы читают поля НАПРЯМУЮ из `Song.kt`, не из публичных DTO.
- **SC-006**: Существующие функциональные сценарии на публичной стороне НЕ сломаны:
  - `/song/{id}` — плеер, embed VK-видео, тексты песни, кнопка «взять в работу» (для self-assign редакторов).
  - `/zakroma` — таблица альбомов/песен с иконками `Cart`, `Player`, `Favorite`, `Playlist`.
  - `/search` — поиск по названию/автору/тексту/альбому.
  - `/author-playlist` — плейлист автора.
- **SC-007**: Существующие функциональные сценарии на legacy-стороне (Thymeleaf `filter.html`, `zakroma.html`, `testpage.html`) **продолжают работать**, НО без иконок/ссылок на соцсети. Картинка логотипа/песни в `testpage.html` отображается по прежней логике (`idStatus >= 3 && !haveVkGroupLink`).

---

## Open Questions / Resolved

### Q1 — Старый Thymeleaf-сайт — РЕШЕНО (B: убрать ссылки также из Thymeleaf)

**Решение пользователя** (2026-08-14): вариант B. Убрать ссылки на соцсети также из Thymeleaf-шаблонов.

**Что это значит для FR**:

1. Все поля ссылок на соцсети удаляются из публичных DTO (`SongPublicDto`, `ZakromaAlbumSongPublicDto`) **полностью** — независимо от того, используются они в legacy или нет.
2. `MainController.kt` MUST формировать `sett` через отдельный `SongOldSiteDto` (новый data class), который содержит всё, что нужно legacy-шаблонам (включая `idStatus`, `haveVkGroupLink`, тексты), **но НЕ содержит полей ссылок на соцсети**.
3. Thymeleaf-шаблоны `filter.html`, `zakroma.html`, `testpage.html` MUST быть отредактированы: удалены все блоки `<a th:linkValue="${sett.linkSponsrPlay}">`, `<a th:linkValue="${sett.linkDzen*}" ...>`, `<a th:linkValue="${sett.linkVk*}" ...>`, `<a th:linkValue="${sett.linkTg*}" ...>`, `<a th:linkValue="${sett.linkMax*}" ...>`, `<a th:linkValue="${sett.linkPl*}" ...>`. Соответствующие `<col>`/`<th>` колонки тоже удаляются; `width: Npx` перераспределяется.
4. `idStatus` и `haveVkGroupLink` — НЕ являются ссылками на соцсети (это служебные поля: статус готовности и флаг наличия ссылки на группу VK). Они ОСТАЮТСЯ в `SongOldSiteDto` (нужны testpage.html для логики отображения картинки). В `SongPublicDto` — удаляются, т.к. публичный SPA их не использует.

### Assumptions (применяются, если не указано иное)

- **A-1**: Поля в БД (`tbl_settings`, `tbl_settings_sync`) НЕ удаляются и НЕ переименовываются. Изменение чисто-косметическое для публичного API; внутренние контракты остаются.
- **A-2**: В модели `Song.kt` поля НЕ удаляются. Админка (`webvue3`) продолжает редактировать ссылки на соцсети для будущих публикаций.
- **A-3**: `ZakromaAlbumSong` (внутренний класс, не DTO) — удаляем поля ссылок, т.к. они используются ТОЛЬКО для построения `ZakromaAlbumSongPublicDto`. Если в будущем появится ещё один потребитель — поля можно вернуть.
- **A-4**: `MainController.kt` остаётся жить (Thymeleaf-legacy). Какое именно поведение для него — определяется Q1 (см. выше).
- **A-5**: Действие «аддитивно обратное»: не сломает существующих потребителей публичного API. Если найдётся потребитель, который парсит JSON по имени поля — это будет считаться багом потребителя (а не фичей API).
- **A-6**: Self-assign редакторы (specs/182) — `SongPublicDto.assignment` остаётся. Для НЕ-self-assign пользователей значение `null` (как сейчас). Никаких дополнительных изменений по FR-005 не нужно.
- **A-7**: Метрика bandwidth / payload — измеряется по ответу `/api/public/zakroma` для автора «КИНО» (с ним в коллекции ~30 onAir-песен по 22 ссылки = 660 строк мусора). После очистки — SC-001.
- **A-8**: Старые шаблоны Thymeleaf НЕ имеют CI-тестов. Если что-то сломается в них — обнаружится только ручной проверкой. Это нормально (legacy).
- **A-9**: `linkBoostyTxt` (в SongPublicDto) vs `linkBoosty` (в ZakromaAlbumSongPublicDto) — две разные формы одного и того же поля в БД. Оба удаляются согласно FR-004 / FR-006.

---

## Notes

### Аудит полей `SongPublicDto` (предварительный, требует подтверждения Q1)

| Поле | Тип | Используется в `karaoke-public/src/`? | Решение |
|---|---|---|---|
| `id` | Long | да (везде) | оставить |
| `songName` | String | да (везде) | оставить |
| `author` | String | да (везде) | оставить |
| `authorAlias` | String | да (SearchView.vue:111,165) | оставить |
| `album` | String | да (SongView.vue:115) | оставить |
| `year` | Long | да (SongView.vue:109) | оставить |
| `track` | Long | да (везде) | оставить |
| `key` | String | да (SongView.vue:126) | оставить |
| `bpm` | Long | да (SongView.vue:131) | оставить |
| `onAir` | Boolean | да (везде) | оставить |
| `datePublish` | String | да (везде) | оставить |
| `airTimestamp` | Long? | да (SongView.vue:440) | оставить |
| `alwaysFree` | Boolean | да (везде, specs/143) | оставить |
| `freelyAvailableNow` | Boolean | да (везде, specs/143) | оставить |
| `freeAccessWindowEndText` | String? | да (везде, specs/143) | оставить |
| `sponsrLinkGeneral` | String | нет (захардкожен в DTO) | **удалить** |
| `haveVkGroupLink` | Boolean | нет (только legacy testpage.html) | **удалить** (или оставить, если Q1=A) |
| `idStatus` | Long | нет (только legacy testpage.html) | **удалить** (или оставить, если Q1=A) |
| `vkPictureBase64` | String | нет (всегда "") | **удалить** |
| `songPictureUrl` | String | да (SongView.vue:63) | оставить |
| `formattedTextSong` | String | да (SongView.vue:328) | оставить |
| `formattedTextTabs` | String | да (SongView.vue:332) | оставить |
| `formattedTextChords` | String | да (SongView.vue:336) | оставить |
| `description` | String | да (SongView.vue:95) | оставить |
| `shortDescription` | String | да (SongView.vue:89) | оставить |
| `warning` | String | да (SongView.vue:84) | оставить |
| `linkSponsrPlay` | String | да (Закрома/Поиск таблицы) | **удалить** (FR-001/002) |
| `linkBoostyTxt` | String | нет | **удалить** |
| `linkDzenKaraoke/Lyrics/Tabs/Chords` | String ×4 | нет | **удалить** |
| `linkVkKaraoke/Lyrics/Tabs/Chords` | String ×4 | нет (только `idVk*` для embed) | **удалить** |
| `linkTgKaraoke/Lyrics/Tabs/Chords` | String ×4 | нет | **удалить** |
| `linkMaxKaraoke/Lyrics/Tabs/Chords` | String ×4 | нет | **удалить** |
| `linkPlKaraoke/Lyrics/Tabs/Chords` | String ×4 | нет | **удалить** |
| `idVkKaraoke/OID/ID` | String ×3 | да (SongView.vue:226-235) | оставить |
| `idVkLyrics/OID/ID` | String ×3 | да (SongView.vue:237-247) | оставить |
| `idVkMelody/OID/ID` | String ×3 | да (SongView.vue:248-258) | оставить |
| `idVkChords/OID/ID` | String ×3 | да (SongView.vue:259-269) | оставить |
| `contentRemoved` | Boolean | да (SongView.vue:43,509) | оставить |
| `songSubscriptionAvailable` | Boolean | да (SongView.vue:470) | оставить |
| `assignment` | SongAssignmentBriefDto? | да (SongView.vue:487+) | оставить |

**Итого**: из 75 полей — остаётся ~25, удаляется ~50 (включая 21 ссылку на соцсети, `idStatus`, `haveVkGroupLink`, `vkPictureBase64`, `sponsrLinkGeneral`).

### Аудит полей `ZakromaAlbumSongPublicDto` (полный аудит)

| Поле | Тип | Используется в `karaoke-public/src/`? | Решение |
|---|---|---|---|
| `id` | Long | да | оставить |
| `track` | Long | да | оставить |
| `songName` | String | да | оставить |
| `onAir` | Boolean | да | оставить |
| `datePublish` | String | да | оставить |
| `airTimestamp` | Long? | да | оставить |
| `songSubscriptionAvailable` | Boolean | да | оставить |
| `alwaysFree` | Boolean | да | оставить |
| `freelyAvailableNow` | Boolean | да | оставить |
| `freeAccessWindowEndText` | String? | да | оставить |
| `linkBoosty` | String | нет | **удалить** |
| `linkSponsrPlay` | String | да (Закрома таблица) | **удалить** (FR-001) |
| `linkDzenKaraoke/Lyrics/Tabs/Chords` | String ×4 | нет | **удалить** |
| `linkVkKaraoke/Lyrics/Tabs/Chords` | String ×4 | нет | **удалить** |
| `linkTgKaraoke/Lyrics/Tabs/Chords` | String ×4 | нет | **удалить** |
| `linkPlKaraoke/Lyrics/Tabs/Chords` | String ×4 | нет | **удалить** |
| `linkMaxKaraoke/Lyrics/Tabs/Chords` | String ×4 | нет | **удалить** |

**Итого**: из 31 поля — остаётся 10, удаляется 21 (только ссылки на соцсети).

### Связь с существующей документацией

- `docs/features/dual-db-sync.md` — не затрагивается.
- `docs/features/song-free-access.md` (specs/143) — использует поля `alwaysFree/freelyAvailableNow/freeAccessWindowEndText` — все ОСТАЮТСЯ.
- `docs/features/editor-tasks.md` (specs/182) — использует поле `assignment` — ОСТАВЛЯЕТСЯ.
- `docs/features/entity-description-fields.md` (specs/012) — использует `description/shortDescription/warning` — ОСТАВЛЯЮТСЯ.
- `AGENTS.md` — обновляем секцию «Рендер MP4 из онлайн-плеера» или «Стратегия роста» — нет. Запись в `docs/architecture-notes.md` (Pass 51+).