# Отчёт по OpenProject #387 — пакет PKG-S3-02 (S3), волна B

**Задача:** аудит #336, пакет `PKG-S3-02`, записи BR-523, BR-524, BR-525 —
тесты и сборочные настройки `karaoke-public`.

**Ветка:** `567-pkg-s3-02-public-tests` (worktree `.worktrees/waveB-public-tests`),
база — master `6cbaad02`.

**Публичное поведение сайта не менялось**: правки касаются только тестовой
инфраструктуры, CI и документации.

---

## 1. BR-523 — нет регрессионного теста на экранирование метки

### Что было

- `karaoke-public/src/composables/__tests__/formatText.test.js` — 9 тестов, все
  про переносы строк и регистр после комментария; ни одного assert'а на
  экранирование (в карточке аудита: grep по `escape|script|&lt;` — 0 совпадений).
- `formatText` в обеих копиях интерполирует метку пользователя в HTML-буфер
  без экранирования:

  ```js
  // karaoke-public/src/composables/useKaraokeEditor.js:490
  const txt = uppercaseFirstLetter((marker.label.split('|')[1] || '').replaceAll('_', ' '))
  result += `<span class="ke-fx-comment">${txt}</span><br>`
  ```

  Та же строка — в админской копии `webvue3/src/components/SongEditor/useReviewModalFormat.js:47`
  (её рендерит `ReviewModal.vue` через `v-html`). Админской копии теста не
  было вообще.
- Следствие (дефект BR-312, S2, OpenProject #377, **волна D**): правка
  `formatText` или регрессия в `markersToSave` → HTML с меткой пользователя
  уходит в `v-html` → XSS повторяется, и **ни один тест не падает**.

### Что стало

| Файл | Что |
| --- | --- |
| `karaoke-public/src/composables/__tests__/formatText-escape.test.js` | новый: 4 проверки XSS-регрессии для публичной копии |
| `webvue3/src/components/SongEditor/__tests__/useReviewModalFormat.test.js` | новый: 4 проверки для админской копии |
| `tools/check-marker-escape-guard.sh` | новый guard «до/после» |
| `tools/fixtures/marker-escape/{vulnerable,fixed}/…` | замороженные снимки `formatText`: уязвимый и исправленный |

Что именно проверяется (по смыслу карточки): в выводе нет сырого `<img`, нет
тега с атрибутом `onerror` (payload не стал живой разметкой), присутствует
экранированная сущность `&lt;img`, и обычный текст не экранируется (нет
двойного экранирования).

> Формулировка карточки «в выводе нет `<img`/`onerror`» реализована как
> «нет **живой разметки**»: после экранирования подстрока `onerror` остаётся
> в виде инертного текста (комментарий должен быть виден пользователю), а
> «убить» её можно только вырезав текст — это уже не экранирование. Проверка
> `!/<[^>]*onerror/` ловит именно опасный случай (тег с атрибутом).

### Почему тест ждёт (skip), а не падает

Сам дефект BR-312 закрывается в **волне D** (OpenProject #377) — он вне
этого пакета. Падающий тест в CI ронял бы master задолго до прихода волны D.
Поэтому в каждом тестовом файле одна точка переключения:

```js
const SKIP_UNTIL_BR312 = 'BR-312 не исправлен (OpenProject #377, волна D)'
```

Тест не «мёртвый»: его состояние под контролем guard'а
`tools/check-marker-escape-guard.sh`, который:

1. гоняет тест против **уязвимого** снимка кода → тест обязан **упасть**
   (иначе он ничего не проверяет);
2. гоняет тест против **исправленного** снимка → тест обязан **пройти**
   (иначе фикс BR-312 не закроет тест);
3. гоняет тест против **настоящего** кода репозитория и сверяет его
   skip-состояние с состоянием кода: код экранирует → тест обязан быть
   включён; код ещё уязвим → тест обязан ждать. Расхождение (волна D закрыла
   дефект, но забыла снять skip) → guard падает.

То есть приход волны D заставит снять skip — иначе CI станет красным.

### Проверка «до/после»

ДО — guard'а и теста не существует:

```
$ bash tools/check-marker-escape-guard.sh
bash: tools/check-marker-escape-guard.sh: No such file or directory
```

ПОСЛЕ:

```
$ bash tools/check-marker-escape-guard.sh
=== Проверка «до/после» для теста экранирования метки (BR-523) ===

OK: тест karaoke-public/src/composables/__tests__/formatText-escape.test.js ловит уязвимый код (exit 1)
OK: тест webvue3/src/components/SongEditor/__tests__/useReviewModalFormat.test.js ловит уязвимый код (exit 1)
OK: тест karaoke-public/src/composables/__tests__/formatText-escape.test.js проходит на исправленном коде (exit 0)
OK: тест webvue3/src/components/SongEditor/__tests__/useReviewModalFormat.test.js проходит на исправленном коде (exit 0)
OK: karaoke-public/src/composables/useKaraokeEditor.js ещё не экранирует метку (дефект BR-312 открыт), тест ... ждёт фикса
OK: webvue3/src/components/SongEditor/useReviewModalFormat.js ещё не экранирует метку (дефект BR-312 открыт), тест ... ждёт фикса

PASS: тест экранирования взведён и соответствует состоянию кода.
```

---

## 2. BR-524 — e2e-проверка контракта по тексту минифицированного бандла

### Что было

`karaoke-public/e2e/public-editor.spec.js` (строки 44–58):

```js
if (body.includes('markertype')) bundle = body // бандль с редактором
...
expect(bundle, 'дедуп по времени (m.time >= …) должен быть в бандле').toMatch(
  /\.time>=\w+&&\w+\.time<=/,
)
expect(bundle, 'защита FIX #018 (time < 0.5) должна быть в бандле').toMatch(/<\s*\.5\s*&&/)
```

Проверка идёт по **тексту минифицированного** кода: смена версии минификатора
ломает тест без дефекта; бандл выбирается как «последний подходящий» (результат
зависит от порядка скриптов в `index.html`).

### Что стало

- Выбор бандла детерминирован: собираются **все** бандлы с `markertype`, берётся
  первый, содержащий строковый литерал `COMMENT| ` (литералы переживают
  минификацию).
- Проверки по минифицированному коду убраны; **поведение**, которое они
  проверяли, перенесено в юнит-тесты (уровень, где оно проверяется устойчиво):
  - `karaoke-public/src/composables/__tests__/syncSpecTags.test.js` — +2 теста:
    «маркер с той же меткой ВНУТРИ окна — дубликат (не добавляем)» и
    «ВНЕ окна — не дубликат (добавляем)» (это и есть дедуп по **временному**
    диапазону, а не по индексному окну — #202);
  - пустой комментарий (#203) уже покрыт `formatText.test.js`.
- Сама проверка бандла помечена тегом `@smoke` и **не блокирует CI**: в CI она
  идёт отдельным шагом с `continue-on-error: true` (второй вариант поправки из
  карточки — «пометить как дымовая и не считать блокирующей»).

### Проверка «до/после»

ДО — регексы по минификату в тесте (цитата из `6cbaad02`):

```
$ git show 6cbaad02:karaoke-public/e2e/public-editor.spec.js | sed -n '49,53p'
    expect(bundle, 'дедуп по времени (m.time >= …) должен быть в бандле').toMatch(
      /\.time>=\w+&&\w+\.time<=/,
    )
    // FIX #018: маркер не встаёт в ноль.
    expect(bundle, 'защита FIX #018 (time < 0.5) должна быть в бандле').toMatch(/<\s*\.5\s*&&/)
```

ПОСЛЕ — в e2e остались только устойчивые к минификации проверки, поведение
#202/FIX #018 проверяется юнит-тестами:

```
$ cd karaoke-public && node --test "src/composables/__tests__/syncSpecTags.test.js"
✔ маркер с той же меткой ВНУТРИ окна — дубликат (не добавляем)
✔ маркер с той же меткой ВНЕ окна — не дубликат (добавляем)
ℹ tests 7  ℹ pass 7  ℹ fail 0
```

---

## 3. BR-525 — e2e не выполнялись в CI + проверка консоли падала на шуме

### Что было

- В `.github/workflows/lint.yml` (единственный workflow репозитория) — **13
  джобов**, ни e2e, ни юнит-тестов `karaoke-public`:

  ```
  $ git show 6cbaad02:.github/workflows/lint.yml | grep -nE "^  [a-z0-9-]+:|node --test"
    44:  backend-lint:
    76:  frontend-lint:
    119:  confirm-escape-guard:
    139:  webvue3-unit-tests:
    158:      - name: node --test
    160:        run: node --test "src/utils/__tests__/*.test.js"
    167:  docs-lint:
    ... (docs-lint, baseline-stats, kdoc-coverage, jsdoc-coverage, ssot-impact,
         knowledge-structure, docker-image-tags-guard, no-jpa-imports-guard,
         no-mp4-mentions-guard)
  ```

- Проверка консоли требовала **нуля** любых ошибок:

  ```js
  expect(errors, 'ошибок при загрузке быть не должно').toHaveLength(0)
  ```

  → падала на 404 фавикона, на недоступный внешний шрифт
  (`fonts.googleapis.com` в `index.html`) и на любой недоступный API.

### Что стало

- **Новый CI-джоб `karaoke-public-e2e`** (16 джобов всего): сборка образа
  `karaoke-public` из `deploy/karaoke-public/Dockerfile` (в CI — публичные
  базовые образы по правилам проекта: `nginx:stable` (R-04) и `node:22-alpine`
  (R-05)), подъём контейнера на 7907 с конфигом
  `karaoke-public/e2e/nginx-e2e.conf` (только статика собранного SPA + заглушка
  `/api/`; боевой конфиг проксирует API на контейнер `karaoke-web`, которого в
  CI нет), установка Chromium, блокирующий прогон (`--grep-invert @smoke`) и
  отдельный дымовой (`--grep @smoke`, `continue-on-error: true`).
- **Проверка консоли сужена** до сетевых/ресурсных ошибок: `Failed to load
  resource`, `net::ERR_*`, favicon, внешние шрифты и HTTP-статусы от XHR
  (`xhr.statusText` в `services/api.js` — когда API недоступен). Необработанные
  исключения JS (`pageerror`) по-прежнему **роняют** тест.

### Проверка «до/после»

ДО — любая ошибка консоли роняет тест (реконструкция старой проверки против
окружения «favicon 404 + API 502»):

```
=== СТАРАЯ ПРОВЕРКА: собрано ошибок = 5 ===
   * Failed to load resource: the server responded with a status of 502 (Bad Gateway)
   * Failed to load resource: the server responded with a status of 502 (Bad Gateway)
   * Failed to load resource: the server responded with a status of 502 (Bad Gateway)
   * Failed to load resource: the server responded with a status of 502 (Bad Gateway)
   * Bad Gateway
✘  1 [chromium] › ... 1 failed
```

ПОСЛЕ — то же окружение проходит:

```
$ E2E_BASE_URL=http://localhost:7917 npx playwright test --grep-invert @smoke
✓  1 [chromium] › e2e/public-editor.spec.js:56:3 › публичный сайт › отдаётся и грузится без ошибок в консоли (1.0s)
  1 passed (1.7s)
```

И — фильтр **не ослепляет** guard: подсунутая реальная ошибка JS по-прежнему
роняет тест:

```
$ E2E_BASE_URL=http://localhost:7917 npx playwright test app-error   # временный диагностический файл
=== DIAG appErrors=0 appPageErrors=1 ===
✘  1 [chromium] › ... Received array:  ["BOOM: реальная ошибка приложения"]
  1 failed
```

Локальный стенд (не контейнер): собранный `karaoke-public/dist` отдавался
node-сервером с тем же поведением, что и `e2e/nginx-e2e.conf` (SPA-fallback,
заглушка `/api/`, 404 на `/minio/`).

---

## 4. Сопутствующие правки (по требованиям задачи)

| Что | Было | Стало |
| --- | --- | --- |
| Скрипт юнит-тестов `karaoke-public` | не было (`npm error Missing script: "test:unit"`) | `npm run test:unit` → `node --test "src/**/__tests__/*.test.js"` |
| Глоб юнит-тестов `webvue3` | `src/utils/__tests__/*.test.js` — 8 composables-тестов **не выполнялись** | `src/**/__tests__/*.test.js` (39 → 47 тестов) |
| CI-джоб юнит-тестов `karaoke-public` | не было | `karaoke-public-unit-tests` (Node 22, `npm ci` + `npm run test:unit`) |
| Pre-commit-хуки | юнит-тесты `webvue3` | + `karaoke-public-unit-tests`, + `marker-escape-guard` (всего 23 активных хука) |
| `playwright.config.js` (строки 29–35) | ветки `process.env.CI` были мёртвыми (e2e в CI не запускались); `retries: process.env.CI ? 1 : 0` маскировал нестабильность (аудит P-50-03) | ветки стали рабочими (появился CI-джоб e2e: запрет `test.only`, репортер GitHub); ретраи убраны → `retries: 0` (прогон детерминирован) |
| `knowledge/` | тестовая инфраструктура публичного SPA не была описана | новый `knowledge/system/frontend/karaoke-public-tests.md`; актуализированы `knowledge/system/infra/ci-tools.md`, `knowledge/system/infra/pre-commit-config.md`, `knowledge/domains/README.md`, `knowledge/system/README.md` |
| `.gitignore` | артефакты прогонов Playwright попадали в `git status` | `playwright-report/`, `.artifacts/` |

---

## 5. Результаты проверок

| Проверка | Результат |
| --- | --- |
| `cd karaoke-public && npm run lint` | PASS (0 ошибок) |
| `cd karaoke-public && npm run build` | PASS (`✓ built in 3.97s`) |
| `cd karaoke-public && npm run format:check` | PASS (`All matched files use Prettier code style!`) |
| `cd karaoke-public && npm run test:unit` | PASS — **28 тестов**, 0 fail (было 26 и ни одного запуска в CI) |
| `cd webvue3 && npm run lint:check` | PASS |
| `cd webvue3 && npm run format:check` | PASS |
| `cd webvue3 && npm run test:unit` | PASS — **47 тестов**, 0 fail (было 39) |
| `bash tools/check-marker-escape-guard.sh` | PASS |
| `bash tools/check-knowledge-structure.sh` | PASS (9/9) |
| `bash tools/check-knowledge-cross-links.sh` | PASS (685 ссылок) |
| `python3 tools/check-doc-references.py` | PASS (4108 ссылок, 2254 пути, 306 эндпоинтов) |
| `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | PASS |
| `bash tools/check-subagent-isolation.sh` | PASS |
| `bash tools/check-container-restart.sh --quiet` | PASS |
| `bash tools/check-docker-image-tags.sh --quiet` | PASS |
| `bash tools/check-frontend-build.sh --quiet` | PASS |
| `bash tools/check-gradle-user-home.sh --quiet` / `check-docker-config.sh --quiet` | PASS |
| `pre-commit run --all-files` | PASS — 23/23 хука (ktlint, eslint, prettier, lychee, guard'ы, юнит-тесты обоих SPA, marker escape guard) |

---

## 6. Что осталось за владельцем

1. **Волна D (OpenProject #377, BR-312)** — снять `SKIP_UNTIL_BR312` в двух
   тестовых файлах вместе с фиксом экранирования. Если фикс придёт без снятия
   skip, guard `tools/check-marker-escape-guard.sh` упадёт в CI и напомнит об
   этом (это намеренный «forcing function»).
2. **Первый прогон нового e2e-джоба в GitHub Actions** — джоб собирает образ
   `karaoke-public` в раннере; локально (без контейнеров) проверен только
   прогон Playwright против собранного SPA на локальном стенде с тем же
   поведением, что и `e2e/nginx-e2e.conf`. Контейнеры и прод не трогались
   (запрет на запуск контейнеров без согласия владельца).
3. **Решение по `retries: 0`** — сделано намеренно (ретраи маскировали
   нестабильность, аудит P-50-03). Если появятся действительно нестабильные
   e2e-тесты, ретраи обсуждаются отдельно.
4. **PR** — не создавался (запрет на push/PR в этой задаче); ветка готова к
   PR в master.

---

## 7. Коммиты

| Хэш | Содержание |
| --- | --- |
| `25994da2` | реализация: BR-523/524/525, скрипты `test:unit`, CI-джобы, guard, knowledge |
| tip ветки | этот отчёт (`specs/387-pkg-s3-02-public-tests/report.md`) — второй коммит ветки, точный хэш в комментарии трекера |

Ветка: `567-pkg-s3-02-public-tests`. Push/PR не выполнялись; master не тронут.
