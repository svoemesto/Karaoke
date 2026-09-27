# Report: Spec 478 — Статистика в админке: все вкладки, кроме KPI, пустые (Issue #184)

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Tasks**: [tasks.md](./tasks.md)
**Branch**: `478-fix-admin-stats-tabs`
**Date**: 2026-09-27
**Issue**: OpenProject [#184](http://localhost:8080/work_packages/184) «Статистика в админке»

## Краткое описание

Регресс, внесённый спекой
[`362-fix-stats-view-element-not-found`](../362-fix-stats-view-element-not-found/spec.md)
(фикс #79, 2026-09-10): `bootstrap-vue-next` отдаёт в `BTabs v-model` **id
панели** (строку `BootstrapVueNext__ID__v-10__tabpane___`), а код ожидал
числовой индекс. Цепочка отказа:

1. `parseInt("BootstrapVueNext__ID__v-10__tabpane___")` → `NaN`;
2. `loadDataForActiveTab(NaN)` проходил проверку валидности
   (`typeof NaN === 'number'`, а `NaN < 0` / `NaN > 7` — ложны);
3. `tabEndpoints[NaN]` → `undefined` → **ни одного HTTP-запроса**, зато в
   Vuex-кеш писался ключ `lastLoadedAt["NaN"]`;
4. со второго переключения TTL-сторож (60 с) считал вкладку «свежей» и глушил
   загрузку: в консоли `[Stats] TTL hit, skipping load {tab: NaN, age: 3}`;
5. `mounted()` использовал `parseInt(...) || 0`, поэтому данные грузила только
   вкладка 0 (KPI) — ровно симптом #184.

## Изменения (7 файлов, +382/−117)

| Файл | Что |
|---|---|
| `webvue3/src/views/StatsView.vue` | `BTabs v-model:index` + явные `id="stats-tab-N"`; `resolveTabIndex()` (число / id панели / числовая строка); строгая проверка индекса; дедупликация dispatch'ей; `force` для «Обновить»; TTL взводится только при успехе |
| `webvue3/src/components/Stats/store.js` | действия вкладок возвращают `Promise<Boolean>`; `getJson(url, timeoutMs = 15_000)` + `withTimeout`; `loadStatsBreakdown` / `loadStatsGeo` — `Promise.allSettled` с независимой обработкой |
| `webvue3/scripts/check-stats-tabs.mjs` | **новое**: воспроизводимая приёмка — обход 8 вкладок, проверка запросов и выхода из загрузки |
| `karaoke-app/…/services/GeoIpService.kt` | `resolveMany(..., timeBudgetMs)` — бюджет времени на внешние GeoIP-резолвы |
| `karaoke-app/…/model/StatBySong.kt` | бюджет 3 с для `/api/stats/countries`, 2 с для страницы `/api/webevents` |
| `archive/docs/features/stats.md` | per-feature документ (FR-009) |
| `knowledge/system/frontend/store-stats.md`, `knowledge/domains/stats/domain.md` | SSoT: контракт загрузки вкладок, changelog |

## Приёмка (фактический вывод)

### 1. Дискриминирующая проверка: старый образ vs исправленный код

Один и тот же скрипт, два стенда. Старый — контейнер `karaoke-webvue3`
(образ от 2026-09-26, без фикса); новый — dev-сервер Vite с прокси на
`karaoke-app:8898` (исправленный код).

**Старый образ (`http://localhost:7906`), exit code 1:**

```
[0] KPI: OK; запросы: /api/stats/summary
[1] Монетизация: FAIL (нет запросов: /api/stats/monetization)
[2] Динамика: FAIL (нет запросов: /api/stats/timeseries)
[3] Разбивки: FAIL (нет запросов: /api/stats/by-type, /api/stats/channels, /api/stats/by-detail)
[4] География: FAIL (нет запросов: /api/stats/referrers)
[5] Пользователи: FAIL (нет запросов: /api/stats/top-users)
[6] Слушают: FAIL (нет запросов: /api/stats/top-listened)
[7] События: FAIL (нет запросов: /api/stats/by-song, /api/webevents)
```

**Исправленный код (`http://localhost:5199`), exit code 0:**

```
[0] KPI: OK; запросы: /api/stats/summary; спиннеров в активной панели: 0
[1] Монетизация: OK; запросы: /api/stats/monetization; спиннеров в активной панели: 0
[2] Динамика: OK; запросы: /api/stats/timeseries; спиннеров в активной панели: 0
[3] Разбивки: OK; запросы: /api/stats/by-type, /api/stats/channels, /api/stats/by-detail; спиннеров: 0
[4] География: OK; запросы: /api/stats/referrers; спиннеров в активной панели: 0
[5] Пользователи: OK; запросы: /api/stats/top-users; спиннеров в активной панели: 0
[6] Слушают: OK; запросы: /api/stats/top-listened; спиннеров в активной панели: 0
[7] События: OK; запросы: /api/stats/by-song, /api/webevents; спиннеров в активной панели: 0
```

### 2. Фактическое содержимое вкладок (Playwright, dev-сервер)

- **KPI**: 25 908 событий, 2 475 уникальных посетителей, 1 054 за сегодня;
- **Монетизация**: 8 894,5 ₽ выручки, 12 подписок на песню, 19 на сайт, 10 активных премиумов;
- **Динамика**: временной ряд 2026-09-20…2026-09-27 (6 000 → 1 054);
- **Разбивки**: UI 54.2 %, плеер 25.9 %, время 17.8 %; детализация — 18 категорий;
- **География**: referrers загружены (yandex.ru 31, google.com 5, …);
  `countries` при холодном GeoIP-кэше не ответил за 15 с → вкладка вышла из
  загрузки с «Нет данных» (без вечного спиннера), при прогретом кэше данные есть;
- **Пользователи**: 2 628 символов таблицы (8 пользователей);
- **Слушают**: 4 558 символов (топ дослушанных песен);
- **События**: 19 719 символов (топ песен + лог последних событий).

До фикса те же вкладки показывали 45–456 символов («Нет данных», «Всего: 0»).

### 3. Точечные проверки

- **Кнопка «Обновить» игнорирует TTL** (FR-004): на только что загруженной
  вкладке «География» клик дал `200 /api/stats/countries` + `200 /api/stats/referrers`.
- **Нет ключей `NaN` в кеше**: в консоли вместо `normalized: NaN` —
  `normalized: 1..7`; предупреждений `TTL hit, skipping load {tab: NaN}` нет.
- **Дедупликация**: вкладка «Разбивки» шлёт 3 запроса (было 9).
- **Нет `pageerror`**; единственные warning'и — ожидаемый таймаут
  `/api/stats/countries` при холодном GeoIP-кэше.

### 4. Линтеры и сборки

| Проверка | Результат |
|---|---|
| `webvue3: npm run lint:check` | OK (0 warnings) |
| `webvue3: npm run format:check` | OK (Prettier clean) |
| `webvue3: npm run build` | OK (`✓ built in 8.03s`) |
| `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` | BUILD SUCCESSFUL |
| `./gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck` | BUILD SUCCESSFUL |

## Что НЕ сделано (out of scope / требует владельца)

- **Образ `karaoke-webvue3` не пересобран и контейнер не перезапущен**: на
  локальном стенде (localhost:7906) до сих пор работает старый бандл. Нужно
  `deploy/do.sh build_webvue3` + `start_webvue3` (по согласию; правки — в
  ветке, не в master).
- **`karaoke-app` не перезапущен** (на nsa-i9 запрещено без согласия) — бюджет
  времени GeoIP вступит в силу после пересборки/перезапуска; приёмка
  «География» выше это учитывает (фронтовый таймаут делает вкладку
  отзывчивой независимо от backend'а).
- **Прод-деплой** — вне scope.
- **Параллелизация GeoIP-резолвов** (вместо бюджета времени) — отдельная
  задача, если после замера кэш всё ещё будет холодным слишком долго.
- **CI-проверка** `check-stats-tabs.mjs` в pipeline не заведена (в проекте нет
  CI-тестов); проверка запускается вручную перед merge/релизом.

## Следующие шаги (governance)

1. `git push -u origin 478-fix-admin-stats-tabs`.
2. `gh pr create --base master --title "fix #184: вкладки «Статистики» снова грузят данные"`.
3. `gh pr checks` — дождаться CI 7/7 PASS.
4. `gh pr merge --merge` (без `--delete-branch`).
5. `bash tools/tracker.sh add-comment 184 --file specs/478-fix-admin-stats-tabs/report.md`.
6. `bash tools/tracker.sh mark-review 184`.
7. Закрытие — после ревью владельцем.
