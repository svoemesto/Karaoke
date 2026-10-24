# PKG-S2-18 — webvue3: COMPAT, DATA, ERR, RACE

- **Задача**: OpenProject #381, пакет `PKG-S2-18` (волна D, бэклог аудита #336)
- **Ветка**: `601-pkg-s2-18` (от `origin/master` @ `0e701a11`), worktree
  `.worktrees/waveD-s2-18`
- **Дата**: 2026-10-25
- **Записи пакета (13)**: BR-449, BR-450, BR-451, BR-467, BR-458, BR-459, BR-460,
  BR-461, BR-462, BR-483, BR-484, BR-485, BR-487
- **Источник правды**: `specs/336-code-audit/findings/P-62a.md`, `P-62b.md`, `P-64.md`,
  `P-66.md`, `P-67.md`; `registry.csv` (порции P-62, P-64, P-66, P-67)
- **Коммиты**: `65a8ecb9` (SubsEdit), `e4897643` (подписки/тарифы), `504c6ab3` (статистика),
  `490ae74e` (StemJobs), `e6493cbd` (плеер), `ab7f9ba1` (knowledge, per-feature документ, отчёт)

## Итог

| BR | Класс | Было | Стало | Файл:строка | Тест | Статус |
|---|---|---|---|---|---|---|
| BR-449 | COMPAT | Поле «Time:» — обычный `<input>` с `v-model` на `currentMarker.time`: `1,5`/`""` уходили строкой в JSON, бэкенд отвечал `bad_markers` и отклонял сохранение всего голоса | `type="number"` + `normalizeMarkerTime()` с откатом к прежнему числу; `getMarkersToSave()` приводит `time` через `parseMarkerTime()` и не отправляет нечисловое время | `SubsEdit.vue:322`, `:3292`, `:3300`, `:3310`; `lib/marker-fields.js:26` | `subsEdit-s2-18.test.js` (4) | исправлено |
| BR-450 | ERR | `catch` сохранения складывал текст в `customConfirmParams`, но не выставлял `isCustomConfirmVisible` — модалка (рендерится по `v-if`) не открывалась, ошибка невидима всегда | `catch`: `isAlert: true`, `isCustomConfirmVisible = true`, `console.error`; `save()` возвращает признак успеха | `SubsEdit.vue:3237-3254` | `subsEdit-s2-18.test.js` (1) | исправлено |
| BR-451 | RACE | Watcher `currentVoice` применял ответы безусловно: поздний ответ прежнего голоса перетирал `sourceMarkers`/`sourceText`, и сохранение записывало разметку голоса 1 в голос 2 | Запрошенный голос + поколение (`voiceLoadToken`) проверяются после каждого `await`; отказ загрузки очищает состояние и блокирует сохранение (`voiceLoadFailed`, fail-closed) | `SubsEdit.vue:2287-2330`, `:3209` | `subsEdit-s2-18.test.js` (2, гонка воспроизведена на фейковом `this`) | исправлено |
| BR-467 | ERR | `case 'chord'` читал `marker.chord.split('|')`: у меток старой формы `chord` нет (226 меток в первых 400 песнях) → `TypeError` внутри `createRegionMarker` обрывал `redrawMarkersFull` | `resolveChordLabel(marker)` (`chord || label`), пустое значение → пустой регион без исключения | `SubsEdit.vue:4250`; `lib/marker-fields.js:53` | `subsEdit-s2-18.test.js` (2) | исправлено; миграция `label`→`chord` — за владельцем |
| BR-458 | COMPAT | Таблица «Подписки» пагинировала клиентом одну серверную страницу (2+ всегда пусты), размер уходил как `perPage` вместо `pageSize`; смена target не чистила дайджест | Серверная пагинация: `reload`/смена страницы/фильтр шлют `page` и `pageSize`; `b-table` не режет строки; смена target очищает дайджест и счётчик | `SubscriptionsTable.vue:40`, `:284`, `:331`, `:345`; `SubscriptionsFilterModal.vue:149` | `subscriptions-pagination.test.js` (4) | исправлено; сохранение фильтра между reload — за владельцем (нужен Vuex-фильтр) |
| BR-459 | RACE | Ответы статистики коммитились без привязки к запросу: поздний ответ прежней БД/периода подменял данные под новой меткой, TTL 60 с «замораживал» подмену | Поколение на каждый action (`statsRequestGuard`), применяется только последний ответ; устаревший возвращает `false` и не сбрасывает `isLoading` | `Stats/store.js:379`, `:398`, `:421`, `:455`, `:480`, `:501`, `:520`, `:541`, `:558`, `:579`; `Stats/request-guard.js` | `stats-s2-18.test.js` (1, ответы в обратном порядке) | исправлено; «частичный успех» вкладки (BR-465) — за владельцем |
| BR-460 | COMPAT | `loadMonetizationTopSongs` объявлен, но нигде не вызывался — блок «Топ песен по подпискам» всегда показывал «нет оплаченных подписок» | Action в `tabEndpoints[1]` + `simpleEndpointActions`, возвращает `Promise<Boolean>`; очистка вкладки чистит и топ | `StatsView.vue:374`, `:394`; `Stats/store.js:595` | `stats-s2-18.test.js` (2) | исправлено |
| BR-461 | ERR | Мутации тарифов без `.catch`: при 4xx/5xx промис отклонялся, ошибки не было, таблица показывала новую цену как сохранённую в прод-БД; пустая цена (`'' >= 0`) уходила с `priceRub=` и получала 400 молча | Мутации возвращают `Promise<Boolean>` и пишут `tariffsError` (баннер); таблица откатывает `editable` к подтверждённому снимку, форма сбрасывается только после успеха; `isValidTariffPrice()` до отправки | `Tariffs/store.js:13`, `:96`, `:111`, `:117`, `:134`; `TariffsTable.vue:159`, `:173` | `tariffs-s2-18.test.js` (5) | исправлено; `KaraokeDbTable.save()` логирует SQL-исключения и не пробрасывает (вне порции) — за владельцем |
| BR-462 | DATA | Смена target не чистила список, `catch` только логировал: под новой меткой висели задания другой БД, а «Удалить» уходило с чужим id и target текущего селектора (удаление файлов из хранилища) | `stemJobsLoadedTarget` фиксирует БД показанного списка; смена target/ошибка очищают список (fail-closed); ответ прежней БД отбрасывается; ошибка видна баннером; stop/delete возвращают `false` | `StemJobs/store.js:20`, `:70`, `:75`, `:95`; `StemJobsTable.vue:15` | `stemJobs-s2-18.test.js` (5) | исправлено |
| BR-483 | DATA | Стрелки «▼» DEMO-экрана анимировались по `Date.now()`: кадры офлайн-рендера снимаются не в реальном времени, файл невоспроизводим | Время кадра передаётся в `_renderDemoEndScreen` явно, фаза `sin(timeSec/0.3)` | `KaraokePlayer.js:2711`, `:2920`, `:2988` | `karaoke-player-s2-18.test.js` (4) | исправлено |
| BR-484 | ERR | Ветка `inlineData` не задавала `songId`: «Сохранить файл» шло на `/api/song/undefined/playerfile`, падало и писало только в console | `songId` из `inlineData.id`; без id `_getPlayerFileUrl()` → `null`, ошибка показывается в пункте меню (`_notifyMenuError`) | `KaraokePlayer.js:41`, `:1479`, `:1494`, `:1508` | `karaoke-player-s2-18.test.js` (2) | исправлено |
| BR-485 | COMPAT | Карта подписей версии покрывала только LYRICS/KARAOKE/DEMO: для CHORDS/TABS третий блок сплэша не рисовался | `KaraokePlayer.RENDER_VERSION_SPLASH` покрывает весь `RenderVersion` (5), о неизвестной версии — `warn` | `KaraokePlayer.js:1167`, `:3276` | `karaoke-player-s2-18.test.js` (2) | исправлено |
| BR-487 | DATA | TTL-кеш сбрасывался только у активной вкладки: после смены БД/периода неактивная вкладка в пределах 60 с показывала цифры другого `target`/`days` | `invalidateAllTabs()` при смене target, `invalidateDayDependentTabs()` (вкладки 0/2/3) при смене days — включая неактивные | `StatsView.vue:851`, `:859`, `:867`, `:884` | `stats-s2-18.test.js` (3) | исправлено |

Итого: 13/13 записей закрыты правкой. Решения, выходящие за безопасный объём пакета, —
в разделе «За владельцем».

## Регресс-контроль «до/после»

Тесты пакета — `node:test`, без браузера и стенда; входят в `npm run test:unit`:

```
webvue3/src/components/Songs/edit/__tests__/subsEdit-s2-18.test.js
webvue3/src/components/Stats/__tests__/stats-s2-18.test.js
webvue3/src/components/Tariffs/__tests__/tariffs-s2-18.test.js
webvue3/src/components/StemJobs/__tests__/stemJobs-s2-18.test.js
webvue3/src/components/Subscriptions/__tests__/subscriptions-pagination.test.js
webvue3/src/player/__tests__/karaoke-player-s2-18.test.js
```

**После правки** (`specs/381-pkg-s2-18/after-tests.txt`):

```
$ cd webvue3 && node --test <6 файлов>
ℹ tests 37
ℹ pass 37
ℹ fail 0
```

**До правки** (те же тесты на коде `HEAD` @ `0e701a11`, полный вывод —
`specs/381-pkg-s2-18/before-tests.txt`):

```
ℹ tests 21
ℹ pass 4
ℹ fail 17
✖ BR-449: время маркера не уходит строкой в JSON
✖ getMarkersToSave приводит time к числу и выбрасывает нечисловые маркеры
✖ поле «Time:» объявлено числовым и нормализуется на change
✖ BR-450: ошибка сохранения видна пользователю
✖ BR-451: ответ другого голоса не перетирает разметку
✖ поздний ответ прежнего голоса игнорируется
✖ отказ загрузки голоса блокирует сохранение (fail-closed)
✖ BR-467: chord-метка без поля chord не роняет redraw
✖ BR-483: DEMO-стрелки позиционируются по времени кадра
✖ BR-484: сохранение файла в inline-режиме знает id песни
✖ BR-485: подписи версии на сплэше покрывают весь RenderVersion
✖ store-тесты (Stats/StemJobs/Subscriptions/Tariffs) не загружались:
   store-модули импортировали lib/utils без расширения .js — Node ESM не резолвит
```

Пояснения к «до/после»:

- BR-449/467: чистые функции `parseMarkerTime`/`resolveChordLabel` — новые модули, поэтому
  их собственные кейсы на старом коде проходят; падают кейсы уровня компонента (поле не
  числовое, ветка `chord` читает `marker.chord`) и `getMarkersToSave` — на старом коде он
  возвращает строку без фильтра.
- BR-451: гонка воспроизводится по-настоящему — тело watcher'а исполняется на фейковом
  `this` с управляемыми промисами; на старом коде поздний ответ голоса 0 перетирает
  состояние голоса 1.
- BR-483: метод вызывается на фейковом canvas-контексте дважды с одним `dt` — на старом
  коде позиции стрелок различаются (разные `Date.now()`), после правки совпадают.

Полный прогон юнит-тестов webvue3: `168 tests, 168 pass, 0 fail` (включая тесты пакета).

## Проверки

```
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
  :karaoke-app:test            → BUILD SUCCESSFUL
  :karaoke-web:test            → BUILD SUCCESSFUL
  ktlintCheck                  → BUILD SUCCESSFUL

cd webvue3 && npm run test:unit → 168 tests, 168 pass, 0 fail
cd webvue3 && npm run lint      → без замечаний
cd webvue3 && npx prettier --check <изменённые файлы> → All matched files use Prettier code style

bash tools/check-knowledge-structure.sh                     → OK (9/9)
python3 tools/lint-knowledge.py --baseline ...              → All documentation passes
python3 tools/check-doc-references.py                       → OK (после добавления report.md)
python3 tools/check-recordhash-consistency.py               → OK
bash tools/check-no-mp4-mentions.sh                         → OK (все упоминания в baseline)
bash tools/check-feature-doc.sh docs/features/*.md          → OK
bash tools/check-subagent-isolation.sh                      → OK (ветка с коммитами)
```

## Документация

- `knowledge/system/frontend/subs-edit-admin-editor.md` — контур сохранения/голоса/Time/chord.
- `knowledge/system/frontend/store-stats.md` — поколение запросов, TTL и инвалидация вкладок.
- `knowledge/system/frontend/store-stem-jobs.md` — смена target, fail-closed при ошибке.
- `knowledge/system/frontend/store-monetization-stores.md` — ошибки тарифов и серверная
  пагинация подписок.
- `knowledge/system/frontend/player-offline-mp4-render.md` — детерминизм офлайн-рендера
  (новый документ).
- `docs/features/webui-state-integrity.md` — per-feature документ (FR-009), секция
  «Известные ловушки» заполнена.

## За владельцем

1. **BR-467 — миграция chord-меток.** Код переживает обе формы (`label`/`chord`), но
   нормализация данных (перенос аккорда в `chord` или наоборот) — изменение БД, решение
   владельца.
2. **BR-458 — сохранение фильтра между запросами.** Сейчас фильтр применяется разово;
   `reload()`/смена страницы отправляют только `page`/`pageSize`, поэтому фильтр теряется
   (поведение не изменилось). Полное решение — хранить фильтр в Vuex (как в других
   таблицах) либо на сервере; это меняет контракт экрана.
3. **BR-459 — «частичный успех» вкладки.** `loadDataForActiveTab` считает вкладку
   загруженной, если успешен хотя бы один из 2–3 endpoint'ов, и взводит TTL на всю
   вкладку (связано с BR-465). Нужен контракт «вкладка загружена целиком» — за владельцем.
4. **BR-461 — `KaraokeDbTable.save()`** логирует SQL-исключения и не пробрасывает их:
   сервер может ответить `200` при фактическом сбое записи. Это бэкенд вне порции
   пакета; фронт теперь показывает ошибку только при реальном отказе HTTP.
5. **BR-483 — визуальный вид DEMO-анимации.** Сделан детерминизм (фаза от времени кадра);
   если владельцу нужен другой характер анимации (частота/амплитуда) — решение за ним.
