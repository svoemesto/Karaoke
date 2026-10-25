# Component: karaoke-public-tests (тестовая инфраструктура публичного SPA)

> **Домен**: system (infrastructure)
> **Компонента**: тесты `karaoke-public` — юнит-тесты (`node --test`), e2e
> (Playwright) и CI-джобы, которые их запускают.

## Назначение

Публичный SPA (`karaoke-public`) — единственный фронт, который виден
посетителям сайта. До OpenProject #387 его тесты **не выполнялись ни в одном
CI-джобе**: регрессия (например, тихая поломка `formatText` или рассинхрон
админской и публичной копий рендера) проходила молча и всплывала на проде.

Компонента описывает три слоя:

1. юнит-тесты на встроенном раннере Node (`node --test`) — без браузера;
2. e2e Playwright — проверка того, что собранный и отдаваемый сайт работает;
3. CI-джобы, которые запускают и то, и другое.

## Юнит-тесты (node --test)

Скрипт запуска: `karaoke-public/package.json` → `npm run test:unit`
(глоб `src/**/__tests__/*.test.js`, встроенный раннер Node, без зависимостей).

Файлы тестов (6):

| Файл | Что фиксирует |
| --- | --- |
| `karaoke-public/src/composables/__tests__/formatText.test.js` | канон рендера #203 (пустой комментарий, регистр, переносы) |
| `karaoke-public/src/composables/__tests__/formatText-escape.test.js` | BR-523: экранирование метки комментария (XSS-регрессия) |
| `karaoke-public/src/composables/__tests__/markersToSave.test.js` | контракт сериализации маркеров (поле `lockLad`) |
| `karaoke-public/src/composables/__tests__/marker-fields-roundtrip.test.js` | round-trip имён полей маркера |
| `karaoke-public/src/composables/__tests__/syncSpecTags.test.js` | #202 (дедуп по временному диапазону) и FIX #018 (маркер не в ноль) |
| `karaoke-public/src/utils/__tests__/dateFormat.test.js` | формат даты и TZ-инвариантность |

Запуск в CI: джоб `karaoke-public-unit-tests` в `.github/workflows/lint.yml`
(Node 22, `npm ci` + `npm run test:unit`). Локальный pre-commit-хук
`karaoke-public-unit-tests` в `.pre-commit-config.yaml` гоняет тот же скрипт.

Админский SPA (`webvue3`) имеет зеркальный набор: скрипт `npm run test:unit`
охватывает все каталоги `__tests__` (`src/**/__tests__/*.test.js`), поэтому
в прогон попадают и тесты composables, и регресс-тест экранирования админской
копии `formatText`.

## Регрессионный тест на экранирование метки (BR-523)

Дефект BR-312 (S2, OpenProject #377, волна D): метка пользователя
(`COMMENT|<img src=x onerror=alert(1)>`) интерполируется в HTML-буфер без
экранирования, а результат уходит в `v-html`. Тест на этот дефект заведён
здесь, но **ждёт фикса**:

- обе копии `formatText` — `karaoke-public/src/composables/useKaraokeEditor.js`
  и `webvue3/src/components/SongEditor/useReviewModalFormat.js` — помечены
  константой `SKIP_UNTIL_BR312` (см. тестовые файлы);
- пока дефект открыт, тест не падает (иначе CI ронял бы master задолго до
  волны D), но и не «мёртвый» — его состояние под контролем guard'а
  `tools/check-marker-escape-guard.sh` (фикстуры «до/после» —
  `tools/fixtures/marker-escape/`).

Guard проверяет три вещи:

1. тест **взведён** — падает на уязвимом снимке кода и проходит на
   исправленном (фикстуры `vulnerable/` и `fixed/`);
2. ожидание теста **верно** — исправленный снимок экранирует метку;
3. состояние **соответствует коду** — если код уже экранирует, тест обязан быть
   включён (skip снят); если код ещё уязвим — тест обязан ждать. Расхождение
   (например, волна D закрыла дефект, но забыла снять skip) роняет guard.

Точка переключения — одна строка в каждом тестовом файле:
`SKIP_UNTIL_BR312` (`false` — тест включён).

## E2E (Playwright)

Конфигурация: `karaoke-public/playwright.config.js`. Тесты —
`karaoke-public/e2e/public-editor.spec.js`.

| Проверка | Смысл | Блокирует CI |
| --- | --- | --- |
| «отдаётся и грузится без ошибок в консоли» | сайт отдан, SPA смонтирован, в консоли нет ошибок приложения | да |
| «контракт развёртывания: правила рендера редактора в отдаваемом бандле» | код редактора реально попал в отдаваемый бандл | нет (тег `@smoke`) |

Решения, зафиксированные BR-524 и BR-525:

- **BR-524.** Проверка контракта не ищет фрагменты минифицированного кода
  (регексы вида `/\.time>=\w+&&\w+\.time<=/` зависят от версии минификатора и
  падают без дефекта). Поведение, которое они проверяли, перенесено в
  юнит-тесты (`syncSpecTags.test.js` — дедуп по временному диапазону и
  FIX #018; `formatText.test.js` — пустой комментарий). Сама проверка бандла
  помечена тегом `@smoke` и не блокирует CI: в CI она идёт отдельным шагом с
  `continue-on-error: true`.
- **BR-525.** Проверка консоли сужена до сетевых и ресурсных ошибок
  (favicon, внешние шрифты, недоступный API): такие сообщения не являются
  дефектом SPA. Необработанные исключения JS (`pageerror`) по-прежнему
  роняют тест.

Ветки `process.env.CI` в конфигурации (запрет `test.only`, репортер GitHub)
стали рабочими после появления CI-джоба; ретраи в CI убраны (маскировали
нестабильность — аудит #336, P-50-03).

Локальный запуск — против работающего контейнера:

```
cd karaoke-public && npm run test:e2e
```

Адрес переопределяется переменной `E2E_BASE_URL`.

## CI-джоб e2e

Джоб `karaoke-public-e2e` в `.github/workflows/lint.yml`:

1. собирает образ `karaoke-public` из `deploy/karaoke-public/Dockerfile`
   (в CI — с публичных базовых образов `node:22-alpine` и `nginx:stable`;
   правила R-04 и R-05);
2. поднимает контейнер на порту 7907 с конфигом
   `karaoke-public/e2e/nginx-e2e.conf` (только статика собранного SPA плюс
   заглушка `/api/` — боевой конфиг проксирует API на контейнер `karaoke-web`,
   которого в CI нет);
3. ставит Chromium (`npx playwright install --with-deps chromium`);
4. гоняет блокирующие тесты (`--grep-invert @smoke`) и отдельно дымовые
   (`--grep @smoke`, `continue-on-error: true`).

## Контрактные тесты «до/после» (пакет PKG-S3-27)

Аудит #336 (`PKG-S3-27`, OpenProject #399) добавил в публичный SPA 16 файлов
тестов и 279 кейсов. Ключевая особенность — **два уровня проверки**:

1. **Поведенческий** — чистые функции из `karaoke-public/src/utils/` и `karaoke-public/src/player/playerGuards.js`
   проверяются напрямую (с депленными `navigator`/`document`/`Storage`).
2. **Контрактный по исходнику** — тест читает текст `.vue`/`.js`-файла
   (`karaoke-public/src/utils/__tests__/sourceContract.js` -> `readSource`, `readCode`) и
   проверяет, что в нём есть новая конструкция и **нет** старой (buggy-паттерна).

Второй уровень нужен потому, что правка живёт в компоненте, а не в чистой
функции: такой тест компилируется и на **старом** коде и падает на нём
(assertion failed), а не «не находит модуль». Это настоящее доказательство
«до/после».

Проверка «до» выполняется так: изменённые исходники откатываются через
`git stash push -- <файлы>` (тесты и новые модули остаются), после чего
прогоняется `npm run test:unit`. Для `PKG-S3-27` на старом коде падают 57
проверок, после правок — 0 (279/279).

Новые файлы тестов (16):

| Файл | Что фиксирует |
| --- | --- |
| `karaoke-public/src/utils/__tests__/clipboard.test.js` | BR-296: результат копирования проверяется |
| `karaoke-public/src/utils/__tests__/cartToggle.test.js` | BR-297, BR-314: ответ toggle и привязка корзины к токену |
| `karaoke-public/src/utils/__tests__/shareSecret.test.js` | BR-299, BR-301: секрет capability-ссылки и сверка с polling |
| `karaoke-public/src/utils/__tests__/requestGuard.test.js` | BR-300, BR-315, BR-321: поколения запросов |
| `karaoke-public/src/utils/__tests__/loadState.test.js` | BR-318, BR-322, BR-323, BR-334, BR-341, BR-343, BR-346: «пусто» ≠ «ошибка» |
| `karaoke-public/src/utils/__tests__/editorGuards.test.js` | BR-331…BR-338: инварианты редактора заданий |
| `karaoke-public/src/utils/__tests__/routeGuard.test.js` | BR-316, BR-317: guard маршрута и polling новостей |
| `karaoke-public/src/utils/__tests__/streamLifecycle.test.js` | BR-320: жизненный цикл NDJSON-стрима |
| `karaoke-public/src/utils/__tests__/statusMap.test.js` | BR-298, BR-325: гейт премиума и карта статусов |
| `karaoke-public/src/utils/__tests__/pricing.test.js` | BR-326: цены из API, а не из текста страницы |
| `karaoke-public/src/utils/__tests__/searchPagination.test.js` | BR-344, BR-345: восстановление среза и разные флаги ошибок |
| `karaoke-public/src/player/__tests__/playerGuards.test.js` | BR-304…BR-311: инварианты плеера |
| `karaoke-public/src/utils/__tests__/sourceContract.js` | хелпер чтения исходников (не тест) |

## Ограничения и известные TODO

- [ ] Тест экранирования ждёт фикса BR-312 (OpenProject #377, волна D) —
      вместе с ним снимается `SKIP_UNTIL_BR312` в двух тестовых файлах.
- [ ] E2E проверяет главную страницу без бэкенда: поднятие полного стека
      (`karaoke-web` + MinIO) в CI не делалось.
- [ ] `retries: 0` в конфигурации e2e — осознанно (детерминированный прогон);
      при появлении действительно нестабильных тестов ретраи обсуждаются
      отдельно, чтобы не маскировать нестабильность.

## Связь с другими компонентами

- **CI tools** ([infra/ci-tools.md](../infra/ci-tools.md)) — guard'ы в `tools/`.
- **Pre-commit config** ([infra/pre-commit-config.md](../infra/pre-commit-config.md)) —
  локальные хуки и джобы CI.
- **karaoke-public composables** ([karaoke-public-composables.md](karaoke-public-composables.md)) —
  `formatText` и остальные composables публичного SPA.
- **Инварианты публичного SPA** ([karaoke-public-audit-invariants.md](karaoke-public-audit-invariants.md)) —
  правила, которые фиксируют новые тесты.
- **Админский редактор** ([subs-edit-admin-editor.md](subs-edit-admin-editor.md)) —
  вторая копия `formatText` (ReviewModal).

## Changelog

- **OpenProject #399** (2026-10-25, аудит #336, `PKG-S3-27`): добавлены 16
  файлов тестов (279 кейсов) и двухуровневая схема проверки — поведенческая
  (чистые функции) + контрактная по исходнику (падает на старом коде).
- **OpenProject #387** (2026-10-24, аудит #336, BR-523/524/525): юнит-тесты
  публичного SPA получили скрипт `npm run test:unit` и CI-джоб; e2e получили
  CI-джоб с контейнером; проверка контракта бандла стала дымовой; проверка
  консоли сужена до сетевых/ресурсных ошибок; добавлен guard
  `tools/check-marker-escape-guard.sh` с фикстурами «до/после».
