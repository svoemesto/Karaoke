# Доступ редакторов к SKIP-авторам и песням

> **Status**: active
> **Feature Key**: `editor-skipped-content-access`
> **Branch**: `293-skip-author-toggle`
> **Spec**: [`specs/293-skip-author-toggle/spec.md`](../../specs/293-skip-author-toggle/spec.md)
> **Plan**: [`specs/293-skip-author-toggle/plan.md`](../../specs/293-skip-author-toggle/plan.md)
> **Last Updated**: 2026-09-02 (Pass 282+ — спека 293)

## Что делает

Добавляет булев флаг `can_work_with_skipped` в `tbl_site_users`,
разрешающий залогиненному пользователю (админу/редактору) **видеть и
работать** с контентом, скрытым от публичной поверхности механизмами
SKIP:

- `tbl_authors.skip = TRUE` — автор целиком (вместе со всеми его песнями).
- Тег `SKIP` в `tbl_songs.tags` (split по пробелам, uppercase-сравнение)
  — конкретная песня.

Галочка «Может работать со SKIP-авторами и песнями» выставляется
**только админом** в форме редактирования пользователя в
`webvue3/SiteUsers/edit/SiteUserEdit.vue` (по выбору пользователя в
`/speckit.specify` — НЕ отображается в `karaoke-public/AccountView.vue`).

В `karaoke-public` редактор с галочкой видит визуальный бейдж «SKIP»
рядом с именем автора / песни — страховка от случайной публикации
скрытого контента.

Раньше редакторы и админы не могли увидеть SKIP-контент через UI — для
исправления метаданных или снятия SKIP-тега приходилось лезть в БД
напрямую. Это замедляло реакцию на реквесты правообладателя.

## Compliance

`SKIP` = «удалено по требованию правообладателя». Share-link для
SKIP-песен **запрещён** независимо от `can_work_with_skipped`
(FR-012):

- UI: кнопки «Поделиться» / «Share-link» скрыты для SKIP-песен
  (`v-if="!isCurrentSongSkipped"`).
- Бэкенд `SongShareLinkService.createLink` бросает `SongSkipped` →
  HTTP `409 Conflict` с `errorCode: "share.songSkipped"` и message
  «Невозможно создать share-link для SKIP-контента».
- Новая `ShareErrorCode.SONG_SKIPPED("share.songSkipped")` отличает
  SKIP от обычной недоступности (`SONG_UNAVAILABLE`) — для UI.

OG/SEO-страницы для ботов (`/api/public/og/song`) НЕ меняются — SKIP-песни
по-прежнему скрыты от индексации (`<meta name="robots"
content="noindex, nofollow">` + видимое предупреждение «Удалено по
требованию правообладателя»). Это критично для compliance — у ботов нет
`Authorization`-заголовка, `canWorkWithSkipped` для них всегда `false`.

## Зачем

Раньше редакторы и админы не могли увидеть SKIP-контент через UI — для
исправления метаданных или снятия SKIP-тега приходилось лезть в БД
напрямую. Это:

- замедляло работу (особенно при срочных реквестах правообладателя);
- увеличивало риск ошибки (ручные правки `tbl_songs.tags` без бэкапа);
- требовало прямого доступа к БД от каждого редактора, что нарушало
  принцип «всё через UI».

После фичи редактор с галочкой работает со SKIP-контентом так же, как с
обычным — через Закрома, историю прослушиваний, страницы песен.

## Как работает

1. **Админ** открывает карточку редактора в `webvue3`, ставит новую
   галочку «Может работать со SKIP-авторами и песнями», нажимает
   «Сохранить».
2. В `tbl_site_users` записывается `can_work_with_skipped = TRUE`.
3. **Редактор** на следующем HTTP-запросе (без logout/login) получает
   эффект: `SiteUserResolver` подгружает актуальное значение из БД.
4. В «Закромах», истории прослушиваний, share-link, OG/SEO — фильтр
   SKIP снимается.
5. В UI редактор видит бейджи «SKIP» рядом с именами SKIP-авторов /
   песен — это визуальный сигнал, что контент скрыт от публики.
6. При попытке создать share-link на SKIP-песню (даже программно) —
   бэкенд возвращает `409 Conflict` с понятным сообщением.

## Инварианты

- **Анонимный пользователь** (без сессии) **всегда** видит строгий
  SKIP-фильтр, независимо от `canWorkWithSkipped`. Никаких cookie-флагов,
  никаких «запомнить выбор». Галочка действует только для залогиненных.
- **OG/SEO для ботов** НЕ зависит от `canWorkWithSkipped`. Боты не имеют
  `Authorization`, и для них фильтр SKIP применяется безусловно
  (compliance с правообладателем — `noindex, nofollow`).
- **Авто-выдачи галочки админам НЕТ** (clarify Q3, 2026-09-02). Админ
  выставляет галочку себе явно, как и любому другому пользователю.
  Унифицированная логика без OR с `is_admin`.
- **Галочка НЕ жёстко привязана к `is_editor=true`** — админ может
  выдать её любому пользователю (по выбору). Основное назначение —
  редакторам.
- **Share-link запрещён для всех SKIP-песен** — `canWorkWithSkipped`
  НЕ прокидывается в `SongShareLinkService.createLink`, даже если
  инициатор — редактор с галочкой. Compliance важнее удобства.
- **recordhash-триггер пересоздан** в миграции V45 (Constitution §III).
  Sync LOCAL↔SERVER работает без дополнительных действий.
- **NFR-001**: SQL-проверка флага — **не чаще 1 раза на HTTP-запрос**.
  Достигается через `SiteUserResolver` без дополнительных SQL.
- **NFR-002**: изменение флага отражается на **следующем HTTP-запросе**
  (без logout/login) — `SiteUserResolver` намеренно не кэширует
  результат.

## Известные ловушки

- **`Song.loadAuthorSongCounts` НЕ фильтрует по `tbl_authors.skip`**
  (считает ВСЕ песни, включая SKIP). Это **намеренно** для админов с
  галочкой, но для анонимов UI-с `каот `Закрама`` фильтрует SKIP-песни
  через `Zakroma.getZakroma`, поэтому `expectedCount` (включает SKIP) и
  `actualCount` (без SKIP) могут различаться. Это **существующее**
  поведение, не моё изменение.
- **`Song.loadListAuthors` фильтрует skip-авторов через
  `tbl_authors.skip = false` только если `withSkiped=false`** (строки
  7236–7239). Когда `withSkiped=true && isSpecialOrder != null` (любое
  значение) — попадает в `else`-ветку, которая раньше возвращала
  `WHERE skip = false` — БАГ. **Исправлено**: добавлены две
  дополнительные ветки (строки 7231–7234 в `Song.kt`) для
  `withSkiped=true && isSpecialOrder=true/false`, возвращающие ВСЕХ авторов
  из `tbl_songs` без фильтра.
- **`getCachedAuthorsTiles` использовал cache key
  `"$scope:$onlyPublished"`** — **не включал `includeSkipped`**. БАГ:
  если аноним загрузил тайлы в кэш, редактор с галочкой получал тот же
  кэшированный список (без SKIP-авторов). **Исправлено**: ключ
  расширен до `"$scope:$onlyPublished:$includeSkipped"`.
- **`/api/public/zakroma/stream` (chunked endpoint) не передавал
  `canSeeSkipped` в `Zakroma.getZakroma`** — БАГ: SKIP-песни
  фильтровались для всех. **Исправлено**.
- **`SongView.vue` показывал заглушку «Удалено по требованию
  правообладателя» для всех** — БАГ: редактор с галочкой не видел
  контент SKIP-песни. **Исправлено**: добавлено `&& !canSeeSkipped` в
  условие `v-else-if="currentSong && currentSong.contentRemoved"`.
- **`SiteUsersController.update()` НЕ принимал параметр
  `canWorkWithSkipped`** — БАГ: флаг не записывался в БД.
  **Исправлено**: добавлен `@RequestParam(required = false)
  canWorkWithSkipped: Boolean?` + `canWorkWithSkipped?.let { user.canWorkWithSkipped = it }`.
- **`Author.loadAuthorTilesWithCounts` всегда использовал
  `WHERE skip = false`** — БАГ. **Исправлено**: добавлен параметр
  `includeSkipped: Boolean = false`, SQL `WHERE skip = false` →
  `WHERE TRUE` если `includeSkipped`.
- **`PublicOgSongController.isSkipped` намеренно НЕ расширен** —
  боты (единственные вызывающие `buildSeoHtmlForBots`) НЕ имеют
  `Authorization`, и для них SKIP-фильтр должен работать безусловно.

## Изменения

| Файл | Изменение |
|------|-----------|
| `deploy/karaoke-db/45_site_user_can_work_with_skipped.sql` | Новая миграция: колонка + пересоздание recordhash-триггера |
| `karaoke-app/.../SiteUser.kt`, `SiteUserDto.kt` | Поле `canWorkWithSkipped` |
| `karaoke-app/.../Zakroma.kt` | Параметр `canSeeSkipped` + поля `authorSkip`/`contentRemoved` |
| `karaoke-app/.../Author.kt` | `loadAuthorTilesWithCounts(includeSkipped)` + SQL-ветки |
| `karaoke-app/.../controllers/SiteUsersController.kt` | `@RequestParam canWorkWithSkipped` в update |
| `karaoke-app/.../controllers/ListeningHistoryController.kt` | SKIP-фильтр снят для admin endpoint |
| `karaoke-web/.../controllers/MainController.kt`, `PublicApiController.kt` | Прокидывание `canSeeSkipped` через `SiteUserResolver`; расширенный cache key |
| `karaoke-web/.../controllers/PublicShareController.kt` | Catch `SongSkipped` → 409 |
| `karaoke-web/.../services/SongShareLinkService.kt` | Exception `SongSkipped` + `songIsSkipped` |
| `karaoke-web/.../util/ShareErrorCode.kt` | Новый код `SONG_SKIPPED` |
| `karaoke-web/.../dto/ZakromaPublicDto.kt` | Поля `authorSkip`/`contentRemoved` |
| `webvue3/.../SiteUsers/edit/SiteUserEdit.vue` | Галочка |
| `webvue3/.../SiteUsers/store.js` | Payload |
| `webvue3/.../SiteUsers/SiteUsersTable.vue` | Колонка «SKIP-доступ» |
| `karaoke-public/.../views/ZakromaView.vue`, `SongView.vue` | Бейджи SKIP + скрытие share + `!canSeeSkipped` в заглушке |

## Диагностика

```bash
# Проверить наличие колонки
docker exec -i karaoke-db psql -U postgres -d karaoke -c "
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name='tbl_site_users' AND column_name='can_work_with_skipped';
"

# Регресс анонима
curl -s 'http://localhost:8897/api/public/zakroma' -o /tmp/anon.json
# → должно быть пусто (skip-авторы скрыты)

# Редактор с галочкой (id=42 для примера)
TOKEN=$(curl -s -X POST 'http://localhost:8897/api/public/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"email":"editor@example.com","password":"..."}' | jq -r .token)
curl -s 'http://localhost:8897/api/public/zakroma' \
  -H "Authorization: Bearer $TOKEN" | jq '[.[] | .author] | length'
# → больше, чем для анонима

# Share-link для SKIP-песни → 409
curl -s -X POST 'http://localhost:8897/api/public/share/create/<SKIP_SONG_ID>' \
  -H "Authorization: Bearer $TOKEN" -w "\nHTTP_CODE: %{http_code}\n"
# → HTTP_CODE: 409
```

## Ссылки

- [Спека: `specs/293-skip-author-toggle/spec.md`](../../specs/293-skip-author-toggle/spec.md)
- [План: `specs/293-skip-author-toggle/plan.md`](../../specs/293-skip-author-toggle/plan.md)
- [Research: `specs/293-skip-author-toggle/research.md`](../../specs/293-skip-author-toggle/research.md)
- [Data model: `specs/293-skip-author-toggle/data-model.md`](../../specs/293-skip-author-toggle/data-model.md)
- [Quickstart: `specs/293-skip-author-toggle/quickstart.md`](../../specs/293-skip-author-toggle/quickstart.md)
- [LiveDoc: `livedocs/features/293-skip-author-toggle.md`](../../livedocs/features/293-skip-author-toggle.md)
- [Self-assign паттерн (аналогия): `archive/docs/features/editor-tasks.md`](../../archive/docs/features/editor-tasks.md)
- [Share-link flow: `archive/docs/features/guest-share-link.md`](../../archive/docs/features/guest-share-link.md)
- [SKIP через OG/SEO: `archive/docs/features/seo-html-for-bots.md`](../../archive/docs/features/seo-html-for-bots.md)
- [SiteUser aggregate: `livedocs/domain/identity.md`](../../livedocs/domain/identity.md)
- [SKIP-механика: `livedocs/domain/catalog.md`](../../livedocs/domain/catalog.md)
- [Constitution §III (recordhash): `.specify/memory/constitution.md`](../../.specify/memory/constitution.md)
- [Constitution §VI FR-009 (per-feature документ): `.specify/memory/constitution.md`](../../.specify/memory/constitution.md)