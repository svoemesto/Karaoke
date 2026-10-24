# PKG-ERR-STATS-ZERO: ошибка БД в статистике больше не выглядит как ноль

> **Status**: active
> **Feature Key**: pkg-err-stats-zero
> **Last Updated**: 2026-10-25
> **Spec**: [specs/385-pkg-err-stats-zero/report.md](../../specs/385-pkg-err-stats-zero/report.md)
> **Issue**: OpenProject #385 (пакет `PKG-ERR-STATS-ZERO`, волна E бэклога аудита #336)
> **Branch**: `604-pkg-err-stats`
> Per-feature документ (Constitution § VI FR-009).

## Что делает

Закрывает две записи аудита #336 класса ERR, у которых один симптом: сбой БД
превращается в `0` и неотличим от настоящего нуля.

- **BR-016** — `StatBySong` (karaoke-web): `runCountQuery` возвращал `0` при
  `SQLException`/отсутствии соединения, `refreshCache` публиковал эти нули в
  кеш счётчиков главной. Теперь снимок заменяется только при успехе всех трёх
  запросов, при сбое сохраняются прежние значения, а наружу отдаётся состояние
  `warming_up` / `ready` / `error`.
- **BR-185** — `MonetizationStats` (karaoke-app): `scalarInt`/`scalarDouble`
  и `getTopSubscribedSongs` гасили `SQLException` и `null`-соединение, отдавая
  `0`/`0.0`/пустой список. Теперь исключение доходит до `StatsController` и
  превращается в `503 stats.unavailable` (FR-003); сводка из 11 скаляров
  читается одним запросом — согласованный снимок и один шанс на ошибку.

## Зачем

Владелец принимает решения (выручка, число подписок и премиумов, размер
коллекции) по этим цифрам. «Нулевая выручка» при недоступной БД и «ноль
оплаченных подписок» — разные факты с разными решениями; дашборд и главная
страница не должны их смешивать. Для admin-статистики контракт FR-003 прямо
требует `503 stats.unavailable`; для публичных счётчиков страница обязана
остаться `200`, поэтому достоверность передаётся состоянием, а не нулём.

## Как работает

- **BR-016, тип возврата и кеш.** `StatBySongCounters` — неизменяемый снимок
  (`total`, `collection`, `freeNow`, `subscriptionOnly`, `inWork`) плюс
  `StatsCacheState` и `hasData`. Публикация — одна `AtomicReference.set`,
  поэтому читатель не видит «полусостояние». `runCountQuery` возвращает
  `Int?`; `null` — нет соединения, `SQLException` или нет строки. Если хотя бы
  один из трёх запросов вернул `null`, снимок не меняется, состояние
  становится `error`, в лог уходит `cache:refreshFailed` с throwable.
- **BR-016, cold-start.** `hasData == false` — единственное условие запуска
  фонового пересчёта (`ensureCacheInitialized`, single-flight CAS +
  `bgExecutor`). После неудачной первой попытки `hasData` остаётся `false` —
  следующий запрос повторит пересчёт, кеш не залипает до часового cron.
- **BR-016, API и отображение.** `/api/public/stats` возвращает прежние пять
  чисел и добавленное поле `state` (`ready` / `warming_up` / `error`) —
  `Map<String, Any>` вместо `Map<String, Int>`. `main.html` получает
  `statsState`: при `warming_up` вместо `0` рендерится `—`, при `error` —
  последние успешные числа и предупреждение «БД временно недоступна»;
  атрибут `data-stats-state` на таблице счётчиков используется тестами.
- **BR-185, модель данных.** `getSummary` — один SQL: 11 скалярных подзапросов
  одной строкой, значения читаются по алиасам. `getTopSubscribedSongs` — тот
  же запрос, что раньше, но без `catch`, гасящего ошибку. `connectionOrFail`
  бросает `SQLException` с текстом `stats.unavailable`, если
  `KaraokeConnection.getConnection()` вернул `null` (этот путь не бросает
  исключение сам — именно поэтому прежний `catch (SQLException)` не срабатывал).
- **BR-185, контроллер.** `/api/stats/monetization` уже шёл через
  `respondCached` (catch `SQLException` → 503), поэтому после правки модели
  контракт FR-003 заработал. `/api/stats/monetization/top-songs` обёрнут в новый
  `respondUncached` (тот же 503, без кеша); успешный формат ответа
  (`{"items": [...]}`) не изменился.

## Инварианты

1. `0` в статистике — только настоящее значение (`state = ready`). Ошибка БД
   не публикует ноль ни в кеш, ни в HTTP-ответ.
2. Снимок `StatBySong` заменяется целиком; частично обновлённого набора не
   существует.
3. Admin-статистика при сбое БД отдаёт `503 stats.unavailable` с
   `Retry-After: 10` и `errorCode`, а не `200` с нулями/пустым списком.
4. Публичный `/api/public/stats` не падает из-за статистики: HTTP `200` +
   `state`; числовые поля всегда присутствуют (fallback `0` при `warming_up`).
5. Ошибка закрытия `ResultSet`/`Statement` не подменяет ошибку запроса и не
   превращает успешное чтение в сбой.

## Известные ловушки

- **Не возвращайте `0` из `runCountQuery`/`scalar*` «для устойчивости»** — это
  ровно тот дефект, который закрыт (BR-016/BR-185). Сбой должен быть `null`/
  исключением, а решение «что показать» принимает слой API/UI.
- **`KaraokeConnection.getConnection()` возвращает `null`, а не бросает.**
  Поэтому проверки только на `SQLException` недостаточно: сценарий «too many
  clients already» до неё не доходит. Новые stats-функции обязаны проверять
  `null` явно (`connectionOrFail`).
- **`respondCached` кеширует только успешный ответ.** Не добавляйте
  `StatsCache.put` до `compute` — иначе нули/исключение залипнут на TTL.
- **Публичный SPA `karaoke-public` пока не читает `state`.** `stats.js` кладёт
  в store только пять чисел; при ошибке запроса остаются начальные нули.
  Бэкенд уже отдаёт `state` — подключение на фронте за владельцем (отдельная
  задача, вне пакета #385; пакет не включает frontend-сборку).
- **`StatsCacheScheduler.warmUp()` вызывается при старте бина**, до первого
  запроса. Если БД ещё не готова — состояние `error`, а не «всё по нулям»;
  счётчики появятся после первого успешного пересчёта.
- **Три запроса `StatBySong` не объединены в транзакцию.** «Атомарная замена
  набора» здесь означает «не публиковать частичный/нулевой набор», а не
  `REPEATABLE READ`: между чтениями песня может измениться. Для публичных
  счётчиков это допустимо и зафиксировано в
  [stats-cache](../../knowledge/domains/publishing/components/stats-cache.md).
- **`getSummary` — один запрос с 11 подзапросами.** Не переписывайте обратно
  на 11 независимых `scalar*` без необходимости: это и лишние round-trip'ы, и
  риск «частично нулевой» сводки при сбое одного из запросов.
- **`limit` в `/api/stats/monetization/top-songs`** — это `Int` из query, он
  подставляется в SQL как число (не строка). Отрицательный `limit` теперь даст
  `503` (ошибка PostgreSQL), а не молча пустой список — валидацию диапазона
  оставили за рамками пакета.

## Ссылки

- [Отчёт пакета и доказательства «до/после»](../../specs/385-pkg-err-stats-zero/report.md)
- [Карточка P-35 (BR-016)](../../specs/336-code-audit/findings/P-35.md)
- [Карточка P-15 (BR-185)](../../specs/336-code-audit/findings/P-15.md)
- [Бэклог аудита #336, блок PKG-ERR-STATS-ZERO](../../specs/336-code-audit/backlog.md)
- [SSoT: кеш счётчиков главной](../../knowledge/domains/publishing/components/stats-cache.md)
- [SSoT: домен stats и контракт 503](../../knowledge/domains/stats/domain.md)
- [SSoT: публичные контроллеры karaoke-web](../../knowledge/domains/karaoke-web/components/public-controllers-3.md)
- [SSoT: Thymeleaf-шаблоны](../../knowledge/domains/karaoke-web/components/thymeleaf-templates.md)
