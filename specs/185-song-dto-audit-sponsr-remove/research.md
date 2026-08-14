# Research: Аудит публичного DTO песни и удаление ссылки на Sponsr

**Дата**: 2026-08-14
**Spec**: [spec.md](./spec.md)
**Branch**: `185-song-dto-audit-sponsr-remove`

## Резюме

Это НЕ новая фича — это аддитивно-обратный рефактор. Не вводим новых абстракций, новых БД-таблиц, новых API-контрактов для пользователя. Удаляем из публичных DTO поля, которые не используются фронтом `karaoke-public` (Vue 3 SPA). Поля в БД и в модели `Song.kt` остаются нетронутыми — они нужны админке (`webvue3`) и публикационным ботам.

Все архитектурные решения уже приняты пользователем на стадии `/speckit.specify` (см. spec.md, секция «Open Questions / Resolved»):
- Q1 = B: убрать ссылки также из Thymeleaf-шаблонов.
- Никаких БД-миграций.
- Никаких изменений модели `Song.kt`.
- Никаких изменений в админке `webvue3`.

## Решённые вопросы (NEEDS CLARIFICATION = 0)

В спеке был ОДИН открытый вопрос (Q1 — статус Thymeleaf-legacy). Пользователь выбрал вариант B в ходе `/speckit.specify`. Все остальные архитектурные решения вытекают из этого.

## Существующие архитектурные паттерны (применяем, не изобретаем)

### 1. Удаление полей из DTO — паттерн «аддитивно-обратный»

В проекте уже есть прецеденты удаления полей из публичных DTO:

- `karaoke-web/src/main/kotlin/.../dto/SongPublicDto.kt:11` — содержит комментарий о том, что `includeDetails=false` пропускает тяжёлые поля для списка (`formattedTextSong/Tabs/Chords`, `description/shortDescription/warning`). Это аналогичный паттерн: данные грузятся ТОЛЬКО там, где они нужны.
- `karaoke-web/src/main/kotlin/.../controllers/PublicApiController.kt:552` — `SongPublicDto.fromSong(it, includeDetails = false)` для списка, `fromSong(it)` для отдельной песни.
- `karaoke-web/src/main/kotlin/.../controllers/PublicApiController.kt:606` — `.copy(assignment = assignmentDto)` для опционального поля self-assign (только для редакторов).

**Применение**: удаляем поля из DTO-классов и из `fromSong` / `fromZakroma` конвертеров. Сам факт удаления поля из data class → Jackson автоматически перестаёт его сериализовать.

### 2. MainController + Thymeleaf: `sett` = объект `Song`

В `MainController.kt:412,449,506` для шаблонов Thymeleaf передаётся объект `Song` напрямую (а не отдельный DTO):

```kotlin
val sett = Song.loadFromDbById(id, database = WORKING_DATABASE, ...)
model.addAttribute("sett", sett)
```

Шаблон `testpage.html:300` использует `${sett.idStatus}` и `${sett.haveVkGroupLink}`. Шаблон `zakroma.html` и `filter.html` используют `${sett.linkSponsrPlay}`, `${sett.linkDzenKaraoke}` и т.п.

**Применение**: НЕ создаём отдельный `SongOldSiteDto` (это было избыточно, см. analysis ниже). Просто удаляем из Thymeleaf-шаблонов все ссылки на соцсети, и они перестают рендериться. Thymeleaf пропускает свойства, к которым шаблон не обращается.

### 3. Контракт DTO → JSON

В проекте Jackson сериализует data class как JSON по имени поля. Из AGENTS.md, Q&A «Jackson отбрасывает is в boolean-полях Kotlin DTO» — все boolean-поля DTO имеют `@JsonProperty("isXxx")` где нужно. Для удаляемых полей это не имеет значения — они просто исчезают из JSON.

**Применение**: после удаления полей из `data class` и из конструктора в `fromSong`/`fromZakroma`/`ZakromaStreamMessageDto.song()` — JSON автоматически становится компактнее.

### 4. Width колонок в таблицах karaoke-public

В `karaoke-public/src/views/ZakromaView.vue` и `SearchView.vue` таблицы имеют `table-layout: fixed` с явными `<col style="width: Npx">` (см. AGENTS.md, секция «Таблицы karaoke-public»). При удалении колонки — нужно перераспределить ширины, чтобы общая ширина таблицы осталась прежней (или хотя бы не «прыгала»).

**Применение**: после удаления колонки `PlatformLink[link-name="sponsr"]` (32px) — оставшиеся колонки получают +32px или 16px (в зависимости от исходной ширины).

### 5. Legacy-таблицы — паттерн «оставляем как есть, пока не сломалось»

В AGENTS.md Q&A «После «Точные маркеры → Apply → Save → reopen» в SubsEdit.vue пропадают маркеры на песне со спецтегами» — пример того, как refactor уже решённой проблемы касается legacy и как важно НЕ задевать его.

**Применение**: legacy Thymeleaf-шаблоны правятся МИНИМАЛЬНО — только удаляются блоки `<a th:linkValue="${sett.linkSponsrPlay}">` и подобные. Не рефакторим шаблоны глобально.

## Анализ вариантов

### Вариант A: удалить поля из DTO, оставить их в `Song.kt` и `ZakromaAlbumSong`

**Плюсы**:
- Минимальное изменение в коде.
- Админка и боты продолжают работать.
- БД не трогаем.
- Legacy-шаблоны автоматически перестают рендерить удалённые поля (хотя на самом деле они продолжат — но мы их явно удалим из шаблонов).

**Минусы**:
- Нужно явно удалить ссылки из legacy-шаблонов (FR-010) — это ~30 строк в каждом из 3 шаблонов.

**Вердикт**: ✅ Принят (это и есть план).

### Вариант B: создать отдельный `SongOldSiteDto` для legacy

**Плюсы**:
- Чёткое разделение публичного DTO и legacy-DTO.

**Минусы**:
- Дополнительный класс, который дублирует поля `Song`.
- Thymeleaf сам по себе пропускает неиспользуемые свойства — нет необходимости в отдельном DTO.
- Усложняет MainController.kt (лишний слой маппинга).

**Вердикт**: ❌ Отклонён как избыточный. Шаблон Thymeleaf — это «view», который читает свойства модели по своему усмотрению. Если шаблон не запрашивает `linkSponsrPlay` — он и не отрендерится. `Song.kt` остаётся моделью; `SongOldSiteDto` НЕ нужен.

### Вариант C: перевести MainController на `SongPublicDto`

**Плюсы**:
- Один DTO для всего.

**Минусы**:
- `SongPublicDto` НЕ содержит `idStatus`, `haveVkGroupLink` (мы их удаляем согласно FR-004), а legacy они нужны.
- Расширять `SongPublicDto` обратно только ради legacy — откат цели рефакторинга.

**Вердикт**: ❌ Отклонён.

## Решения

### D-1: Архитектура — НЕ создаём `SongOldSiteDto`

**Решение**: Шаблон Thymeleaf получает `Song` напрямую, как сейчас. Удаляем ссылки на соцсети из шаблонов. Поля в `Song` остаются.

**Обоснование**: Thymeleaf-view сам выбирает, какие свойства модели рендерить. Не нужен промежуточный DTO.

### D-2: Ширины колонок в karaoke-public после удаления PlatformLink[sponsr]

**Решение**: Перед удалением смотрим текущие `<col style="width: ...">` в ZakromaView.vue и SearchView.vue. После удаления — добавляем освободившуюся ширину к ближайшей «резиновой» колонке (название песни) или оставляем как есть (сжатие таблицы).

**Обоснование**: таблица становится уже — это даже лучше для мобильных.

### D-3: Поведение `SongPublicDto.fromSong` для оставшихся полей

**Решение**: `fromSong(s, includeDetails = true)` (по умолчанию) сохраняет поведение для отдельной песни. `includeDetails = false` для списка сохраняет текущую логику.

**Обоснование**: `includeDetails=false` уже оптимизирует `formattedText*` и описания; поля ссылок на соцсети просто исчезают из обоих режимов.

### D-4: Поведение `ZakromaAlbumSongPublicDto.fromZakroma` для стрима

**Решение**: `zakromaStream` в `PublicApiController.kt:344-389` напрямую конструирует `ZakromaAlbumSongPublicDto(...)` без `fromZakroma()`. Удаляем 21 ссылку из конструктора, оставляя 10 полей.

**Обоснование**: используется тот же data class, но конструируется inline. Просто удаляем параметры.

### D-5: Поведение `ZakromaAlbumSong` (внутренняя модель)

**Решение**: Удаляем 21 поле `linkBoosty`, `linkSponsrPlay`, `linkDzen*`, `linkVk*`, `linkTg*`, `linkPl*`, `linkMax*` из `data class ZakromaAlbumSong` (строки 265-285 `Zakroma.kt`). Метод-конвертер (строки 207-225) тоже очищается.

**Обоснование**: эти поля используются ТОЛЬКО для построения `ZakromaAlbumSongPublicDto` (см. codegraph выше). Никакой другой потребитель не найден.

### D-6: KDoc для удалённых полей

**Решение**: В `SongPublicDto.kt` и `ZakromaPublicDto.kt` оставляем короткий комментарий-ссылку на эту спеку (`specs/185-song-dto-audit-sponsr-remove`) — чтобы будущий разработчик понимал, почему поля нет.

**Обоснование**: AGENTS.md Q&A «Jackson отбрасывает is в boolean-полях Kotlin DTO» — пример того, как опасные паттерны документируются в коде. Удаление полей — такой же значимый паттерн.

## Альтернативы, рассмотренные и отклонённые

| Альтернатива | Почему отклонена |
|---|---|
| Создавать отдельный `SongOldSiteDto` | Избыточно. Thymeleaf сам не использует лишние поля. |
| Использовать `@JsonIgnore` вместо удаления полей | `@JsonIgnore` оставляет поле в Kotlin-классе, но запрещает сериализацию. Не работает для Kotlin-data-class без явной аннотации на каждом поле; для 21 поля — шум в коде. Прямое удаление проще и чище. |
| Делать поля nullable и заполнять `null` | Слоты полей остаются в JSON (ключи с `null`-значениями). Не решает задачу «убрать мусор». |
| Не трогать legacy Thymeleaf | Вариант A в Q1 (отклонён пользователем — B). |
| Выключить старый сайт полностью | Вариант C в Q1 (отклонён пользователем — B). |

## Риски

| Риск | Митигация |
|---|---|
| Кто-то парсит JSON по имени поля и сломается | SC-012: после деплоя проверить `jq 'keys'` на ответе. Если есть внешний потребитель (не karaoke-public) — придётся вернуть. На текущий момент внешних потребителей публичного DTO нет. |
| Ширина таблицы в karaoke-public «прыгнет» | D-2: явно перераспределяем `<col style="width">`. |
| Legacy testpage.html:300 использует `idStatus` и `haveVkGroupLink` — если случайно удалим — тестовая страница сломается | D-1: эти поля НЕ удаляем. В `Song.kt` и в шаблоне они остаются. |
| `ZakromaAlbumSong` используется где-то ещё, не только в `ZakromaAlbumSongPublicDto` | Перед удалением — `grep -rn "ZakromaAlbumSong" karaoke-app/src/main/kotlin/`. Если найдутся новые потребители — оставляем поля, помечаем `@Deprecated`. |
| `Zakroma.kt` использует `linkSponsrPlay` где-то ещё (не только в `ZakromaAlbumSong`) | Перед удалением — `grep -rn "linkSponsrPlay\|linkBoosty\|linkDzen\|linkVk\|linkTg\|linkMax\|linkPl" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt`. По коду выше — только в ZakromaAlbumSong. |
| `SongPublicDto.fromSong` используется где-то ещё с ожиданием старых полей | Перед изменением — `grep -rn "SongPublicDto" karaoke-web/src/main/kotlin/`. По коду выше — только в PublicApiController.kt (2 раза, оба нашего проекта). |

## Что НЕ делаем

- Не удаляем поля из `Song.kt` (нужны админке и публикационным ботам).
- Не удаляем поля из БД (`tbl_settings`, `tbl_settings_sync`).
- Не трогаем `webvue3` (админка).
- Не делаем БД-миграций.
- Не рефакторим Thymeleaf-шаблоны глобально — только удаляем блоки ссылок на соцсети и `<col>`/`<th>`.
- Не добавляем `SongOldSiteDto` — избыточно (см. D-1).

## Что делаем

1. Удаляем из `SongPublicDto` (`karaoke-web/.../dto/SongPublicDto.kt`):
   - `sponsrLinkGeneral, haveVkGroupLink, idStatus, vkPictureBase64, linkSponsrPlay, linkBoostyTxt, linkDzenKaraoke/Lyrics/Tabs/Chords, linkVkKaraoke/Lyrics/Tabs/Chords, linkTgKaraoke/Lyrics/Tabs/Chords, linkMaxKaraoke/Lyrics/Tabs/Chords, linkPlKaraoke/Lyrics/Tabs/Chords` (всего 21 + 4 служебных = 25 полей).
2. Удаляем из `ZakromaAlbumSongPublicDto` (`karaoke-web/.../dto/ZakromaPublicDto.kt`):
   - `linkBoosty, linkSponsrPlay, linkDzen*, linkVk*, linkTg*, linkPl*, linkMax*` (всего 21 поле).
3. Удаляем те же 21 поле из `ZakromaAlbumSong` (`karaoke-app/.../model/Zakroma.kt`).
4. В `PublicApiController.kt`:
   - Обновляем `zakromaStream` (строки 344-389) — удаляем 21 параметр из конструктора `ZakromaAlbumSongPublicDto`.
   - `SongPublicDto.fromSong` обновляется автоматически (нет отдельного вызова — Jackson рефлексия).
5. В `karaoke-public/src/views/SearchView.vue`:
   - Удаляем `<td>` с `PlatformLink link-name="sponsr" ...>` (строки 140-147).
   - Удаляем `<div>` с тем же содержимым в карточке (строки 183-189).
   - Пересчитываем `<col style="width: ...">` для оставшихся колонок.
6. В `karaoke-public/src/views/ZakromaView.vue`:
   - Удаляем `<td>` (строки 319-326) и `<div>` в карточке (строки 354-360).
   - Пересчитываем `<col style="width: ...">`.
7. В `karaoke-web/src/main/resources/templates/filter.html`, `zakroma.html`, `testpage.html`:
   - Удаляем блоки `<a th:linkValue="${sett.linkSponsrPlay}">`, `<a th:linkValue="${sett.linkDzen*}" ...>`, `<a th:linkValue="${sett.linkVk*}" ...>`, `<a th:linkValue="${sett.linkTg*}" ...>`, `<a th:linkValue="${sett.linkMax*}" ...>`, `<a th:linkValue="${sett.linkPl*}" ...>`, `<a th:linkValue="${sett.linkBoosty}" ...>` (и их обрамляющие `<span th:if="...">` / `<span th:unless="...">`).
   - Удаляем соответствующие `<col>`, `<th>` колонки. Пересчитываем `width: Npx`.
   - **ОСТАВЛЯЕМ** в testpage.html:300 `${sett.idStatus}` и `${sett.haveVkGroupLink}` (нужны для логики отображения картинки).

## Зависимости

- Никаких новых внешних зависимостей.
- Никаких изменений в БД.
- Никаких изменений в API-контрактах для пользователя (только сужение полей — backwards-compatible для потребителей, которые используют `unknown fields`).

## Совместимость

- **karaoke-public** (Vue): полностью совместимо. Удаляются только НЕиспользуемые поля.
- **webvue3** (админка): не затрагивается (читает из `Song.kt`, не из `SongPublicDto`).
- **MainController + Thymeleaf**: совместимо. Шаблон продолжит получать `Song` со всеми полями; мы НЕ используем удалённые ссылки в шаблоне, поэтому они не отрендерятся. `idStatus` и `haveVkGroupLink` ОСТАЮТСЯ в `Song` и в шаблоне — testpage.html продолжает работать.
- **Публикационные боты**: не затрагиваются (читают из `Song.kt`).
- **API контракт для пользователя**: сужается. Любой потребитель, который ПАРСИТ JSON по имени поля, сломается. На текущий момент единственный потребитель — `karaoke-public` (Vue). Внешних потребителей нет (проект публичный, но не имеет third-party интеграций).