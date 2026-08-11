# UI-контракт: `COMPARISON_ROWS` массив в `PremiumView.vue`

> Этот документ фиксирует UI-контракт: **как массив `COMPARISON_ROWS` должен
> выглядеть**, чтобы рендер на `/premium` работал корректно. Не API-контракт
> (никаких новых сетевых эндпоинтов не появляется).

---

## 1. Расположение

`karaoke-public/src/views/PremiumView.vue:204-220` — inline-объявление
`const COMPARISON_ROWS = [...]`, **выше** `export default { ... }`.

Используется внутри компонента:

```js
data() {
  return {
    // ...
    comparisonRows: COMPARISON_ROWS,
  }
},
```

и рендерится в шаблоне:

```html
<tbody>
  <tr v-for="row in comparisonRows" :key="row.feature">
    <td class="km-compare-feature-col" data-label="Фича">{{ row.feature }}</td>
    <td data-label="FREE">
      <span v-if="typeof row.free === 'boolean'" :aria-label="row.free ? 'есть' : 'нет'">
        {{ row.free ? '✅' : '❌' }}
      </span>
      <span v-else>{{ row.free }}</span>
    </td>
    <td class="km-compare-premium-col" data-label="PREMIUM">
      <span v-if="typeof row.premium === 'boolean'" :aria-label="row.premium ? 'есть' : 'нет'">
        {{ row.premium ? '✅' : '❌' }}
      </span>
      <span v-else>{{ row.premium }}</span>
    </td>
  </tr>
</tbody>
```

---

## 2. Формат элемента

```ts
interface ComparisonRow {
  feature: string;             // Заголовок строки (1-я колонка).
  free: boolean | string;      // Значение FREE (2-я колонка).
  premium: boolean | string;   // Значение PREMIUM (3-я колонка).
}
```

### 2.1. Значение `boolean`

- `true` рендерится как `✅` (U+2705).
- `false` рендерится как `❌` (U+274C).
- Обе формы дополняются `aria-label` («есть» / «нет») — соответствует
  NFR-003 (WCAG AA, не только цвет).

### 2.2. Значение `string`

- Любая непустая строка рендерится буквально.
- Примеры в существующей таблице:
  - `'до 100'` (FREE favorites)
  - `'до 500'` (PREMIUM favorites)
  - `'1 (избранное)'` (FREE playlists)
  - `'до 50'` (PREMIUM playlists)
- Для share-link в этой реализации строка НЕ используется (значения `boolean`).

---

## 3. Полный пример (после PR 169)

```js
const COMPARISON_ROWS = [
  { feature: 'Онлайн-плеер для песен «в эфире»', free: true, premium: true },
  { feature: 'Поиск и каталог', free: true, premium: true },
  { feature: 'Демо-фрагменты эксклюзивных', free: true, premium: true },
  { feature: 'Полный доступ к плееру (все песни)', free: false, premium: true },
  { feature: 'Избранное', free: 'до 100', premium: 'до 500' },
  { feature: 'Свои плейлисты', free: '1 (избранное)', premium: 'до 50' },
  {
    feature: 'Непрерывное воспроизведение, повтор, случайный порядок',
    free: false,
    premium: true,
  },
  { feature: 'Смена скорости воспроизведения в плеере', free: false, premium: true },
  { feature: 'Смена тональности (транспонирование) в плеере', free: false, premium: true },
  { feature: 'Создание минусовок (Demucs)', free: false, premium: true },
  { feature: 'Чат с автором проекта', free: false, premium: true },
  { feature: 'Временная ссылка на песню', free: false, premium: true }, // ← НОВАЯ
]
```

---

## 4. Инварианты

### 4.1. Длина `feature`

- **≤ 60 символов** (визуально умещается на мобильном 360px, см.
  `PremiumView.vue:437-470` для адаптивной раскладки).
- Текущий максимум в существующих строках: 47 символов
  («Смена скорости воспроизведения в плеере»). Новая строка
  «Временная ссылка на песню» — 26 символов, укладывается с запасом.

### 4.2. Семантика FREE vs PREMIUM

- **`free` ≤ `premium` концептуально**: FREE — это «ограничение», PREMIUM — это
  «полный доступ». Фича не отбирается при переходе на премиум, только
  расширяется. Новая строка удовлетворяет: FREE `false` ⊂ PREMIUM `true`.
- Сейчас в таблице 3 строки с `free: true, premium: true` (фичи доступны всем).
  8 строк с `free: false, premium: true` (только премиум). Новая 12-я строка
  попадает во вторую категорию.

### 4.3. Порядок строк

- **Append-only**: новая строка добавляется в **конец** массива (после
  существующих 11), чтобы не сбивать нумерацию и согласование с
  `005-free-vs-premium/spec.md FR-003` (см. `169-spec.md/FR-006`).
- Перестановка строк — **отдельная фича**, не входит в 169.

### 4.4. Уникальность

- `:key="row.feature"` — Vue требует уникальный `key` в `v-for`. Каждое
  `feature` должно быть уникальным. Уже соблюдается (11 уникальных строк),
  новая 12-я укладывается.

---

## 5. Расширение фразы «Что вы получили» (для премиум-юзера)

Помимо массива, расширяется **отдельная фраза** в premium-блоке (для
пользователей с `user.effectivePremium = true`). Это **не часть массива**
`COMPARISON_ROWS` и рендерится напрямую в HTML.

Точные правки — в [`../data-model.md`](../data-model.md#3-премиум-блок-что-вы-получили-расширение-фразы).

---

## 6. Линтер / стиль

- `cd karaoke-public && npm run lint:check` — обязательно к CI.
- Стиль: одинарные кавычки (`'feature'`), trailing comma у многострочных объектов
  (как в существующих 9-11 строках). См. `CONTRIBUTING.md`.

---

## 7. Совместимость

- **Обратная совместимость**: ✅ — массив редактируется только **в сторону
  увеличения**, существующие 11 строк не трогаются, их `feature` строки
  не изменяются. Никакие другие компоненты от массива не зависят
  (лендинг таблицы — изолированный view).
- **На проде**: после PR 169 пользователь увидит 12 строк вместо 11.
  Никаких действий не требуется — таблица inline, перезагрузки страницы
  достаточно.

---

## 8. Cross-references

- `PremiumView.vue:204-220` — массив.
- `PremiumView.vue:23-51` — рендер таблицы.
- `PremiumView.vue:56-65` — premium-блок «Что вы получили».
- `005-free-vs-premium/spec.md` — родительская спека, у которой
  было 11 строк.
- `169-research.md` — Decision 2 («только ✅», не строка).
