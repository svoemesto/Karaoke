# Target-aware запросы админки: поколение ответа и БД строки

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/lib/target-request-guard.js` + три target-aware экрана
> (NewsTemplates, Promotions, ShareLinks).

## Ответственность | Responsibility

Закрывает класс гонок «целевая БД» (`target: 'local' | 'remote'`) в админ-SPA
`webvue3`: ответ запроса применяется только к той БД, для которой он получен, а
разрушительное действие адресуется БД строки, а не текущему положению селекта.

Аудит #336, пакет `PKG-RACE-TARGET` (OpenProject #367, BR-397 / BR-407 / BR-409):

| BR | Экран | Что было |
|---|---|---|
| BR-397 | `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue` | цепочка из двух POST (title, body) читала `target` и `bodyValue` в момент каждого запроса: переключение БД между ними уводило поля в разные базы |
| BR-407 | `webvue3/src/components/Promotions/store.js` | ответ списка применялся без сверки target; `save`/`delete` подставляли текущий target |
| BR-409 | `webvue3/src/components/ShareLinks/store.js` | ответ дайджеста применялся без сверки target; `revoke` подставлял текущий target вместо БД строки |

**Граница**: контекст НЕ отвечает за:
- серверную атомарность записи (её даёт существующий API; здесь только клиент);
- выбор БД по умолчанию и синхронизацию local ↔ remote (см.
  [two-db-sync.md](../../domains/processing/components/two-db-sync.md));
- пагинацию и фильтры дайджеста share-ссылок (BR-410/BR-411 — другие пакеты аудита).

## Интерфейсы и Контракты | Interfaces and Contracts

`webvue3/src/lib/target-request-guard.js`:

- **`createRequestGeneration()`** возвращает `{ next(), isCurrent(ticket), current() }` —
  монотонную эпоху запросов на модуль store. `next()` увеличивает эпоху и отдаёт
  номер запроса; `isCurrent(ticket)` отвечает, остался ли этот номер последним.
- **`isStaleTargetResponse({ ticket, requestTarget, currentTarget, generation })`** —
  `true`, если ответ устарел: другая эпоха ИЛИ target, с которым отправляли, уже не
  выбран. Единственное место, где сформулировано правило отбрасывания.

`webvue3/src/components/NewsTemplates/templateSaveChain.js`:

- **`saveTemplateChain({ target, titleKey, titleValue, bodyKey, bodyValue, saveOne })`** —
  две последовательные POST (`saveOne(key, value, target)`), target и значения
  зафиксированы на входе.
- **`saveTemplateFromEditor({ editor, saveOne })`** — точка входа компонента: снимает
  `target`, `titleValue`, `bodyValue` с редактора один раз и передаёт в закреплённую
  цепочку (именно её вызывает `saveTemplate()`).

Vuex-контракты (новые поля помечены):

| Store | Поле/геттер | Смысл |
|---|---|---|
| `Promotions` | `promoRulesListTarget` / `getPromoRulesListTarget` | БД, из которой реально получен показанный список; `null` — ещё не загружался |
| `Promotions` | мутации `create`/`save`/`delete` | адресуются `promoRulesListTarget`, а не `promoRulesTarget`; пока `null` — `Promise.resolve(false)` |
| `ShareLinks` | `shareLinksDigestTarget` / `getShareLinksDigestTarget` | БД показанного дайджеста |
| `ShareLinks` | `revokeShareLink({ shareLinkId, reason, target })` | `target` — БД строки; при отсутствии берётся `shareLinksDigestTarget`, иначе реджект |

## Логика и Алгоритмы | Logic and Algorithms

### Правило

1. На отправке запрос фиксирует `requestTarget` и получает `ticket = generation.next()`.
2. На ответе: `isStaleTargetResponse(...)` → `return` (state не меняется). Иначе —
   коммит данных и `…ListTarget/…DigestTarget = requestTarget`.
3. Смена target (`set…Target`) очищает список/дайджест: строки прежней БД не должны
   изображать данные новой, пока не пришёл ответ. `…ListTarget/…DigestTarget` при этом
   НЕ переписывается — это происхождение последнего загруженного набора.
4. UI блокирует мутации, пока `listTarget/digestTarget !== target` или идёт загрузка
   (`PromotionsTable.canMutate`, `ShareLinksTable.isDigestInSync`).

### Почему не отмена запроса и не серверный ключ

- `promisedXMLHttpRequest` (`webvue3/src/lib/utils.js`) не отдаёт `AbortController` —
  отмена потребовала бы правки транспорта ради трёх экранов. Отбросить ответ дешевле:
  состояние меняет только актуальный.
- Уникальный ключ/условный UPDATE — инструмент серверной гонки. Здесь гонка целиком в
  клиентском состоянии (какой ответ применить и в какую БД писать), БД правится
  существующими атомарными методами; API не меняется.

### Почему `ListTarget`, а не target строки в каждой строке

Строка дайджеста не несёт target с бэкенда (ответ одного и того же эндпоинта), поэтому
происхождение хранится на уровне набора. Для share-ссылок компонент дополнительно
фиксирует БД в момент открытия подтверждения (`revokeTargetTarget`), чтобы смена
селекта до нажатия «Отозвать» не перенаправила запрос.

## Зависимости | Dependencies

- `promisedXMLHttpRequest` ([utils.js](../../../webvue3/src/lib/utils.js)) — транспорт.
- Vuex-модули `Promotions`, `ShareLinks`, `NewsTemplates` (см.
  [vuex-patterns.md](vuex-patterns.md)).
- Регрессионные тесты: `webvue3/src/lib/__tests__/target-request-guard.test.js`,
  `webvue3/src/components/Promotions/__tests__/target-race.test.js`,
  `webvue3/src/components/ShareLinks/__tests__/target-race.test.js`,
  `webvue3/src/components/NewsTemplates/__tests__/template-save-chain.test.js`
  (шов `webvue3/src/test-utils/fakeXhr.js`).

## Ловушки

- **`…ListTarget = null` — это не «local»**. Пока список не загружен, мутации
  отказывают; fallback на текущий target вернул бы ровно ту гонку, которую закрыли.
- **Очистка списка при смене target — часть контракта.** Если её убрать, строки прежней
  БД останутся на экране и кнопки снова станут доступны раньше ответа.
- **Поколение живёт на модуль store, а не в state.** Это осознанно: счётчик не нужен
  рендеру. Не переносите его в `state` без причины — получите лишние перерисовки и
  «грязный» state в devtools.
- **Не смешивайте `target` строки и `target` селекта.** В `revokeShareLink` явный
  `target` старше `digestTarget`, а `digestTarget` старше текущего селекта — обратный
  порядок возвращает BR-409.
- **Тест гонки обязан быть перемежающимся.** Последовательное «запрос → ответ → запрос»
  проходит и на сломанном коде; шов `FakeXhr` нужен, чтобы ответить в обратном порядке.
- **`NewsTemplatesEditor` не импортируется node-тестом** (`.vue`), поэтому цепочка
  сохранения вынесена в `templateSaveChain.js`. Не возвращайте чтение `this.target`
  внутрь `saveOne` — гонка вернётся и тест этого не увидит.

## Changelog

- **2026-10-24** (OpenProject #367, аудит #336, BR-397/407/409): Initial. Поколение
  запроса + БД происхождения набора; UI-блокировка мутаций; тесты «до/после» на шве.
