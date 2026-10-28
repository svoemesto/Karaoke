# Report — плагин «Forgejo CI» (KARAOKE-305)

## Phase 1+2 (каркас и чистая логика) — сделано

- Пакет: `package.json` (bundle + client face, ноль зависимостей),
  `cordis.patch.yml` без секретов, локали ru/en, иконка, `PROVENANCE.md`,
  README с «Известными ограничениями».
- Логика: `lib/config.js`, `lib/runs.js`, `lib/buffer.js`, `lib/snapshot.js`,
  `lib/forgejo.js`, копия контракта `lib/state.schema.json` (источник правды —
  `specs/670-forgejo-ci-plugin/contracts/state.schema.json`).
- Тесты: `node --test` — **52**, pass 52, fail 0.
- Исправлен дефект ревью: устаревшие `running`-задачи завершённого прогона
  больше не поднимают `runner.state` и не заполняют `queue` (регресс + зеркало
  + граница `waiting` без `run_started_at`). `actions/tasks` стал опциональным,
  токен резолвится один раз на опрос.

## US1 (host-половина + первая панель) — сделано, ждёт установки

- `index.js`: опрос раз в 15 с через `ctx.effect`, кеш снимка, маршрут
  `GET /forgejo-ci/state` (`405` на прочие методы, `no-store`), деградация в
  `stale` без исключений, токен — `credentials.resolve(tokenRef)` на каждый
  опрос.
- `client.js`: иконка в `sidebar.panellist` (`id: forgejo-ci`, `order: 100`) с
  точкой при `failure` и кольцом при `running`; страница в `main`
  (`key: forgejo-ci`) с таблицей прогонов, строкой раннера и строкой живой
  очереди; общий опрос на оба компонента, `AbortController` и снятие таймера
  при уходе последнего подписчика.

### Прогон host-половины на реальном Forgejo (без установки в профиль)

Прогон через локальную обвязку: настоящий `apply(ctx, {})`, подставной `ctx`
(`webServer.register`, `credentials.resolve`, `effect`), настоящий API Forgejo,
маршрут поднят на `127.0.0.1:3099`. Токен читался из хранилища секретов и в
вывод не попадал.

```
HTTP 200, bytes: 633
{
  "schemaVersion": 1,
  "stale": false,
  "error": null,
  "lastOkAt": "2026-10-28T07:05:23.094Z",
  "runner": { "name": "nsa-i9", "version": "v6.4.0", "state": "idle",
              "lastActivityAt": "2026-10-28T07:01:35.000Z",
              "queue": { "running": 0, "waiting": 0 } },
  "runs": [ { "id": 399, "number": 33, "ref": "master", "event": "", "status": "failure",
              "durationSec": 643, "fromCache": false } ],
  "jobs": null
}
POST -> 405 {"error":"method-not-allowed"}
```

Что это доказывает: снимок собирается из живого API, `duration` переведён в
секунды (643), ссылка строится из `index_in_repo` (33), устаревшие `running`
задачи не делают раннер работающим (`state: idle`, `queue: 0/0`, `jobs: null`),
маршрут отвечает и отвергает не-GET.

### Регистрации клиента (проверка без браузера)

Подставной `window.__ModuleLoader__` + подставные слоты:

```
injections: sidebar.panellist, main
locale регистрации: [{"ns":"forgejo-ci","locales":["ru","en"]}]
register: {"name":"sidebar.panellist","id":"forgejo-ci","order":100}
register: {"name":"main","key":"forgejo-ci"}
```

## Что осталось до полной панели

- **Установка в профиль (T039–T041, делает лид)** — без неё панель в живом GUI
  не проверена; `curl` в GUI и загрузка `client.js` браузером — следующий шаг.
- US2: `lib/tools.js` + регистрация `forgejo_ci_status` / `forgejo_ci_wait`.
- US3: toast в `shell.overlay` (детекция перехода, дедуп, гейт FR-013).
- US4: экраны деградации в панели (`stale`-полоса уже есть; нужны «токен не
  настроен» / «нет доступа» / «версия схемы»), сценарии проверки.
- T043/T045 Karaoke: запись в `knowledge/system/infra/ci-tools.md`, трекер.

## Известные ограничения (переносятся в README)

- История прогонов живёт в памяти процесса: после перезапуска DSH пуста
  (Forgejo хранит историю недолго).
- Разбивки «прогон → джобы» нет; строка «сейчас бегут» — только последний
  активный прогон и только когда `actions/tasks` отвечает.
- Если Forgejo не отдал `created`/`updated`, подставляется время наблюдения.

## US3 + US4 (уведомления и деградация) — сделано, ждёт перезагрузки страницы

- **US3**: слой toast'ов в `shell.overlay` (`id: forgejo-ci-toasts`, `order: 100`).
  Переход `waiting|running → терминальный` между двумя опросами; дедуп по
  `run.id`; гейт FR-013 (первый снимок после загрузки только запоминает, что уже
  завершено); падение — заметное (8 с, акцентная полоса), успех — тихое (3 с,
  приглушённое); не больше 3 на экране и один в 5 с; `pointer-events: auto` на
  своих элементах (слой click-through); клик → `html_url` в новой вкладке;
  крестик закрывает; все таймеры снимаются `dispose()` через `ctx.effect`.
- **US4**: плашка при неизвестном `schemaVersion`; жёсткие ошибки
  (`token-missing`, `forbidden`) заменяют панель текстом, мягкие (`network`,
  `http-4xx`, `http-5xx`) идут полосой над таблицей — снимок не пустеет;
  пустое состояние «прогонов пока нет».
- Тесты: `test/toast.test.mjs` — 11 проверок в песочнице браузерного модуля
  (подставные React, таймеры, `Date`, `fetch`): гейт FR-013, дедуп, потолки,
  авто-скрытие, `dispose`, разметка toast'а со ссылкой и `aria-label`.
- `node --test → exit=0, 84 pass`.

Слоты клиента после US3 (проверка песочницей): `sidebar.panellist`
(`id: forgejo-ci`, 100), `main` (`key: forgejo-ci`), `shell.overlay`
(`id: forgejo-ci-toasts`, 100).
