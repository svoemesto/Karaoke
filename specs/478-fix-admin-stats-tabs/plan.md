# Implementation Plan: Починить статистику в админке — все вкладки, кроме KPI, пустые (Issue #184)

**Spec**: [spec.md](./spec.md) | **Branch**: `478-fix-admin-stats-tabs` | **Date**: 2026-09-27

## Summary

Восстановить ленивую загрузку вкладок админ-раздела «Статистика»: `BTabs` в
`bootstrap-vue-next` отдаёт в `v-model` id панели (строку), а код спеки 362
ожидал числовой индекс → `NaN` проходил защиту → endpoint'ы не грузились, а в
кеш `lastLoadedAt` попадал ключ `NaN`, глушивший TTL-сторожем все следующие
переключения. Дополнительно: фронтовый таймаут запроса, независимая загрузка
endpoint'ов вкладки и бюджет времени на внешние GeoIP-резолвы (иначе
«География» висела 25+ секунд).

## Technical Context

- **Frontend**: `webvue3` (Vue 3 + Vuex + bootstrap-vue-next 0.40.9), Vite build.
- **Backend**: `karaoke-app` (Kotlin, сырой JDBC), `GeoIpService` (внешний
  `https://api.country.is/`).
- **Тесты**: CI-тестов нет (Constitution), приёмка — воспроизводимая локальная
  проверка `webvue3/scripts/check-stats-tabs.mjs` (Playwright).
- **Ограничения**: `GRADLE_USER_HOME`, frontend-сборка только из `webvue3/`
  (AGENTS.md Tier-1); `karaoke-app` на nsa-i9 не перезапускается без согласия.

## Constitution Check

| Принцип | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн | PASS | Изменений пайплайна нет. |
| II. Сырой JDBC | PASS | SQL-слой не менялся (только выборка IP и лимиты резолва). |
| III. SyncRegistry | PASS | Sync не затронут. |
| IV. Async-очередь | PASS | Не затронута. |
| V. Двух-фронтенд | PASS | Правки только в `webvue3`; `karaoke-public` не тронут. |
| VI. Code Standards (FR-006/007/009) | PASS | `npm run lint:check` + `format:check` + build — чисто; per-feature документ `archive/docs/features/stats.md` обновлён. |
| VII. Cross-Machine Setup | PASS | Локальные конфиги не коммитятся; временный `vite.probe.config.mjs` удалён. |
| VIII. Секреты и git-гигиена | PASS | Секретов нет; `git ls-files` проверяется в pre-commit. |
| IX. Knowledge-first | PASS | Pre-flight пройден (`tools/spec-knowledge-preflight.sh`, 9 ссылок); `knowledge/system/frontend/store-stats.md` + `knowledge/domains/stats/domain.md` обновлены. |

## Проектные решения

1. **Контракт вкладок — числовой индекс.** `<BTabs v-model:index="activeTab">`
   (в bootstrap-vue-next это `index` + `update:index`), каждая `<BTab>` получает
   явный `id="stats-tab-N"`. `resolveTabIndex()` понимает число, id панели и
   числовую строку — смена соглашения библиотеки больше не ломает загрузку.
   Отвергнуто: `parseInt(...) || 0` (молча подменяет вкладку; именно это скрыло
   регресс) и хардкод авто-сгенерированных id (`BootstrapVueNext__ID__v-N__…`
   нестабильны между версиями).
2. **TTL взводится только при успехе.** Действия store возвращают
   `Promise<Boolean>`; `loadDataForActiveTab` ждёт `allSettled` и пишет
   `setLastLoadedAt` лишь если что-то реально загрузилось (FR-004).
3. **«Обновить» игнорирует TTL** — параметр `force: true` у
   `loadDataForActiveTab` (кнопка в toolbar).
4. **Таймаут 15s в `getJson`.** XHR не отменяется (в `promisedXMLHttpRequest`
   нет abort), но UI выходит из состояния загрузки — FR-006/FR-007.
5. **Композитные загрузки независимы** (`Promise.allSettled` вместо
   `Promise.all`): таймаут `countries` не стирает `referrers`; таймаут одного из
   `by-type/channels/by-detail` не обнуляет остальные.
6. **Дедупликация dispatch'ей**: «Разбивки» (3 endpoint'а, один action) шлют
   3 запроса вместо 9 — наблюдение при разборе #184.
7. **Backend: бюджет времени GeoIP** `GeoIpService.resolveMany(..., timeBudgetMs)`;
   call-sites — `/api/stats/countries` (3 с) и страница `/api/webevents` (2 с).
   Не поместившиеся IP возвращаются как «Не определено» и домерджатся в
   следующих вызовах (кэш уже ведёт себя так по `maxFetch`).
   Отвергнуто: параллелизация резолвов (внешний сервис + троттлинг, отдельная
   задача) и простое уменьшение `maxFetch` (не ограничивает худший случай по
   времени из-за пауз и сетевых таймаутов).
8. **Приёмка коммитится в репозиторий** — `webvue3/scripts/check-stats-tabs.mjs`
   (Playwright уже devDependency webvue3). Проверка дискриминирующая: на старом
   образе `karaoke-webvue3` падает (7 из 8 вкладок без запросов), на новом — 0.

## Структура изменений

```
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/StatBySong.kt                 # timeBudgetMs у двух call-site GeoIP
└── services/GeoIpService.kt            # бюджет времени на внешние резолвы
webvue3/
├── src/views/StatsView.vue             # v-model:index, resolveTabIndex, force, дедуп, TTL-on-success
├── src/components/Stats/store.js       # Promise<Boolean>, таймаут, allSettled
└── scripts/check-stats-tabs.mjs        # НОВОЕ: воспроизводимая приёмка вкладок
specs/478-fix-admin-stats-tabs/         # spec/plan/tasks/report/checklists
archive/docs/features/stats.md          # per-feature документ (FR-009)
knowledge/…/store-stats.md, stats/domain.md  # SSoT
```

## Проверка (как убедиться)

1. `cd webvue3 && node scripts/check-stats-tabs.mjs <BASE_URL>` — 0 провалов.
2. До фикса та же команда на `http://localhost:7906` (старый образ) — провал
   по 7 вкладкам (см. `report.md`).
3. `npm run lint:check`, `npm run format:check`, `npm run build` в `webvue3`;
   `./gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck` + bootJar.

## Complexity Tracking

Нарушений нет: изменений архитектуры нет, новые зависимости не добавлялись
(Playwright уже в devDependencies `webvue3`), объём правок — точечный.
