# Plan: 562 — Реализация спеки #558 (экранирование пользовательских данных в подтверждениях админки)

**Спека-контракт**: [`specs/558-escape-song-name-confirm/spec.md`](../558-escape-song-name-confirm/spec.md)
**Research**: [`research.md`](research.md) · **Tasks**: [`tasks.md`](tasks.md) · **Quickstart**: [`quickstart.md`](quickstart.md)
**Ветка**: `562-impl-xss-escape` · **База**: `master` @ `cf97b82f` · **OpenProject**: #360

## Сводка решения

Два рубежа (решение владельца, «гибрид»):

1. **Экранирование на входе** — пользовательские значения экранируются в момент входа в
   HTML-буфер подтверждения (43 места, 8 семейств значений).
2. **Текстовый режим по умолчанию** — общий компонент `CustomConfirm.vue` выводит заголовок и
   текст как текст; сырая разметка принимается только при явном `allowHtml: true`.

Плюс **автоматическая проверка** (FR-014), которая падает на коде до правки и проходит после,
и включена в проверки проекта (pre-commit + CI).

## Архитектура

```
                    ┌──────────────────────────────────────────────┐
  вызывающий код →  │ customConfirmParams = {                      │
  (SongEdit.vue,    │   header: '…',                               │
   HomeView.vue, …) │   body: `…${escapeHtml(this.song.songName)}…`,│
                    │   allowHtml: true,   ← явное разрешение HTML │
                    │ }                                             │
                    └───────────────┬──────────────────────────────┘
                                    ▼
                    ┌──────────────────────────────────────────────┐
                    │ CustomConfirm.vue                            │
                    │   v-if="params.allowHtml" → v-html            │
                    │   v-else               → v-text (textContent)│
                    └──────────────────────────────────────────────┘
```

- `escapeHtml` — `webvue3/src/utils/escapeHtml.js` (канонический модуль; `highlightWords.js`
  реэкспортирует его, обратная совместимость сохранена).
- `allowHtml` — единственный признак «здесь сознательно разрешена разметка».
- Инвариант: `allowHtml: true` ⇔ в `header`/`body` есть статичная разметка.

## Что меняется в коде

| Файл | Изменение |
| --- | --- |
| `webvue3/src/utils/escapeHtml.js` (NEW) | канонический `escapeHtml` |
| `webvue3/src/utils/highlightWords.js` | `escapeHtml` импортируется из нового модуля и реэкспортируется (поведение не меняется) |
| `webvue3/src/utils/__tests__/escapeHtml.test.js` (NEW) | «до/после»: baseline совпадает, 0 двойных экранирований |
| `webvue3/src/components/Common/CustomConfirm.vue` | текстовый режим по умолчанию + `allowHtml`; комментарий про «message должен быть подготовленным» исправлен |
| `webvue3/src/components/Songs/edit/SongEdit.vue` | 25 мест: `escapeHtml(this.song.songName)`; `allowHtml: true` |
| `webvue3/src/views/HomeView.vue` | 9 мест: имя автора (5), слово и словарь (4); `allowHtml: true` |
| `webvue3/src/components/SiteUsers/edit/SiteUserEdit.vue` | 3 места: `escapeHtml(this.siteUserCurrent.email)`; `allowHtml: true` |
| `webvue3/src/components/Songs/edit/SubsEdit.vue` | 2 места: `escapeHtml(selectedText.toLowerCase())`; `allowHtml: true` |
| `webvue3/src/components/Common/SmartCopy/SmartCopyModal.vue` | 2 места: шаблон переименования и путь копирования; `allowHtml: true` |
| `webvue3/src/components/Pictures/edit/PictureEdit.vue` | 1 место: `escapeHtml(this.pictureCurrent.name)`; `allowHtml: true` |
| `webvue3/src/components/Sync/SyncTable.vue` | 1 место (отчёт синхронизации): `escapeHtml` для `r.displayName` и `r.error`; `allowHtml: true` |
| `webvue3/src/components/{Albums,Authors,Dictionaries}/*Table.vue`, `PropertiesTable.vue`, `ProcessesBulkUpdateModal.vue`, `SongsTable.vue` | только `allowHtml: true` (значения не пользовательские — FR-012) |
| `webvue3/.eslintrc.cjs` | только комментарий: правило `vue/no-v-html` остаётся выключенным (FR-015), обоснование приведено в факт |
| `tools/check-confirm-escape.py` (NEW) | guard FR-014: R1 (компонент), R2 (`allowHtml` ⇔ разметка), R3 (экранирование) |
| `config/frontend/confirm-safe-expressions.txt` (NEW) | явный список выражений, не являющихся пользовательскими данными |
| `tools/check-confirm-escape-fixtures.sh` (NEW) + `tools/fixtures/confirm-escape/*` (NEW) | «до/после» для самого guard'а: уязвимая фикстура → FAIL, исправленная → PASS |
| `.pre-commit-config.yaml` | хук `confirm-escape-guard` |
| `.github/workflows/lint.yml` | job `confirm-escape-guard` |
| `docs/features/escape-confirm-user-data.md` (NEW) | per-feature документ (FR-009) |
| `knowledge/system/frontend/*.md`, `knowledge/domains/*` | правило проекта «экранирование на входе» + новый компонент `confirm-escape`; решение по `vue/no-v-html` |

## Стратегия тестирования

| Уровень | Что проверяет | Где |
| --- | --- | --- |
| unit | `escapeHtml`: baseline совпадает, одна `&amp;`, `null` → `''` | `webvue3/src/utils/__tests__/escapeHtml.test.js` |
| guard «до/после» | guard падает на уязвимом коде и проходит на исправленном | `tools/check-confirm-escape-fixtures.sh` |
| guard на репозитории | 0 нарушений R1/R2/R3 во всех 137 блоках | `tools/check-confirm-escape.py` |
| регресс проекта | линт, сборка, формат, knowledge-гейты, pre-commit | список в `tasks.md` (Phase 6) |

## Риски и смягчения

| Риск | Смягчение |
| --- | --- |
| забыт `allowHtml: true` → пропало оформление | guard R2 (падает) |
| лишний `allowHtml: true` → лишняя доверенная поверхность | guard R2 (падает) |
| забыто `escapeHtml` → XSS | guard R3 (падает) |
| двойное экранирование (`&amp;amp;`) | unit-тест + отсутствие экранирования на выходе в компоненте |
| изменение отображения не- пользовательских значений | 32 места вне охвата (FR-012), safe-list, проверка вручную |
| `vue/no-v-html` выключен → новая дыра | текстовый режим по умолчанию (D1) + guard R1 |

## Вне охвата

BR-312 (метки маркеров редактора), серверная валидация e-mail при публичной регистрации,
возврат правила `vue/no-v-html`, публичный сайт, Thymeleaf, бэкенд.
