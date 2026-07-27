# Contract: UI окна результатов поиска текста песни (SearchText.vue)

Использует уже существующий универсальный компонент `CustomConfirm.vue`
(`webvue3/src/components/Common/CustomConfirm.vue`) — поддерживает
`params.fields[]` с `fldIsSelect: true` + `fldOptions` + `fldValue`,
результат возвращается в `callback(ret)`, где `ret[fldName] = выбранное
значение (см. существующий метод `ok()` компонента). Новый UI-компонент не
требуется — только новые вызовы уже существующего.

## Действие «Искать заново» (новая кнопка в `.st-footer`)

```js
customConfirmParams = {
  header: 'Выполнить поиск заново?',
  body: 'Ранее сохранённые результаты поиска для этой песни будут удалены. Продолжить?',
  fields: [
    {
      fldName: 'engine',
      fldLabel: 'Движок поиска',
      fldIsSelect: true,
      fldOptions: ['YANDEX_SYNC', 'YANDEX_ASYNC', 'SEARXNG', 'FOURGET'],
      fldValue: <текущий движок по умолчанию>, // из настроек, см. ниже
    },
  ],
  callback: (ret) => this.doResearch(ret.engine),
}
```

`doResearch(engine)` вызывает `searchTextForSong` (vuex action) с
`{ forceResearch: true, engine }`, затем перечитывает список
результатов (аналогично текущей логике `mounted()`).

## Действие «Удалить результаты поиска» (новая кнопка в `.st-footer`)

```js
customConfirmParams = {
  header: 'Удалить результаты поиска?',
  body: 'Все сохранённые результаты поиска текста для этой песни будут удалены. Продолжить?',
  callback: () => this.doDeleteResults(),
}
```

`doDeleteResults()` вызывает новый vuex action `deleteSearchResults(songId)`
→ `POST /api/song/deletesearchresults`, затем очищает локальное состояние
компонента (`searchResultsList = []`, `currentSearchAsync = undefined`) —
без запуска нового поиска (FR-008/FR-009 `spec.md`).

## Источник значения движка по умолчанию для `fldValue`

Значение по умолчанию для селектора берётся из уже существующего механизма
настроек (`KaraokeProperties.lyricsSearchEngine`) — способ получить его во
Vue-компоненте (отдельный getter/проп vs. включение в существующий ответ
`getSearchAsyncList`) решается на этапе `/speckit-tasks`/реализации;
контракт фиксирует только то, что значение ДОЛЖНО совпадать с текущим
серверным дефолтом на момент открытия модалки (FR-006 `spec.md`).

## Кнопка на главной странице админки: «Удалить результаты поиска готовых песен»

По образцу уже существующей кнопки «Пересчитать готовность плеера»
(`HomeView.vue`, dispatch `recalcPlayerReadinessPromise` →
`/api/utils/recalcplayerreadiness`) — новая кнопка `.button-action` рядом с
ней, dispatch нового action `deleteSearchResultsForReadySongsPromise` →
`POST /api/utils/deletesearchresultsforreadysongs`. Подтверждение перед
запуском (`CustomConfirm`, простой да/нет, без полей) — операция необратима
и затрагивает потенциально много песен.

## Инвариант — обе кнопки видны независимо от `searchIsDone`

В отличие от сегодняшнего единственного авто-показа `CustomConfirm` только
при отсутствии результатов (`mounted()`), кнопки «Искать заново» и «Удалить
результаты поиска» в `.st-footer` ДОЛЖНЫ быть доступны и когда результаты уже
есть (`searchIsDone === true`), и когда их нет — иначе некому будет запросить
повторный поиск для песни без результатов, кроме автоматического диалога при
открытии.
