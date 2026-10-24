# Отчёт по #360 — Экранирование пользовательских данных в подтверждениях админки (BR-123, спека 558)

> **Спека-контракт**: [`specs/558-escape-song-name-confirm/spec.md`](../558-escape-song-name-confirm/spec.md) ·
> **Реализация**: [`specs/562-impl-xss-escape/`](.) · **Ветка**: `562-impl-xss-escape` ·
> **База**: `master` @ `cf97b82f` · **Дата**: 2026-10-24 ·
> **Аудит**: #336, дефект **BR-123** (severity **S1**).
> **Решение владельца**: гибрид — (а) экранирование при сборке текста подтверждения
> в 43 местах, (б) общий компонент по умолчанию не принимает сырую разметку.

## Что было

Пользовательские данные (имя песни, имя автора, e-mail публично зарегистрированного
пользователя сайта, имя картинки, выделенный фрагмент текста песни, текст ошибки
двуx-БД синхронизации, параметры Smart Copy) попадали в HTML через `v-html` в общем
компоненте подтверждения `webvue3/src/components/Common/CustomConfirm.vue:5–6`:

```html
<div :style="styleHeader" v-html="params.header" />
<div :style="styleBody" v-html="params.body" />
```

Комментарий компонента утверждал, что «message должен быть подготовленным, не
пользовательским вводом» — это было **фактически неверно**: в 43 местах сборки
`customConfirmParams` пользовательские данные подставлялись в разметку как есть.
Администратор открывал подтверждение — и разметка из имени песни (например
`<img src=x onerror=…>`) выполнялась как код в его сессии, в origin админки, откуда
доступны все административные действия без дополнительного подтверждения.

Правило линтера `vue/no-v-html` в проекте выключено (`webvue3/.eslintrc.cjs`) с
неверным обоснованием, поэтому «только правка мест вызова» оставляла ловушку для
новых вызовов.

## Что изменено

| Файл | Что |
|---|---|
| `webvue3/src/components/Common/CustomConfirm.vue` | **Текстовый режим по умолчанию**: `header`/`body` выводятся через `v-text` (`textContent`); сырая разметка — только при явном `params.allowHtml === true` (`v-if`/`v-else`). Комментарий компонента переписан (было неверное утверждение). |
| `webvue3/src/utils/escapeHtml.js` (NEW) | Канонический модуль экранирования (`& < > " '`). |
| `webvue3/src/utils/highlightWords.js` | `escapeHtml` импортируется из нового модуля и реэкспортируется — обратная совместимость сохранена, поведение не изменилось (`SubsEdit.vue` и его тест работают как прежде). |
| `webvue3/src/components/Songs/edit/SongEdit.vue` | **25 мест**: `${this.song.songName}` → `${escapeHtml(this.song.songName)}`. |
| `webvue3/src/views/HomeView.vue` | **9 мест**: имя автора (4 прямых + 1 вложенное в `scope`), слово (`this.dictValue.toLowerCase()`) и название словаря (`this.dictType`). |
| `webvue3/src/components/SiteUsers/edit/SiteUserEdit.vue` | **3 места**: `${this.siteUserCurrent.email}` → `escapeHtml(...)`. |
| `webvue3/src/components/Songs/edit/SubsEdit.vue` | **2 места**: `${selectedText.toLowerCase()}` → `escapeHtml(...)`. |
| `webvue3/src/components/Common/SmartCopy/SmartCopyModal.vue` | **2 места**: шаблон переименования и путь копирования. |
| `webvue3/src/components/Pictures/edit/PictureEdit.vue` | **1 место**: `${this.pictureCurrent.name}`. |
| `webvue3/src/components/Sync/SyncTable.vue` | **1 место** (отчёт синхронизации): `r.displayName` и `r.error` экранируются при построении строк отчёта. |
| 13 файлов с `customConfirmParams` | **`allowHtml: true`** в 95 блоках, где в `header`/`body` есть статичная разметка. Блоки без разметки (42) остались в текстовом режиме — их вывод не изменился. |
| `tools/check-confirm-escape.py` (NEW) | Guard FR-014: **R1** (компонент не принимает сырую разметку по умолчанию), **R2** (`allowHtml: true` ⇔ в тексте есть разметка), **R3** (пользовательские значения экранированы). Умеет разворачивать промежуточные локальные переменные (`scope`, `body`, `lines`). |
| `config/frontend/confirm-safe-expressions.txt` (NEW) | Явный список выражений, не являющихся пользовательскими данными (счётчики, id, закрытые перечисления, справочные подписи) — с причиной по каждой строке. Всё, чего в списке нет, считается пользовательскими данными (deny-by-default). |
| `tools/check-confirm-escape-fixtures.sh` (NEW) + `tools/fixtures/confirm-escape/*` (NEW) | «До/после» для самого guard'а: на уязвимом снимке кода guard обязан упасть, на исправленном — пройти. |
| `webvue3/src/utils/__tests__/escapeHtml.test.js` (NEW) | Регресс-контроль по образцу спеки #215: baseline совпадает, 0 двойных экранирований. |
| `webvue3/package.json` | Новый скрипт `test:unit` (`node --test "src/utils/__tests__/*.test.js"`) — unit-тесты админки впервые включаются в прогон проверок проекта. |
| `.pre-commit-config.yaml` | Хуки `confirm-escape-guard` (BR-123) и `webvue3-unit-tests`. |
| `.github/workflows/lint.yml` | Job `confirm-escape-guard` (guard + его «до/после») и job `webvue3-unit-tests`. |
| `webvue3/.eslintrc.cjs` | Правило `vue/no-v-html` **остаётся выключенным** (FR-015); исправлено только фактически неверное обоснование в комментарии — теперь там зафиксировано, что 43 места закрыты, защиту обеспечивает guard, а не линтер. |
| `docs/features/escape-confirm-user-data.md` (NEW) | Per-feature документ (FR-009). |
| `knowledge/system/frontend/confirm-escape.md` (NEW) | Знание о правиле, инвариантах и проверке; `knowledge/system/README.md` и `knowledge/domains/README.md` — реестры; `knowledge/domains/integration/components/song-public-dto.md` — правило «экранирование на входе» распространено на текст подтверждений. |
| `specs/558-escape-song-name-confirm/spec.md` | Уточнения по итогам перепроверки (см. ниже). |

**Не менялось**: публичный сайт (`karaoke-public` — `v-html` только к серверно-экранированным
`formattedText*`), Thymeleaf-шаблоны (`th:utext` — 0 вхождений), бэкенд
(`karaoke-app`/`karaoke-web`), сами данные в базе (включая уже испорченные — правка
носит характер отображения), набор подтверждений, их заголовки, кнопки, таймауты
и действия по кнопке «Да».

## Реальный охват мест (перепроверка по коду)

Инвентарь спеки перепроверен полным перебором: разобраны **все** блоки
`customConfirmParams` в `webvue3` — **137 блоков в 18 файлах** (собственный сканер +
построчное чтение кода).

| Семейство значений | Спека #558 | Перепроверка | Итог |
|---|---|---|---|
| Имя песни (`SongEdit.vue`) | 25 | 25 | совпало |
| Имя автора (`HomeView.vue`) | 5 | 5 | совпало |
| Слово и словарь (`HomeView.vue`) | 4 | 4 | совпало |
| e-mail сайта (`SiteUserEdit.vue`) | 3 | 3 | совпало |
| Выделенный текст песни (`SubsEdit.vue`) | 2 | 2 | совпало |
| Параметры Smart Copy (`SmartCopyModal.vue`) | 2 | 2 | совпало |
| Имя картинки (`PictureEdit.vue`) | 1 | 1 | совпало |
| Отчёт синхронизации (`SyncTable.vue`) | 1 | 1 | совпало |
| **Итого с пользовательскими данными** | **43** | **43** | **совпало** |
| Без пользовательских данных (исключены) | 32 | 32 | совпало |

Расхождений, меняющих **состав охвата**, не найдено. Единственное расхождение —
арифметика в тексте спеки: указано «75 точек интерполяции». Фактически 75 — это
**мест сборки текста подтверждения** (43 + 32), а отдельных подставляемых выражений
(интерполяций) — **77**: в пяти местах их по две (`SongEdit.vue:4322` — `version` +
`songName`; `HomeView.vue:831, 848, 857, 874` — слово + словарь; `SyncTable.vue:227` —
`displayName` + направление). Формулировка спеки исправлена в этой ветке, раздел
«Уточнения по итогам перепроверки».

Три утверждения исходного перебора подтверждены: `SongEdit.vue:4321` — заголовок с
версией рендера (`version` ∈ {`LYRICS`, `KARAOKE`, `DEMO`}, значение не из данных);
`HomeView.vue:641` — вложенная подстановка `scope` (сама содержит `this.author`);
`SmartCopyModal.vue:250, 252` — тело собирается в переменную `body` до присваивания
параметров.

## Проверки

| Проверка | Результат |
|---|---|
| `python3 tools/check-confirm-escape.py` (на коде после правки) | **PASS** — 137 блоков, 95 в HTML-режиме, 0 нарушений |
| `python3 tools/check-confirm-escape.py --src <снимок master>` (на коде **до** правки) | **FAIL (ожидаемо)** — exit 1: R1 ×4, R2 ×95, **R3 ×49** = все 43 места (46 интерполяций) |
| `bash tools/check-confirm-escape-fixtures.sh` | **PASS** — уязвимая фикстура отклонена, исправленная принята, репозиторий чист |
| `cd webvue3 && npm run lint` | **PASS** — 0 ошибок, 0 предупреждений |
| `cd webvue3 && npm run lint:check` (`--max-warnings 0`) | **PASS** |
| `cd webvue3 && npm run build` | **PASS** — сборка успешна |
| `cd webvue3 && npm run format:check` | **PASS** — все файлы в стиле Prettier |
| `cd webvue3 && npm run test:unit` | **PASS** — 39 тестов, 0 падений (было 33; +6 в `escapeHtml.test.js`) |
| `cd karaoke-public && npm run lint` | **PASS** — публичный SPA не затронут |
| `bash tools/check-knowledge-structure.sh` | **PASS** — 9/9 |
| `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | **PASS** — «All documentation passes architectural style and structural checks» |
| `bash tools/check-knowledge-cross-links.sh` | **PASS** — 680 ссылок валидны |
| `python3 tools/check-doc-references.py` | **PASS** — 4042 ссылки, 2212 путей, 304 эндпоинта, 0 проблем |
| `bash tools/check-feature-doc.sh docs/features/*.md` | **PASS** — все документы валидны |
| `bash tools/check-subagent-isolation.sh` | **PASS** |
| `bash tools/check-frontend-build.sh` | **PASS** (R-375) |
| `pre-commit run --all-files` | **PASS** — все хуки зелёные |
| `grep -rn "th:utext" --include=*.html .` | **0** вхождений (вне охвата, зафиксировано) |
| `grep -rn "v-html" karaoke-public/src/` | 4 вхождения — только `formattedText*` (вне охвата, зафиксировано) |

Бэкенд не затронут — gradle-проверки не требовались (ни один файл `karaoke-app`/
`karaoke-web` не изменялся).

## Ключевые решения

Полные решения — в [`research.md`](research.md) (D1…D12). Кратко:

- **D1/D2/D3** — текстовый режим по умолчанию с флагом `params.allowHtml`; `v-text`
  против `v-html` под `v-if`/`v-else`.
- **D4** — инвариант `allowHtml: true` ⇔ в `header`/`body` есть статичная разметка:
  и забытый флаг, и лишний флаг одинаково ловятся проверкой.
- **D5** — экранирование **на входе** в HTML-буфер (правило проекта, прецедент #215),
  а не «на выходе»: экран готового HTML ломает статичную разметку.
- **D6** — канонический модуль `webvue3/src/utils/escapeHtml.js`; `highlightWords.js`
  реэкспортирует его (обратная совместимость).
- **D7** — явный safe-list вместо эвристики: deny-by-default ловит и новые источники
  пользовательских данных.
- **D8/D9** — guard + pre-commit + CI; регресс-контроль по образцу спеки #215
  («выдача совпадает с базовой линией» + «0 двойных экранирований»).

## Что осталось за владельцем

1. **Ревью и merge.** Правка не задеплоена, PR не открыт, push не делался (прямой
   запрет в задании). Ветка готова к ревью: `562-impl-xss-escape`.
2. **Шаги OpenProject-workflow после merge** (AGENTS.md § OpenProject):
   `tracker.sh add-comment 360 --file specs/562-impl-xss-escape/report.md` →
   `tracker.sh mark-review 360` → закрытие после ревью владельцем. Claim был
   выполнен ранее (2026-10-24, статус `In progress`).
3. **Ручная проверка сценариев на стенде** (SC-006, SC-007): 43 подтверждения с
   «вредным» именем песни / e-mail / словом — что на экране текст, посторонних
   обращений нет; и 5 обычных операций (удаление песни, создание картинки, бан,
   удаление картинки, синхронизация) — оформление не изменилось.
4. **Наблюдение после развёртывания**: SC-003 (0 новых мест за 30 суток — контроль
   автоматической проверкой + ревью) и SC-005 (0 инцидентов за 7 суток).
5. **Отдельные задачи (вне этой)**:
   - серверная валидация e-mail при публичной регистрации (решение владельца);
   - возврат правила линтера `vue/no-v-html` (14 мест в проекте);
   - **BR-312** — метки маркеров редактора (`ReviewModal.vue:98`,
     `SongKaraokeEditorView.vue:317`, `karaoke-public`).
6. **Решение по `vue/no-v-html`** (FR-015): правило **остаётся выключенным**;
   исправлено только неверное обоснование в комментарии. Защиту обеспечивает
   `tools/check-confirm-escape.py`. Если владелец решит вернуть правило — это
   отдельная задача (часть мест потребует перехода на санитайзер).

## Коммиты

Ветка `562-impl-xss-escape` (база `master` @ `cf97b82f`):

| SHA | Сообщение |
|---|---|
| `ee44d18a` | `spec 562: решения и задачи по экранированию данных в подтверждениях [tracker-claim-360]` |
| `35dea4c3` | `fix(security): экранирование пользовательских данных в подтверждениях админки (BR-123)` |

Коммит с этим отчётом — третий в ветке; его SHA указывается в
`tracker.sh add-comment 360`.
