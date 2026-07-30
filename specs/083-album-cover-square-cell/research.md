# Research: Альбомы — квадратная ячейка обложки альбома

**Phase 0 output for**: `083-album-cover-square-cell`
**Date**: 2026-07-29
**Spec**: [spec.md](./spec.md)

## Вопросы для research

Из Technical Context и спек вытекают 4 вопроса. Каждый завершается Decision /
Rationale / Alternatives considered.

---

### Q1. Какая фактическая высота строки таблицы в `AlbumsTable.vue`?

**Decision**: 54px (определяется ячейкой `.fld-picture-preview { height: 54px }`).

**Rationale**:
- В `AlbumsTable.vue:724` (style-блок) у `.fld-picture-preview` явно задано
  `height: 54px`. Это самая высокая ячейка в строке (остальные — однострочный
  текст или `form-select-sm` ~30px).
- Bootstrap `small` для `BTable` уменьшает дефолтную высоту строки до ~30px,
  но `.fld-picture-preview` со своим `height: 54px` всё равно доминирует.
- Картинка внутри — `.preview-image { height: 50px }`, оставляя 2px паддинга
  сверху и 2px снизу (визуально — небольшой «кожух» вокруг изображения).
- Проверено в `git log -p` для `AlbumsTable.vue`: значение 54px введено вместе
  с появлением preview-колонок, не менялось с момента коммита. A-001 в
  спеке подтверждает.

**Alternatives considered**:
- Измерить через DevTools — невозможно из CLI. Положились на статичный
  CSS-блок.
- Использовать `line-height` Bootstrap small — отвергнуто, не относится
  к ячейке с фиксированной высотой.

---

### Q2. Как сделать колонку квадратной (54×54)?

**Decision**: установить `minWidth: '54px'` и `maxWidth: '54px'` в `style`
поля `albumPicture` массива `albumDigestFields`. Параллельно обновить CSS
внутри ячейки, чтобы картинка/плейсхолдер занимал квадрат 50×50 с
центрированием.

**Rationale**:
- `BTable` в bootstrap-vue-next применяет `field.style` к `<col>` через
  `table-colgroup` слот (см. `AlbumsTable.vue:44-46`). Это надёжный
  способ задать ширину колонки на уровне colgroup, а не наследовать от
  контента ячейки.
- Значение 54px (а не 50px) выбрано сознательно: соответствует текущей
  высоте строки; уменьшение до 50px привело бы к тому, что картинка
  50×50 с `object-fit: contain` «упиралась» бы в границы ячейки без
  зазора — 4px по бокам сверху/снизу служат визуальным «кожухом»
  (как было раньше, но для квадрата).
- Внутри ячейки:
  - `.fld-picture-preview` оставляем `height: 54px`, убираем
    `max-width: 125px` (он мешает сузить до 54px — colgroup принудительно
    сделает 54px, но `max-width: 125px` бесполезен и провоцирует
    рассинхрон при будущих правках).
  - `.preview-image` меняем с `width: auto; height: 50px` на
    `max-width: 50px; max-height: 50px; width: auto; height: auto;
    object-fit: contain` — это сохраняет пропорции, центрирует
    изображение в квадрате 50×50 (или меньше, если картинка меньше).
  - `.no-image-placeholder` остаётся как есть — он и так вложен в
    квадрат 54×54, текст центрируется через `display: flex;
    align-items: center; justify-content: center` родителя.

**Alternatives considered**:
- Поменять только CSS `.fld-picture-preview { width: 54px }` без правки
  `style` поля — отвергнуто: ширина колонки тогда останется 125px (colgroup
  определит её по `minWidth/maxWidth` поля), а внутри ячейки появится
  «лишнее» пустое пространство 71px. Квадрата не получится.
- Использовать CSS-переменную `--row-height` в `<style>` —
  избыточно для одного места, добавляет indirection без выгоды.
- Заменить `BTable` на grid-layout — отвергнуто, выходит за scope
  (FR-004: правки только в `AlbumsTable.vue`).

---

### Q3. Нужно ли трогать колонку `(автор)` (authorPicture)?

**Decision**: НЕТ. См. A-002 в спеке.

**Rationale**:
- Запрос пользователя — только про колонку `(альбом)`. См. спек US2/SC-004
  и FR-004: «Правки ДОЛЖНЫ быть ограничены компонентом `AlbumsTable.vue`
  (и, при необходимости, связанными CSS-правилами в этом же файле)».
- Колонка `(автор)` остаётся 125×54 (прямоугольник) — это согласуется с
  текущим визуальным дизайном и с P2 в спеке.
- Если в будущем пользователь попросит квадратить и автора — это будет
  отдельная фича с собственной спекой.

**Alternatives considered**:
- Сделать обе колонки квадратными «заодно» — отвергнуто, не входит в scope
  и не было запрошено.

---

### Q4. Нужно ли менять что-то в публичной части (`karaoke-public`)?

**Decision**: НЕТ. См. FR-004 в спеке.

**Rationale**:
- Запрос касается админки (`webvue3`, раздел «Альбомы»). Публичный сайт
  не использует компонент `AlbumsTable.vue` (он экспортируется только в
  admin-роутинге).
- `karaoke-public` использует другие компоненты для отображения
  обложек альбомов в Закромах и т.п. (например, `AlbumCover.vue` или
  инлайн `<img>` в `ZakromaView.vue`). Эти компоненты не подпадают под
  данный запрос.
- Скрин админки не должен влиять на публичный сайт (Principle V
  Конституции: «Смешивание ответственностей между admin и public
  ЗАПРЕЩЕНО»).

**Alternatives considered**: нет — `AlbumsTable.vue` не импортируется в
`karaoke-public` (проверено через `grep -r "AlbumsTable" karaoke-public/` —
0 совпадений, кроме самого `AlbumsTable.vue` в `webvue3/`).

---

## Сводка изменений

| Файл | Тип правки | LOC |
|------|------------|-----|
| `webvue3/src/components/Albums/AlbumsTable.vue` | Изменить `style` поля `albumPicture` (125→54px) + подправить CSS `.fld-picture-preview` (убрать `max-width: 125px`) + `.preview-image` (добавить `max-width: 50px; max-height: 50px; width: auto; height: auto;`) | ~4 строки |
| **ИТОГО** | **1 файл** | **~4 строки** |

Никаких новых файлов, никаких изменений в бэкенде, никаких изменений
`recordhash`-триггеров, никаких новых эндпоинтов, никаких изменений в
других компонентах. Фича — чисто визуальная, локальная.

## Открытые вопросы

Нет. Все 4 вопроса resolved.

## Cross-references

- `specs/014-album-cell-album-cover-modal/` — клик по preview-ячейке
  открывает `AlbumCoverModal`; клик-логика сохраняется без изменений (A-003).
- `webvue3/src/components/Authors/AuthorsTable.vue:55-67, 716-743` —
  аналогичный preview-блок для колонки автора; для понимания паттерна
  preview-ячейки, но в нашей фиче НЕ правится (Q3).
- `AGENTS.md` §«Сборка и запуск» — `cd webvue3 && npm run build` для
  проверки сборки после правки.
- `AGENTS.md` §«Как проверить, что CI пройдёт» — `npm run lint:check`
  в `webvue3` после правки.
- `constitution.md` §Principle V — admin vs public separation; наша фича
  не нарушает (только admin).
