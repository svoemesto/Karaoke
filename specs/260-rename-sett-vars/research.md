# Research: Переименование `sett`/`settings` → `song` (260-rename-sett-vars)

**Input**: [`spec.md`](./spec.md) (5 разрешённых Clarifications Q1–Q5)
**Прецедент**: [`specs/102-rename-song-settings-vars/`](../../../specs/102-rename-song-settings-vars/) — спекa 102 покрыла полную форму `settings`/`settingsId`/`settings_*` в Kotlin/DTO/HTTP/SSE/Thymeleaf, **вышла** без покрытия сокращённой формы `sett` (см. `specs/102-rename-song-settings-vars/tasks.md` Phase 3, T004–T011 — там переименована форма `settings` → `song`, но не `sett`).
**Дата**: 2026-08-28

## 1. Полный baseline очагов legacy (зафиксирован для SC-001/SC-002)

| Категория | Файлы (исходники, **исключая `build/`**) | Совпадений | Тип |
|---|---|---|---|
| **Kotlin: класс-функция/лямбда `sett`** | `karaoke-app/.../mlt/mko/*.kt` (13 файлов: `MkoChord*`, `MkoChords`, `MkoElement`, `MkoFill`, `MkoLines`, `MkoLineTrack`, `MkoMelodyNote`, `MkoMelodyTabs`, `MkoSepar`, `MkoString`), `karaoke-app/.../controllers/ApiController.kt` (60+ в `song?.let { sett -> ... }`), `karaoke-app/.../services/TelegramUpdatesConsumer.kt`, `karaoke-app/.../model/{CrossSong,Pictures,StatBySong}.kt` | ≥200 | `Song`-типизированный |
| **Kotlin: `val settings = Song.loadFromDbById(...)` (полная форма, забытая спекой 102)** | `karaoke-app/.../controllers/ApiController.kt:6851` (`publishToVkNow`), `:6900` (`publishPremiumTelegram`), `:6990` (`publishPremiumVk`) — **3 места, не 2 как было в спеке 102 baseline**; возможны ещё в новых методах, добавленных после спеки 102 | 3 | `Song`-типизированный |
| **Kotlin `karaoke-web`** | `karaoke-web/.../controllers/{MainController,PublicApiController}.kt` | ≥30 | `Song`-типизированный |
| **Thymeleaf: `model.addAttribute("sett", ...)` + `${sett.*}` + `th:each="...:${sett}"`** | `MainController.kt` (`setAttribute("sett", ...)` — точки надо grep-проверить перед PR; SPEC FR-003) + шаблоны `area_left_column.html:115`, `songs.html:2286`, `area_center_column.html` (закомментированные блоки), `filter.html:376-410`, `zakroma.html:436-500` | ≥60 | `Song`-типизированный + строковое имя атрибута |
| **Vue/JS: `sett` в `karaoke-public`** | `SearchView.vue` (60+), `ZakromaView.vue` (60+, включая `v-for="sett in item.alb.albumSettings"`), `AuthorPlaylistView.vue:280` (`setts` мн.ч.), `useZakromaStreamProgress.js:225` (комментарий) | ≥200 | `Song`-типизированный |
| **SQL inline: алиас `tbl_songs sett`** | `karaoke-app/.../model/StatBySong.kt:485, 487, 611, 621` (2 SQL-запроса) | 4+ | `Song`-типизированный |
| **KDoc/JSDoc: забытая `tbl_settings`** | `karaoke-web/.../dto/ZakromaPublicDto.kt:9, 19`, `karaoke-web/.../services/ShareLinkSweeper.kt:130` | 3 | комментарии |
| **Полный baseline grep-ом**: `grep -rn '\bsett\b' --include='*.kt' --include='*.html' --include='*.js' --include='*.vue' --include='*.ts' --include='*.sql' karaoke-app karaoke-web karaoke-public` (без `build/`) | ≥**546** совпадений в **30+** файлах |

**Расхождение со спекой 102 baseline**: спекa 102 имела baseline 54 `settings: Song` сигнатуры + 227 `val/var settings` в Kotlin. Текущий baseline `sett` ≈ вдвое меньше по Kotlin (т.к. сокращение используется в более локальных scope), но **больше** по HTML/Vue (т.к. `sett` активно использовалось в шаблонах как UI-имя).

**Неточность, найденная при написании research**: SC-002 спеки говорит «минимум 5 таких мест в `ApiController.kt:6851, 6900`». Реально — **минимум 6** (6851, 6900, 6990). Это **необходимо зафиксировать** в `tasks.md` Phase 2 (baseline) и в SC-002 (если не поправить — план пройдёт, но ручной grep на этапе T002 даст правильную цифру).

## 2. Решения (резолюции Clarifications уже в спеке)

### Decision 1 — Полный остаточный охват: `sett` + оставшиеся `settings` + `tbl_settings` в KDoc (Clarifications Q1)

**Решение**: переименовать все три категории вместе (по аналогии с решением спеки 102 Q1, где был выбран полный охват включая DTO+frontend).

**Rationale**:
- Три категории — это один тип артефакта (legacy-наследие переименования класса Settings→Song 2024–2025), просто разные формы.
- Разделение на три отдельные задачи создаст три отдельных PR с одинаковой мотивацией — bus-factor и review-overhead.
- Категория `tbl_settings` в KDoc/JSDoc — единственный безопасный «бесплатный» класс (нет runtime-impact, нет контрактных изменений).

**Alternatives, рассмотренные и отклонённые**:
- **A1**: Только `sett` в Kotlin, без `settings` и KDoc — оставляет непоследовательный код (`sett.*` переименовано, а `publishToVkNow.settings.*` остаётся); пользователь явно попросил «переменные sett должны быть переименованы в song» — формулировка распространяется и на оставшиеся `settings`.
- **A2**: Только Kotlin, без HTML/Vue — сломает рендеринг legacy-страниц админки (Thymeleaf `model.addAttribute("sett", ...)` после переименования в `"song"` отдаст `null` всем шаблонам `${sett.*}`).
- **A3**: Только Kotlin+HTML, без Vue — сломает `karaoke-public` Search/Zakroma/AuthorPlaylist (итераторы песен `v-for="sett in searchResults"`).

**Выбран**: **всё вместе** (Clarifications Q1, A: «все три категории»).

### Decision 2 — `albumSettings` (поле DTO) НЕ входит в эту задачу (Clarifications Q2)

**Решение**: `val albumSettings: List<ZakromaAlbumSongPublicDto>` в `ZakromaPublicDto` + JSON-ключ — отдельная задача с webvue3-sync PR.

**Rationale**:
- `albumSettings` — это **поле DTO, сериализуемое в JSON** и читаемое фронтендом (`v-for="sett in alb.albumSettings"`, `setts || []`). Это wire-контракт, а не просто идентификатор.
- Переименование поля DTO синхронно задевает: backend (Kotlin DTO + `ZakromaPublicDto.albumSettings = ...`), webvue3 (если он где-то читает), karaoke-public (`SearchView.vue`, `ZakromaView.vue`, `AuthorPlaylistView.vue`, `useZakromaStreamProgress.js`).
- Спека текущей задачи ограничена «остаточным `sett`» (CLI-уровень: переменные/идентификаторы). Контрактное поле DTO — отдельный фронт работ.
- Не путать с `sett` (Vue-итератор), который читает из этого поля — `sett` мы переименовываем (`sett in alb.albumSettings` → `song in alb.albumSettings`), а само `albumSettings` оставляем.

**Alternatives, рассмотренные и отклонённые**:
- **A1**: Включить `albumSettings` → `albumSongs`. Создаёт рассинхрон webvue3+backend, требует `ZakromaAlbumMetaPublicDto.albumSettings` → `albumSongs` + JSON-ключа + всех Vue-итераторов + все `useZakromaStreamProgress.js:222-238`. Это отдельный PR с webvue3-sync.
- **A2**: Переименовать поле, оставить строковое имя ключа в JSON. Невозможно — Jackson Kotlin использует имя поля по дефолту; нужен `@JsonProperty("albumSongs")`, что ещё хуже рассинхронизирует.

### Decision 3 — Атомарный синхронный деплой `model.addAttribute("sett")` + Thymeleaf-шаблоны (Clarifications Q3)

**Решение**: `model.addAttribute("sett", sett)` → `model.addAttribute("song", song)`, плюс все `${sett.*}` и `th:each="song:${sett}"` в шаблонах — **в одном коммите/PR с backend** (аналог спеки 102 FR-016).

**Rationale**:
- Шаблон атрибута живёт в той же JVM-памяти, что и `MainController`; deployment — один и тот же артефакт (`karaoke-app.war` / bootJar).
- Промежуточное состояние (новый атрибут, старый шаблон) ломает рендеринг — все песни с `id`, `songName` и т.д. отдают `null` в шаблон (Spring Model не находит ключ `"sett"` если backend отдаёт `"song"`).
- Поиск по шаблонам: `grep -rn 'sett' karaoke-app/src/main/resources/templates` — нужно зафиксировать baseline.

**Alternatives, рассмотренные и отклонённые**:
- **A1**: Сначала шаблоны, потом backend — обратное направление, та же проблема.
- **A2**: Feature flag на стороне backend (`model.addAttribute(song OR sett)`) — over-engineering для внутреннего рефакторинга.
- **A3**: Два коммита в одном PR (последовательно, не параллельно) — допустимо для удобства review, **но** не должно создавать промежуточной релизной сборки.

### Decision 4 — Vue `sett` в `karaoke-public` включаем (Clarifications Q4)

**Решение**: переименовать Vue-итератор `sett` (и `setts` мн.ч.) в `song`/`songs` во всех `.vue`/`.js` модуля `karaoke-public`, кроме `src/player/KaraokePlayer.js`.

**Rationale**:
- В спеке 102 FR-014 модуль `karaoke-public` был выведен из охвата — **но только для настроек плеера** (`LS_SETTINGS_KEY` и аналогичных), которые НЕ являются сущностью `Song`. Vue-переменная `sett` в `SearchView`/`ZakromaView`/`AuthorPlaylistView` — это итератор по песням, не плеер-настройки.
- Не разделять на два PR (один для karaoke-public, другой для остального) — это один поток работы: «все остаточные `sett` представляющие песни».

**Точное покрытие**:
- `SearchView.vue`: `v-for="sett in searchResults"` (строки 74, 124), плюс методы `showCoin(sett)`, `dateLabel(sett)`, `onSubscribeClick(sett)`, `isSongActiveForUser(sett)`, `showCartIcon(sett)`, `isPurchased(sett)`, `showDate(sett)`, `isSongContentReady(sett)`.
- `ZakromaView.vue`: то же + `v-for="sett in item.alb.albumSettings"`.
- `AuthorPlaylistView.vue:280`: `const setts = [...(alb.albumSettings || [])].sort(...)` — итератор списка, переименовать в `songs` (мн.ч.).
- `useZakromaStreamProgress.js:225`: JSDoc-комментарий, в котором упоминается `v-for="sett in alb.albumSettings"` — обновить имя до коммита.

**НЕ покрывается**: `karaoke-public/src/player/KaraokePlayer.js` (`LS_SETTINGS_KEY`, `PlayerSettings`) — это пользовательские настройки плеера, прецедент спеки 102 FR-014.

### Decision 5 — `webvue3` вне scope (Clarifications Q5)

**Решение**: `webvue3` admin SPA целиком вне scope этой задачи. Контрактные файлы webvue3 (SongsTable.vue, Songs/store.js, HealthReport/*) уже переименованы спекой 102. Единственный остаток `sett` в `webvue3` — `SubsEdit.vue:183` (label UI-кнопки редактора субтитров «sett» / «eol» / «endofline») — **НЕ переменная типа `Song`**, это label marker-type.

**Rationale**:
- Constitution V: «Двух-фронтенд: админка и публичный сайт — разные приложения. Смешивание ответственностей ЗАПРЕЩЕНО». `webvue3` — admin SPA, `karaoke-public` — public SPA.
- Переименование label «sett» в «song» — это UX-решение (label кнопки в редакторе субтитров), не legacy-переименование от Settings→Song. Label «setting marker» — это «вид маркера», не «settings of song».
- Альтернатива (B: переименовать) требовала бы UX-исследования (какой label предпочтительнее для пользователя редактора) — не задача механического рефакторинга.

**Alternatives, рассмотренные и отклонённые**:
- **A1**: Переименовать label `sett` → `song` в `SubsEdit.vue:183`. Сломает UX (label «setting marker» → «song marker» — не имеет смысла; кроме того, в `@click="onOffShowMarkerType('setting')"` — строковый литерал остаётся, что создаёт рассинхрон между label и JS-аргументом).
- **A2**: Отложить до планирования. Решено на этом этапе — спека получает явный ответ Q5, plan/tasks ссылается на него.

## 3. Технические находки (для tasks.md Phase 2/3)

### 3.1 Конфликт имён в `mlt/mko/*.kt`

**Проблема**: файлы в `karaoke-app/.../mlt/mko/*.kt` имеют **оба** имени: `class SomeClass(val song: Song)` (параметр конструктора) и `val sett = song` (локальная переменная). Прямое переименование `sett → song` создаст shadowing/conflikt.

**Найденные файлы** (полный список — в `data-model.md` секция «Файлы с потенциальным конфликтом имён»):
- `MkoChordPictureElement.kt`, `MkoChordPictureFader.kt`, `MkoChordPictureImage.kt`, `MkoChordPictureLines.kt`, `MkoChords.kt`, `MkoElement.kt`, `MkoFill.kt`, `MkoLines.kt`, `MkoLineTrack.kt`, `MkoMelodyNote.kt`, `MkoMelodyTabs.kt`, `MkoSepar.kt`, `MkoString.kt` — все имеют `val song` или параметр `song: Song`.

**Решение**: использовать явное осмысленное имя для локальной переменной вместо `song`. Варианты: `targetSong`, `renderSong`, `songForRender`. Конкретный выбор — на этапе implementation per file (по смыслу использования).

### 3.2 SQL inline-строки и Kotlin string-template

**Проблема**: `StatBySong.kt:485, 611` — multi-line Kotlin string с SQL `tbl_songs sett on e.song_id = sett.id`. Если строка собирается через Kotlin string template (`$limit`, `$offset`), обращение к **имени алиаса** в SQL не зависит от Kotlin. Но заменять нужно строку как литерал.

**Найденные SQL-запросы**:
- Запрос 1 (line 458–491): `from tbl_events e left join tbl_songs sett on e.song_id = sett.id where ... group by e.song_id, sett.song_author, sett.song_album, sett.song_name`.
- Запрос 2 (line 591–621): `from tbl_songs sett on sett.id = e.song_id where ... group by s.song_id, sett.song_author, sett.song_album, sett.song_name`.

**Решение**: простой string-replace `sett → song` внутри literal-string (между `"""` и `"""`). PostgreSQL схавает любой алиас; имя таблицы `tbl_songs` остаётся.

### 3.3 Линт baselines

**Проблема**: `config/ktlint/baseline-karaoke-app.xml`, `baseline-karaoke-web.xml` — фиксируют известные нарушения ktlint. Параметр `val foo: Song` vs `val song: Song` — нарушением не является (это валидный Kotlin, не зависит от имени). Так что переименование **не должно плодить новых нарушений** — `tools/check-eslint-baseline.sh`/`./gradlew ktlintCheck` сработают как baseline-check без новых строк.

**Найденные базлайны**:
- `config/ktlint/baseline-karaoke-app.xml`
- `config/ktlint/baseline-karaoke-web.xml`
- `config/ktlint/baseline-karaoke-db.xml` (не затрагивается)
- `webvue3/.eslint-baseline.json` (webvue3 не затрагивается после Q5)
- `karaoke-public/.eslint-baseline.json` (затрагивается только переименование Vue-итератора, которое не меняет lint-категории)

**Решение**: Phase 6 Polish должен прогнать `ktlintCheck` и `check-eslint-baseline.sh` ДО мержа и убедиться, что `baseline-*.xml` / `eslint-baseline.json` не изменились (или изменились только тривиально — например, имя файла в comment-line внутри baseline).

### 3.4 Inline JSDoc-комментарии `useZakromaStreamProgress.js:225`

**Найдено**: `// итерирует \`v-for="sett in alb.albumSettings"\`.`. Это **комментарий-документация**, в которой имена итераторов — это код, который читатель увидит, открыв связанный файл. После переименования `sett` → `song` этот комментарий устаревает.

**Решение**: на этапе implementation обновить имя до `song`.

### 3.5 Атрибуты шаблона vs CSS (HTML)

**Потенциально ловушка**: некоторые атрибуты Thymeleaf/CSS могут случайно содержать `sett` (например, `class="sett-row"` или `id="sett-..."`). На момент research не подтверждено (нужен grep `sett` без границ), но нужно проверить в Phase 2 (T002).

**Решение**: при baseline-проходе — фиксировать не только `\bsett\b` целое слово, но и совпадения в `class`/`id`/`data-*` атрибутах, во избежание сюрпризов в Phase 3.

## 4. Прецедент: спека 102 — структура plan/research/tasks

Спека 102 (завершена, ветка `102-rename-song-settings-vars` влита) использовала следующую раскладку Phase 3/4 в `tasks.md`:

- Phase 3 (US1, internal): 23 задачи T004–T024 по 17 Kotlin-файлам + чекпоинт компиляции.
- Phase 4 (US2, contract sync): 12 задач T025–T036 (DTO + webvue3 + SSE + Thymeleaf `songs_update`).
- Phase 5 (US3, exclusion verification): 4 задачи T037–T040.
- Phase 6 (polish): документация + `.git-blame-ignore-revs` follow-up (T041–T046).

Текущая задача (260) использует **ту же модель**, но с поправкой на:
- Mеньше DTO-полей (только `KaraokeProcessDTO.settingsId` уже переименован спекой 102, ничего нового).
- Добавлен SQL-rename (StatBySong.kt, новая подкатегория).
- Добавлен Vue `sett` в `karaoke-public` (новая категория для спеки 260).
- `webvue3` исключён целиком (решение Q5).

## 5. Что НЕ покрывается (явные deferrals)

| Категория | Почему отдельно | Когда делать |
|---|---|---|
| `albumSettings` (DTO поле, JSON ключ) | Wire-контракт с webvue3 + karaoke-public | Отдельная задача после 260 |
| `tbl_settings` → `tbl_songs` (физическая миграция БД) | Out of scope этой задачи (прецедент спеки 102 FR-005) | Никогда (прецедент спеки 011 — оставлено как `tbl_songs` без полного переименования) |
| `KaraokePlatform.settingsFieldPublicationId` / `settingsFieldVersionNumber` | Конфигурация per-платформе, не Song | Не трогать (спека 102 FR-004) |
| `SyncTarget.key = "settings"` | Двух-БД sync-конфигурация | Не трогать (спека 102) |
| `LS_SETTINGS_KEY` в `karaoke-public/src/player/KaraokePlayer.js` и `webvue3/src/player/KaraokePlayer.js` | Настройки плеера | Не трогать (спека 102 FR-014, Q4/Q5) |
| `@PostMapping("/playlists/{id}/settings")` | Endpoint настройки плейлиста | Не трогать (найден при baseline, не Song) |
| `tbl_public_settings` | Другая таблица | Не трогать (не Song) |
| `webvue3/src/components/Songs/edit/SubsEdit.vue:183` label `sett` | Label UI-кнопки, не Song | Не трогать (Q5) |

## 6. Constitution Check

| Принцип | Соответствие | Замечание |
|---|---|---|
| **I. Self-contained** | N/A | Рефакторинг, не runtime ML |
| **II. Сырой JDBC** | N/A | SQL-rename — алиас, не statement |
| **III. SyncRegistry** | N/A | Нет новых sync-targets |
| **IV. Async-queue** | N/A | Не затрагивается |
| **V. Two-frontend** | ✅ Соблюдён | `webvue3` и `karaoke-public` явно разделены (Q5, FR-014); `SubsEdit.vue` label — в `webvue3`, не затрагивается |
| **VI. Code Standards** | ✅ Соблюдён | KDoc/JSDoc обновляются точечно (FR-008); базлайны ktlint/eslint сохраняются (FR-010); per-feature документ (`docs/features/rename-sett-vars.md`) создаётся в Phase 6 Polish |
| **VII. Cross-Machine** | ✅ Соблюдён | После мержа коммит(ы) добавляются в `.git-blame-ignore-revs` (постановка в Phase 6 polish — следует прецеденту спеки 102 T046) |
| **VIII. Secrets** | N/A | Не трогает секрет-файлы |

**Вердикт**: нет нарушений Constitution; complexity tracking не требуется.

## 7. Сводка для Phase 1 (data-model.md, contracts/, quickstart.md)

- **Data-model**: ноль изменений (задача чисто переименовательная; физическая схема БД не меняется).
- **Contracts**: ноль изменений (DTO-поля, HTTP, SSE — спека 102 уже покрыла; `albumSettings` явно вне scope).
- **Quickstart**: проверочные сценарии — grep-проходы для SC-001/SC-002, ручная проверка UI страниц `area_left_column.html`/`songs.html` (рендеринг legacy), один сценарий smoke-test публичного сайта (Search/Zakroma в `karaoke-public` после деплоя). Никаких новых endpoint'ов.

## 8. Open issues / Risks

| Issue | Mitigation |
|---|---|
| SC-002 baseline-число («≥5 мест») устарело — реально 6+ | Обновить в `tasks.md` T002 baseline; SC-002 можно оставить как «≥6» |
| Grep в `build/resources` плодит дубликаты | Всегда `--exclude-dir=build` (или `grep` только в `src/`) |
| Лишние `sett` в шаблонных class/id/data-* атрибутах | Расширить grep в Phase 2 baseline |
| Несколько коммитов в одном PR — нормально, но если хочется один commit — все правится одной кассой sed + ручная верификация | Указать в tasks.md, что один squash-commit приемлем |
| Возможно, что после переименования `sett` всплывут ещё `sett` в JSDoc/комментариях вне списка baseline | Финальный grep в Phase 5 T040 ловит такие случаи |
