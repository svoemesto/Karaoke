# Отчёт по KARAOKE-384 «Портировать KARAOKE-361 (новость с source=admin) переносимо на текущий master»

* **Issue**: KARAOKE-384 (Plane, проект KARAOKE, тип `type:bug`)
* **Источник подхода**: ветка `697-owner-news-admin-source`, WIP-коммит `649fbd0b` (2026-11-01 14:52),
  снапшот — `.scratch/owner-local-20261101-1452/`
* **Ветка**: `708-news-source-admin` → PR #53 (https://git.sm-karaoke.ru/svoemesto/Karaoke/pulls/53)
* **Дата**: 2026-11-01
* **Статус**: реализовано, PR #53 смержен (merge-коммит `5f399560`)

## Постановка

Владелец: «портируй переносимым способом» — после того, как я объяснил, что WIP
владельца нельзя переносить «как есть»: он откатывает финальное правку PR #40
(создание новости на прод-стороне + доставка флага `newsAvailableAnnounced` через
`SyncTarget.extraPushableDiff`).

## Разбор истории (почему WIP не переносится напрямую)

PR #40 (автор — владелец, 2026-11-01 08:03, «доставлять на прод флаг
newsAvailableAnnounced — новость создаёт прод», KARAOKE-361) удалил:

| Удалено в PR #40 | Что это |
|---|---|
| `KaraokeRuntimeRole.kt` (-32) | механизм роли процесса ADMIN/PROD |
| admin-side хук в `Song.kt` (-60) | создание новости «в коллекции» в админке |
| WIP-изменения `News.kt` (-61) | константы источников, `hashSyncSourceFilter`, параметр `source` |
| `NewsSyncSourceTest.kt` (-47) | контракт источников |

и добавил финальное решение: `SyncTarget.kt` (+49) и `SongNewsFlagSyncTest.kt` (+87).

WIP владельца сделан поверх **старой** базы `50f48687` (merge-base с master),
поэтому содержит удалённое: прямой cherry-pick откатил бы решение PR #40.

Знание (`knowledge/domains/publishing/components/publishing-services.md`) фиксирует
и принципиальное ограничение: «push новостей из админки отключён — все
`sync_news_push_*_allowed = false`», и то, что авто-новости живут только на проде
(спека 089, защита от mirror-delete).

## Решение: что переносится, а что нет

Переносится **идея** — «источник новости управляет её судьбой»:

1. **Роль процесса** (`KaraokeRuntimeRole`) — восстановлена из истории; роль
   выставляется в `main` каждого приложения: `KaraokeAppApplication` → ADMIN,
   `KaraokeWebApplication` → PROD (по умолчанию PROD — на проде не должно быть
   дублей новостей).
2. **Источники как константы** — `News.SOURCE_MANUAL` / `SOURCE_AUTO` / `SOURCE_ADMIN`
   вместо строковых литералов; выбор источника в **одном месте**:
   `News.collectionAnnouncementSource(isAdminRuntime)`.
3. **`admin` участвует в hash-diff** — `News.hashSyncSourceFilter()` =
   `source IN ('manual','admin')`; `auto` исключён (спека 089).
4. **Admin-side создание возвращено** — новость «в коллекции» создаётся в админке
   в момент перехода флага `newsAvailableAnnounced` false→true.
5. **UI** — три источника (`auto` / `admin` / ручная).
6. **Контракт** — `NewsSyncSourceTest.kt` (офлайн, без БД).

Не переносится (осознанно):

- **прод-путь PR #40** — новость на проде по-прежнему создаёт прод; он остаётся
  страховкой, идемпотентен (гард `News.existsAnnouncement`) и дублей не создаёт;
- **включение доставки в коде** — это настройка `sync_news_push_insert_allowed`
  (по умолчанию выключена). Пока она выключена, поведение master не меняется;
  включение — осознанное решение владельца, а не побочный эффект правки.

## Переносимость: хук не привязан к рефакторингу `saveToDb`

WIP-версия вызывала `announceCollectionNewsIfJustAnnounced` из `saveToDb` —
но структура `saveToDb` в master уже другая (нет `saveToDbInternal`, функция
разрослась на ~400 строк), и старый хук просто не применим. В порте хук вызывается
из `markNewsAvailableIfReady()` — **точки установки флага** `newsAvailableAnnounced`:

* хук переживает дальнейший рефакторинг `saveToDb` ✓;
* срабатывает в обеих ветках сохранения (INSERT и UPDATE) ✓ — `markNewsAvailableIfReady()`
  вызывается в обеих, функция теперь возвращает `Boolean` (был ли переход false→true) ✓.

## Фактическое состояние доставки (проверено)

Запрос `POST http://localhost:7906/api/properties/getproperties` на админ-машине
(2026-11-01):

```
sync_news_push_insert_allowed = false
sync_news_push_update_allowed = false
sync_news_push_delete_allowed = false
sync_news_push_move_allowed  = false
sync_news_pull_insert_allowed = true
sync_news_pull_update_allowed = true
sync_news_pull_delete_allowed = true
sync_news_pull_move_allowed  = false
```

То есть доставка новостей из админки на прод **выключена**, и после этого изменения
поведение master не меняется: новость на проде создаёт прод-путь (PR #40). Участие
`admin` в hash-diff — подготовленная возможность, а не включённая доставка.

## Известный риск (зафиксирован в знании)

Если владелец включит `sync_news_push_insert_allowed`, `admin`-новости поедут на
прод. Гард `News.existsAnnouncement` защищает от обратного порядка (на проде уже
есть `admin` → `auto` не создастся), но **не** от прямого: если `auto`-новость про
ту же песню создана раньше, а `admin` приедет позже — в ленте окажутся обе строки.
Поэтому в знании зафиксировано: перед включением доставки новостей проверить, что
на проде нет `auto`-новостей про те же песни.

## Знание

`knowledge/domains/publishing/components/publishing-services.md` — новый подраздел
«Источник новости решает, участвует ли она в синхронизации»: таблица источников,
роль процесса, доставка через настройку, идемпотентность, ссылка на контракт.

## Проверка

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin`, `:karaoke-web:compileKotlin` | BUILD SUCCESSFUL |
| `:karaoke-app:ktlintCheck`, `:karaoke-web:ktlintCheck` | BUILD SUCCESSFUL |
| `:karaoke-app:test` | BUILD SUCCESSFUL, `NewsSyncSourceTest` 3/3 |
| `webvue3`: `npm run lint`, `npm run format:check` | чисто |
| `tools/ci-checks.py --local` (зоны интереса) | все применимые проверки зелёные |
| `lint-knowledge`, `check-knowledge-structure`, `check-doc-references` | OK |

## Границы изменений

Тронуты только: `KaraokeRuntimeRole.kt` (восстановлен), `KaraokeAppApplication.kt`,
`KaraokeWebApplication.kt`, `News.kt`, `Song.kt`, `SongReleaseAnnouncementService.kt`,
`NewsTable.vue`, `NewsSyncSourceTest.kt` (новый), `publishing-services.md`.
Чужие изменения (News.kt/Song.kt/SongReleaseAnnouncementService.kt/publishing-services.md/
NewsTable.vue из WIP владельца) не затрагивались — правки наложены поверх master.
