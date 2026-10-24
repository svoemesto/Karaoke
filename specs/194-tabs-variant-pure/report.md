# Отчёт: #194 — запись в состояние из computed

**PR:** #609, ветка `517-fix-tabs-variant-side-effect`. **Severity: S3.**

## Что было

`getStringsForAllNotesInSong()` присваивал `this.indexTabsVariant`, а метод
вызывается из computed `lstIndexesTabsVariant`, используемого в шаблоне.
Реактивное состояние мутировалось в фазе render; computed был нечистым.

## Что сделано

Метод стал чистым, кламп перенесён в watcher по `lstIndexesTabsVariant` с
`immediate: true`. Попутно удалён `console.log('result', result)`.

Зависимости у метода от `indexTabsVariant` не было — поле только записывалось.

## Проверка

- присваиваний `this.X =` внутри `computed`: 0
- присваиваний внутри `getStringsForAllNotesInSong`: 0
- e2e `tabs-variant.spec.js`: блок отрисован, значение в границах списка, ошибок нет
- полный набор 15/15 на живом контейнере

## Методологическая заметка

Первый скан искал присваивания внутри блока `computed` и нашёл ноль — то есть
дефект «исчез». Он был в методе, который computed вызывает. Проверять чистоту
нужно по графу вызовов, а не по синтаксическому блоку.
