# Vuex store: StemJobs

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/StemJobs/store.js` — премиум
> фича «Создать минусовку» (админ-панель).

## Файл

`webvue3/src/components/StemJobs/store.js` (86 строк)

## State

```javascript
state: {
    stemJobs: [],
    stemJobsIsLoading: false,
    stemJobsTarget: 'remote',   // default
}
```

## Назначение

Премиум-фича «Создать минусовку из аудиофайла» — **админ-панель**
(управление заданиями пользователей публичного сайта).

**`stemJobsTarget`** (`'local'|'remote'`):
- `'remote'` (default) — реальные задания создают пользователи на
  **прод-сайте** (karaoke-web).
- `'local'` — для локальной отладки очереди (тот же паттерн, что
  `siteUsersTarget`/`chatTarget`).

## Hot paths

- **`/api/stemjobs/list`** — список.
- **`/api/stemjobs/{id}/delete`** — удалить.
- **`/api/stemjobs/{id}/stop`** — остановить.

## Связь

## Смена target и ошибки загрузки (пакет PKG-S2-18)

Аудит #336, BR-462. До правки при смене `stemJobsTarget` список не очищался, а
`catch` загрузки только логировал — таблица продолжала показывать задания **другой**
БД под новой меткой. «Остановить»/«Удалить» отправляли `target` текущего селектора и
`id` устаревшей строки: в прод-БД это удаление файлов чужого задания из хранилища.

Теперь:

- `stemJobsLoadedTarget` хранит, какой БД соответствует показанный список;
- `setStemJobsTarget` сразу очищает список, `loadedTarget` и ошибку;
- `loadStemJobs` сверяет `target` на момент ответа и при несовпадении ответ
  отбрасывает; при ошибке список очищается, выставляется `stemJobsError`
  (fail-closed — кликать нечего);
- `stopStemJob`/`deleteStemJob` имеют `.catch` и возвращают `false`, а ошибка видна
  баннером в `StemJobsTable.vue`.

### Известные ловушки

- Очистка списка при ошибке — намеренная: пустая таблица безопаснее строк чужой БД,
  по которым можно нажать «Удалить». Не «оптимизируйте» это кешированием.
- `loadedTarget` — не то же самое, что `stemJobsTarget`: первый отвечает на вопрос
  «откуда данные», второй — «куда уйдут действия».

- **StemJob** ([remaining-models.md#stemjob](../../domains/catalog/components/remaining-models.md#stemjob)) —
  entity.
- **StemJobPollScheduler** ([schedulers.md](../../domains/processing/components/schedulers.md)) —
  обработка.
- **Monetization** ([monetization domain](../../domains/monetization/domain.md)) —
  premium-фича.

## Changelog

- **Pass 386** (2026-09-09): Initial. Автор: agent (Karaoke).