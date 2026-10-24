# Target-aware запросы админки: ответ принадлежит БД, а не селекту

> **Status**: active
> **Feature Key**: `target-scoped-admin-requests`
> **Created**: 2026-10-24
> **Last updated**: 2026-10-24
> **Source**: OpenProject #367 — «[#336] S2: Гонка чтения/записи целевого объекта» (BR-397, BR-407, BR-409)
> **Audit**: [specs/336-code-audit/findings/P-54.md](../../specs/336-code-audit/findings/P-54.md),
> [P-55.md](../../specs/336-code-audit/findings/P-55.md),
> [P-56.md](../../specs/336-code-audit/findings/P-56.md)
> **Report**: [specs/367-pkg-race-target/report.md](../../specs/367-pkg-race-target/report.md)

## Что делает

Админ-SPA `webvue3` умеет работать с двумя БД (`target: 'local' | 'remote'`): селект
«Локальная/Сервер» есть у акций, share-ссылок, шаблонов новостей, тарифов и ещё
нескольких экранов. Правка закрепляет для трёх из них правило: **ответ target-aware
запроса применяется только к той БД, для которой он получен, а запись/отзыв адресуется
БД строки, а не текущему положению селекта.**

- **BR-397** (`webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue`) —
  сохранение шаблона новости (две последовательные POST: title, body) фиксирует target
  и оба значения на всю цепочку.
- **BR-407** (`webvue3/src/components/Promotions/store.js`) — список акций помнит БД, из
  которой получен (`promoRulesListTarget`); ответ прежней БД отбрасывается, а
  `create`/`save`/`delete` идут в БД показанного списка.
- **BR-409** (`webvue3/src/components/ShareLinks/store.js`) — дайджест share-ссылок
  помнит БД (`shareLinksDigestTarget`); «Отозвать» фиксирует БД строки в момент
  открытия подтверждения.

## Зачем

Быстрое переключение «Локальная/Сервер» порождало два параллельных запроса. Побеждал
**последний пришедший ответ**, а не последний запрос, поэтому таблица могла показать
строки чужой БД. Дальше действие брало `id` строки из показанного списка, а `target` —
из текущего селекта:

- акция: правка/удаление уходили в другую БД (при совпадении id менялось одноимённое
  правило, при несовпадении сервер отвечал `0`/`false` и UI молча перезагружал список);
- share-ссылка: «Отозвать» отзывало запись с тем же id в другой базе — необратимое
  действие не над той строкой;
- шаблон новости: title уходил в одну БД, body — в другую, причём body мог быть уже
  перезаписан значением из новой БД (`reload()` по watcher'у).

Периметр админский (нужен гоночный сценарий по времени), поэтому severity S2/S3, но
последствие — запись/отзыв не в той базе, вплоть до прод-настроек для `target=remote`.

## Как работает

1. Запрос фиксирует `requestTarget` и получает номер поколения
   (`createRequestGeneration().next()`).
2. На ответе `isStaleTargetResponse({ ticket, requestTarget, currentTarget, generation })`:
   если поколение уже не текущее или target сменился — ответ отбрасывается, state не
   меняется. Иначе данные коммитятся вместе с БД происхождения набора.
3. Смена target очищает список/дайджест: строки прежней БД не изображают данные новой,
   пока не пришёл ответ. БД происхождения последнего загруженного набора при этом не
   переписывается.
4. UI блокирует мутации, пока БД показанного набора не совпадает с выбранной
   (`PromotionsTable.canMutate`, `ShareLinksTable.isDigestInSync`).

### Архитектурные решения

| # | Решение | Обоснование |
|---|---------|-------------|
| D-1 | Поколение запроса на модуль store, не в Vuex `state` | счётчик не участвует в рендере; мутация на каждый запрос дала бы лишние перерисовки |
| D-2 | Не отменять запрос (`AbortController`), а отбрасывать ответ | `promisedXMLHttpRequest` не отдаёт отмену; правка транспорта ради трёх экранов — лишний риск |
| D-3 | БД происхождения хранится на уровне набора (`…ListTarget`, `…DigestTarget`) | бэкенд не возвращает target в строке дайджеста |
| D-4 | Для share-ссылок target строки фиксируется ещё и в компоненте | смена селекта между открытием подтверждения и нажатием «Отозвать» не должна менять адресата |
| D-5 | Цепочка сохранения шаблона вынесена в `templateSaveChain.js` | `.vue` не импортируется node-тестом; гонку нужно воспроизводить на том же коде, что в проде |

### Изменённые файлы

| Файл | Что |
|------|-----|
| `webvue3/src/lib/target-request-guard.js` | NEW — поколение запроса и правило отбрасывания устаревшего ответа |
| `webvue3/src/components/NewsTemplates/templateSaveChain.js` | NEW — закреплённая цепочка сохранения title+body |
| `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue` | поколение `reload()`, закреплённый target, блокировка селекта на время сохранения |
| `webvue3/src/components/Promotions/store.js` | `promoRulesListTarget`, поколение загрузки, мутации по БД списка, очистка при смене target |
| `webvue3/src/components/Promotions/PromotionsTable.vue` | `canMutate`, блокировка «Сохранить»/«Удалить»/«Добавить» |
| `webvue3/src/components/ShareLinks/store.js` | `shareLinksDigestTarget`, поколение дайджеста, отзыв по БД строки, очистка при смене target |
| `webvue3/src/components/ShareLinks/ShareLinksTable.vue` | `isDigestInSync`, фиксация `revokeTargetTarget`, блокировка «Отозвать» |

### Регрессионные тесты

| Файл | Что проверяет |
|------|---------------|
| `webvue3/src/lib/__tests__/target-request-guard.test.js` | семантика поколения и отбрасывания ответа |
| `webvue3/src/components/Promotions/__tests__/target-race.test.js` | ответ прежней БД последним; очистка списка; save/delete по БД списка |
| `webvue3/src/components/ShareLinks/__tests__/target-race.test.js` | дайджест, очистка, отзыв по БД строки, отказ без target |
| `webvue3/src/components/NewsTemplates/__tests__/template-save-chain.test.js` | расщепление БД на legacy-цепочке и закрепление на исправленной |

Шов — `webvue3/src/test-utils/fakeXhr.js`: подменяет `XMLHttpRequest` и отдаёт ответы в
обратном порядке, воспроизводя перемежающийся сценарий.

## Инварианты

- **MUST**: ответ target-aware запроса не меняет state, если `requestTarget` не равен
  текущему target **или** его поколение уже не последнее.
- **MUST**: `create`/`save`/`delete` акций адресуются `promoRulesListTarget`; при
  `null` мутация не выполняется. Fallback на текущий селект запрещён — это возвращает
  BR-407.
- **MUST**: `revokeShareLink` адресуется БД строки (явный `target` старше
  `shareLinksDigestTarget`); при отсутствии обеих — реджект, запрос не уходит.
- **MUST**: смена target очищает список/дайджест до прихода ответа новой БД.
- **MUST**: сохранение шаблона новости отправляет title и body в один target — тот,
  что был выбран в момент нажатия «Сохранить оба поля», — и ровно те значения, которые
  были на экране.
- **MUST**: UI не даёт нажать мутирующую кнопку, пока БД показанного набора не
  совпадает с выбранной.
- **SHOULD**: изменения по этим экранам сопровождать обновлением
  [target-request-guard.md](../../knowledge/system/frontend/target-request-guard.md).

## Известные ловушки

- **`…ListTarget = null` — это не `local`.** Пока список не загружен, мутации
  отказывают. Фолбэк «нет target — возьмём текущий» возвращает ровно ту гонку, которую
  закрыли (BR-407).
- **Очистка набора при смене target — часть контракта.** Уберите её — строки прежней БД
  останутся на экране и кнопки станут доступны раньше ответа.
- **Поколение живёт на модуль**, не в Vuex `state`: счётчик не нужен рендеру, а мутация
  на каждый запрос даёт лишние перерисовки и «грязный» state в devtools.
- **Не путайте БД строки и БД селекта.** Порядок приоритета в `revokeShareLink`:
  явный `target` → `shareLinksDigestTarget` → отказ. Другой порядок = BR-409.
- **Тест гонки обязан быть перемежающимся.** Последовательное «запрос → ответ → запрос»
  проходит и на сломанном коде; нужен `FakeXhr`, который отвечает в обратном порядке.
- **`NewsTemplatesEditor.vue` не импортируется node-тестом.** Не возвращайте чтение
  `this.target`/`this.bodyValue` внутрь `saveOne` — гонка вернётся, а тест этого не
  увидит: он проверяет `templateSaveChain.js`, куда снимок вынесен.
- **`promisedXMLHttpRequest` требует `sessionStorage`/`crypto.randomUUID`.** В node их
  ставит `FakeXhr.install()`; без этого транспорт падает до отправки.
- **Тесты магазинов импортируют store как ESM.** Относительные импорты внутри store
  обязаны иметь расширение `.js`, иначе `node --test` не разрешит модуль (Vite
  расширение не требует, node — требует).

## Ссылки

- [`specs/336-code-audit/findings/P-54.md`](../../specs/336-code-audit/findings/P-54.md)
  — BR-397 (исходная карточка P-54-11).
- [`specs/336-code-audit/findings/P-55.md`](../../specs/336-code-audit/findings/P-55.md)
  — BR-407 (P-55-09).
- [`specs/336-code-audit/findings/P-56.md`](../../specs/336-code-audit/findings/P-56.md)
  — BR-409 (P-56-01).
- [`specs/336-code-audit/backlog.md`](../../specs/336-code-audit/backlog.md) — блок
  `PKG-RACE-TARGET`.
- [`specs/367-pkg-race-target/report.md`](../../specs/367-pkg-race-target/report.md) —
  отчёт о поставке, доказательства «до/после».
- [`knowledge/system/frontend/target-request-guard.md`](../../knowledge/system/frontend/target-request-guard.md)
  — SSoT: правило, контракты, ловушки.
- [`webvue3/src/lib/target-request-guard.js`](../../webvue3/src/lib/target-request-guard.js)
  — поколение запроса.
- [`webvue3/src/components/Promotions/store.js`](../../webvue3/src/components/Promotions/store.js)
  — `promoRulesListTarget`.
- [`webvue3/src/components/ShareLinks/store.js`](../../webvue3/src/components/ShareLinks/store.js)
  — `shareLinksDigestTarget`.
- [`webvue3/src/components/NewsTemplates/templateSaveChain.js`](../../webvue3/src/components/NewsTemplates/templateSaveChain.js)
  — закреплённая цепочка сохранения.
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/PromoController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/PromoController.kt)
  — `/api/promorules/*` (target-aware API).
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ShareLinksAdminController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ShareLinksAdminController.kt)
  — `/api/sharelinks/digest`.
- [`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/SiteShareLinksController.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/SiteShareLinksController.kt)
  — `/api/siteusers/share/links/revoke` (переиспользуется без изменений).
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/NewsTemplateController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/NewsTemplateController.kt)
  — `/api/news/templates` (UPSERT по key+target).
